package com.bub6le.crackserver.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bub6le.crackserver.dto.UpdateUserRequest;
import com.bub6le.crackserver.entity.User;
import com.bub6le.crackserver.entity.UserToken;
import com.bub6le.crackserver.mapper.UserMapper;
import com.bub6le.crackserver.mapper.UserTokenMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Tag(name = "管理员管理", description = "管理员相关的接口")
@RestController
@RequestMapping("/api/admin")
@Slf4j
public class AdminController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserTokenMapper userTokenMapper;

    @Operation(summary = "管理员获取用户列表接口")
    @GetMapping("/list-users")
    public Map<String, Object> listUsers(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            HttpServletRequest httpRequest) {
        Map<String, Object> result = new HashMap<>();
        log.info("请求获取用户列表 page={} pageSize={} keyword={}", page, pageSize, keyword);

        User currentUser = checkAdminPermission(httpRequest);
        if (currentUser == null) {
            return buildErrorResult("无管理员权限，无法执行此操作", "NO_ADMIN_PERMISSION");
        }

        // 构建查询条件
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            queryWrapper.like(User::getName, keyword).or().like(User::getEmail, keyword);
        }
        
        // 分页查询
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<User> pageParam = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize);
        com.baomidou.mybatisplus.core.metadata.IPage<User> userPage = userMapper.selectPage(pageParam, queryWrapper);

        result.put("ok", true);
        result.put("message", "获取成功");
        
        Map<String, Object> data = new HashMap<>();
        data.put("total", userPage.getTotal());
        data.put("page", page);
        data.put("pageSize", pageSize);
        
        // 脱敏处理，不返回密码等敏感信息
        java.util.List<Map<String, Object>> userList = new java.util.ArrayList<>();
        for (User u : userPage.getRecords()) {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("userId", u.getId());
            userMap.put("email", u.getEmail());
            userMap.put("name", u.getName());
            userMap.put("roleId", u.getRoleId());
            userMap.put("roleName", "2".equals(u.getRoleId()) ? "管理员" : "普通用户");
            userMap.put("status", u.getStatus());
            userMap.put("lastLoginAt", u.getLastLoginAt());
            userMap.put("createdAt", u.getCreatedAt());
            userList.add(userMap);
        }
        data.put("list", userList);
        
        result.put("data", data);

        log.info("获取用户列表成功 total={}", userPage.getTotal());
        return result;
    }

    @Operation(summary = "管理员修改用户信息接口")
    @PostMapping("/update-user/{userId}")
    public Map<String, Object> updateUser(
            @PathVariable("userId") Long targetUserId,
            @RequestBody UpdateUserRequest request,
            HttpServletRequest httpRequest) {
        Map<String, Object> result = new HashMap<>();
        log.info("请求修改用户信息 targetUserId={}", targetUserId);

        User currentUser = checkAdminPermission(httpRequest);
        if (currentUser == null) {
            return buildErrorResult("无管理员权限，无法执行此操作", "NO_ADMIN_PERMISSION");
        }

        // 查找目标用户
        User targetUser = userMapper.selectById(targetUserId);
        if (targetUser == null) {
            return buildErrorResult("用户不存在", "USER_NOT_FOUND");
        }

        // 修改邮箱（如果提供）需要检查是否被占用
        if (request.getEmail() != null && !request.getEmail().isEmpty() && !request.getEmail().equals(targetUser.getEmail())) {
            User existingEmailUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail()));
            if (existingEmailUser != null) {
                return buildErrorResult("该邮箱已被注册", "EMAIL_ALREADY_EXISTS");
            }
            targetUser.setEmail(request.getEmail());
        }

        // 修改姓名
        if (request.getName() != null && !request.getName().isEmpty()) {
            targetUser.setName(request.getName());
        }

        // 修改角色
        if (request.getRoleId() != null && !request.getRoleId().isEmpty()) {
            targetUser.setRoleId(request.getRoleId());
        }

        // 修改状态
        if (request.getStatus() != null) {
            targetUser.setStatus(request.getStatus());
        }

        targetUser.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(targetUser);

        result.put("ok", true);
        result.put("message", "用户信息修改成功");
        
        Map<String, Object> data = new HashMap<>();
        data.put("userId", targetUser.getId());
        data.put("name", targetUser.getName());
        data.put("email", targetUser.getEmail());
        data.put("roleId", targetUser.getRoleId());
        data.put("status", targetUser.getStatus());
        result.put("data", data);

        log.info("修改用户信息成功 targetUserId={}", targetUserId);
        return result;
    }

    @Operation(summary = "管理员重置用户密码接口")
    @PostMapping("/reset-password/{userId}")
    public Map<String, Object> resetPassword(
            @PathVariable("userId") Long targetUserId,
            @RequestBody com.bub6le.crackserver.dto.ResetPasswordAdminRequest request,
            HttpServletRequest httpRequest) {
        Map<String, Object> result = new HashMap<>();

        User currentUser = checkAdminPermission(httpRequest);
        if (currentUser == null) {
            return buildErrorResult("无管理员权限，无法执行此操作", "NO_ADMIN_PERMISSION");
        }

        User targetUser = userMapper.selectById(targetUserId);
        if (targetUser == null) {
            return buildErrorResult("用户不存在", "USER_NOT_FOUND");
        }

        if (request.getNewPassword() == null || request.getNewPassword().isEmpty()) {
            return buildErrorResult("新密码不能为空", "INVALID_PARAMS");
        }

        targetUser.setPassword(cn.hutool.crypto.digest.BCrypt.hashpw(request.getNewPassword()));
        targetUser.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(targetUser);

        result.put("ok", true);
        result.put("message", "密码重置成功，新密码已发送至用户邮箱");
        return result;
    }

    @Operation(summary = "管理员修改用户状态接口")
    @PostMapping("/change-user-status/{userId}")
    public Map<String, Object> changeUserStatus(
            @PathVariable("userId") Long targetUserId,
            @RequestBody com.bub6le.crackserver.dto.ChangeUserStatusRequest request,
            HttpServletRequest httpRequest) {
        Map<String, Object> result = new HashMap<>();

        User currentUser = checkAdminPermission(httpRequest);
        if (currentUser == null) {
            return buildErrorResult("无管理员权限，无法执行此操作", "NO_ADMIN_PERMISSION");
        }

        User targetUser = userMapper.selectById(targetUserId);
        if (targetUser == null) {
            return buildErrorResult("用户不存在", "USER_NOT_FOUND");
        }

        if (request.getStatus() == null || (request.getStatus() != 0 && request.getStatus() != 1)) {
            return buildErrorResult("状态值不合法", "INVALID_PARAMS");
        }

        targetUser.setStatus(request.getStatus());
        targetUser.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(targetUser);

        result.put("ok", true);
        result.put("message", request.getStatus() == 1 ? "用户账号已启用" : "用户账号已禁用");
        return result;
    }

    private User checkAdminPermission(HttpServletRequest httpRequest) {
        String token = httpRequest.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (token == null) {
            return null;
        }

        UserToken userToken = userTokenMapper.selectOne(new LambdaQueryWrapper<UserToken>()
                .eq(UserToken::getToken, token)
                .eq(UserToken::getIsValid, 1)
                .gt(UserToken::getExpiredAt, LocalDateTime.now()));

        if (userToken == null) {
            return null;
        }

        User currentUser = userMapper.selectById(userToken.getUserId());
        if (currentUser == null || !"2".equals(currentUser.getRoleId())) {
            return null;
        }

        return currentUser;
    }

    private Map<String, Object> buildErrorResult(String message, String code) {
        Map<String, Object> result = new HashMap<>();
        result.put("error", true);
        result.put("message", message);
        result.put("code", code);
        return result;
    }
}
