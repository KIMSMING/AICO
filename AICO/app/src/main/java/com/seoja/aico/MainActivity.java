package com.seoja.aico;

import android.content.Intent;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.seoja.aico.user.LoginActivity;
import com.seoja.aico.user.UserViewActivity;
import com.seoja.aico.QuestActivity;

import java.security.MessageDigest;

public class MainActivity extends AppCompatActivity {

    Button btnQuest, btnUserView;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();

        // 1. 로그인 상태 체크
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            redirectToLogin();
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

        // printKeyHash(); // 해쉬 키 필요할 때만 쓰면 됨

        btnUserView = (Button) findViewById(R.id.btnGoUserView);
        btnQuest = (Button) findViewById(R.id.btnQuest);

        // 유저정보
        btnUserView.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, UserViewActivity.class));
        });

        // 퀘스트
        btnQuest.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, FieldActivity.class));
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 앱이 백그라운드에서 복귀할 때마다 로그인 상태 확인
        if (mAuth.getCurrentUser() == null) {
            redirectToLogin();
        }
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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
