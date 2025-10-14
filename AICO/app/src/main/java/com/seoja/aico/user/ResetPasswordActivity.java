package com.seoja.aico.user;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;
import com.seoja.aico.R;

public class ResetPasswordActivity extends AppCompatActivity {

    private EditText editTextEmail, editTextPhone;
    private Button btnSend;
    private ImageButton btnBack;
    private TextView textResult, titleTextView;
    private FirebaseAuth mAuth;
    private DatabaseReference database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        mAuth = FirebaseAuth.getInstance();
        database = FirebaseDatabase.getInstance().getReference("users");

        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPhone = findViewById(R.id.editTextPhoneNumber);
        btnBack = findViewById(R.id.btnBack);
        btnSend = findViewById(R.id.btnSend);
        textResult = findViewById(R.id.textResult);
        titleTextView = findViewById(R.id.header_title);

        titleTextView.setText("비밀번호 재설정");

        btnBack.setOnClickListener(v -> finish());

        btnSend.setOnClickListener(v -> {
            String email = editTextEmail.getText().toString().trim();
            String phone = editTextPhone.getText().toString().trim();

            if (!validateInputs(email, phone)) return;

            checkUserExists(email, phone);
        });
    }

    private boolean validateInputs(String email, String phone) {
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextEmail.setError("유효한 이메일을 입력하세요");
            return false;
        }
        if (TextUtils.isEmpty(phone) || phone.replaceAll("[^0-9]", "").length() < 10) {
            editTextPhone.setError("올바른 전화번호를 입력하세요");
            return false;
        }
        return true;
    }

    private void checkUserExists(String email, String phone) {
        // 1. Realtime DB에서 이메일로 사용자 조회
        database.orderByChild("email").equalTo(email)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            showError("가입된 이메일이 아닙니다");
                            return;
                        }

                        // 2. 전화번호 일치 확인
                        boolean isMatch = false;
                        for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                            String storedPhone = userSnapshot.child("phone").getValue(String.class);
                            if (storedPhone != null &&
                                    sanitizePhone(storedPhone).equals(sanitizePhone(phone))) {
                                isMatch = true;
                                break;
                            }
                        }

                        if (!isMatch) {
                            showError("등록된 전화번호와 일치하지 않습니다");
                            return;
                        }

                        // 3. 비밀번호 재설정 이메일 전송 시도
                        mAuth.sendPasswordResetEmail(email)
                                .addOnCompleteListener(task -> {
                                    // 소셜로그인 감지
                                    if (task.isSuccessful()) {
                                        showSuccess("이메일로 임시 비밀번호가 전송되었습니다");
                                    } else {
                                        showError("비밀번호 초기화가 불가능한 계정입니다");
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        showError("데이터 조회 실패: " + error.getMessage());
                    }
                });
    }

    // 전화번호 형식 정리 (숫자만 남기기)
    private String sanitizePhone(String phone) {
        return phone.replaceAll("[^0-9]", "");
    }


    private void sendPasswordResetEmail(String email) {
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        showSuccess("비밀번호 재설정 메일을 발송했습니다");
                    } else {
                        showError("메일 전송 실패: " + task.getException().getMessage());
                    }
                });
    }

    private void showSuccess(String message) {
        textResult.setText(message);
        textResult.setTextColor(getResources().getColor(R.color.teal_700));
        textResult.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        textResult.setText(message);
        textResult.setTextColor(getResources().getColor(R.color.red_700));
        textResult.setVisibility(View.VISIBLE);
    }
}