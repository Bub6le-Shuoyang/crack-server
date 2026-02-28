package com.bub6le.crackserver.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String email;
    private String password;
    private String name;
    private String verificationCode;
}
