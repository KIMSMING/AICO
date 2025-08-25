package com.example.aicoserver.user.controller;

import com.example.aicoserver.user.service.SttService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/stt")
public class SttController {

    @Autowired
    private SttService sttService;

    @PostMapping("/stt")
    public String handleSTT(@RequestParam("file") MultipartFile file) {
        try {
            return sttService.transcribeAudio(file);
        } catch (IOException e) {
            e.printStackTrace();
            return "STT 변환 실패: " + e.getMessage();
        }
    }
}
