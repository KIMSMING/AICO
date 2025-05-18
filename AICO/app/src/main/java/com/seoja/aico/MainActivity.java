package com.seoja.aico;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.security.MessageDigest;

public class MainActivity extends AppCompatActivity {

    Button btnLogin, btnQuest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 로그인 상태 체크
        if (!isUserLoggedIn()) {
            Intent intent = new Intent(MainActivity.this, com.seoja.aico.user.LoginActivity.class);
            startActivity(intent);
            finish(); // MainActivity 종료
            return;
        }

        // 2. 로그인 되어 있으면 MainActivity 화면 세팅
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        printKeyHash();

        btnLogin = findViewById(R.id.btnGoLogin);
        btnQuest = findViewById(R.id.btnQuest);

        btnLogin.setOnClickListener(v -> {
            // 로그아웃 처리
            SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
            prefs.edit().putBoolean("isLoggedIn", false).apply();

            // LoginActivity로 이동
            Intent intent = new Intent(MainActivity.this, com.seoja.aico.user.LoginActivity.class);
            startActivity(intent);
            finish();
        });


        btnQuest.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, QuestActivity.class);
            startActivity(intent);
        });
    }

    // 로그인 상태 확인
    private boolean isUserLoggedIn() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        return prefs.getBoolean("isLoggedIn", false);
    }

    private void printKeyHash() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                String keyHash = Base64.encodeToString(md.digest(), Base64.NO_WRAP);
                Log.d("KeyHash", keyHash);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
