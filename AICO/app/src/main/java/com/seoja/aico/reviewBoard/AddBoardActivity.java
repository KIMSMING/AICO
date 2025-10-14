package com.seoja.aico.reviewBoard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.seoja.aico.R;

import java.io.IOException;
import java.util.HashMap;

public class AddBoardActivity extends AppCompatActivity {

    private TextView titleTextView;
    private EditText editPostTitle, editPostContent;
    private Button btnUpload, btnAddImage;
    private ImageButton btnBack;
//    private ImageView imagePreview;

    private Uri selectedImageUri = null;

    // Firebase
    private DatabaseReference boardRef;
    private StorageReference storageRef;

    // 이미지 선택 결과 처리
    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    showImagePreview(selectedImageUri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_board);

        // 바인딩
        btnBack = findViewById(R.id.btnBack);
        editPostTitle = findViewById(R.id.editPostTitle);
        editPostContent = findViewById(R.id.editPostContent);
//        btnAddImage = findViewById(R.id.btnAddImage);
//        imagePreview = findViewById(R.id.imagePreview);
        btnUpload = findViewById(R.id.btnUpload);
        titleTextView = findViewById(R.id.header_title);

        titleTextView.setText("후기 추가");

        // Firebase 참조
        boardRef = FirebaseDatabase.getInstance().getReference("board");
        storageRef = FirebaseStorage.getInstance().getReference("boardImages");

        // 뒤로가기 버튼
        btnBack.setOnClickListener(v -> finish());

        // 이미지 첨부 버튼
//        btnAddImage.setOnClickListener(v -> openImagePicker());

        // 업로드 버튼
        btnUpload.setOnClickListener(v -> uploadPost());
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void showImagePreview(Uri imageUri) {
        if (imageUri != null) {
//            imagePreview.setVisibility(View.VISIBLE);
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
//                imagePreview.setImageBitmap(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "이미지 로드 실패", Toast.LENGTH_SHORT).show();
            }
        } else {
//            imagePreview.setVisibility(View.GONE);
        }
    }

    private void uploadPost() {
        String title = editPostTitle.getText().toString().trim();
        String content = editPostContent.getText().toString().trim();

        if (title.isEmpty()) {
            Toast.makeText(this, "제목을 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (content.isEmpty()) {
            Toast.makeText(this, "내용을 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnUpload.setEnabled(false);

        if (selectedImageUri != null) {
            // 1. 이미지를 Storage에 업로드
            String imageFileName = "IMG_" + System.currentTimeMillis() + ".jpg";
            StorageReference imageRef = storageRef.child(imageFileName);
            imageRef.putFile(selectedImageUri)
                    .addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        // 2. 이미지 URL과 함께 게시글 데이터 저장
                        savePostToDatabase(title, content, user, uri.toString());
                    }))
                    .addOnFailureListener(e -> {
                        btnUpload.setEnabled(true);
                        Toast.makeText(this, "이미지 업로드 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } else {
            // 이미지 없이 게시글만 저장
            savePostToDatabase(title, content, user, "");
        }
    }

    private void savePostToDatabase(String title, String content, FirebaseUser user, String imageUrl) {
        String uniqueKey = String.valueOf(System.currentTimeMillis());

        // 사용자 닉네임 조회
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());
        userRef.child("nickname").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String nickname = snapshot.exists() ? snapshot.getValue(String.class) : "";

                BoardPost post = new BoardPost(
                        uniqueKey,
                        title,
                        content,
                        user.getUid(),
                        user.getDisplayName() != null ? user.getDisplayName() : user.getEmail(),
                        nickname,
                        System.currentTimeMillis(),
                        imageUrl,
                        0,
                        new HashMap<>()
                );


                boardRef.child(uniqueKey).setValue(post)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(AddBoardActivity.this, "게시글이 업로드되었습니다!", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            btnUpload.setEnabled(true);
                            Toast.makeText(AddBoardActivity.this, "게시글 저장 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                btnUpload.setEnabled(true);
                Toast.makeText(AddBoardActivity.this, "닉네임 조회 실패: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
