package com.bub6le.crackserver.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bub6le.crackserver.entity.User;
import com.bub6le.crackserver.entity.UserToken;
import com.bub6le.crackserver.entity.VerificationCode;
import com.bub6le.crackserver.mapper.UserMapper;
import com.bub6le.crackserver.mapper.UserTokenMapper;
import com.bub6le.crackserver.mapper.VerificationCodeMapper;
import com.bub6le.crackserver.service.EmailService;
import com.bub6le.crackserver.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private VerificationCodeMapper verificationCodeMapper;

    @Autowired
    private UserTokenMapper userTokenMapper;

    @Autowired
    private EmailService emailService;

    @Override
    public Map<String, Object> login(String email, String password) {
        Map<String, Object> result = new HashMap<>();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            result.put("error", true);
            result.put("message", "用户不存在");
            result.put("code", "USER_NOT_FOUND");
            return result;
        }

        if (!BCrypt.checkpw(password, user.getPassword())) {
            result.put("error", true);
            result.put("message", "密码错误");
            result.put("code", "INVALID_PASSWORD");
            return result;
        }

        // Generate Token
        String tokenStr = UUID.randomUUID().toString().replace("-", "");
        UserToken userToken = new UserToken();
        userToken.setToken(tokenStr);
        userToken.setUserId(user.getId());
        userToken.setExpiredAt(LocalDateTime.now().plusDays(7));
        userToken.setIsValid(1);
        userToken.setCreatedAt(LocalDateTime.now());
        userToken.setUpdatedAt(LocalDateTime.now());
        userTokenMapper.insert(userToken);

        // Update last login time
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        result.put("token", tokenStr);
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("name", user.getName());
        userInfo.put("email", user.getEmail());
        userInfo.put("role_id", user.getRoleId());
        result.put("user", userInfo);

        return result;
    }

    @Override
    public Map<String, Object> sendVerificationCode(String email) {
        Map<String, Object> result = new HashMap<>();
        // Check if email format is valid (basic check)
        if (email == null || !email.contains("@")) {
            result.put("error", true);
            result.put("message", "邮箱不存在或邮箱不可用");
            result.put("code", "INVALID_EMAIL_FORMAT");
            return result;
        }

        try {
            // Determine type: 1=register (user not exists), 2=reset (user exists)
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
            int type = (user == null) ? 1 : 2;

            String code = RandomUtil.randomNumbers(6);
            VerificationCode vc = new VerificationCode();
            vc.setEmail(email);
            vc.setCode(code);
            vc.setType(type);
            vc.setExpiredAt(LocalDateTime.now().plusMinutes(10));
            vc.setIsUsed(0);
            vc.setCreatedAt(LocalDateTime.now());
            verificationCodeMapper.insert(vc);

            emailService.sendVerificationCode(email, code);

            result.put("ok", true);
            result.put("message", "验证码已发送");
        } catch (Exception e) {
            result.put("error", true);
            result.put("message", "邮件发送服务暂时不可用，请稍后再试");
            result.put("code", "EMAIL_SERVICE_UNAVAILABLE");
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public Map<String, Object> register(String email, String password, String name, String code) {
        Map<String, Object> result = new HashMap<>();

        // Check if email exists
        User existingUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (existingUser != null) {
            result.put("error", true);
            result.put("message", "该邮箱已被注册");
            result.put("code", "EMAIL_ALREADY_EXISTS");
            return result;
        }

        // Validate code
        VerificationCode vc = verificationCodeMapper.selectOne(new LambdaQueryWrapper<VerificationCode>()
                .eq(VerificationCode::getEmail, email)
                .eq(VerificationCode::getCode, code)
                .eq(VerificationCode::getIsUsed, 0)
                .orderByDesc(VerificationCode::getId) // Get latest
                .last("LIMIT 1"));

        if (vc == null || vc.getExpiredAt().isBefore(LocalDateTime.now())) {
            result.put("error", true);
            result.put("message", "验证码错误或已过期");
            result.put("code", "INVALID_VERIFICATION_CODE");
            return result;
        }

        // Password validation (optional as per requirement)
        if (password.length() < 6) {
             result.put("error", true);
             result.put("message", "密码长度需不少于6位");
             result.put("code", "INVALID_PASSWORD_FORMAT");
             return result;
        }

        // Mark code as used
        vc.setIsUsed(1);
        verificationCodeMapper.updateById(vc);

        // Create User
        User user = new User();
        user.setEmail(email);
        user.setPassword(BCrypt.hashpw(password));
        user.setName(name);
        user.setRoleId("1"); // Default role
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        result.put("ok", true);
        result.put("message", "注册成功");
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("name", user.getName());
        userInfo.put("email", user.getEmail());
        userInfo.put("roleid", user.getRoleId());
        result.put("user", userInfo);

        return result;
    }

    @Override
    public Map<String, Object> forgotPassword(String email, String code, String newPassword) {
        Map<String, Object> result = new HashMap<>();

        // Validate code
        VerificationCode vc = verificationCodeMapper.selectOne(new LambdaQueryWrapper<VerificationCode>()
                .eq(VerificationCode::getEmail, email)
                .eq(VerificationCode::getCode, code)
                .eq(VerificationCode::getIsUsed, 0)
                .orderByDesc(VerificationCode::getId)
                .last("LIMIT 1"));

        if (vc == null || vc.getExpiredAt().isBefore(LocalDateTime.now())) {
            result.put("error", true);
            result.put("message", "验证信息错误，修改密码失败");
            result.put("code", "INVALID_VERIFICATION_CODE");
            return result;
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            result.put("error", true);
            result.put("message", "用户不存在"); // Should not happen if logic is correct, but for safety
            result.put("code", "USER_NOT_FOUND");
            return result;
        }

        // Mark code as used
        vc.setIsUsed(1);
        verificationCodeMapper.updateById(vc);

        // Update password
        user.setPassword(BCrypt.hashpw(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        result.put("ok", true);
        result.put("message", "密码重置成功");
        return result;
    }

    @Override
    public Map<String, Object> logout(String token) {
        Map<String, Object> result = new HashMap<>();
        if (token != null) {
            UserToken userToken = userTokenMapper.selectOne(new LambdaQueryWrapper<UserToken>().eq(UserToken::getToken, token));
            if (userToken != null) {
                userToken.setIsValid(0);
                userToken.setUpdatedAt(LocalDateTime.now());
                userTokenMapper.updateById(userToken);
            }
        }
        result.put("ok", true);
        return result;
    }
}
