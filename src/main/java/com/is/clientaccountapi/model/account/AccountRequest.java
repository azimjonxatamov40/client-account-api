package com.is.clientaccountapi.model.account;

import lombok.Getter;
import lombok.Setter;

// Postman'dan keladigan JSON
@Getter
@Setter
public class AccountRequest {
    private String balanceType;   // hisob turi, 5 raqam (masalan 20206)
    private String currency;      // 860, 840, 978, 643
}