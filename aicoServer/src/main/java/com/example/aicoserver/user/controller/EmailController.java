package com.example.aicoserver.user.controller;

import com.example.aicoserver.user.service.EmailService;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/email")
public class EmailController {
    @Autowired
    private EmailService emailService;

    // 간단 예시: 메모리에 인증번호 임시 저장 (실무는 Redis 등 사용 권장)
    private Map<String, String> verificationCodes = new ConcurrentHashMap<>();

    @PostMapping("/send-code")
    public String sendVerificationCode(@RequestParam String email) {
        String code = emailService.generateVerificationCode();
        emailService.sendVerificationEmail(email, code);
        verificationCodes.put(email, code);
        return "인증번호 전송 완료";
    }

    @PostMapping("/verify-code")
    public boolean verifyCode(@RequestParam String email, @RequestParam String code) {
        String savedCode = verificationCodes.get(email);
        return savedCode != null && savedCode.equals(code);
    }
}
