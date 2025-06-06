package com.seoja.aico.reviewBoard;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.seoja.aico.ApiService;
import com.seoja.aico.R;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class AddBoardActivity extends AppCompatActivity {

    private EditText editPostTitle, editPostContent;
    private Button btnUpload, btnAddImage;
    private ImageButton btnBack;
    private ImageView imagePreview;

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
        btnAddImage = findViewById(R.id.btnAddImage);
        imagePreview = findViewById(R.id.imagePreview);
        btnUpload = findViewById(R.id.btnUpload);

        // Firebase 참조
        boardRef = FirebaseDatabase.getInstance().getReference("board");
        storageRef = FirebaseStorage.getInstance().getReference("boardImages");

        // 뒤로가기 버튼
        btnBack.setOnClickListener(v -> finish());

        // 이미지 첨부 버튼
        btnAddImage.setOnClickListener(v -> openImagePicker());

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
            imagePreview.setVisibility(View.VISIBLE);
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                imagePreview.setImageBitmap(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "이미지 로드 실패", Toast.LENGTH_SHORT).show();
            }
        } else {
            imagePreview.setVisibility(View.GONE);
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

        String baseUrl = "http://" + getString(R.string.server_url) + "/";

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ApiService apiService = retrofit.create(ApiService.class);

        if (selectedImageUri != null) {
            String filePath = getRealPathFromURI(selectedImageUri);
            if (filePath == null) {
                Toast.makeText(this, "이미지 파일 경로를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                btnUpload.setEnabled(true);
                return;
            }
            File file = new File(getRealPathFromURI(selectedImageUri));
            RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), file);
            MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);

            Call<ResponseBody> call = apiService.uploadImage(body);
            call.enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            String imageUrl = response.body().string(); // 서버에서 반환한 이미지 URL
                            // 1. 게시글 데이터에 이미지 URL 저장
                            savePostToDatabase(title, content, user, imageUrl);
                            // 2. ImageView에 바로 이미지 표시 (예: imagePreview가 ImageView인 경우)
                            Glide.with(AddBoardActivity.this)
                                    .load(imageUrl)
                                    .into(imagePreview);

                            Toast.makeText(AddBoardActivity.this, "이미지 업로드 성공!", Toast.LENGTH_SHORT).show();
                        } catch (IOException e) {
                            e.printStackTrace();
                            Toast.makeText(AddBoardActivity.this, "이미지 처리 오류", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(AddBoardActivity.this, "이미지 업로드 실패: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Toast.makeText(AddBoardActivity.this, "서버 연결 실패: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });

        } else {
            savePostToDatabase(title, content, user, "");
        }
    }
    //  Content Uri를 실제 파일 경로로 변환 (API 19 이상)
    private String getRealPathFromURI(Uri uri) {
        String filePath = null;
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(uri, proj, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                filePath = cursor.getString(column_index);
            }
            cursor.close();
        }
        // SAF 방식 등 다른 경로가 필요하면 추가 구현 필요
        return filePath;
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
