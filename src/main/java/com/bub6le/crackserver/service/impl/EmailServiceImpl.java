package com.bub6le.crackserver.service.impl;

import com.bub6le.crackserver.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Override
    public void sendVerificationCode(String to, String code) {
        log.info("准备发送邮件 email={}", to);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Crack Server 验证码");
        message.setText("您的验证码是：" + code + "，请在10分钟内使用。");
        try {
            mailSender.send(message);
            log.info("邮件发送成功 email={}", to);
        } catch (Exception e) {
            log.error("邮件发送失败 email={}", to, e);
            throw e;
        }
    }
}
