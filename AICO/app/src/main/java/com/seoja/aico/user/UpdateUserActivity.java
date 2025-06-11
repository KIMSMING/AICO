package com.seoja.aico.user;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.seoja.aico.R;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import android.text.TextWatcher;
import android.text.Editable;

public class UpdateUserActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;

    private ImageView imageProfile, btnBack;
    private Button btnChangeProfile;
    private EditText editTextNickname, editTextEmail, editTextName, editTextBirth, editTextAddress, editTextPhone;
    private RadioGroup radioGroupGender;
    private TextView btnUpdate;

    private Uri imageUri;
    private String photoUrl;

    private DatabaseReference database;
    private FirebaseUser currentUser;
    private StorageReference storageRef;

    // 원래 값 저장
    private String originalNickname, originalName, originalBirth, originalGender, originalAddress, originalPhone, originalPhotoUrl;
    private boolean isChanged = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_user);

        imageProfile = findViewById(R.id.imageProfile);
//        btnChangeProfile = findViewById(R.id.btnChangeProfile);
        btnUpdate = findViewById(R.id.btnUpdate);
        btnBack = findViewById(R.id.btnBack);
        editTextNickname = findViewById(R.id.editTextNickname);
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextName = findViewById(R.id.editTextName);
        editTextBirth = findViewById(R.id.editTextBirth);
        editTextAddress = findViewById(R.id.editTextAddress);
        editTextPhone = findViewById(R.id.editTextPhone);
        radioGroupGender = findViewById(R.id.radioGroupGender);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        database = FirebaseDatabase.getInstance().getReference("users");
        storageRef = FirebaseStorage.getInstance().getReference("profile_images");

        // 기존 사용자 정보 불러오기
        loadUserInfo(currentUser.getUid());

        // 프로필 사진 변경
        btnChangeProfile.setOnClickListener(v -> openImagePicker());

        // 수정 완료
        btnUpdate.setOnClickListener(v -> updateUserInfo());

        // 취소 버튼
        btnBack.setOnClickListener(v -> finish());

        // 처음엔 비활성화
        btnUpdate.setEnabled(false);
        btnUpdate.setBackgroundTintList(getResources().getColorStateList(android.R.color.darker_gray));

        // 변경 감지 리스너 등록 (초기값 세팅 후에도 다시 등록함)
        setupChangeWatchers();
    }

    private void loadUserInfo(String uid) {
        database.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                originalNickname = snapshot.child("nickname").getValue(String.class);
                originalName = snapshot.child("name").getValue(String.class);
                originalBirth = snapshot.child("birth").getValue(String.class);
                originalGender = snapshot.child("gender").getValue(String.class);
                originalAddress = snapshot.child("address").getValue(String.class);
                originalPhone = snapshot.child("phone").getValue(String.class);
                originalPhotoUrl = snapshot.child("photoUrl").getValue(String.class);

                editTextNickname.setText(originalNickname);
                editTextEmail.setText(snapshot.child("email").getValue(String.class));
                editTextName.setText(originalName);
                editTextBirth.setText(originalBirth);
                editTextAddress.setText(originalAddress);
                editTextPhone.setText(originalPhone);

                if (originalGender != null) {
                    if (originalGender.equals("M")) radioGroupGender.check(R.id.radioMale);
                    else if (originalGender.equals("F")) radioGroupGender.check(R.id.radioFemale);
                }
                if (originalPhotoUrl != null && !originalPhotoUrl.isEmpty()) {
                    Glide.with(UpdateUserActivity.this)
                            .load(originalPhotoUrl)
                            .placeholder(R.drawable.ic_person)
                            .into(imageProfile);
                } else {
                    imageProfile.setImageResource(R.drawable.ic_person);
                }

                // 값 세팅 후 변경 감지 리스너 등록
                setupChangeWatchers();
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void setupChangeWatchers() {
        TextWatcher watcher = new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkIfChanged();
            }
        };
        editTextNickname.addTextChangedListener(watcher);
        editTextName.addTextChangedListener(watcher);
        editTextBirth.addTextChangedListener(watcher);
        editTextAddress.addTextChangedListener(watcher);
        editTextPhone.addTextChangedListener(watcher);

        radioGroupGender.setOnCheckedChangeListener((group, checkedId) -> checkIfChanged());
        // 프로필 이미지 변경 시에도 checkIfChanged() 호출
        btnChangeProfile.setOnClickListener(v -> {
            openImagePicker();
            // openImagePicker 이후 onActivityResult에서 checkIfChanged() 호출
        });
    }

    private void checkIfChanged() {
        String nickname = editTextNickname.getText().toString().trim();
        String name = editTextName.getText().toString().trim();
        String birth = editTextBirth.getText().toString().trim();
        String address = editTextAddress.getText().toString().trim();
        String phone = editTextPhone.getText().toString().trim();
        String gender = (radioGroupGender.getCheckedRadioButtonId() == R.id.radioMale) ? "M" : "F";
        boolean imageChanged = (imageUri != null);

        boolean changed = !equals(nickname, originalNickname)
                || !equals(name, originalName)
                || !equals(birth, originalBirth)
                || !equals(address, originalAddress)
                || !equals(phone, originalPhone)
                || !equals(gender, originalGender)
                || imageChanged;

        btnUpdate.setEnabled(changed);
        if (changed) {
            btnUpdate.setBackgroundTintList(getResources().getColorStateList(R.color.blue_700));
        } else {
            btnUpdate.setBackgroundTintList(getResources().getColorStateList(android.R.color.darker_gray));
        }
        isChanged = changed;
    }

    // null-safe equals
    private boolean equals(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            imageUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                imageProfile.setImageBitmap(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
            checkIfChanged();
        }
    }

    private void updateUserInfo() {
        String nickname = editTextNickname.getText().toString().trim();
        String name = editTextName.getText().toString().trim();
        String birth = editTextBirth.getText().toString().trim();
        String address = editTextAddress.getText().toString().trim();
        String phone = editTextPhone.getText().toString().trim();
        String gender = (radioGroupGender.getCheckedRadioButtonId() == R.id.radioMale) ? "M" : "F";

        if (nickname.isEmpty()) {
            Toast.makeText(this, "닉네임을 입력하세요.", Toast.LENGTH_SHORT).show();
            editTextNickname.requestFocus();
            return;
        }
        if (name.isEmpty()) {
            Toast.makeText(this, "이름을 입력하세요.", Toast.LENGTH_SHORT).show();
            editTextName.requestFocus();
            return;
        }
        if (birth.isEmpty()) {
            Toast.makeText(this, "생년월일을 입력하세요.", Toast.LENGTH_SHORT).show();
            editTextBirth.requestFocus();
            return;
        }
        if (address.isEmpty()) {
            Toast.makeText(this, "주소를 입력하세요.", Toast.LENGTH_SHORT).show();
            editTextAddress.requestFocus();
            return;
        }
        if (phone.isEmpty()) {
            Toast.makeText(this, "전화번호를 입력하세요.", Toast.LENGTH_SHORT).show();
            editTextPhone.requestFocus();
            return;
        }

        // 프로필 이미지가 변경된 경우 업로드
        if (imageUri != null) {
            StorageReference fileRef = storageRef.child(currentUser.getUid() + ".jpg");
            fileRef.putFile(imageUri)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) throw task.getException();
                        return fileRef.getDownloadUrl();
                    })
                    .addOnSuccessListener(uri -> {
                        photoUrl = uri.toString();
                        saveUserToDatabase(nickname, name, birth, gender, address, phone, photoUrl);
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "프로필 이미지 업로드 실패", Toast.LENGTH_SHORT).show());
        } else {
            saveUserToDatabase(nickname, name, birth, gender, address, phone, originalPhotoUrl);
        }
    }

    private void saveUserToDatabase(String nickname, String name, String birth, String gender, String address, String phone, String photoUrl) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("nickname", nickname);
        updates.put("name", name);
        updates.put("birth", birth);
        updates.put("gender", gender);
        updates.put("address", address);
        updates.put("phone", phone);
        if (photoUrl != null) updates.put("photoUrl", photoUrl);

        database.child(currentUser.getUid()).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "정보가 수정되었습니다.", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "수정 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // 간단한 TextWatcher 구현
    abstract class SimpleTextWatcher implements android.text.TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
        @Override public void afterTextChanged(android.text.Editable s) {}
    }
}
