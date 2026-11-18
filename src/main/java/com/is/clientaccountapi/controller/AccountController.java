package com.is.clientaccountapi.controller;

import com.is.clientaccountapi.model.account.AccountInfo;
import com.is.clientaccountapi.model.account.AccountRequest;
import com.is.clientaccountapi.model.result.ApiResponse;
import com.is.clientaccountapi.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.is.clientaccountapi.model.account.DepositRequest;
import java.util.List;
import com.is.clientaccountapi.model.account.TransferRequest;
import com.is.clientaccountapi.model.account.TransferResult;
import com.is.clientaccountapi.model.account.OperationInfo;
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    // Mijozga hisob ochish
    @PostMapping("/clients/{clientId}/accounts")
    public ResponseEntity<ApiResponse<AccountInfo>> openAccount(@PathVariable Long clientId,
                                                                @RequestBody AccountRequest request) {
        log.info("OpenAccount start: clientId: {}", clientId);
        return ClientController.toResponse(accountService.openAccount(clientId, request));
    }


    // Mijozning hisoblari
    @GetMapping("/clients/{clientId}/accounts")
    public ResponseEntity<ApiResponse<List<AccountInfo>>> getClientAccounts(@PathVariable Long clientId) {
        log.info("GetClientAccounts start: clientId: {}", clientId);
        ApiResponse<List<AccountInfo>> res = accountService.getClientAccounts(clientId);
        return ResponseEntity.status(res.getHttpStatus()).body(res);
    }

    // Hisobni yopish
    @PutMapping("/accounts/{id}/close")
    public ResponseEntity<ApiResponse<AccountInfo>> closeAccount(@PathVariable Long id) {
        log.info("CloseAccount start: id: {}", id);
        ApiResponse<AccountInfo> res = accountService.closeAccount(id);
        return ResponseEntity.status(res.getHttpStatus()).body(res);
    }
    // Hisobni to'ldirish
    @PostMapping("/accounts/{id}/deposit")
    public ResponseEntity<ApiResponse<AccountInfo>> deposit(@PathVariable Long id,
                                                            @RequestBody DepositRequest request) {
        log.info("Deposit start: id: {}, amount: {}", id, request.getAmount());
        ApiResponse<AccountInfo> res = accountService.deposit(id, request);
        return ResponseEntity.status(res.getHttpStatus()).body(res);
    }

    // Hisobdan hisobga o'tkazish
    @PostMapping("/accounts/transfer")
    public ResponseEntity<ApiResponse<TransferResult>> transfer(@RequestBody TransferRequest request) {
        log.info("Transfer start: from: {}, to: {}, amount: {}",
                request.getFromAccountId(), request.getToAccountId(), request.getAmount());
        ApiResponse<TransferResult> res = accountService.transfer(request);
        return ResponseEntity.status(res.getHttpStatus()).body(res);
    }


    // Operatsiyalar tarixi
    @GetMapping("/accounts/{id}/operations")
    public ResponseEntity<ApiResponse<List<OperationInfo>>> getOperations(
            @PathVariable Long id,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        log.info("GetOperations start: id: {}, from: {}, to: {}", id, from, to);
        ApiResponse<List<OperationInfo>> res = accountService.getOperations(id, from, to);
        return ResponseEntity.status(res.getHttpStatus()).body(res);
    }



}
