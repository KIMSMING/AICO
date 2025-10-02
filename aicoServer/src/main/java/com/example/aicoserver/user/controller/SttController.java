package com.example.aicoserver.user.controller;

import com.example.aicoserver.user.service.SttService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/stt")
public class SttController {

    @Autowired
    private SttService sttService;

    public SttController(SttService sttService) {
        this.sttService = sttService;
    }

    @PostMapping("/trans")
    public ResponseEntity<String> transcribeAudioFile(@RequestParam("file") MultipartFile file) {
        // 안드로이드에서 보낸 'file'이라는 이름의 데이터를 MultipartFile 객체로 받음
        try {
            if (file.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File is empty");
            }
            String transcript = sttService.transcribeAudio(file);
            return ResponseEntity.ok(transcript);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing audio: " + e.getMessage());
        }
    }

    @GetMapping("/test-conversion")
    public String testConversion() {
        sttService.testAudioConversion();
        return "Conversion test executed. Check server logs.";
    }

}
