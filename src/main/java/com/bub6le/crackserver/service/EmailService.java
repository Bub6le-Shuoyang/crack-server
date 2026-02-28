package com.bub6le.crackserver.service;

public interface EmailService {
    void sendVerificationCode(String to, String code);
}
