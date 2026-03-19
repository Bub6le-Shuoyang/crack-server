package com.bub6le.crackserver.controller;

import com.bub6le.crackserver.common.Result;
import com.bub6le.crackserver.dto.AdminResetPasswordRequest;
import com.bub6le.crackserver.dto.AdminUpdateUserRequest;
import com.bub6le.crackserver.dto.ChangeUserStatusRequest;
import com.bub6le.crackserver.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理员用户管理", description = "管理员修改用户信息、重置密码、查询列表等接口")
@RestController
@RequestMapping("/api/admin")
@Slf4j
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    @Operation(summary = "管理员修改用户基础信息")
    @PostMapping("/update-user/{userId}")
    public Result updateUser(
            @PathVariable Long userId,
            @RequestBody AdminUpdateUserRequest request) {
        return userService.adminUpdateUser(userId, request);
    }

    @Operation(summary = "管理员重置用户密码")
    @PostMapping("/reset-password/{userId}")
    public Result resetPassword(
            @PathVariable Long userId,
            @RequestBody AdminResetPasswordRequest request) {
        return userService.adminResetPassword(userId, request);
    }

    @Operation(summary = "管理员查询用户列表")
    @GetMapping("/list-users")
    public Result listUsers(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "roleId", required = false) String roleId,
            @RequestParam(value = "status", required = false) Integer status) {
        return userService.adminListUsers(page, pageSize, keyword, roleId, status);
    }

    @Operation(summary = "管理员禁用 / 启用用户账号")
    @PostMapping("/change-user-status/{userId}")
    public Result changeUserStatus(
            @PathVariable Long userId,
            @RequestBody ChangeUserStatusRequest request) {
        return userService.adminChangeUserStatus(userId, request);
    }
}
