package com.is.clientaccountapi.model.account;

import lombok.*;

// Postman'ga qaytadigan bitta operatsiya
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationInfo {
    private Long id;
    private Long accountId;
    private Long amount;        // tiyinda; plyus = kirim, minus = chiqim
    private String purpose;
    private String createdAt;
}