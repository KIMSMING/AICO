package com.seoja.aico.user;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;

import com.google.firebase.auth.*;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.seoja.aico.R;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText editTextPassword, editTextPasswordConfirm,
            editTextName, editTextBirth, editTextAddress, editTextPhone,
            editTextEmail, editTextVerificationCode;
    private RadioGroup radioGroupGender;
    private Button btnSignUp, btnSendEmail, btnResendCode;
    private TextView passwordRule1, passwordRule2, passwordRule3;

    // 추가: 초록색 V 아이콘
    private ImageView ivCodeCheck, ivPwCheck;

    private DatabaseReference database;
    private FirebaseAuth mAuth;

    // 이메일 인증 관련
    private String sentVerificationCode = null;
    private boolean isEmailVerified = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        database = FirebaseDatabase.getInstance().getReference("users");

        editTextPassword = findViewById(R.id.editTextPassword);
        editTextPasswordConfirm = findViewById(R.id.editTextPasswordConfirm);
        editTextName = findViewById(R.id.editTextName);
        editTextBirth = findViewById(R.id.editTextBirth);
        editTextAddress = findViewById(R.id.editTextAddress);
        editTextPhone = findViewById(R.id.editTextPhone);
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextVerificationCode = findViewById(R.id.editTextVerificationCode);
        radioGroupGender = findViewById(R.id.radioGroupGender);
        btnSignUp = findViewById(R.id.btnSignUp);
        btnSendEmail = findViewById(R.id.btnSendEmail);
        btnResendCode = findViewById(R.id.btnResendCode);
        passwordRule1 = findViewById(R.id.passwordRule1);
        passwordRule2 = findViewById(R.id.passwordRule2);
        passwordRule3 = findViewById(R.id.passwordRule3);

        // 추가: 초록색 V 아이콘
        ivCodeCheck = findViewById(R.id.ivCodeCheck);
        ivPwCheck = findViewById(R.id.ivPwCheck);

        // 인증번호 입력칸, 재전송 버튼, V 아이콘 초기 숨김
        editTextVerificationCode.setVisibility(View.GONE);
        btnResendCode.setVisibility(View.GONE);
        ivCodeCheck.setVisibility(View.GONE);
        ivPwCheck.setVisibility(View.GONE);

        // 이메일 인증번호 전송
        btnSendEmail.setOnClickListener(v -> {
            String email = editTextEmail.getText().toString().trim();
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "올바른 이메일을 입력하세요.", Toast.LENGTH_SHORT).show();
                editTextEmail.requestFocus();
                return;
            }
            // 인증번호 생성 (실제 서비스는 서버에서 이메일 발송)
            sentVerificationCode = String.valueOf((int)(Math.random()*900000)+100000);
            Toast.makeText(this, "인증번호(테스트): " + sentVerificationCode, Toast.LENGTH_LONG).show();

            editTextVerificationCode.setVisibility(View.VISIBLE);
            btnResendCode.setVisibility(View.VISIBLE);
            isEmailVerified = false;
            editTextVerificationCode.setText("");
            ivCodeCheck.setVisibility(View.GONE); // 새로 전송 시 V 숨김
        });

        // 인증번호 재전송
        btnResendCode.setOnClickListener(v -> btnSendEmail.performClick());

        // 인증번호 실시간 확인 + V 아이콘 표시
        editTextVerificationCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (sentVerificationCode != null && s.toString().equals(sentVerificationCode)) {
                    isEmailVerified = true;
                    editTextVerificationCode.setBackgroundColor(Color.parseColor("#D0FFD0"));
                    ivCodeCheck.setVisibility(View.VISIBLE);
                } else {
                    isEmailVerified = false;
                    editTextVerificationCode.setBackgroundColor(Color.parseColor("#FFD0D0"));
                    ivCodeCheck.setVisibility(View.GONE);
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
        });

        // 비밀번호 조건 실시간 체크
        editTextPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String pw = s.toString();
                boolean rule1 = pw.length() >= 8 && pw.length() <= 20;
                boolean rule2 = pw.matches(".*[!@#$%^&*()_+=\\-].*");
                boolean rule3 = pw.matches(".*[A-Z].*") && pw.matches(".*[a-z].*");

                passwordRule1.setVisibility(rule1 ? View.GONE : View.VISIBLE);
                passwordRule2.setVisibility(rule2 ? View.GONE : View.VISIBLE);
                passwordRule3.setVisibility(rule3 ? View.GONE : View.VISIBLE);
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
        });

        // 비밀번호 확인 실시간 체크 + V 아이콘 표시
        editTextPasswordConfirm.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String pw = editTextPassword.getText().toString();
                String pwConfirm = s.toString();
                if (!pw.isEmpty() && pw.equals(pwConfirm)) {
                    ivPwCheck.setVisibility(View.VISIBLE);
                } else {
                    ivPwCheck.setVisibility(View.GONE);
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
        });

        btnSignUp.setOnClickListener(v -> trySignUp());
    }

    private void trySignUp() {
        String password = editTextPassword.getText().toString().trim();
        String passwordConfirm = editTextPasswordConfirm.getText().toString().trim();
        String name = editTextName.getText().toString().trim();
        String birth = editTextBirth.getText().toString().trim();
        String address = editTextAddress.getText().toString().trim();
        String phone = editTextPhone.getText().toString().trim();
        String email = editTextEmail.getText().toString().trim();
        String gender = (radioGroupGender.getCheckedRadioButtonId() == R.id.radioMale) ? "M" : "F";

        // 미입력 체크 & 포커스 이동
        if (email.isEmpty()) {
            Toast.makeText(this, "이메일을 입력하세요.", Toast.LENGTH_SHORT).show();
            editTextEmail.requestFocus();
            return;
        }
        if (!isEmailVerified) {
            Toast.makeText(this, "이메일 인증을 완료하세요.", Toast.LENGTH_SHORT).show();
            editTextVerificationCode.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            Toast.makeText(this, "비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show();
            editTextPassword.requestFocus();
            return;
        }
        if (passwordConfirm.isEmpty()) {
            Toast.makeText(this, "비밀번호 확인을 입력하세요.", Toast.LENGTH_SHORT).show();
            editTextPasswordConfirm.requestFocus();
            return;
        }
        if (!password.equals(passwordConfirm)) {
            Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
            editTextPasswordConfirm.requestFocus();
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

        // 비밀번호 조건
        boolean rule1 = password.length() >= 8 && password.length() <= 20;
        boolean rule2 = password.matches(".*[!@#$%^&*()_+=\\-].*");
        boolean rule3 = password.matches(".*[A-Z].*") && password.matches(".*[a-z].*");

        if (!rule1 || !rule2 || !rule3) {
            Toast.makeText(this, "비밀번호 조건을 모두 만족해야 합니다.", Toast.LENGTH_SHORT).show();
            editTextPassword.requestFocus();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(RegisterActivity.this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            String uid = firebaseUser.getUid();

                            Map<String, Object> userMap = new HashMap<>();
                            userMap.put("uid", uid);
                            userMap.put("email", email);
                            userMap.put("name", name);
                            userMap.put("nickname", uid);
                            userMap.put("birth", birth);
                            userMap.put("gender", gender);
                            userMap.put("address", address);
                            userMap.put("phone", phone);

                            database.child(uid).setValue(userMap)
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(RegisterActivity.this, "회원가입 성공", Toast.LENGTH_SHORT).show();
                                        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                                        startActivity(intent);
                                        finish();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(RegisterActivity.this, "데이터 저장 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    });
                        }
                    } else {
                        String errorMessage = "회원가입 실패";
                        if (task.getException() instanceof FirebaseAuthException) {
                            errorMessage = ((FirebaseAuthException) task.getException()).getMessage();
                        } else if (task.getException() != null) {
                            errorMessage = task.getException().getMessage();
                        }
                        Toast.makeText(RegisterActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
