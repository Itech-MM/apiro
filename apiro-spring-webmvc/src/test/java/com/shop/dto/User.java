package com.shop.dto;

import javax.validation.constraints.Email;

public class User {
    @Email
    public String email;
}
