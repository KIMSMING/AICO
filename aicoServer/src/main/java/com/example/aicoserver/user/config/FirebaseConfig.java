package com.example.aicoserver.user.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import javax.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;

import org.springframework.core.io.ClassPathResource;

@Configuration
public class FirebaseConfig {
    @PostConstruct
    public void init() {
        try {
            // ClassPathResource로 클래스패스에서 파일 로드
            InputStream serviceAccount = new ClassPathResource("serviceAccountKey.json").getInputStream();

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setDatabaseUrl("https://aico-1853c-default-rtdb.firebaseio.com") // 필요 시 추가
                    .build();

            FirebaseApp.initializeApp(options);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
