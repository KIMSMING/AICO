package com.seoja.aico.user;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

/**
 * 소셜 회원가입 처리 클래스
 * - FirebaseUser 정보를 기반으로 Realtime Database에 사용자 데이터 저장
 * - 닉네임, 이메일, 프로필 사진 등 기본 정보 저장
 * - 추가 정보(생일, 성별 등)는 기본값으로 초기화
 */
public class SocialRegisterImpl {

    private static final String TAG = "SocialRegisterImpl";

    // 데이터베이스 필드명 상수
    private static final String FIELD_UID = "uid";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_NICKNAME = "nickname";
    private static final String FIELD_BIRTH = "birth";
    private static final String FIELD_GENDER = "gender";
    private static final String FIELD_ADDRESS = "address";
    private static final String FIELD_PHONE = "phone";
    private static final String FIELD_PHOTO_URL = "photoUrl";
    private static final String FIELD_CREATED_AT = "createdAt";

    /**
     * 소셜 회원가입 처리 메서드
     * @param user FirebaseUser 객체
     * @param callback 회원가입 결과 콜백
     */
    public static void registerUser(@NonNull FirebaseUser user, @NonNull RegisterCallback callback) {
        if (user.getUid() == null || user.getUid().isEmpty()) {
            callback.onFailure("FirebaseUser UID가 유효하지 않습니다.");
            return;
        }

        DatabaseReference database = FirebaseDatabase.getInstance().getReference("users");
        String uid = user.getUid();

        Map<String, Object> userMap = new HashMap<>();
        userMap.put(FIELD_UID, uid);
        userMap.put(FIELD_EMAIL, user.getEmail() != null ? user.getEmail() : "");
        userMap.put(FIELD_NAME, user.getDisplayName() != null ? user.getDisplayName() : "");
        userMap.put(FIELD_NICKNAME, generateDefaultNickname(uid));
        userMap.put(FIELD_BIRTH, "");
        userMap.put(FIELD_GENDER, "");
        userMap.put(FIELD_ADDRESS, "");
        userMap.put(FIELD_PHONE, "");
        userMap.put(FIELD_PHOTO_URL, user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "");
        userMap.put(FIELD_CREATED_AT, System.currentTimeMillis());

        database.child(uid).setValue(userMap)
                .addOnSuccessListener(aVoid -> {
                    Log.i(TAG, "소셜 회원가입 성공 | UID: " + uid);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "소셜 회원가입 실패 | UID: " + uid + " | Error: " + e.getMessage());
                    callback.onFailure(e.getMessage());
                });
    }

    private static String generateDefaultNickname(String uid) {
        String suffix = uid.length() > 6 ? uid.substring(uid.length() - 6) : uid;
        return "User" + suffix;
    }

    public interface RegisterCallback {
        void onSuccess();
        void onFailure(String errorMsg);
    }
}
