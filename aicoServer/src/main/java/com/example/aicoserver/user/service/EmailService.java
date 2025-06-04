package com.example.aicoserver.user.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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

    // HTML 이메일 발송
    public void sendVerificationEmail(String toEmail, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setReplyTo("AICO");
            helper.setFrom("seoja250502@gmail.com");
            helper.setSubject("[AICO] 이메일 인증번호 안내");

            String htmlContent = """
                    <div style="font-family: 'Apple SD Gothic Neo', 'Malgun Gothic', sans-serif; padding: 20px;">
                        <h2 style="color: #333;">안녕하세요, <span style="color:#007BFF;">AICO</span>입니다.</h2>
                        <p style="font-size: 16px;">요청하신 이메일 인증번호는 아래와 같습니다.</p>
                        <div style="margin: 30px 0; padding: 20px; background-color: #f9f9f9; border: 2px dashed #007BFF; border-radius: 10px; text-align: center;">
                            <span style="font-size: 30px; font-weight: bold; color: #007BFF;">%s</span>
                        </div>
                        <p style="font-size: 14px; color: #555;">이 인증번호는 보안을 위해 <strong>3분간만 유효</strong>하며, 타인에게 노출되지 않도록 주의해 주세요.</p>
                        <br>
                        <p style="font-size: 14px;">감사합니다.<br><strong>- AICO 드림 -</strong></p>
                    </div>
                    """.formatted(code);

            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("이메일 발송 중 오류 발생", e);
        }
    }
}
