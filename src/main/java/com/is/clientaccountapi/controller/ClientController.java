package com.is.clientaccountapi.controller;

import com.is.clientaccountapi.model.client.ClientInfo;
import com.is.clientaccountapi.model.client.ClientPage;
import com.is.clientaccountapi.model.client.ClientRequest;
import com.is.clientaccountapi.model.result.ApiResponse;
import com.is.clientaccountapi.service.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    // Mijoz qo'shish
    @PostMapping
    public ResponseEntity<ApiResponse<ClientInfo>> createClient(@RequestBody ClientRequest request) {
        log.info("CreateClient start: pinfl: {}", request.getPinfl());
        return toResponse(clientService.createClient(request));
    }

    // Mijozlar ro'yxati
    @GetMapping
    public ResponseEntity<ApiResponse<ClientPage>> getClients(@RequestParam(required = false) Integer page,
                                                              @RequestParam(required = false) Integer size,
                                                              @RequestParam(required = false) String search) {
        log.info("GetClients start: page: {}, size: {}, search: {}", page, size, search);
        return toResponse(clientService.getClients(page, size, search));
    }

    // Bitta mijoz
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientInfo>> getClientById(@PathVariable Long id) {
        log.info("GetClientById start: id: {}", id);
        return toResponse(clientService.getClientById(id));
    }

    // Mijoz ma'lumotini o'zgartirish
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientInfo>> updateClient(@PathVariable Long id,
                                                                @RequestBody ClientRequest request) {
        log.info("UpdateClient start: id: {}", id);
        return toResponse(clientService.updateClient(id, request));
    }

    // Mijozni o'chirish
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> deleteClient(@PathVariable Long id) {
        log.info("DeleteClient start: id: {}", id);
        return toResponse(clientService.deleteClient(id));
    }

    static <T> ResponseEntity<ApiResponse<T>> toResponse(ApiResponse<T> res) {
        return ResponseEntity.status(res.getHttpStatus()).body(res);
    }
}
