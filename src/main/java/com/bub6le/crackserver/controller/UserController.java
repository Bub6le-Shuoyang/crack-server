package com.bub6le.crackserver.controller;

import com.bub6le.crackserver.common.Result;
import com.bub6le.crackserver.dto.*;
import com.bub6le.crackserver.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

@Tag(name = "用户管理", description = "用户登录、注册、找回密码及个人信息修改接口")
@RestController
@RequestMapping("/api/user")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @Operation(summary = "登录接口")
    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest request) {
        log.info("请求登录 email={}", request.getEmail());
        Result res = userService.login(request.getEmail(), request.getPassword());
        if (res.get("error") != null) {
            log.warn("登录失败 email={} code={} message={}", request.getEmail(), res.get("code"), res.get("message"));
        } else {
            log.info("登录成功 email={}", request.getEmail());
        }
        return res;
    }

    @Operation(summary = "发送验证码接口", description = "用于注册和找回密码")
    @PostMapping("/send-verification-code")
    public Result sendVerificationCode(@RequestBody SendCodeRequest request) {
        log.info("请求发送验证码 email={}", request.getEmail());
        Result res = userService.sendVerificationCode(request.getEmail());
        if (res.get("error") != null) {
            log.warn("发送验证码失败 email={} code={} message={}", request.getEmail(), res.get("code"), res.get("message"));
        } else {
            log.info("发送验证码成功 email={}", request.getEmail());
        }
        return res;
    }

    @Operation(summary = "注册接口")
    @PostMapping("/register")
    public Result register(@RequestBody RegisterRequest request) {
        log.info("请求注册 email={} name={}", request.getEmail(), request.getName());
        Result res = userService.register(request.getEmail(), request.getPassword(), request.getName(), request.getVerificationCode());
        if (res.get("error") != null) {
            log.warn("注册失败 email={} code={} message={}", request.getEmail(), res.get("code"), res.get("message"));
        } else {
            log.info("注册成功 email={}", request.getEmail());
        }
        return res;
    }

    @Operation(summary = "找回密码接口")
    @PostMapping("/forgot-password")
    public Result forgotPassword(@RequestBody ResetPasswordRequest request) {
        log.info("请求找回密码 email={}", request.getEmail());
        Result res = userService.forgotPassword(request.getEmail(), request.getVerificationCode(), request.getNewPassword());
        if (res.get("error") != null) {
            log.warn("找回密码失败 email={} code={} message={}", request.getEmail(), res.get("code"), res.get("message"));
        } else {
            log.info("找回密码成功 email={}", request.getEmail());
        }
        return res;
    }

    @Operation(summary = "登出接口")
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        log.info("请求登出");
        Result res = userService.logout(token);
        log.info("登出结果 ok={}", res.get("ok"));
        return res;
    }

    @Operation(summary = "修改基础个人信息（姓名 / 邮箱）")
    @PostMapping("/update-profile")
    public Result updateProfile(@RequestBody UpdateProfileRequest requestBody) {
        return userService.updateProfile(requestBody);
    }

    @Operation(summary = "上传 / 修改用户头像")
    @PostMapping("/update-avatar")
    public Result updateAvatar(@RequestParam("file") MultipartFile file) {
        return userService.updateAvatar(file);
    }

    @Operation(summary = "修改登录密码（用户自主修改）")
    @PostMapping("/update-password")
    public Result updatePassword(@RequestBody UpdatePasswordRequest requestBody) {
        return userService.updatePassword(requestBody);
    }
}
