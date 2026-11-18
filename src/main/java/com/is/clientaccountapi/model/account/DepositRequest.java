package com.is.clientaccountapi.model.account;

import lombok.*;

// Postman'dan keladigan JSON
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DepositRequest {
    private Long amount;      // tiyinda, 0 dan katta
    private String purpose;   // to'lov maqsadi (ixtiyoriy)
}