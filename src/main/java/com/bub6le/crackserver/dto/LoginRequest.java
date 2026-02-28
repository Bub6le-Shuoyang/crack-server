package com.bub6le.crackserver.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    private String password;
}
