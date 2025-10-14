package com.seoja.aico.reviewBoard;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.seoja.aico.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BoardActivity extends AppCompatActivity {

    private Button btnEdit, btnDelete;
    private ImageButton btnBack, btnLike;
    private TextView textPostTitle, textPostInfo, textPostContent, tvLikes, titleTextView;
    private ImageView imagePost;

    // Firebase
    private DatabaseReference boardRef;
    private StorageReference storageRef;

    // 게시글 데이터
    private String postKey;
    private String authorUid;
    private String imageUrl;
    private boolean isLiked = false;
    private int likeCount = 0;

    private ValueEventListener likeListener;

    // ResultLauncher: 수정 후 결과 받기
    private final ActivityResultLauncher<Intent> updateLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    loadPostData();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_board);

        // 바인딩
        btnBack = findViewById(R.id.btnBack);
        btnEdit = findViewById(R.id.btnEdit);
        btnDelete = findViewById(R.id.btnDelete);
        btnLike = findViewById(R.id.btnLike);
        textPostTitle = findViewById(R.id.textPostTitle);
        textPostInfo = findViewById(R.id.textPostInfo);
        textPostContent = findViewById(R.id.textPostContent);
        imagePost = findViewById(R.id.imagePost);
        tvLikes = findViewById(R.id.tvLikes);
        titleTextView = findViewById(R.id.header_title);

        titleTextView.setText("면접 후기");

        // Firebase 참조
        boardRef = FirebaseDatabase.getInstance().getReference("board");
        storageRef = FirebaseStorage.getInstance().getReference();

        // Intent에서 postKey 받아오기
        postKey = getIntent().getStringExtra("postKey");
        if (postKey == null) {
            Toast.makeText(this, "잘못된 접근입니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 뒤로가기
        btnBack.setOnClickListener(v -> finish());

        // 게시글 데이터 로드
        loadPostData();

        // 수정 버튼 클릭 리스너
        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(BoardActivity.this, UpdateBoardActivity.class);
            intent.putExtra("postKey", postKey);
            updateLauncher.launch(intent);
        });

        // 삭제 버튼 클릭 리스너
        btnDelete.setOnClickListener(v -> deletePost());

        // 좋아요 버튼 클릭 리스너
        btnLike.setOnClickListener(v -> toggleLike());
    }

    private void loadPostData() {
        boardRef.child(postKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                BoardPost post = snapshot.getValue(BoardPost.class);
                if (post == null) {
                    Toast.makeText(BoardActivity.this, "게시글이 존재하지 않습니다.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                // 데이터 바인딩
                textPostTitle.setText(post.title);
                textPostInfo.setText(post.nickname + " · " + formatDate(post.createdAt));
                textPostContent.setText(post.content);
                tvLikes.setText(String.valueOf(post.likes));
                authorUid = post.authorUid;
                imageUrl = post.imageUrl;
                likeCount = post.likes;

                // 이미지 로드 (예: Glide 사용)
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    imagePost.setVisibility(View.VISIBLE);
                    // Glide.with(BoardActivity.this).load(imageUrl).into(imagePost);
                } else {
                    imagePost.setVisibility(View.GONE);
                }

                // 좋아요 상태 확인
                checkLikeStatus(post);

                // 작성자 확인 후 버튼 표시
                checkCurrentUser();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(BoardActivity.this, "데이터 불러오기 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkLikeStatus(BoardPost post) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            isLiked = false;
            updateLikeButton();
            return;
        }
        isLiked = post.likedUsers != null && post.likedUsers.containsKey(user.getUid());
        updateLikeButton();
    }

    private void toggleLike() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "로그인이 필요합니다", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference postRef = boardRef.child(postKey);
        postRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData mutableData) {
                BoardPost post = mutableData.getValue(BoardPost.class);
                if (post == null) return Transaction.success(mutableData);

                if (post.likedUsers == null) {
                    post.likedUsers = new java.util.HashMap<>();
                }

                if (post.likedUsers.containsKey(user.getUid())) {
                    // 좋아요 취소
                    post.likes = Math.max(0, post.likes - 1);
                    post.likedUsers.remove(user.getUid());
                } else {
                    // 좋아요 추가
                    post.likes++;
                    post.likedUsers.put(user.getUid(), true);
                }
                mutableData.setValue(post);
                setResult(RESULT_OK);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (error != null) {
                    Toast.makeText(BoardActivity.this, "좋아요 실패: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                } else {
                    // 즉시 UI 반영
                    loadPostData();
                }
            }
        });
    }

    private void updateLikeButton() {
        if (isLiked) {
            btnLike.setImageResource(R.drawable.ic_heart_fill);
        } else {
            btnLike.setImageResource(R.drawable.ic_heart);
        }
        tvLikes.setText(String.valueOf(likeCount));
    }

    private void checkCurrentUser() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getUid().equals(authorUid)) {
            btnEdit.setVisibility(View.VISIBLE);
            btnDelete.setVisibility(View.VISIBLE);
        } else {
            btnEdit.setVisibility(View.GONE);
            btnDelete.setVisibility(View.GONE);
        }
    }

    private void deletePost() {
        boardRef.child(postKey).removeValue()
                .addOnSuccessListener(aVoid -> {
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        StorageReference imageRef = FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl);
                        imageRef.delete();
                    }
                    Toast.makeText(this, "게시글이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "삭제 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
