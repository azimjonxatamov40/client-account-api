package com.is.clientaccountapi.model.client;

import lombok.Getter;
import lombok.Setter;

// Postman'dan keladigan JSON
@Getter
@Setter
public class ClientRequest {
    private String pinfl;
    private String firstName;
    private String lastName;
    private String patronymic;
    private String birthDate;   // yyyy-mm-dd
    private String passport;
    private String phone;
}