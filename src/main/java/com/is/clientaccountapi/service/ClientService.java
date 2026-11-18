package com.is.clientaccountapi.service;

import com.is.clientaccountapi.component.CheckRequest;
import com.is.clientaccountapi.model.client.ClientInfo;
import com.is.clientaccountapi.model.client.ClientPage;
import com.is.clientaccountapi.model.client.ClientRequest;
import com.is.clientaccountapi.model.result.ApiResponse;
import com.is.clientaccountapi.model.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientService {

    private final JdbcTemplate jdbcTemplate;
    private final CheckRequest checkRequest;

    private static final String CLIENT_SELECT = "select c.id, \n" +
            "       c.pinfl, \n" +
            "       c.first_name, \n" +
            "       c.last_name, \n" +
            "       c.patronymic, \n" +
            "       c.birth_date, \n" +
            "       c.passport, \n" +
            "       c.phone, \n" +
            "       c.state, \n" +
            "       c.created_at \n" +
            "  from cm_client c \n";

    // Bazadagi qatorni ClientInfo ga aylantirish
    private static final RowMapper<ClientInfo> CLIENT_MAPPER = (rs, rowNum) -> {
        Date birthDate = rs.getDate("birth_date");
        return ClientInfo.builder()
                .id(rs.getLong("id"))
                .pinfl(rs.getString("pinfl"))
                .firstName(rs.getString("first_name"))
                .lastName(rs.getString("last_name"))
                .patronymic(rs.getString("patronymic"))
                .birthDate(birthDate == null ? null : birthDate.toString())
                .passport(rs.getString("passport"))
                .phone(rs.getString("phone"))
                .state(rs.getInt("state"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime().toString())
                .build();
    };

    // POST /api/v1/clients - Mijoz qo'shish
    public ApiResponse<ClientInfo> createClient(ClientRequest request) {

        // 1. Majburiy maydonlarni tekshirish
        List<String> fields = new ArrayList<>();
        if (checkRequest.isEmpty(request.getPinfl())) {
            fields.add("pinfl");
        }
        if (checkRequest.isEmpty(request.getFirstName())) {
            fields.add("firstName");
        }
        if (checkRequest.isEmpty(request.getLastName())) {
            fields.add("lastName");
        }
        if (!fields.isEmpty()) {
            return ApiResponse.error(ErrorCode.EMPTY_PARAM, fields);
        }

        // 2. Qiymatlarni tekshirish
        boolean hasBirthDate = !checkRequest.isEmpty(request.getBirthDate());
        if (!request.getPinfl().matches("\\d{14}")) {
            fields.add("pinfl");
        }
        if (hasBirthDate && !checkRequest.isDate(request.getBirthDate())) {
            fields.add("birthDate");
        }
        if (!fields.isEmpty()) {
            return ApiResponse.error(ErrorCode.INVALID_PARAM, fields);
        }

        try {
            // 3. Bu PINFL bilan mijoz bormi
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from cm_client c where c.pinfl = ?", Integer.class, request.getPinfl());
            if (count != null && count > 0) {
                return ApiResponse.error(ErrorCode.CLIENT_EXISTS, Collections.singletonList("pinfl"));
            }

            // 4. Yangi ID olish
            Long id = jdbcTemplate.queryForObject("select cm_client_seq.nextval from dual", Long.class);

            // 5. Bazaga yozish
            jdbcTemplate.update("insert into cm_client \n" +
                            "  (id, pinfl, first_name, last_name, patronymic, birth_date, passport, phone) \n" +
                            "values (?, ?, ?, ?, ?, ?, ?, ?)",
                    id,
                    request.getPinfl(),
                    request.getFirstName(),
                    request.getLastName(),
                    request.getPatronymic(),
                    hasBirthDate ? Date.valueOf(request.getBirthDate()) : null,
                    request.getPassport(),
                    request.getPhone());

            log.info("Client created: id: {}, pinfl: {}", id, request.getPinfl());

            // 6. Yozilgan mijozni bazadan o'qib qaytarish
            return ApiResponse.ok(getClient(id), 201);
        } catch (Exception e) {
            return checkRequest.dbError(e);
        }
    }

    // GET /api/v1/clients - Mijozlar ro'yxati (sahifalab, qidirish bilan)
    public ApiResponse<ClientPage> getClients(Integer page, Integer size, String search) {

        // 1. Parametrlarni tekshirish
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;

        List<String> fields = new ArrayList<>();
        if (p < 0) {
            fields.add("page");
        }
        if (s < 1 || s > 100) {
            fields.add("size");
        }
        if (!fields.isEmpty()) {
            return ApiResponse.error(ErrorCode.INVALID_PARAM, fields);
        }

        // 2. Shart: faqat faol mijozlar + qidiruv (berilgan bo'lsa)
        String where = " where c.state = 1 \n";
        List<Object> args = new ArrayList<>();

        if (!checkRequest.isEmpty(search)) {
            where += "   and (lower(c.first_name) like ? \n" +
                    "     or lower(c.last_name) like ? \n" +
                    "     or lower(c.patronymic) like ?) \n";
            String like = "%" + search.toLowerCase() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }

        try {
            // 3. Jami nechta mijoz bor
            Long total = jdbcTemplate.queryForObject(
                    "select count(*) from cm_client c " + where, Long.class, args.toArray());

            // 4. Faqat shu sahifadagi mijozlarni olish
            String sql = "select * from ( \n" +
                    "  select t.*, rownum rn from ( \n" +
                    CLIENT_SELECT + where +
                    "     order by c.id \n" +
                    "  ) t where rownum <= ? \n" +
                    ") where rn > ?";

            args.add((p + 1) * s);   // oxirgi qator
            args.add(p * s);         // birinchi qatordan oldingi

            List<ClientInfo> clients = jdbcTemplate.query(sql, CLIENT_MAPPER, args.toArray());

            // 5. Javobni yig'ish
            long totalCount = total == null ? 0 : total;
            ClientPage result = ClientPage.builder()
                    .content(clients)
                    .page(p)
                    .size(s)
                    .totalElements(totalCount)
                    .totalPages((int) ((totalCount + s - 1) / s))
                    .build();

            return ApiResponse.ok(result, 200);
        } catch (Exception e) {
            return checkRequest.dbError(e);
        }
    }

    // GET /api/v1/clients/{id} - Bitta mijoz
    public ApiResponse<ClientInfo> getClientById(Long id) {

        // 1. ID tekshirish
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.INVALID_PARAM, Collections.singletonList("id"));
        }

        try {
            // 2. Topilmasa yoki o'chirilgan bo'lsa - 404
            ClientInfo client = getActiveClient(id);
            if (client == null) {
                return ApiResponse.error(ErrorCode.CLIENT_NOT_FOUND, Collections.singletonList("id"));
            }

            return ApiResponse.ok(client, 200);
        } catch (Exception e) {
            return checkRequest.dbError(e);
        }
    }

    // PUT /api/v1/clients/{id} - Mijoz ma'lumotini o'zgartirish
    public ApiResponse<ClientInfo> updateClient(Long id, ClientRequest request) {

        // 1. ID tekshirish
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.INVALID_PARAM, Collections.singletonList("id"));
        }

        // 2. Majburiy maydonlarni tekshirish
        List<String> fields = new ArrayList<>();
        if (checkRequest.isEmpty(request.getFirstName())) {
            fields.add("firstName");
        }
        if (checkRequest.isEmpty(request.getLastName())) {
            fields.add("lastName");
        }
        if (!fields.isEmpty()) {
            return ApiResponse.error(ErrorCode.EMPTY_PARAM, fields);
        }

        // 3. Sanani tekshirish
        boolean hasBirthDate = !checkRequest.isEmpty(request.getBirthDate());
        if (hasBirthDate && !checkRequest.isDate(request.getBirthDate())) {
            return ApiResponse.error(ErrorCode.INVALID_PARAM, Collections.singletonList("birthDate"));
        }

        try {
            // 4. Mijoz bormi va faolmi
            if (getActiveClient(id) == null) {
                return ApiResponse.error(ErrorCode.CLIENT_NOT_FOUND, Collections.singletonList("id"));
            }

            // 5. Bazada yangilash (pinfl o'zgarmaydi)
            jdbcTemplate.update("update cm_client c \n" +
                            "   set c.first_name = ?, \n" +
                            "       c.last_name  = ?, \n" +
                            "       c.patronymic = ?, \n" +
                            "       c.birth_date = ?, \n" +
                            "       c.passport   = ?, \n" +
                            "       c.phone      = ?, \n" +
                            "       c.updated_at = sysdate \n" +
                            " where c.id = ?",
                    request.getFirstName(),
                    request.getLastName(),
                    request.getPatronymic(),
                    hasBirthDate ? Date.valueOf(request.getBirthDate()) : null,
                    request.getPassport(),
                    request.getPhone(),
                    id);

            log.info("Client updated: id: {}", id);

            // 6. Yangilangan mijozni qaytarish
            return ApiResponse.ok(getClient(id), 200);
        } catch (Exception e) {
            return checkRequest.dbError(e);
        }
    }

    // DELETE /api/v1/clients/{id} - Mijozni bazadan o'chirish
    public ApiResponse<Boolean> deleteClient(Long id) {

        // 1. ID tekshirish
        if (id == null || id <= 0) {
            return ApiResponse.error(ErrorCode.INVALID_PARAM, Collections.singletonList("id"));
        }

        try {
            // 2. Bazadan o'chirish
            int deleted = jdbcTemplate.update("delete from cm_client c where c.id = ?", id);

            // 3. Hech narsa o'chmagan bo'lsa - bunday mijoz yo'q
            if (deleted == 0) {
                return ApiResponse.error(ErrorCode.CLIENT_NOT_FOUND, Collections.singletonList("id"));
            }

            log.info("Client deleted: id: {}", id);

            return ApiResponse.ok(true, 200);
        } catch (Exception e) {
            return checkRequest.dbError(e);
        }
    }

    // Faol mijozni ID bo'yicha olish (topilmasa yoki o'chirilgan bo'lsa - null)
    public ClientInfo getActiveClient(Long id) {
        ClientInfo client = getClient(id);
        return client == null || client.getState() == 0 ? null : client;
    }

    // Mijozni ID bo'yicha olish (topilmasa - null)
    ClientInfo getClient(Long id) {
        List<ClientInfo> list = jdbcTemplate.query(CLIENT_SELECT + " where c.id = ?", CLIENT_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }
}
