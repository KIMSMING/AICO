package com.example.aicoserver.user.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Service
public class SttService {

    private SpeechClient speechClient;

    @PostConstruct
    public void init() throws Exception {
        InputStream serviceAccount = new ClassPathResource("stt-service.json").getInputStream();

        GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccount);
        SpeechSettings speechSettings = SpeechSettings.newBuilder()
                .setCredentialsProvider(() -> credentials)
                .build();

        speechClient = SpeechClient.create(speechSettings);
    }

    public String transcribeAudio(MultipartFile file) throws IOException {
        ByteString audioBytes = ByteString.readFrom(file.getInputStream());

        RecognitionConfig config = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)  // 녹음 포맷에 맞게
                .setSampleRateHertz(16000)
                .setLanguageCode("ko-KR")
                .build();

        RecognitionAudio audio = RecognitionAudio.newBuilder()
                .setContent(audioBytes)
                .build();

        RecognizeResponse response = speechClient.recognize(config, audio);

        StringBuilder resultText = new StringBuilder();
        for (SpeechRecognitionResult result : response.getResultsList()) {
            resultText.append(result.getAlternativesList().get(0).getTranscript());
        }

        return resultText.toString();
    }

    // 컨트롤러에선 speechClient.close() 호출이 필요할 수 있으니 적절히 관리하세요
}