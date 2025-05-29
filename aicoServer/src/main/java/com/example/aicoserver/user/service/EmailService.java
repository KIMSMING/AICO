package com.example.aicoserver.user.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class EmailService {
    @Autowired
    private JavaMailSender mailSender;

    // 인증번호 생성 (6자리)
    public String generateVerificationCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    public void sendVerificationEmail(String toEmail, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setReplyTo("AICO");
        message.setFrom("minwns10@gmail.com");
        message.setSubject("이메일 인증번호");
        message.setText("\n\n인증번호는 " + code + " 입니다.\n\n");
        mailSender.send(message);
    }
}
