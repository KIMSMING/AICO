package com.seoja.aico.user;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.AuthResult;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.seoja.aico.MainActivity;
import com.seoja.aico.R;

public class LoginActivity extends AppCompatActivity {

    private EditText editTextId, editTextPassword;
    private Button btnLogin, btnSignUp, btnFindById, btnFindByPw;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        editTextId = findViewById(R.id.editTextId);
        editTextPassword = findViewById(R.id.editTextPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnSignUp = findViewById(R.id.btnSignUp);
        btnFindById = findViewById(R.id.btnFindById);
        btnFindByPw = findViewById(R.id.btnFindByPw);

        // 로그인 버튼
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = editTextId.getText().toString().trim();
                String password = editTextPassword.getText().toString().trim();

                if(email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(LoginActivity.this, "아이디와 비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Firebase 로그인 시도
                mAuth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(LoginActivity.this, new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                if(task.isSuccessful()) {
                                    FirebaseUser user = mAuth.getCurrentUser();
                                    Intent intent = new Intent(LoginActivity.this, com.seoja.aico.user.ResultActivity.class);
                                    startActivity(intent);
                                } else {
                                    Toast.makeText(LoginActivity.this, "아이디와 비밀번호가 일치하지 않습니다. ", Toast.LENGTH_LONG).show();
                                }
                            }
                        });
            }
        });

        // 회원가입 버튼
        btnSignUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, com.seoja.aico.user.RegisterActivity.class);
                startActivity(intent);
            }
        });

        // 아이디 찾기
        btnFindById.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        // 비밀번호 찾기
        btnFindByPw.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
    }
}
