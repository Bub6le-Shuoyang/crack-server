package com.bub6le.crackserver.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bub6le.crackserver.common.UserContext;
import com.bub6le.crackserver.entity.UserToken;
import com.bub6le.crackserver.exception.BusinessException;
import com.bub6le.crackserver.mapper.UserTokenMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

@Slf4j
@Component
public class TokenInterceptor implements HandlerInterceptor {

    @Autowired
    private UserTokenMapper userTokenMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 放行跨域预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (!StringUtils.hasText(token)) {
            // 支持从参数获取
            token = request.getParameter("token");
        }

        if (!StringUtils.hasText(token)) {
            throw new BusinessException("INVALID_TOKEN", "缺少Token，请先登录");
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // 校验Token
        UserToken userToken = userTokenMapper.selectOne(new LambdaQueryWrapper<UserToken>()
                .eq(UserToken::getToken, token)
                .eq(UserToken::getIsValid, 1)
                .gt(UserToken::getExpiredAt, LocalDateTime.now()));

        if (userToken == null) {
            throw new BusinessException("INVALID_TOKEN", "Token无效或已过期");
        }

        // 存入 ThreadLocal
        UserContext.setUserId(userToken.getUserId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 防止内存泄漏
        UserContext.clear();
    }
}
