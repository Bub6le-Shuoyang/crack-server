package com.bub6le.crackserver.controller;

import com.bub6le.crackserver.dto.LoginRequest;
import com.bub6le.crackserver.dto.RegisterRequest;
import com.bub6le.crackserver.dto.ResetPasswordRequest;
import com.bub6le.crackserver.dto.SendCodeRequest;
import com.bub6le.crackserver.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Tag(name = "用户管理", description = "用户登录、注册、找回密码等接口")
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Operation(summary = "登录接口")
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        return userService.login(request.getEmail(), request.getPassword());
    }

    @Operation(summary = "发送验证码接口", description = "用于注册和找回密码")
    @PostMapping("/send-verification-code")
    public Map<String, Object> sendVerificationCode(@RequestBody SendCodeRequest request) {
        return userService.sendVerificationCode(request.getEmail());
    }

    @Operation(summary = "注册接口")
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterRequest request) {
        return userService.register(request.getEmail(), request.getPassword(), request.getName(), request.getVerificationCode());
    }

    @Operation(summary = "找回密码接口")
    @PostMapping("/forgot-password")
    public Map<String, Object> forgotPassword(@RequestBody ResetPasswordRequest request) {
        return userService.forgotPassword(request.getEmail(), request.getVerificationCode(), request.getNewPassword());
    }

    @Operation(summary = "登出接口")
    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return userService.logout(token);
    }
}
