package com.bub6le.crackserver.dto;

import lombok.Data;

@Data
public class ResetPasswordRequest {
    private String email;
    private String verificationCode;
    private String newPassword;
}
