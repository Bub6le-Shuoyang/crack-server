package com.bub6le.crackserver.service;

import java.util.Map;
import com.bub6le.crackserver.dto.*;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
    Map<String, Object> login(String email, String password);
    Map<String, Object> sendVerificationCode(String email);
    Map<String, Object> register(String email, String password, String name, String code);
    Map<String, Object> forgotPassword(String email, String code, String newPassword);
    Map<String, Object> logout(String token);

    // 个人信息修改
    Map<String, Object> updateProfile(UpdateProfileRequest request, String token);
    Map<String, Object> updateAvatar(MultipartFile file, String token);
    Map<String, Object> updatePassword(UpdatePasswordRequest request, String token);

    // 管理员用户管理
    Map<String, Object> adminUpdateUser(Long userId, AdminUpdateUserRequest request, String token);
    Map<String, Object> adminResetPassword(Long userId, AdminResetPasswordRequest request, String token);
    Map<String, Object> adminListUsers(int page, int pageSize, String keyword, String roleId, Integer status, String token);
    Map<String, Object> adminChangeUserStatus(Long userId, ChangeUserStatusRequest request, String token);
}
