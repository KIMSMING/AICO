package com.example.aicoserver.user.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {
    @PostConstruct
    public void init() throws IOException {
        InputStream serviceAccount = getClass().getResourceAsStream("/aico-1853c-firebase-adminsdk-fbsvc-3b763ae0b9.json");
        FirebaseOptions options = new FirebaseOptions.Builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .setDatabaseUrl("https://aico-1853c.firebaseio.com")
                .build();
        FirebaseApp.initializeApp(options);
    }
}
