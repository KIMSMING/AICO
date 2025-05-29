//package com.example.aicoserver.user.util;
//
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.auth.FirebaseAuthException;
//import java.util.Map;
//
//public class FirebaseTokenUtil {
//    // 기본 커스텀 토큰 생성
//    public static String createCustomToken(String uid) throws FirebaseAuthException {
//        return FirebaseAuth.getInstance().createCustomToken(uid);
//    }
//
//    // 클레임 포함 커스텀 토큰 생성
//    public static String createCustomToken(String uid, Map<String, Object> claims) throws FirebaseAuthException {
//        return FirebaseAuth.getInstance().createCustomToken(uid, claims);
//    }
//}
