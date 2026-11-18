package com.is.clientaccountapi.service;

import com.is.clientaccountapi.component.CheckRequest;
import com.is.clientaccountapi.model.account.AccountInfo;
import com.is.clientaccountapi.model.account.AccountRequest;
import com.is.clientaccountapi.model.client.ClientInfo;
import com.is.clientaccountapi.model.result.ApiResponse;
import com.is.clientaccountapi.model.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.is.clientaccountapi.model.account.DepositRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import java.sql.Date;
import java.sql.ResultSet;                              // TO'G'RILANDI: qo'shildi
import java.sql.SQLException;                           // TO'G'RILANDI: qo'shildi
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;     // TO'G'RILANDI: qo'shildi
import com.is.clientaccountapi.model.account.TransferRequest;
import com.is.clientaccountapi.model.account.TransferResult;
import com.is.clientaccountapi.model.account.OperationInfo;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final JdbcTemplate jdbcTemplate;
    private final CheckRequest checkRequest;
    private final ClientService clientService;

    private static final List<String> CURRENCIES = Arrays.asList("860", "840", "978", "643");

    // TO'G'RILANDI: qaytarildi. getClientAccounts va getAccount shu SQL'ni ishlatadi.
    private static final String ACCOUNT_SELECT = "select a.id, \n" +
            "       a.client_id, \n" +
            "       a.account_number, \n" +
            "       a.balance_type, \n" +
            "       a.currency, \n" +
            "       a.balance, \n" +
            "       a.state, \n" +
            "       a.date_open, \n" +
            "       a.date_close \n" +
            "  from cm_account a \n";


    // PUT /api/v1/accounts/{id}/close - Hisobni yopish
    public ApiResponse<AccountInfo> closeAccount(Long id) {

        // 1. ID tekshirish
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.INVALID_PARAM, Collections.singletonList("id"));
        }

        try {
            // 2. Hisob bormi
            AccountInfo account = getAccount(id);
            if (account == null) {
                return ApiResponse.error(ErrorCode.ACC_NOT_FOUND, Collections.singletonList("id"));
            }

            // 3. Allaqachon yopilganmi (3 = yopiq)
            if (account.getState() == 3) {
                return ApiResponse.error(ErrorCode.ACC_ALREADY_CLOSED, Collections.singletonList("id"));
            }

            // 4. Qoldiq 0 bo'lishi kerak
            if (account.getBalance() != 0) {
                return ApiResponse.error(ErrorCode.ACC_HAS_BALANCE, Collections.singletonList("balance"));
            }

            // 5. Yopish
            jdbcTemplate.update("update cm_account a \n" +
                    "   set a.state = 3, \n" +
                    "       a.date_close = sysdate \n" +
                    " where a.id = ?", id);

            log.info("Account closed: id: {}, number: {}", id, account.getAccountNumber());

            // 6. Yangilangan hisobni qaytarish
            return ApiResponse.ok(getAccount(id), 200);
        } catch (Exception e) {
            return checkRequest.dbError(e);
        }
    }


    // POST /api/v1/accounts/{id}/deposit - Hisobni to'ldirish
    @Transactional
    public ApiResponse<AccountInfo> deposit(Long id, DepositRequest request) {

        // 1. Maydonlarni tekshirish
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.INVALID_PARAM, Collections.singletonList("id"));
        }
        if (request.getAmount() == null) {
            return ApiResponse.error(ErrorCode.EMPTY_PARAM, Collections.singletonList("amount"));
        }
        if (request.getAmount() <= 0) {
            return ApiResponse.error(ErrorCode.INVALID_PARAM, Collections.singletonList("amount"));
        }

        try {
            // 2. Hisob bormi
            AccountInfo account = getAccount(id);
            if (account == null) {
                return ApiResponse.error(ErrorCode.ACC_NOT_FOUND, Collections.singletonList("id"));
            }

            // 3. Hisob ochiqmi (2 = ochiq)
            if (account.getState() != 2) {
                return ApiResponse.error(ErrorCode.ACC_CLOSED, Collections.singletonList("id"));
            }

            // 4. Qoldiqni oshirish
            jdbcTemplate.update("update cm_account a \n" +
                    "   set a.balance = a.balance + ? \n" +
                    " where a.id = ?", request.getAmount(), id);

            // 5. Operatsiyani tarixga yozish
            jdbcTemplate.update("insert into cm_operation \n" +
                            "  (id, account_id, amount, purpose) \n" +
                            "values (cm_operation_seq.nextval, ?, ?, ?)",
                    id, request.getAmount(), request.getPurpose());

            log.info("Deposit: accountId: {}, amount: {}", id, request.getAmount());

            // 6. Yangilangan hisobni qaytarish
            return ApiResponse.ok(getAccount(id), 200);
        } catch (Exception e) {
            // Xato bo'lsa, 4- va 5-qadamlarni bekor qilish
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return checkRequest.dbError(e);
        }
    }


    // POST /api/v1/accounts/transfer - Hisobdan hisobga o'tkazish
    @Transactional
    public ApiResponse<TransferResult> transfer(TransferRequest request) {

        // 1. Majburiy maydonlar
        List<String> fields = new ArrayList<>();
        if (request.getFromAccountId() == null) {
            fields.add("fromAccountId");
        }
        if (request.getToAccountId() == null) {
            fields.add("toAccountId");
        }
        if (request.getAmount() == null) {
            fields.add("amount");
        }
        if (!fields.isEmpty()) {
            return ApiResponse.error(ErrorCode.EMPTY_PARAM, fields);
        }

        // 2. Qiymatlar
        if (request.getAmount() <= 0) {
            fields.add("amount");
        }
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            fields.add("toAccountId");   // o'ziga o'zi o'tkazib bo'lmaydi
        }
        if (!fields.isEmpty()) {
            return ApiResponse.error(ErrorCode.INVALID_PARAM, fields);
        }

        try {
            // 3. Ikkala hisob bormi
            AccountInfo from = getAccount(request.getFromAccountId());
            AccountInfo to = getAccount(request.getToAccountId());
            if (from == null) {
                return ApiResponse.error(ErrorCode.ACC_NOT_FOUND, Collections.singletonList("fromAccountId"));
            }
            if (to == null) {
                return ApiResponse.error(ErrorCode.ACC_NOT_FOUND, Collections.singletonList("toAccountId"));
            }

            // 4. Ikkalasi ochiqmi
            if (from.getState() != 2) {
                return ApiResponse.error(ErrorCode.ACC_CLOSED, Collections.singletonList("fromAccountId"));
            }
            if (to.getState() != 2) {
                return ApiResponse.error(ErrorCode.ACC_CLOSED, Collections.singletonList("toAccountId"));
            }

            // 5. Valyuta bir xilmi
            if (!from.getCurrency().equals(to.getCurrency())) {
                return ApiResponse.error(ErrorCode.CURRENCY_MISMATCH, Arrays.asList("fromAccountId", "toAccountId"));
            }

            // 6. Jo'natuvchidan yechish - faqat pul yetarli bo'lsa
            int updated = jdbcTemplate.update("update cm_account a \n" +
                            "   set a.balance = a.balance - ? \n" +
                            " where a.id = ? \n" +
                            "   and a.balance >= ?",
                    request.getAmount(), request.getFromAccountId(), request.getAmount());
            if (updated == 0) {
                return ApiResponse.error(ErrorCode.INSUFFICIENT_FUNDS, Collections.singletonList("amount"));
            }

            // 7. Qabul qiluvchiga qo'shish
            jdbcTemplate.update("update cm_account a \n" +
                            "   set a.balance = a.balance + ? \n" +
                            " where a.id = ?",
                    request.getAmount(), request.getToAccountId());

            // 8. Tarixga ikkita yozuv: chiqim (minus) va kirim (plyus)
            String sql = "insert into cm_operation (id, account_id, amount, purpose) \n" +
                    "values (cm_operation_seq.nextval, ?, ?, ?)";
            jdbcTemplate.update(sql, request.getFromAccountId(), -request.getAmount(), request.getPurpose());
            jdbcTemplate.update(sql, request.getToAccountId(), request.getAmount(), request.getPurpose());

            log.info("Transfer: from: {}, to: {}, amount: {}",
                    request.getFromAccountId(), request.getToAccountId(), request.getAmount());

            // 9. O'tkazmadan keyingi holat
            TransferResult result = TransferResult.builder()
                    .amount(request.getAmount())
                    .fromAccount(getAccount(request.getFromAccountId()))
                    .toAccount(getAccount(request.getToAccountId()))
                    .build();

            return ApiResponse.ok(result, 200);
        } catch (Exception e) {
            // Xato bo'lsa, 6-8 qadamlarni bekor qilish
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return checkRequest.dbError(e);
        }
    }



    // GET /api/v1/accounts/{id}/operations - Operatsiyalar tarixi
    public ApiResponse<List<OperationInfo>> getOperations(Long id, String from, String to) {

        // 1. Parametrlarni tekshirish
        List<String> fields = new ArrayList<>();
        if (id == null || id <= 0) {
            fields.add("id");
        }
        if (!checkRequest.isEmpty(from) && !checkRequest.isDate(from)) {
            fields.add("from");
        }
        if (!checkRequest.isEmpty(to) && !checkRequest.isDate(to)) {
            fields.add("to");
        }
        if (!fields.isEmpty()) {
            return ApiResponse.error(ErrorCode.INVALID_PARAM, fields);
        }

        try {
            // 2. Hisob bormi
            if (getAccount(id) == null) {
                return ApiResponse.error(ErrorCode.ACC_NOT_FOUND, Collections.singletonList("id"));
            }

            // 3. SQL: sana filtrlari faqat berilgan bo'lsa qo'shiladi
            String sql = "select o.id, o.account_id, o.amount, o.purpose, o.created_at \n" +
                    "  from cm_operation o \n" +
                    " where o.account_id = ? \n";
            List<Object> args = new ArrayList<>();
            args.add(id);

            if (!checkRequest.isEmpty(from)) {
                sql += "   and o.created_at >= ? \n";
                args.add(Date.valueOf(from.trim()));
            }
            if (!checkRequest.isEmpty(to)) {
                sql += "   and o.created_at < ? + 1 \n";   // "to" kunining oxirigacha
                args.add(Date.valueOf(to.trim()));
            }
            sql += " order by o.id desc";

            // 4. Bazadan olish
            List<OperationInfo> operations = new ArrayList<>();
            jdbcTemplate.query(sql, (ResultSet rs) -> {
                operations.add(OperationInfo.builder()
                        .id(rs.getLong("id"))
                        .accountId(rs.getLong("account_id"))
                        .amount(rs.getLong("amount"))
                        .purpose(rs.getString("purpose"))
                        .createdAt(rs.getTimestamp("created_at").toLocalDateTime().toString())
                        .build());
            }, args.toArray());

            return ApiResponse.ok(operations, 200);
        } catch (Exception e) {
            return checkRequest.dbError(e);
        }
    }

    // POST /api/v1/clients/{clientId}/accounts - Mijozga hisob ochish
    public ApiResponse<AccountInfo> openAccount(Long clientId, AccountRequest request) {

        // 1. Majburiy maydonlarni tekshirish
        List<String> fields = new ArrayList<>();
        if (checkRequest.isEmpty(request.getBalanceType())) {
            fields.add("balanceType");
        }
        if (checkRequest.isEmpty(request.getCurrency())) {
            fields.add("currency");
        }
        if (!fields.isEmpty()) {
            return ApiResponse.error(ErrorCode.EMPTY_PARAM, fields);
        }

        // 2. Qiymatlarni tekshirish
        if (!request.getBalanceType().matches("\\d{5}")) {
            fields.add("balanceType");
        }
        if (!CURRENCIES.contains(request.getCurrency())) {
            fields.add("currency");
        }
        if (!fields.isEmpty()) {
            return ApiResponse.error(ErrorCode.INVALID_PARAM, fields);
        }

        try {
            // 3. Mijoz bormi va faolmi
            // TO'G'RILANDI: getActiveClient o'rniga getClient (ClientService'da shu metod bor)
            ClientInfo client = clientService.getClient(clientId);
            if (client == null || client.getState() == 0) {
                return ApiResponse.error(ErrorCode.CLIENT_NOT_FOUND, Collections.singletonList("clientId"));
            }

            // 4. Hisob raqamini yaratish
            String accountNumber = buildAccountNumber(clientId, request.getBalanceType(), request.getCurrency());

            // 5. Yangi ID olish
            Long id = jdbcTemplate.queryForObject("select cm_account_seq.nextval from dual", Long.class);

            // 6. Bazaga yozish
            jdbcTemplate.update("insert into cm_account \n" +
                            "  (id, client_id, account_number, balance_type, currency) \n" +
                            "values (?, ?, ?, ?, ?)",
                    id,
                    clientId,
                    accountNumber,
                    request.getBalanceType(),
                    request.getCurrency());

            log.info("Account opened: id: {}, number: {}", id, accountNumber);

            // 7. Yozilgan hisobni qaytarish
            return ApiResponse.ok(getAccount(id), 201);
        } catch (Exception e) {
            return checkRequest.dbError(e);
        }
    }


    // GET /api/v1/clients/{clientId}/accounts - Mijozning hisoblari
    public ApiResponse<List<AccountInfo>> getClientAccounts(Long clientId) {

        // 1. ID tekshirish
        if (clientId == null || clientId <= 0) {
            return ApiResponse.error(ErrorCode.INVALID_PARAM, Collections.singletonList("clientId"));
        }

        try {
            // 2. Mijoz bormi
            ClientInfo client = clientService.getClient(clientId);
            if (client == null || client.getState() == 0) {
                return ApiResponse.error(ErrorCode.CLIENT_NOT_FOUND, Collections.singletonList("clientId"));
            }

            // 3. Mijozning hamma hisoblarini olish
            List<AccountInfo> accounts = new ArrayList<>();
            jdbcTemplate.query(ACCOUNT_SELECT +
                    " where a.client_id = ? \n" +
                    " order by a.id", (ResultSet rs) -> {
                accounts.add(mapAccount(rs));
            }, clientId);

            return ApiResponse.ok(accounts, 200);
        } catch (Exception e) {
            return checkRequest.dbError(e);
        }
    }


    // ---------------- Yordamchi metodlar ----------------

    // 20 xonali hisob raqami: turi(5) + valyuta(3) + nazorat(1) + mijoz(8) + tartib(3)
    private String buildAccountNumber(Long clientId, String balanceType, String currency) {
        // Hisob raqamida so'm 000 bilan yoziladi
        String currencyCode = "860".equals(currency) ? "000" : currency;

        // Mijozning shu turdagi va shu valyutadagi nechanchi hisobi
        Integer count = jdbcTemplate.queryForObject("select count(*) \n" +
                "  from cm_account a \n" +
                " where a.client_id = ? \n" +
                "   and a.balance_type = ? \n" +
                "   and a.currency = ?", Integer.class, clientId, balanceType, currency);

        String order = String.format("%03d", (count == null ? 0 : count) + 1);
        String client = String.format("%08d", clientId);

        return balanceType + currencyCode + "0" + client + order;
    }

    // TO'G'RILANDI: qaytarildi. Hisobni ID bo'yicha olish (openAccount oxirida ishlatiladi)
    public AccountInfo getAccount(Long id) {
        AtomicReference<AccountInfo> account = new AtomicReference<>();

        jdbcTemplate.query(ACCOUNT_SELECT + " where a.id = ?", (ResultSet rs) -> {
            account.set(mapAccount(rs));
        }, id);

        return account.get();
    }

    // TO'G'RILANDI: qaytarildi. Bazadagi qatorni AccountInfo ga aylantirish
    private AccountInfo mapAccount(ResultSet rs) throws SQLException {
        Date dateOpen = rs.getDate("date_open");
        Date dateClose = rs.getDate("date_close");
        return AccountInfo.builder()
                .id(rs.getLong("id"))
                .clientId(rs.getLong("client_id"))
                .accountNumber(rs.getString("account_number"))
                .balanceType(rs.getString("balance_type"))
                .currency(rs.getString("currency"))
                .balance(rs.getLong("balance"))
                .state(rs.getInt("state"))
                .dateOpen(dateOpen == null ? null : dateOpen.toString())
                .dateClose(dateClose == null ? null : dateClose.toString())
                .build();
    }
}