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
    private TextView textResult;
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
        btnSend = findViewById(R.id.btnSend);
        textResult = findViewById(R.id.textResult);

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
        // 1. 이메일 존재 여부 확인
        mAuth.fetchSignInMethodsForEmail(email).addOnCompleteListener(task -> {
            Log.d("ResetPassword", "입력 이메일: [" + email + "]");
            if (!task.isSuccessful() || task.getResult().getSignInMethods().isEmpty()) {
                showError("존재하지 않는 이메일입니다");
                return;
            }

            // 2. Realtime DB에서 사용자 조회
            database.orderByChild("email").equalTo(email)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot snapshot) {
                            if (!snapshot.exists()) {
                                showError("가입 정보를 찾을 수 없습니다");
                                return;
                            }

                            // 3. 전화번호 일치 확인
                            boolean isMatch = false;
                            for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                                String storedPhone = userSnapshot.child("phone").getValue(String.class);
                                if (storedPhone != null &&
                                        storedPhone.replaceAll("[^0-9]", "").equals(phone.replaceAll("[^0-9]", ""))) {
                                    isMatch = true;
                                    break;
                                }
                            }

                            if (isMatch) {
                                sendPasswordResetEmail(email);
                            } else {
                                showError("등록된 전화번호와 일치하지 않습니다");
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
                            showError("데이터 조회 실패: " + error.getMessage());
                        }
                    });
        });
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
