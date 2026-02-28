package com.bub6le.crackserver.controller;

import com.bub6le.crackserver.dto.LoginRequest;
import com.bub6le.crackserver.dto.RegisterRequest;
import com.bub6le.crackserver.dto.ResetPasswordRequest;
import com.bub6le.crackserver.dto.SendCodeRequest;
import com.bub6le.crackserver.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Tag(name = "用户管理", description = "用户登录、注册、找回密码等接口")
@RestController
@RequestMapping("/api/user")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @Operation(summary = "登录接口")
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        log.info("请求登录 email={}", request.getEmail());
        Map<String, Object> res = userService.login(request.getEmail(), request.getPassword());
        if (res.get("error") != null) {
            log.warn("登录失败 email={} code={} message={}", request.getEmail(), res.get("code"), res.get("message"));
        } else {
            log.info("登录成功 email={}", request.getEmail());
        }
        return res;
    }

    @Operation(summary = "发送验证码接口", description = "用于注册和找回密码")
    @PostMapping("/send-verification-code")
    public Map<String, Object> sendVerificationCode(@RequestBody SendCodeRequest request) {
        log.info("请求发送验证码 email={}", request.getEmail());
        Map<String, Object> res = userService.sendVerificationCode(request.getEmail());
        if (res.get("error") != null) {
            log.warn("发送验证码失败 email={} code={} message={}", request.getEmail(), res.get("code"), res.get("message"));
        } else {
            log.info("发送验证码成功 email={}", request.getEmail());
        }
        return res;
    }

    @Operation(summary = "注册接口")
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterRequest request) {
        log.info("请求注册 email={} name={}", request.getEmail(), request.getName());
        Map<String, Object> res = userService.register(request.getEmail(), request.getPassword(), request.getName(), request.getVerificationCode());
        if (res.get("error") != null) {
            log.warn("注册失败 email={} code={} message={}", request.getEmail(), res.get("code"), res.get("message"));
        } else {
            log.info("注册成功 email={}", request.getEmail());
        }
        return res;
    }

    @Operation(summary = "找回密码接口")
    @PostMapping("/forgot-password")
    public Map<String, Object> forgotPassword(@RequestBody ResetPasswordRequest request) {
        log.info("请求找回密码 email={}", request.getEmail());
        Map<String, Object> res = userService.forgotPassword(request.getEmail(), request.getVerificationCode(), request.getNewPassword());
        if (res.get("error") != null) {
            log.warn("找回密码失败 email={} code={} message={}", request.getEmail(), res.get("code"), res.get("message"));
        } else {
            log.info("找回密码成功 email={}", request.getEmail());
        }
        return res;
    }

    @Operation(summary = "登出接口")
    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        log.info("请求登出");
        Map<String, Object> res = userService.logout(token);
        log.info("登出结果 ok={}", res.get("ok"));
        return res;
    }
}
