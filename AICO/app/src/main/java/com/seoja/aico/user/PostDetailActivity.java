package com.seoja.aico.user;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.seoja.aico.R; // R 클래스 import

import java.util.List;

public class PostDetailActivity extends AppCompatActivity {

    private ImageView profileImageView;
    private TextView userNameTextView, questionTextView, answerTextView, feedbackTextView;
    private DatabaseReference historyRef, usersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);

        // Firebase 데이터베이스 참조 초기화
        historyRef = FirebaseDatabase.getInstance().getReference("history");
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        // UI 요소 초기화
        profileImageView = findViewById(R.id.profileImageView);
        userNameTextView = findViewById(R.id.userNameTextView);
        questionTextView = findViewById(R.id.questionTextView);
        answerTextView = findViewById(R.id.answerTextView);
        feedbackTextView = findViewById(R.id.feedbackTextView);

        handleIntent(getIntent());
    }

    private void handleIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;

        Uri dataUri = intent.getData();
        List<String> pathSegments = dataUri.getPathSegments();

        if (pathSegments.size() >= 2) { // 최소 2개 이상만 있으면 처리
            String userId = pathSegments.get(0);
            String historyId = pathSegments.get(1);

            loadUserData(userId);
            loadHistoryData(userId, historyId);
        } else {
            Toast.makeText(this, "잘못된 링크 형식입니다.", Toast.LENGTH_SHORT).show();
        }
    }


    // 이 메서드가 UserViewActivity의 로직을 적용하는 부분입니다.
    private void loadUserData(String userId) {
        // UserViewActivity와 동일하게 "users" 경로에서 userId로 데이터를 찾습니다.
        usersRef.child(userId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                DataSnapshot userSnapshot = task.getResult();

                // UserViewActivity에서 사용한 필드명과 동일하게 'name'과 'photoUrl'을 가져옵니다.
                String name = userSnapshot.child("name").getValue(String.class);
                String photoUrl = userSnapshot.child("photoUrl").getValue(String.class);

                // 가져온 데이터로 UI를 업데이트합니다.
                userNameTextView.setText(name);

                // UserViewActivity와 동일하게 Glide를 사용해 프로필 사진을 표시합니다.
                if (photoUrl != null && !photoUrl.isEmpty()) {
                    Glide.with(this)
                            .load(photoUrl)
                            .circleCrop()
                            .placeholder(R.drawable.ic_person) // 기본 이미지 (ic_person.xml 필요)
                            .into(profileImageView);
                } else {
                    profileImageView.setImageResource(R.drawable.ic_person);
                }

            } else {
                userNameTextView.setText("알 수 없는 사용자");
            }
        });
    }

    private void loadHistoryData(String userId, String historyId) {
        historyRef.child(userId).child(historyId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                DataSnapshot historySnapshot = task.getResult();
                String question = historySnapshot.child("question").getValue(String.class);
                String answer = historySnapshot.child("answer").getValue(String.class);
                String feedback = historySnapshot.child("feedback").getValue(String.class);

                questionTextView.setText(question);
                answerTextView.setText(answer);
                feedbackTextView.setText(feedback);
            } else {
                Toast.makeText(this, "면접 기록을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}