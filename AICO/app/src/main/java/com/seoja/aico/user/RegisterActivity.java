package com.seoja.aico.user;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.seoja.aico.R;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText editTextId, editTextPassword, editTextPasswordConfirm,
            editTextName, editTextBirth, editTextAddress, editTextPhone,
            editTextEmail, editTextVerificationCode;
    private RadioGroup radioGroupGender;
    private Button btnSignUp;

    private DatabaseReference database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Firebase DB 참조 가져오기
        database = FirebaseDatabase.getInstance().getReference("users");

        // View 바인딩
        editTextId = findViewById(R.id.editTextId);
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

        // 버튼 클릭 시 데이터 수집 & Firebase 저장
        btnSignUp.setOnClickListener(v -> saveUserToFirebase());
    }

    private void saveUserToFirebase() {
        String id = editTextId.getText().toString();
        String password = editTextPassword.getText().toString();
        String passwordConfirm = editTextPasswordConfirm.getText().toString();
        String name = editTextName.getText().toString();
        String birth = editTextBirth.getText().toString();
        String address = editTextAddress.getText().toString();
        String phone = editTextPhone.getText().toString();
        String email = editTextEmail.getText().toString();
        String gender = (radioGroupGender.getCheckedRadioButtonId() == R.id.radioMale) ? "M" : "F";

        if (!password.equals(passwordConfirm)) {
            Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 유저 정보 맵 구성
        Map<String, Object> user = new HashMap<>();
        user.put("id", id);
        user.put("password", password);
        user.put("name", name);
        user.put("birth", birth);
        user.put("gender", gender);
        user.put("address", address);
        user.put("tel", phone);
        user.put("email", email);
        user.put("profileImage", ""); // 추후 구현

        // DB에 저장
        database.child(id).setValue(user)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(RegisterActivity.this, "회원가입 성공", Toast.LENGTH_SHORT).show();
                    finish(); // 회원가입 후 액티비티 종료 또는 이동
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(RegisterActivity.this, "회원가입 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
