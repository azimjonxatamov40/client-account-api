package com.is.clientaccountapi.model.account;

import lombok.Builder;
import lombok.Getter;

// Postman'ga qaytadigan JSON
@Getter
@Builder
public class AccountInfo {
    private Long id;
    private Long clientId;
    private String accountNumber;
    private String balanceType;
    private String currency;
    private Long balance;        // tiyinda
    private Integer state;       // 2 = ochiq, 3 = yopiq
    private String dateOpen;
    private String dateClose;
}