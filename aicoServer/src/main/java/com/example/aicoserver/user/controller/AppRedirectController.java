//package com.example.aicoserver.user.controller;
//
//import org.springframework.web.bind.annotation.PathVariable;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.HashMap;
//import java.util.Map;
//
//@RestController
//public class AppRedirectController {
//
//    // 루트 접속 시 안내 페이지
//    @GetMapping("/")
//    public Map<String, String> home() {
//        Map<String, String> map = new HashMap<>();
//        map.put("message", "테스트용 서버입니다. /history/{userId}/{historyId}로 접근하세요.");
//        return map;
//    }
//
//    // 딥링크 처리용 (앱에서 intent로 직접 URL을 받도록)
//    @GetMapping("/history/{userId}/{historyId}")
//    public Map<String, String> handleAndroidDeepLink(
//            @PathVariable String userId,
//            @PathVariable String historyId) {
//
//        Map<String, String> result = new HashMap<>();
//
//        if (userId == null || userId.isEmpty() || historyId == null || historyId.isEmpty()) {
//            result.put("error", "Invalid IDs");
//            return result;
//        }
//
//        // 테스트용 데이터 반환
//        result.put("userId", userId);
//        result.put("historyId", historyId);
//        result.put("note", "앱에서 이 데이터를 받아 UI 갱신 가능");
//
//        // ⚠️ 기존 302 리다이렉트 제거
//        return result;
//    }
//}
