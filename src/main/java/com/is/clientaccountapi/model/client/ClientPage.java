package com.is.clientaccountapi.model.client;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ClientPage {
    private List<ClientInfo> content;
    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
}
