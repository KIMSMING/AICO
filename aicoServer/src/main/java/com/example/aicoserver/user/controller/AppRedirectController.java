package com.example.aicoserver.user.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
public class AppRedirectController {

    @GetMapping("/")
    public void redirectToApp(HttpServletResponse response) throws IOException {
        // Android 앱으로 리다이렉트
        String intentUrl = "intent://home/#Intent;scheme=aico;package=com.example.aicoserver;end;";
        String fallbackUrl = "https://example.com/app-not-installed"; // 앱 없는 경우 안내용 페이지

        // fallback URL 적용
        String redirectUrl = intentUrl.replace(";end;", ";S.browser_fallback_url="
                + URLEncoder.encode(fallbackUrl, StandardCharsets.UTF_8.toString()) + ";end;");

        response.sendRedirect(redirectUrl);
    }
}
