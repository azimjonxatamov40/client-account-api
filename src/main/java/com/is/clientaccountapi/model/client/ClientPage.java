package com.is.clientaccountapi.model.client;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

// Sahifalangan ro'yxat
@Getter
@Builder
public class ClientPage {
    private List<ClientInfo> content;   // shu sahifadagi mijozlar
    private Integer page;               // joriy sahifa
    private Integer size;               // sahifa hajmi
    private Long totalElements;         // jami mijozlar soni
    private Integer totalPages;         // jami sahifalar soni
}