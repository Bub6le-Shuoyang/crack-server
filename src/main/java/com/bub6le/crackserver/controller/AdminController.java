package com.bub6le.crackserver.controller;

import com.bub6le.crackserver.dto.AdminResetPasswordRequest;
import com.bub6le.crackserver.dto.AdminUpdateUserRequest;
import com.bub6le.crackserver.dto.ChangeUserStatusRequest;
import com.bub6le.crackserver.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理员用户管理", description = "管理员修改用户信息、重置密码、查询列表等接口")
@RestController
@RequestMapping("/api/admin")
@Slf4j
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    @Operation(summary = "管理员修改用户基础信息")
    @PostMapping("/update-user/{userId}")
    public Map<String, Object> updateUser(
            @PathVariable Long userId,
            @RequestBody AdminUpdateUserRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return userService.adminUpdateUser(userId, request, token);
    }

    @Operation(summary = "管理员重置用户密码")
    @PostMapping("/reset-password/{userId}")
    public Map<String, Object> resetPassword(
            @PathVariable Long userId,
            @RequestBody AdminResetPasswordRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return userService.adminResetPassword(userId, request, token);
    }

    @Operation(summary = "管理员查询用户列表")
    @GetMapping("/list-users")
    public Map<String, Object> listUsers(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "roleId", required = false) String roleId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return userService.adminListUsers(page, pageSize, keyword, roleId, status, token);
    }

    @Operation(summary = "管理员禁用 / 启用用户账号")
    @PostMapping("/change-user-status/{userId}")
    public Map<String, Object> changeUserStatus(
            @PathVariable Long userId,
            @RequestBody ChangeUserStatusRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return userService.adminChangeUserStatus(userId, request, token);
    }
}
