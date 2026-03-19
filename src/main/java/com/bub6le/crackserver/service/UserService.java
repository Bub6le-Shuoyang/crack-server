package com.bub6le.crackserver.service;

import com.bub6le.crackserver.common.Result;
import com.bub6le.crackserver.dto.*;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
    Result login(String email, String password);
    Result sendVerificationCode(String email);
    Result register(String email, String password, String name, String code);
    Result forgotPassword(String email, String code, String newPassword);
    Result logout(String token);

    // 个人信息修改
    Result updateProfile(UpdateProfileRequest request);
    Result updateAvatar(MultipartFile file);
    Result updatePassword(UpdatePasswordRequest request);

    // 管理员用户管理
    Result adminUpdateUser(Long userId, AdminUpdateUserRequest request);
    Result adminResetPassword(Long userId, AdminResetPasswordRequest request);
    Result adminListUsers(int page, int pageSize, String keyword, String roleId, Integer status);
    Result adminChangeUserStatus(Long userId, ChangeUserStatusRequest request);
}
