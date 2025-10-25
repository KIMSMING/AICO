package com.seoja.aico.user;

import static com.seoja.aico.user.SocialRegisterImpl.generateDefaultNickname;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import com.seoja.aico.R;

import okhttp3.*;

public class RegisterActivity extends AppCompatActivity {

    private EditText editTextEmail, editTextVerificationCode, editTextPassword,
            editTextPasswordConfirm, editTextName, editTextBirth, editTextAddress, editTextPhone;
    private RadioGroup radioGroupGender;
    private Button btnSignUp, btnSendEmail, btnCancle;
    private TextView codeTimer, passwordRule1, passwordRule2, passwordRule3, titleTextView;
    private ImageView ivCodeCheck, ivPwCheck, btnBack;
    private LinearLayout codeBar;

    private boolean isEmailVerified = false;
    private boolean isTimerRunning = false;
    private CountDownTimer resendTimer;
    private static final long RESEND_COOLDOWN_MILLIS = 3 * 60 * 1000;

    // 유효성 플래그
    private boolean isNameValid = false, isBirthValid = false, isGenderValid = false,
            isAddressValid = false, isPhoneValid = false, isPwValid = false, isPwConfirmValid = false;

    private DatabaseReference database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // 뷰 바인딩
        editTextEmail = findViewById(R.id.editTextEmail);
        codeBar = findViewById(R.id.codeBar);
        editTextVerificationCode = findViewById(R.id.editTextVerificationCode);
        editTextPassword = findViewById(R.id.editTextPassword);
        editTextPasswordConfirm = findViewById(R.id.editTextPasswordConfirm);
        editTextName = findViewById(R.id.editTextName);
        editTextBirth = findViewById(R.id.editTextBirth);
        radioGroupGender = findViewById(R.id.radioGroupGender);
        editTextAddress = findViewById(R.id.editTextAddress);
        editTextPhone = findViewById(R.id.editTextPhone);
        btnSignUp = findViewById(R.id.btnSignUp);
        btnSendEmail = findViewById(R.id.btnSendEmail);
        btnCancle = findViewById(R.id.btnCancle);
        codeTimer = findViewById(R.id.codeTimer);
        ivCodeCheck = findViewById(R.id.ivCodeCheck);
        ivPwCheck = findViewById(R.id.ivPwCheck);
        passwordRule1 = findViewById(R.id.passwordRule1);
        passwordRule2 = findViewById(R.id.passwordRule2);
        passwordRule3 = findViewById(R.id.passwordRule3);
        btnBack = findViewById(R.id.btnBack);
        titleTextView = findViewById(R.id.header_title);

        titleTextView.setText("회원가입");

        database = FirebaseDatabase.getInstance().getReference("users");

        btnCancle.setOnClickListener(v -> finish());

        // 실시간 입력 체크
        setupRequiredFieldValidation(editTextName, () -> isNameValid = !editTextName.getText().toString().trim().isEmpty());
        setupRequiredFieldValidation(editTextBirth, () -> isBirthValid = !editTextBirth.getText().toString().trim().isEmpty());
        setupRequiredFieldValidation(editTextAddress, () -> isAddressValid = !editTextAddress.getText().toString().trim().isEmpty());
        setupRequiredFieldValidation(editTextPhone, () -> isPhoneValid = !editTextPhone.getText().toString().trim().isEmpty());
        radioGroupGender.setOnCheckedChangeListener((group, checkedId) -> {
            isGenderValid = checkedId != -1;
            updateSignUpButton();
        });

        btnBack.setOnClickListener(v -> finish());

