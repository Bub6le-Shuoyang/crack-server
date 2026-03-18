package com.bub6le.crackserver.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bub6le.crackserver.dto.*;
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
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.IdUtil;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

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

    private User getUserByToken(String token) {
        Long userId = getUserIdByToken(token);
        if (userId == null) return null;
        return userMapper.selectById(userId);
    }

    @Override
    public Map<String, Object> updateProfile(UpdateProfileRequest request, String token) {
        Map<String, Object> result = new HashMap<>();
        User user = getUserByToken(token);
        if (user == null) {
            result.put("error", true);
            result.put("message", "Token无效或已过期");
            result.put("code", "INVALID_TOKEN");
            return result;
        }

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (request.getPassword() == null || !BCrypt.checkpw(request.getPassword(), user.getPassword())) {
                result.put("error", true);
                result.put("message", "密码错误，无法修改邮箱");
                result.put("code", "INVALID_PASSWORD");
                return result;
            }
            User existUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail()));
            if (existUser != null) {
                result.put("error", true);
                result.put("message", "该邮箱已被注册");
                result.put("code", "EMAIL_ALREADY_EXISTS");
                return result;
            }
            user.setEmail(request.getEmail());
        }

        if (request.getName() != null) {
            user.setName(request.getName());
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
    public Map<String, Object> updateAvatar(MultipartFile file, String token) {
        Map<String, Object> result = new HashMap<>();
        User user = getUserByToken(token);
        if (user == null) {
            result.put("error", true);
            result.put("message", "Token无效或已过期");
            result.put("code", "INVALID_TOKEN");
            return result;
        }

        if (file.getSize() > 2 * 1024 * 1024) {
            result.put("error", true);
            result.put("message", "头像大小不能超过2MB");
            result.put("code", "FILE_TOO_LARGE");
            return result;
        }

        String originalFilename = file.getOriginalFilename();
        String ext = FileNameUtil.extName(originalFilename).toLowerCase();
        if (!Arrays.asList("jpg", "png", "webp", "jpeg").contains(ext)) {
            result.put("error", true);
            result.put("message", "仅支持jpg/png/webp格式的图片");
            result.put("code", "UNSUPPORTED_FILE_TYPE");
            return result;
        }

        try {
            String dateDir = DateUtil.format(new java.util.Date(), "yyyyMMdd");
            String newFileName = "avatar_" + IdUtil.simpleUUID() + "." + ext;
            String relativePath = "/uploads/avatars/" + dateDir + "/" + newFileName;
            String UPLOAD_ROOT = System.getProperty("user.dir") + File.separator + "uploads" + File.separator;
            String absolutePath = UPLOAD_ROOT + "avatars" + File.separator + dateDir + File.separator + newFileName;

            File dest = new File(absolutePath);
            FileUtil.touch(dest);
            file.transferTo(dest);

            user.setAvatarUrl(relativePath);
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);

            result.put("ok", true);
            result.put("message", "头像修改成功");
            Map<String, Object> data = new HashMap<>();
            data.put("avatarId", user.getId());
            data.put("avatarUrl", relativePath);
            result.put("data", data);
        } catch (IOException e) {
            log.error("Avatar upload failed", e);
            result.put("error", true);
            result.put("message", "头像上传失败");
            result.put("code", "UPLOAD_FAILED");
        }
        return result;
    }

    @Override
    public Map<String, Object> updatePassword(UpdatePasswordRequest request, String token) {
        Map<String, Object> result = new HashMap<>();
        User user = getUserByToken(token);
        if (user == null) {
            result.put("error", true);
            result.put("message", "Token无效或已过期");
            result.put("code", "INVALID_TOKEN");
            return result;
        }

        if (!BCrypt.checkpw(request.getOldPassword(), user.getPassword())) {
            result.put("error", true);
            result.put("message", "原密码错误");
            result.put("code", "INVALID_PASSWORD");
            return result;
        }

        if (request.getNewPassword().length() < 6 || !request.getNewPassword().matches(".*[a-zA-Z].*") || !request.getNewPassword().matches(".*\\d.*")) {
            result.put("error", true);
            result.put("message", "密码长度需不少于6位，且包含字母和数字");
            result.put("code", "INVALID_PASSWORD_FORMAT");
            return result;
        }

        user.setPassword(BCrypt.hashpw(request.getNewPassword(), BCrypt.gensalt()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        result.put("ok", true);
        result.put("message", "密码修改成功，请重新登录");
        return result;
    }

    @Override
    public Map<String, Object> adminUpdateUser(Long userId, AdminUpdateUserRequest request, String token) {
        Map<String, Object> result = new HashMap<>();
        User admin = getUserByToken(token);
        if (admin == null || !"2".equals(admin.getRoleId())) {
            result.put("error", true);
            result.put("message", "无管理员权限，无法执行此操作");
            result.put("code", "NO_ADMIN_PERMISSION");
            return result;
        }

        User targetUser = userMapper.selectById(userId);
        if (targetUser == null) {
            result.put("error", true);
            result.put("message", "用户不存在");
            result.put("code", "USER_NOT_FOUND");
            return result;
        }

        if (request.getEmail() != null && !request.getEmail().equals(targetUser.getEmail())) {
             User existUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail()));
            if (existUser != null) {
                result.put("error", true);
                result.put("message", "该邮箱已被注册");
                result.put("code", "EMAIL_ALREADY_EXISTS");
                return result;
            }
            targetUser.setEmail(request.getEmail());
        }

        if (request.getName() != null) targetUser.setName(request.getName());
        if (request.getRoleId() != null) targetUser.setRoleId(request.getRoleId());
        if (request.getStatus() != null) targetUser.setStatus(request.getStatus());
        
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
        return result;
    }

    @Override
    public Map<String, Object> adminResetPassword(Long userId, AdminResetPasswordRequest request, String token) {
        Map<String, Object> result = new HashMap<>();
        User admin = getUserByToken(token);
        if (admin == null || !"2".equals(admin.getRoleId())) {
            result.put("error", true);
            result.put("message", "无管理员权限");
            result.put("code", "NO_ADMIN_PERMISSION");
            return result;
        }

        User targetUser = userMapper.selectById(userId);
        if (targetUser == null) {
            result.put("error", true);
            result.put("message", "用户不存在");
            result.put("code", "USER_NOT_FOUND");
            return result;
        }

        targetUser.setPassword(BCrypt.hashpw(request.getNewPassword(), BCrypt.gensalt()));
        targetUser.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(targetUser);
        
        result.put("ok", true);
        result.put("message", "密码重置成功，新密码已发送至用户邮箱");
        return result;
    }

    @Override
    public Map<String, Object> adminListUsers(int page, int pageSize, String keyword, String roleId, Integer status, String token) {
        Map<String, Object> result = new HashMap<>();
        User admin = getUserByToken(token);
        if (admin == null || !"2".equals(admin.getRoleId())) {
            result.put("error", true);
            result.put("message", "无管理员权限");
            result.put("code", "NO_ADMIN_PERMISSION");
            return result;
        }

        Page<User> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(User::getName, keyword).or().like(User::getEmail, keyword));
        }
        if (roleId != null && !roleId.isEmpty()) {
            wrapper.eq(User::getRoleId, roleId);
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getCreatedAt);

        Page<User> userPage = userMapper.selectPage(p, wrapper);

        result.put("ok", true);
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> list = new ArrayList<>();
        for (User u : userPage.getRecords()) {
            Map<String, Object> map = new HashMap<>();
            map.put("userId", u.getId());
            map.put("name", u.getName());
            map.put("email", u.getEmail());
            map.put("roleId", u.getRoleId());
            map.put("roleName", "1".equals(u.getRoleId()) ? "普通用户" : "管理员");
            map.put("status", u.getStatus());
            map.put("lastLoginAt", u.getLastLoginAt() != null ? DateUtil.format(u.getLastLoginAt(), "yyyy-MM-dd HH:mm:ss") : null);
            map.put("createdAt", DateUtil.format(u.getCreatedAt(), "yyyy-MM-dd HH:mm:ss"));
            list.add(map);
        }
        data.put("list", list);
        data.put("total", userPage.getTotal());
        data.put("page", userPage.getCurrent());
        data.put("pageSize", userPage.getSize());
        result.put("data", data);
        return result;
    }

    @Override
    public Map<String, Object> adminChangeUserStatus(Long userId, ChangeUserStatusRequest request, String token) {
        Map<String, Object> result = new HashMap<>();
        User admin = getUserByToken(token);
        if (admin == null || !"2".equals(admin.getRoleId())) {
            result.put("error", true);
            result.put("message", "无管理员权限");
            result.put("code", "NO_ADMIN_PERMISSION");
            return result;
        }

        User targetUser = userMapper.selectById(userId);
        if (targetUser == null) {
            result.put("error", true);
            result.put("message", "用户不存在");
            result.put("code", "USER_NOT_FOUND");
            return result;
        }

        targetUser.setStatus(request.getStatus());
        targetUser.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(targetUser);

        result.put("ok", true);
        result.put("message", request.getStatus() == 1 ? "用户账号已启用" : "用户账号已禁用");
        return result;
    }
}
