package com.bub6le.crackserver.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bub6le.crackserver.common.Result;
import com.bub6le.crackserver.common.UserContext;
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
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.upload.root:${user.dir}/uploads/}")
    private String uploadRoot;

    @Override
    public Result login(String email, String password) {
        log.info("登录开始 email={}", email);
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            return Result.error("USER_NOT_FOUND", "用户不存在");
        }

        if (!BCrypt.checkpw(password, user.getPassword())) {
            return Result.error("INVALID_PASSWORD", "密码错误");
        }

        String tokenStr = UUID.randomUUID().toString().replace("-", "");
        UserToken userToken = new UserToken();
        userToken.setToken(tokenStr);
        userToken.setUserId(user.getId());
        userToken.setExpiredAt(LocalDateTime.now().plusDays(7));
        userToken.setIsValid(1);
        userToken.setCreatedAt(LocalDateTime.now());
        userToken.setUpdatedAt(LocalDateTime.now());
        userTokenMapper.insert(userToken);

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("name", user.getName());
        userInfo.put("email", user.getEmail());
        userInfo.put("role_id", user.getRoleId());
        
        return Result.success("登录成功").put("token", tokenStr).put("user", userInfo);
    }

    @Override
    public Result sendVerificationCode(String email) {
        if (email == null || !email.contains("@")) {
            return Result.error("INVALID_EMAIL_FORMAT", "邮箱不存在或邮箱不可用");
        }

        try {
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
            return Result.success("验证码已发送");
        } catch (Exception e) {
            log.error("发送验证码异常 email={}", email, e);
            return Result.error("EMAIL_SERVICE_UNAVAILABLE", "邮件发送服务暂时不可用，请稍后再试");
        }
    }

    @Override
    public Result register(String email, String password, String name, String code) {
        User existingUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (existingUser != null) {
            return Result.error("EMAIL_ALREADY_EXISTS", "该邮箱已被注册");
        }

        VerificationCode vc = verificationCodeMapper.selectOne(new LambdaQueryWrapper<VerificationCode>()
                .eq(VerificationCode::getEmail, email)
                .eq(VerificationCode::getCode, code)
                .eq(VerificationCode::getIsUsed, 0)
                .orderByDesc(VerificationCode::getId)
                .last("LIMIT 1"));

        if (vc == null || vc.getExpiredAt().isBefore(LocalDateTime.now())) {
            return Result.error("INVALID_VERIFICATION_CODE", "验证码错误或已过期");
        }

        if (password.length() < 6) {
             return Result.error("INVALID_PASSWORD_FORMAT", "密码长度需不少于6位");
        }

        vc.setIsUsed(1);
        verificationCodeMapper.updateById(vc);

        User user = new User();
        user.setEmail(email);
        user.setPassword(BCrypt.hashpw(password));
        user.setName(name);
        user.setRoleId("1");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("name", user.getName());
        userInfo.put("email", user.getEmail());
        userInfo.put("roleid", user.getRoleId());
        
        return Result.success("注册成功").put("user", userInfo);
    }

    @Override
    public Result forgotPassword(String email, String code, String newPassword) {
        VerificationCode vc = verificationCodeMapper.selectOne(new LambdaQueryWrapper<VerificationCode>()
                .eq(VerificationCode::getEmail, email)
                .eq(VerificationCode::getCode, code)
                .eq(VerificationCode::getIsUsed, 0)
                .orderByDesc(VerificationCode::getId)
                .last("LIMIT 1"));

        if (vc == null || vc.getExpiredAt().isBefore(LocalDateTime.now())) {
            return Result.error("INVALID_VERIFICATION_CODE", "验证信息错误，修改密码失败");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            return Result.error("USER_NOT_FOUND", "用户不存在");
        }

        vc.setIsUsed(1);
        verificationCodeMapper.updateById(vc);

        user.setPassword(BCrypt.hashpw(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        return Result.success("密码重置成功");
    }

    @Override
    public Result logout(String token) {
        if (token != null) {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            UserToken userToken = userTokenMapper.selectOne(new LambdaQueryWrapper<UserToken>().eq(UserToken::getToken, token));
            if (userToken != null) {
                userToken.setIsValid(0);
                userToken.setUpdatedAt(LocalDateTime.now());
                userTokenMapper.updateById(userToken);
            }
        }
        return Result.success();
    }

    private User getCurrentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) return null;
        return userMapper.selectById(userId);
    }

    @Override
    public Result updateProfile(UpdateProfileRequest request) {
        User user = getCurrentUser();

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (request.getPassword() == null || !BCrypt.checkpw(request.getPassword(), user.getPassword())) {
                return Result.error("INVALID_PASSWORD", "密码错误，无法修改邮箱");
            }
            User existUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail()));
            if (existUser != null) {
                return Result.error("EMAIL_ALREADY_EXISTS", "该邮箱已被注册");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getName() != null) {
            user.setName(request.getName());
        }

        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        Map<String, Object> data = new HashMap<>();
        data.put("name", user.getName());
        data.put("email", user.getEmail());
        return Result.success("个人信息修改成功").put("data", data);
    }

    @Override
    public Result updateAvatar(MultipartFile file) {
        User user = getCurrentUser();

        if (file.getSize() > 2 * 1024 * 1024) {
            return Result.error("FILE_TOO_LARGE", "头像大小不能超过2MB");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = FileNameUtil.extName(originalFilename).toLowerCase();
        if (!Arrays.asList("jpg", "png", "webp", "jpeg").contains(ext)) {
            return Result.error("UNSUPPORTED_FILE_TYPE", "仅支持jpg/png/webp格式的图片");
        }

        try {
            String dateDir = DateUtil.format(new java.util.Date(), "yyyyMMdd");
            String newFileName = "avatar_" + IdUtil.simpleUUID() + "." + ext;
            String relativePath = "/uploads/avatars/" + dateDir + "/" + newFileName;
            String absolutePath = uploadRoot + "avatars" + File.separator + dateDir + File.separator + newFileName;

            File dest = new File(absolutePath);
            FileUtil.touch(dest);
            file.transferTo(dest);

            user.setAvatarUrl(relativePath);
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);

            Map<String, Object> data = new HashMap<>();
            data.put("avatarId", user.getId());
            data.put("avatarUrl", relativePath);
            return Result.success("头像修改成功").put("data", data);
        } catch (IOException e) {
            log.error("Avatar upload failed", e);
            return Result.error("UPLOAD_FAILED", "头像上传失败");
        }
    }

    @Override
    public Result updatePassword(UpdatePasswordRequest request) {
        User user = getCurrentUser();

        if (!BCrypt.checkpw(request.getOldPassword(), user.getPassword())) {
            return Result.error("INVALID_PASSWORD", "原密码错误");
        }

        if (request.getNewPassword().length() < 6 || !request.getNewPassword().matches(".*[a-zA-Z].*") || !request.getNewPassword().matches(".*\\d.*")) {
            return Result.error("INVALID_PASSWORD_FORMAT", "密码长度需不少于6位，且包含字母和数字");
        }

        user.setPassword(BCrypt.hashpw(request.getNewPassword(), BCrypt.gensalt()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        return Result.success("密码修改成功，请重新登录");
    }

    @Override
    public Result adminUpdateUser(Long userId, AdminUpdateUserRequest request) {
        User admin = getCurrentUser();
        if (admin == null || !"2".equals(admin.getRoleId())) {
            return Result.error("NO_ADMIN_PERMISSION", "无管理员权限，无法执行此操作");
        }

        User targetUser = userMapper.selectById(userId);
        if (targetUser == null) {
            return Result.error("USER_NOT_FOUND", "用户不存在");
        }

        if (request.getEmail() != null && !request.getEmail().equals(targetUser.getEmail())) {
             User existUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail()));
            if (existUser != null) {
                return Result.error("EMAIL_ALREADY_EXISTS", "该邮箱已被注册");
            }
            targetUser.setEmail(request.getEmail());
        }

        if (request.getName() != null) targetUser.setName(request.getName());
        if (request.getRoleId() != null) targetUser.setRoleId(request.getRoleId());
        if (request.getStatus() != null) targetUser.setStatus(request.getStatus());
        
        targetUser.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(targetUser);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", targetUser.getId());
        data.put("name", targetUser.getName());
        data.put("email", targetUser.getEmail());
        data.put("roleId", targetUser.getRoleId());
        data.put("status", targetUser.getStatus());
        return Result.success("用户信息修改成功").put("data", data);
    }

    @Override
    public Result adminResetPassword(Long userId, AdminResetPasswordRequest request) {
        User admin = getCurrentUser();
        if (admin == null || !"2".equals(admin.getRoleId())) {
            return Result.error("NO_ADMIN_PERMISSION", "无管理员权限");
        }

        User targetUser = userMapper.selectById(userId);
        if (targetUser == null) {
            return Result.error("USER_NOT_FOUND", "用户不存在");
        }

        targetUser.setPassword(BCrypt.hashpw(request.getNewPassword(), BCrypt.gensalt()));
        targetUser.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(targetUser);
        
        return Result.success("密码重置成功，新密码已发送至用户邮箱");
    }

    @Override
    public Result adminListUsers(int page, int pageSize, String keyword, String roleId, Integer status) {
        User admin = getCurrentUser();
        if (admin == null || !"2".equals(admin.getRoleId())) {
            return Result.error("NO_ADMIN_PERMISSION", "无管理员权限");
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
        
        return Result.success().put("data", data);
    }

    @Override
    public Result adminChangeUserStatus(Long userId, ChangeUserStatusRequest request) {
        User admin = getCurrentUser();
        if (admin == null || !"2".equals(admin.getRoleId())) {
            return Result.error("NO_ADMIN_PERMISSION", "无管理员权限");
        }

        User targetUser = userMapper.selectById(userId);
        if (targetUser == null) {
            return Result.error("USER_NOT_FOUND", "用户不存在");
        }

        targetUser.setStatus(request.getStatus());
        targetUser.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(targetUser);

        return Result.success(request.getStatus() == 1 ? "用户账号已启用" : "用户账号已禁用");
    }
}