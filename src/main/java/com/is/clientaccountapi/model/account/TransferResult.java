package com.is.clientaccountapi.model.account;

import lombok.*;

// Postman'ga qaytadigan JSON: o'tkazmadan keyingi ikkala hisob
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResult {
    private Long amount;
    private AccountInfo fromAccount;
    private AccountInfo toAccount;
}