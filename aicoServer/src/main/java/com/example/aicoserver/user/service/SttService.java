package com.example.aicoserver.user.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.io.ClassPathResource; // ClassPathResource를 사용한다면
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class SttService implements DisposableBean { // DisposableBean 구현

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

    public void testAudioConversion() {
        // 1. 테스트할 m4a 파일을 서버의 특정 경로에 미리 넣어두세요.
        //    예: C:/temp/test.m4a 또는 /home/user/test.m4a
        Path sourcePath = Paths.get("C:/Temp/test.m4a"); // 본인 환경에 맞게 경로 수정!
        File sourceFile = sourcePath.toFile();

        // 2. 변환될 wav 파일 경로 설정
        File targetFile = new File(sourceFile.getParent(), "converted_test.wav");

        System.out.println("테스트 시작: " + sourceFile.getAbsolutePath() + " 파일 변환 시도...");

        if (!sourceFile.exists()) {
            System.out.println("오류: 원본 테스트 파일이 존재하지 않습니다!");
            return;
        }

        try {
            AudioAttributes audio = new AudioAttributes();
            audio.setCodec("pcm_s16le");
            audio.setChannels(1);
            audio.setSamplingRate(16000);

            EncodingAttributes attrs = new EncodingAttributes();
            attrs.setOutputFormat("wav");
            attrs.setAudioAttributes(audio);

            Encoder encoder = new Encoder();
            encoder.encode(new MultimediaObject(sourceFile), targetFile, attrs);

            System.out.println("✅ 변환 성공! 생성된 파일: " + targetFile.getAbsolutePath());
            System.out.println("생성된 파일 크기: " + targetFile.length() + " bytes");

        } catch (Exception e) {
            System.out.println("❌ 변환 실패! 오류 발생:");
            e.printStackTrace();
        }
    }

    public String transcribeAudio(MultipartFile file) throws Exception {
        // 1. 수신된 MultipartFile을 임시 m4a 파일로 저장
        File sourceM4a = Files.createTempFile("source_", ".m4a").toFile();
        file.transferTo(sourceM4a);

        // 2. 변환될 wav 파일 경로 설정
        File targetWav = Files.createTempFile("target_", ".wav").toFile();

        try {
            // 3. 오디오 변환 설정 (WAV, PCM 16-bit, 16000Hz, 1채널)
            AudioAttributes audio = new AudioAttributes();
            audio.setCodec("pcm_s16le"); // LINEAR16에 해당하는 코덱
            audio.setChannels(1); // 모노 채널
            audio.setSamplingRate(16000); // 샘플링 레이트

            EncodingAttributes attrs = new EncodingAttributes();
            attrs.setOutputFormat("wav");
            attrs.setAudioAttributes(audio);

            // 4. 변환 실행
            Encoder encoder = new Encoder();
            encoder.encode(new MultimediaObject(sourceM4a), targetWav, attrs);

            // 5. 변환된 wav 파일의 byte를 읽어 STT API로 전송
            ByteString audioBytes = ByteString.copyFrom(Files.readAllBytes(targetWav.toPath()));

            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16) // 이제 형식이 일치함
                    .setSampleRateHertz(16000)
                    .setLanguageCode("ko-KR")
                    .build();

            RecognitionAudio recognitionAudio = RecognitionAudio.newBuilder()
                    .setContent(audioBytes)
                    .build();

            // 6. STT 실행 및 결과 반환
            RecognizeResponse response = speechClient.recognize(config, recognitionAudio);

            System.out.println("Google STT API 응답: " + response);

            StringBuilder resultText = new StringBuilder();
            for (SpeechRecognitionResult result : response.getResultsList()) {
                resultText.append(result.getAlternativesList().get(0).getTranscript());
            }
            return resultText.toString();

        } finally {
            // 7. 임시 파일 삭제
            sourceM4a.delete();
            targetWav.delete();
        }
    }

    @Override
    public void destroy() throws Exception {
        if (speechClient != null) {
            System.out.println("Shutting down SpeechClient.");
            speechClient.close();
        }
    }
}