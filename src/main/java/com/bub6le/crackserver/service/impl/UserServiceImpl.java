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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private VerificationCodeMapper verificationCodeMapper;

    @Autowired
    private UserTokenMapper userTokenMapper;

    @Autowired
    private EmailService emailService;

    // Save path: current directory + /uploads/avatars/
    private final String AVATAR_UPLOAD_ROOT = System.getProperty("user.dir") + File.separator + "uploads" + File.separator + "avatars" + File.separator;

    private Long getUserIdByToken(String token) {
        if (token == null) return null;
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        UserToken userToken = userTokenMapper.selectOne(new LambdaQueryWrapper<UserToken>()
                .eq(UserToken::getToken, token)
                .eq(UserToken::getIsValid, 1)
                .gt(UserToken::getExpiredAt, LocalDateTime.now()));
        return userToken != null ? userToken.getUserId() : null;
    }

    @Override
    public Map<String, Object> login(String email, String password) {
        Map<String, Object> result = new HashMap<>();
        log.info("登录开始 email={}", email);
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            log.warn("登录失败，用户不存在 email={}", email);
            result.put("error", true);
            result.put("message", "用户不存在");
            result.put("code", "USER_NOT_FOUND");
            return result;
        }

        if (!BCrypt.checkpw(password, user.getPassword())) {
            log.warn("登录失败，密码错误 email={}", email);
            result.put("error", true);
            result.put("message", "密码错误");
            result.put("code", "INVALID_PASSWORD");
            return result;
        }

        log.debug("生成登录令牌 email={}", email);
        String tokenStr = UUID.randomUUID().toString().replace("-", "");
        UserToken userToken = new UserToken();
        userToken.setToken(tokenStr);
        userToken.setUserId(user.getId());
        userToken.setExpiredAt(LocalDateTime.now().plusDays(7));
        userToken.setIsValid(1);
        userToken.setCreatedAt(LocalDateTime.now());
        userToken.setUpdatedAt(LocalDateTime.now());
        userTokenMapper.insert(userToken);

        log.debug("更新最后登录时间 email={}", email);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        result.put("token", tokenStr);
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("name", user.getName());
        userInfo.put("email", user.getEmail());
        userInfo.put("role_id", user.getRoleId());
        result.put("user", userInfo);

        log.info("登录成功 email={}", email);
        return result;
    }

    @Override
    public Map<String, Object> sendVerificationCode(String email) {
        Map<String, Object> result = new HashMap<>();
        log.info("发送验证码开始 email={}", email);
        if (email == null || !email.contains("@")) {
            log.warn("发送验证码失败，邮箱格式错误 email={}", email);
            result.put("error", true);
            result.put("message", "邮箱不存在或邮箱不可用");
            result.put("code", "INVALID_EMAIL_FORMAT");
            return result;
        }

        try {
            log.debug("检查邮箱是否已注册 email={}", email);
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
            int type = (user == null) ? 1 : 2;

            String code = RandomUtil.randomNumbers(6);
            log.debug("生成验证码 email={}", email);
            VerificationCode vc = new VerificationCode();
            vc.setEmail(email);
            vc.setCode(code);
            vc.setType(type);
            vc.setExpiredAt(LocalDateTime.now().plusMinutes(10));
            vc.setIsUsed(0);
            vc.setCreatedAt(LocalDateTime.now());
            verificationCodeMapper.insert(vc);

            log.debug("发送邮件 email={}", email);
            emailService.sendVerificationCode(email, code);

            result.put("ok", true);
            result.put("message", "验证码已发送");
            log.info("发送验证码成功 email={}", email);
        } catch (Exception e) {
            log.error("发送验证码异常 email={}", email, e);
            result.put("error", true);
            result.put("message", "邮件发送服务暂时不可用，请稍后再试");
            result.put("code", "EMAIL_SERVICE_UNAVAILABLE");
        }
        return result;
    }

    @Override
    public Map<String, Object> register(String email, String password, String name, String code) {
        Map<String, Object> result = new HashMap<>();
        log.info("注册开始 email={}", email);
        User existingUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (existingUser != null) {
            log.warn("注册失败，邮箱已存在 email={}", email);
            result.put("error", true);
            result.put("message", "该邮箱已被注册");
            result.put("code", "EMAIL_ALREADY_EXISTS");
            return result;
        }

        log.debug("校验验证码 email={}", email);
        VerificationCode vc = verificationCodeMapper.selectOne(new LambdaQueryWrapper<VerificationCode>()
                .eq(VerificationCode::getEmail, email)
                .eq(VerificationCode::getCode, code)
                .eq(VerificationCode::getIsUsed, 0)
                .orderByDesc(VerificationCode::getId)
                .last("LIMIT 1"));

        if (vc == null || vc.getExpiredAt().isBefore(LocalDateTime.now())) {
            log.warn("注册失败，验证码错误或过期 email={}", email);
            result.put("error", true);
            result.put("message", "验证码错误或已过期");
            result.put("code", "INVALID_VERIFICATION_CODE");
            return result;
        }

        if (password.length() < 6) {
             log.warn("注册失败，密码格式不合法 email={}", email);
             result.put("error", true);
             result.put("message", "密码长度需不少于6位");
             result.put("code", "INVALID_PASSWORD_FORMAT");
             return result;
        }

        log.debug("标记验证码已使用 email={}", email);
        vc.setIsUsed(1);
        verificationCodeMapper.updateById(vc);

        log.debug("创建用户 email={}", email);
        User user = new User();
        user.setEmail(email);
        user.setPassword(BCrypt.hashpw(password));
        user.setName(name);
        user.setRoleId("1");
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

        log.info("注册成功 email={}", email);
        return result;
    }

    @Override
    public Map<String, Object> forgotPassword(String email, String code, String newPassword) {
        Map<String, Object> result = new HashMap<>();
        log.info("找回密码开始 email={}", email);
        VerificationCode vc = verificationCodeMapper.selectOne(new LambdaQueryWrapper<VerificationCode>()
                .eq(VerificationCode::getEmail, email)
                .eq(VerificationCode::getCode, code)
                .eq(VerificationCode::getIsUsed, 0)
                .orderByDesc(VerificationCode::getId)
                .last("LIMIT 1"));

        if (vc == null || vc.getExpiredAt().isBefore(LocalDateTime.now())) {
            log.warn("找回密码失败，验证码错误或过期 email={}", email);
            result.put("error", true);
            result.put("message", "验证信息错误，修改密码失败");
            result.put("code", "INVALID_VERIFICATION_CODE");
            return result;
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            log.warn("找回密码失败，用户不存在 email={}", email);
            result.put("error", true);
            result.put("message", "用户不存在");
            result.put("code", "USER_NOT_FOUND");
            return result;
        }

        log.debug("标记验证码已使用 email={}", email);
        vc.setIsUsed(1);
        verificationCodeMapper.updateById(vc);

        log.debug("更新用户密码 email={}", email);
        user.setPassword(BCrypt.hashpw(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        result.put("ok", true);
        result.put("message", "密码重置成功");
        log.info("找回密码成功 email={}", email);
        return result;
    }

    @Override
    public Map<String, Object> logout(String token) {
        Map<String, Object> result = new HashMap<>();
        log.info("登出开始");
        if (token != null) {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            UserToken userToken = userTokenMapper.selectOne(new LambdaQueryWrapper<UserToken>().eq(UserToken::getToken, token));
            if (userToken != null) {
                log.debug("标记令牌失效");
                userToken.setIsValid(0);
                userToken.setUpdatedAt(LocalDateTime.now());
                userTokenMapper.updateById(userToken);
            }
        }
        result.put("ok", true);
        log.info("登出成功");
        return result;
    }

    @Override
    public Map<String, Object> updateProfile(String token, String name, String email, String password) {
        Map<String, Object> result = new HashMap<>();
        Long userId = getUserIdByToken(token);
        if (userId == null) {
            result.put("error", true);
            result.put("message", "Token无效或已过期");
            result.put("code", "INVALID_TOKEN");
            return result;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            result.put("error", true);
            result.put("message", "用户不存在");
            result.put("code", "USER_NOT_FOUND");
            return result;
        }

        boolean updateEmail = email != null && !email.isEmpty() && !email.equals(user.getEmail());
        
        if (updateEmail) {
            if (password == null || password.isEmpty() || !BCrypt.checkpw(password, user.getPassword())) {
                result.put("error", true);
                result.put("message", "密码错误，无法修改邮箱");
                result.put("code", "INVALID_PASSWORD");
                return result;
            }
            
            User existingEmailUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
            if (existingEmailUser != null) {
                result.put("error", true);
                result.put("message", "该邮箱已被注册");
                result.put("code", "EMAIL_ALREADY_EXISTS");
                return result;
            }
            user.setEmail(email);
        }

        if (name != null && !name.isEmpty()) {
            user.setName(name);
        }

        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        result.put("ok", true);
        result.put("message", "个人信息修改成功");
        
        Map<String, Object> data = new HashMap<>();
        data.put("name", user.getName());
        data.put("email", user.getEmail());
        result.put("data", data);
        
        return result;
    }

    @Override
    public Map<String, Object> updateAvatar(String token, MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        Long userId = getUserIdByToken(token);
        if (userId == null) {
            result.put("error", true);
            result.put("message", "Token无效或已过期");
            result.put("code", "INVALID_TOKEN");
            return result;
        }

        if (file == null || file.isEmpty()) {
            result.put("error", true);
            result.put("message", "文件不能为空");
            result.put("code", "FILE_EMPTY");
            return result;
        }

        // Validate file type
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            result.put("error", true);
            result.put("message", "无效的文件名");
            result.put("code", "INVALID_FILENAME");
            return result;
        }
        
        String extName = cn.hutool.core.io.file.FileNameUtil.extName(originalFilename).toLowerCase();
        if (!Arrays.asList("jpg", "jpeg", "png", "webp").contains(extName)) {
            result.put("error", true);
            result.put("message", "仅支持jpg/png/webp格式的图片");
            result.put("code", "UNSUPPORTED_FILE_TYPE");
            return result;
        }

        // Validate file size (2MB)
        long maxSize = 2 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            result.put("error", true);
            result.put("message", "头像大小不能超过2MB");
            result.put("code", "FILE_TOO_LARGE");
            return result;
        }

        try {
            // Save file
            String datePath = DateUtil.format(new java.util.Date(), "yyyyMMdd");
            String newFileName = "avatar_" + UUID.randomUUID().toString().replace("-", "") + "." + extName;
            String fileDirPath = AVATAR_UPLOAD_ROOT + datePath;
            File dir = new File(fileDirPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File dest = new File(fileDirPath + File.separator + newFileName);
            file.transferTo(dest);

            // Construct URL
            String fileUrl = "http://127.0.0.1:7022/uploads/avatars/" + datePath + "/" + newFileName;

            // Update user
            User user = userMapper.selectById(userId);
            if (user != null) {
                user.setAvatarUrl(fileUrl);
                user.setUpdatedAt(LocalDateTime.now());
                userMapper.updateById(user);
            }

            result.put("ok", true);
            result.put("message", "头像修改成功");
            
            Map<String, Object> data = new HashMap<>();
            data.put("avatarId", user != null ? user.getId() : null); // Return userId as avatarId for simplicity or generate a new ID
            data.put("avatarUrl", fileUrl);
            result.put("data", data);

        } catch (IOException e) {
            log.error("Avatar upload failed", e);
            result.put("error", true);
            result.put("message", "头像上传失败，请重试");
            result.put("code", "UPLOAD_FAILED");
        }

        return result;
    }

    @Override
    public Map<String, Object> updatePassword(String token, String oldPassword, String newPassword) {
        Map<String, Object> result = new HashMap<>();
        Long userId = getUserIdByToken(token);
        if (userId == null) {
            result.put("error", true);
            result.put("message", "Token无效或已过期");
            result.put("code", "INVALID_TOKEN");
            return result;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            result.put("error", true);
            result.put("message", "用户不存在");
            result.put("code", "USER_NOT_FOUND");
            return result;
        }

        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            result.put("error", true);
            result.put("message", "原密码错误");
            result.put("code", "INVALID_PASSWORD");
            return result;
        }

        if (newPassword == null || newPassword.length() < 6 || !newPassword.matches(".*[a-zA-Z]+.*") || !newPassword.matches(".*[0-9]+.*")) {
            result.put("error", true);
            result.put("message", "密码长度需不少于6位，且包含字母和数字");
            result.put("code", "INVALID_PASSWORD_FORMAT");
            return result;
        }

        user.setPassword(BCrypt.hashpw(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        // Optional: Invalidate current token so user must login again
        UserToken userToken = userTokenMapper.selectOne(new LambdaQueryWrapper<UserToken>().eq(UserToken::getToken, token.replace("Bearer ", "")));
        if (userToken != null) {
            userToken.setIsValid(0);
            userToken.setUpdatedAt(LocalDateTime.now());
            userTokenMapper.updateById(userToken);
        }

        result.put("ok", true);
        result.put("message", "密码修改成功，请重新登录");
        return result;
    }

    @Override
    public Map<String, Object> getUserInfo(String token) {
        Map<String, Object> result = new HashMap<>();
        Long userId = getUserIdByToken(token);
        if (userId == null) {
            result.put("error", true);
            result.put("message", "Token无效或已过期");
            result.put("code", "INVALID_TOKEN");
            return result;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            result.put("error", true);
            result.put("message", "用户不存在");
            result.put("code", "USER_NOT_FOUND");
            return result;
        }

        result.put("ok", true);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("name", user.getName());
        data.put("email", user.getEmail());
        data.put("avatarUrl", user.getAvatarUrl());
        data.put("roleId", user.getRoleId());
        data.put("roleName", "2".equals(user.getRoleId()) ? "管理员" : "普通用户");
        data.put("status", user.getStatus());
        data.put("lastLoginAt", user.getLastLoginAt());
        data.put("createdAt", user.getCreatedAt());
        result.put("data", data);
        
        return result;
    }
}
