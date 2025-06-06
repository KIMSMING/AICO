package com.seoja.aico.reviewBoard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
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
import com.seoja.aico.ApiService;
import com.seoja.aico.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class UpdateBoardActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private EditText editTitle, editContent;
    private Button btnAddImage, btnUpdate, btnCancel;
    private RecyclerView rvImages;

    private final List<Uri> imageUriList = new ArrayList<>();
    private ImageAdapter imageAdapter;
    private String postKey;
    private String oldImageUrl = "";

    // Firebase
    private DatabaseReference boardRef;

    // Retrofit 서비스
    private ApiService apiService;

    // 이미지 선택
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

        // UI 바인딩
        btnBack = findViewById(R.id.btnBack);
        editTitle = findViewById(R.id.editTitle);
        editContent = findViewById(R.id.editContent);
        btnAddImage = findViewById(R.id.btnAddImage);
        btnUpdate = findViewById(R.id.btnUpdate);
        btnCancel = findViewById(R.id.btnCancel);
        rvImages = findViewById(R.id.rvImages);

        // Firebase 초기화
        boardRef = FirebaseDatabase.getInstance().getReference("board");

        // Retrofit 초기화
        String baseUrl = "http://" + getString(R.string.server_url) + "/";
        apiService = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);

        // 게시글 키 확인
        postKey = getIntent().getStringExtra("postKey");
        if (postKey == null) {
            Toast.makeText(this, "잘못된 접근", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 뒤로가기/취소 버튼
        btnBack.setOnClickListener(v -> finish());
        btnCancel.setOnClickListener(v -> finish());

        // 이미지 어댑터 설정
        imageAdapter = new ImageAdapter(imageUriList, position -> {
            imageUriList.remove(position);
            imageAdapter.notifyDataSetChanged();
        });
        rvImages.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvImages.setAdapter(imageAdapter);

        // 이미지 추가 버튼
        btnAddImage.setOnClickListener(v -> openImagePicker());

        // 수정 버튼
        btnUpdate.setOnClickListener(v -> updatePost());

        // 기존 데이터 로드
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
                    Toast.makeText(UpdateBoardActivity.this, "게시글 없음", Toast.LENGTH_SHORT).show();
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
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updatePost() {
        String title = editTitle.getText().toString().trim();
        String content = editContent.getText().toString().trim();

        if (title.isEmpty() || content.isEmpty()) {
            Toast.makeText(this, "제목/내용 필수", Toast.LENGTH_SHORT).show();
            return;
        }

        btnUpdate.setEnabled(false);

        // 새 이미지가 있는 경우 업로드
        if (!imageUriList.isEmpty() && !isOldImage(imageUriList.get(0))) {
            uploadNewImage(imageUriList.get(0));
        } else {
            saveUpdatedPost(title, content, oldImageUrl);
        }
    }

    private boolean isOldImage(Uri uri) {
        return uri.toString().equals(oldImageUrl);
    }

    private void uploadNewImage(Uri newImageUri) {
        String filePath = FileUtils.getPath(this, newImageUri);
        if (filePath == null) {
            handleError("이미지 파일 경로를 찾을 수 없습니다.");
            return;
        }
        File file = new File(filePath);
        RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);

        apiService.uploadImage(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String newImageUrl = response.body().string();
                        deleteOldImageIfNeeded(newImageUrl);
                        saveUpdatedPost(editTitle.getText().toString(),
                                editContent.getText().toString(),
                                newImageUrl);
                    } catch (IOException e) {
                        handleError("이미지 처리 오류: " + e.getMessage());
                    }
                } else {
                    handleError("서버 오류: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                handleError("네트워크 오류: " + t.getMessage());
            }
        });
    }

    private void deleteOldImageIfNeeded(String newImageUrl) {
        if (!oldImageUrl.isEmpty() && !oldImageUrl.equals(newImageUrl)) {
            // 서버에 이미지 삭제 요청 (Retrofit 인터페이스에서 @DELETE, @Query("url") String imageUrl 필요)
            apiService.deleteImage(oldImageUrl).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> call, Response<Void> response) {}
                @Override public void onFailure(Call<Void> call, Throwable t) {}
            });
        }
    }

    private void saveUpdatedPost(String title, String content, String imageUrl) {
        boardRef.child(postKey).child("title").setValue(title);
        boardRef.child(postKey).child("content").setValue(content);
        boardRef.child(postKey).child("imageUrl").setValue(imageUrl)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "수정 성공!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> handleError("저장 실패: " + e.getMessage()));
    }

    private void handleError(String message) {
        btnUpdate.setEnabled(true);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // FileUtils: SAF 미대응, 기본 갤러리/사진만 지원
    public static class FileUtils {
        public static String getPath(Context context, Uri uri) {
            String[] projection = { MediaStore.Images.Media.DATA };
            Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null) {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                cursor.moveToFirst();
                String path = cursor.getString(column_index);
                cursor.close();
                return path;
            }
            return null;
        }
    }
}
