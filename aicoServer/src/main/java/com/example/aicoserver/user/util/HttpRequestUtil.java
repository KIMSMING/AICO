//package com.example.aicoserver.user.util;
//
//import org.springframework.http.*;
//import org.springframework.web.client.RestTemplate;
//
//public class HttpRequestUtil {
//    private static final RestTemplate restTemplate = new RestTemplate();
//
//    // GET 요청
//    public static <T> ResponseEntity<T> get(String url, HttpHeaders headers, Class<T> responseType) {
//        HttpEntity<?> entity = new HttpEntity<>(headers);
//        return restTemplate.exchange(url, HttpMethod.GET, entity, responseType);
//    }
//
//    // POST 요청
//    public static <T> ResponseEntity<T> post(String url, HttpHeaders headers, Object body, Class<T> responseType) {
//        HttpEntity<?> entity = new HttpEntity<>(body, headers);
//        return restTemplate.exchange(url, HttpMethod.POST, entity, responseType);
//    }
//}