        // 이메일 인증번호 전송
        btnSendEmail.setOnClickListener(v -> {
            if (isTimerRunning) return;
            String email = editTextEmail.getText().toString().trim();
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showToast("올바른 이메일을 입력하세요.");
                editTextEmail.requestFocus();
                return;
            }
            checkEmailExists(email);
        });

        // 인증번호 입력 실시간 검증
        editTextVerificationCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String code = s.toString().trim();
                if (code.length() == 6) {
                    verifyCodeWithServer(editTextEmail.getText().toString().trim(), code);
                } else {
                    setCodeValidation(false);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // 비밀번호 실시간 검증
        editTextPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validatePassword(s.toString());
                checkPasswordsMatch();
                updateSignUpButton();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        editTextPasswordConfirm.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkPasswordsMatch();
                updateSignUpButton();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // 가입 버튼 클릭
        btnSignUp.setOnClickListener(v -> trySignUp());
    }

    // 이메일 중복 체크
    private void checkEmailExists(String email) {
        database.orderByChild("email").equalTo(email).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    showToast("이미 가입된 이메일입니다.");
                    editTextEmail.requestFocus();
                } else {
                    sendVerificationCodeToServer(email);
                    startResendCooldown();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showToast("DB 오류: " + error.getMessage());
            }
        });
    }

    // 인증번호 전송
    private void sendVerificationCodeToServer(String email) {
        OkHttpClient client = new OkHttpClient();
        RequestBody body = new FormBody.Builder().add("email", email).build();
        Request request = new Request.Builder()
                .url("http://" + getString(R.string.server_url) + "/api/email/send-code")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> showToast("인증번호 요청 실패: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        codeBar.setVisibility(View.VISIBLE);
                        showToast("이메일로 인증번호를 전송했습니다.");
                    } else {
                        showToast("인증번호 전송 실패");
                    }
                });
            }
        });
    }

    // 인증번호 검증
    private void verifyCodeWithServer(String email, String code) {
        OkHttpClient client = new OkHttpClient();
        try {
            RequestBody body = new FormBody.Builder()
                    .addEncoded("email", URLEncoder.encode(email, "UTF-8"))
                    .addEncoded("code", URLEncoder.encode(code, "UTF-8"))
                    .build();
            Request request = new Request.Builder()
                    .url("http://" + getString(R.string.server_url) + "/api/email/verify-code")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        showToast("서버 연결 실패: " + e.getMessage());
                        setCodeValidation(false);
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    boolean isValid = response.isSuccessful() && response.body().string().equals("true");
                    runOnUiThread(() -> {
                        setCodeValidation(isValid);
                        if (isValid) { // 인증 성공 시 타이머 즉시 종료
                            if (resendTimer != null) resendTimer.cancel();
                            codeTimer.setVisibility(View.GONE);
                            isTimerRunning = false;
                        }
                    });
                }
            });
        } catch (Exception e) {
            showToast("인코딩 오류");
        }
    }

    // 타이머 시작
    private void startResendCooldown() {
        isTimerRunning = true;
        btnSendEmail.setEnabled(false);
        codeTimer.setVisibility(View.VISIBLE);
        resendTimer = new CountDownTimer(RESEND_COOLDOWN_MILLIS, 1000) {
            public void onTick(long millisUntilFinished) {
                codeTimer.setText(String.format("%02d:%02d",
                        millisUntilFinished / 60000, (millisUntilFinished % 60000) / 1000));
            }

            public void onFinish() {
                isTimerRunning = false;
                btnSendEmail.setEnabled(true);
                codeTimer.setVisibility(View.GONE);
                showToast("인증번호가 만료되었습니다.");
            }
        }.start();
    }

    // 비밀번호 검증
    private void validatePassword(String password) {
        boolean hasLength = password.length() >= 8 && password.length() <= 20;
        boolean hasSpecialChar = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\",./<>?].*");
        boolean hasUpperLower = password.matches(".*[A-Z].*") && password.matches(".*[a-z].*");
        isPwValid = hasLength && hasSpecialChar && hasUpperLower;

        passwordRule1.setVisibility(hasLength ? View.GONE : View.VISIBLE);
        passwordRule2.setVisibility(hasSpecialChar ? View.GONE : View.VISIBLE);
        passwordRule3.setVisibility(hasUpperLower ? View.GONE : View.VISIBLE);
    }


    // 비밀번호 일치 확인
    private void checkPasswordsMatch() {
        String pw = editTextPassword.getText().toString();
        String pwConfirm = editTextPasswordConfirm.getText().toString();
        isPwConfirmValid = pw.equals(pwConfirm) && !pw.isEmpty();
        if (pw.isEmpty() || pwConfirm.isEmpty()) {
            ivPwCheck.setVisibility(View.GONE);
        } else {
            ivPwCheck.setVisibility(View.VISIBLE);
            ivPwCheck.setImageResource(isPwConfirmValid ? R.drawable.ic_check : R.drawable.ic_not);
        }
    }

    // 가입 버튼 상태 업데이트
    private void updateSignUpButton() {
        boolean allValid = isEmailVerified && isNameValid && isBirthValid && isGenderValid &&
                isAddressValid && isPhoneValid && isPwValid && isPwConfirmValid;
        btnSignUp.setEnabled(allValid);
    }

    // 필수 입력란 실시간 체크
    private void setupRequiredFieldValidation(EditText editText, Runnable validationCheck) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validationCheck.run();
                updateSignUpButton();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    // 인증번호 검증 UI
    private void setCodeValidation(boolean isValid) {
        isEmailVerified = isValid;
        editTextVerificationCode.setBackgroundColor(isValid ? Color.parseColor("#D0FFD0") : Color.parseColor("#FFD0D0"));
        ivCodeCheck.setVisibility(isValid ? View.VISIBLE : View.VISIBLE);
        ivCodeCheck.setImageResource(isValid ? R.drawable.ic_check : R.drawable.ic_not);
        updateSignUpButton();
    }

    // 가입 시도
    private void trySignUp() {
        String password = editTextPassword.getText().toString().trim();
        String passwordConfirm = editTextPasswordConfirm.getText().toString().trim();
        String name = editTextName.getText().toString().trim();
        String birth = editTextBirth.getText().toString().trim();
        String address = editTextAddress.getText().toString().trim();
        String phone = editTextPhone.getText().toString().trim();
        String email = editTextEmail.getText().toString().trim();
        String gender = (radioGroupGender.getCheckedRadioButtonId() == R.id.radioMale) ? "M" : "W";

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

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        String uid = firebaseUser.getUid();

                        Map<String, Object> userMap = new HashMap<>();
                        userMap.put("uid", uid);
                        userMap.put("email", email);
                        userMap.put("name", name);
                        userMap.put("nickname", generateDefaultNickname(uid));
                        userMap.put("birth", birth);
                        userMap.put("gender", gender);
                        userMap.put("address", address);
                        userMap.put("phone", phone);
                        userMap.put("photoUrl", "");
                        userMap.put("level", 1);
                        userMap.put("experience", 0);

                        database.child(uid).setValue(userMap)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(RegisterActivity.this, "회원가입이 완료되었습니다.", Toast.LENGTH_SHORT).show();
                                    startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                                    finish();
                                })
                                .addOnFailureListener(e -> Toast.makeText(RegisterActivity.this, "DB 저장 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    } else {
                        String message = "회원가입 실패";
                        if (task.getException() instanceof FirebaseAuthException) {
                            message = ((FirebaseAuthException) task.getException()).getMessage();
                        }
                        Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (resendTimer != null) resendTimer.cancel();
        super.onDestroy();
    }
}
