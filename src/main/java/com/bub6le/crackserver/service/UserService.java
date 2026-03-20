package com.bub6le.crackserver.service;

import java.util.Map;

public interface UserService {
    Map<String, Object> login(String email, String password);
    Map<String, Object> sendVerificationCode(String email);
    Map<String, Object> register(String email, String password, String name, String code);
    Map<String, Object> forgotPassword(String email, String code, String newPassword);
    Map<String, Object> logout(String token);
    
    // User profile interfaces
    Map<String, Object> updateProfile(String token, String name, String email, String password);
    Map<String, Object> updateAvatar(String token, org.springframework.web.multipart.MultipartFile file);
    Map<String, Object> updatePassword(String token, String oldPassword, String newPassword);
    Map<String, Object> getUserInfo(String token);
}
