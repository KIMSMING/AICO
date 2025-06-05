package com.seoja.aico.reviewBoard;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.seoja.aico.R;

import java.util.ArrayList;
import java.util.List;

public class UpdateBoardActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private EditText editTitle, editContent;
    private Button btnAddImage, btnUpdate, btnCancel;
    private RecyclerView rvImages;

    private final List<Uri> imageUriList = new ArrayList<>();
    private ImageAdapter imageAdapter;

    // 게시글 고유키
    private String postKey;
    private String oldImageUrl = "";

    private DatabaseReference boardRef;
    private StorageReference storageRef;

    // 이미지 선택 결과 처리
    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        imageUriList.clear();
                        imageUriList.add(imageUri);
                        imageAdapter.notifyDataSetChanged();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_update_board);

        btnBack = findViewById(R.id.btnBack);
        editTitle = findViewById(R.id.editTitle);
        editContent = findViewById(R.id.editContent);
        btnAddImage = findViewById(R.id.btnAddImage);
        btnUpdate = findViewById(R.id.btnUpdate);
        btnCancel = findViewById(R.id.btnCancel);
        rvImages = findViewById(R.id.rvImages);

        // Firebase 참조
        boardRef = FirebaseDatabase.getInstance().getReference("board");
        storageRef = FirebaseStorage.getInstance().getReference("boardImages");

        // 게시글 고유키 받아오기
        postKey = getIntent().getStringExtra("postKey");
        if (postKey == null) {
            Toast.makeText(this, "잘못된 접근입니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 뒤로가기 및 취소
        btnBack.setOnClickListener(v -> finish());
        btnCancel.setOnClickListener(v -> finish());

        // 이미지 RecyclerView 세팅
        imageAdapter = new ImageAdapter(imageUriList, position -> {
            imageUriList.remove(position);
            imageAdapter.notifyDataSetChanged();
        });
        rvImages.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvImages.setAdapter(imageAdapter);

        // 사진 추가
        btnAddImage.setOnClickListener(v -> openImagePicker());

        // 수정 완료
        btnUpdate.setOnClickListener(v -> updatePost());

        // 기존 게시글 데이터 불러오기
        loadPostData();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void loadPostData() {
        boardRef.child(postKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                BoardPost post = snapshot.getValue(BoardPost.class);
                if (post == null) {
                    Toast.makeText(UpdateBoardActivity.this, "게시글이 존재하지 않습니다.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                editTitle.setText(post.title);
                editContent.setText(post.content);
                oldImageUrl = post.imageUrl != null ? post.imageUrl : "";
                imageUriList.clear();
                if (!oldImageUrl.isEmpty()) {
                    imageUriList.add(Uri.parse(oldImageUrl));
                }
                imageAdapter.notifyDataSetChanged();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updatePost() {
        String title = editTitle.getText().toString().trim();
        String content = editContent.getText().toString().trim();

        if (title.isEmpty()) {
            Toast.makeText(this, "제목을 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (content.isEmpty()) {
            Toast.makeText(this, "내용을 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnUpdate.setEnabled(false);

        // 이미지가 새로 첨부되었는지 확인
        if (!imageUriList.isEmpty() && (imageUriList.get(0).getScheme() == null || !"http".equals(imageUriList.get(0).getScheme()) && !"https".equals(imageUriList.get(0).getScheme()))) {
            // 새 이미지 업로드
            Uri newImageUri = imageUriList.get(0);
            String imageFileName = "IMG_" + System.currentTimeMillis() + ".jpg";
            StorageReference imageRef = storageRef.child(imageFileName);
            imageRef.putFile(newImageUri)
                    .addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        // 기존 이미지가 있으면 삭제
                        if (!oldImageUrl.isEmpty()) {
                            StorageReference oldImageRef = FirebaseStorage.getInstance().getReferenceFromUrl(oldImageUrl);
                            oldImageRef.delete();
                        }
                        saveUpdatedPost(title, content, uri.toString());
                    }))
                    .addOnFailureListener(e -> {
                        btnUpdate.setEnabled(true);
                        Toast.makeText(this, "이미지 업로드 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } else {
            saveUpdatedPost(title, content, oldImageUrl);
        }
    }

    private void saveUpdatedPost(String title, String content, String imageUrl) {
        boardRef.child(postKey).child("title").setValue(title);
        boardRef.child(postKey).child("content").setValue(content);
        boardRef.child(postKey).child("imageUrl").setValue(imageUrl)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "게시글이 수정되었습니다!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnUpdate.setEnabled(true);
                    Toast.makeText(this, "게시글 수정 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
