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
import com.seoja.aico.gpt.AskRequest;
import com.seoja.aico.gpt.GptApi;
import com.seoja.aico.gpt.GptResponse;
import com.seoja.aico.gpt.HistoryItem;
import com.seoja.aico.gpt.RetrofitClient;
import com.seoja.aico.reviewBoard.BoardListActivity;
import com.seoja.aico.user.LoginActivity;
import com.seoja.aico.user.UserViewActivity;
import com.seoja.aico.QuestActivity;

import java.security.MessageDigest;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    Button btnQuest, btnUserView, btnBoard1, btnBoard2, btnBoard3 ;
    Button btnQuest, btnUserView, btnTestApi;
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
        btnBoard1 = (Button) findViewById(R.id.btnBoard1);
        btnBoard2 = (Button) findViewById(R.id.btnBoard2);
        btnBoard3 = (Button) findViewById(R.id.btnBoard3);

        // 유저정보
        btnUserView.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, UserViewActivity.class));
        });

        // 퀘스트
        btnQuest.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, FieldActivity.class));
        });

        //API Test
        btnTestApi = findViewById(R.id.btnTestApi);
        btnTestApi.setOnClickListener(v -> {
            runFeedbackTest();
        });

        //RetrofitClient 초기화
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://10.0.2.2:8000/") //에뮬레이터에서 Localhost
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        GptApi api = retrofit.create(GptApi.class);

        //GPT 피드백 요청 보내기
        AskRequest ask = new AskRequest("testuser1", "자기소개 해주세요");
        api.askGpt(ask).enqueue(new Callback<GptResponse>() {
            @Override
            public void onResponse(Call<GptResponse> call, Response<GptResponse> response) {
                if (response.isSuccessful()) {
                    String feedback = response.body().getContent();
                    Log.d("GPT_FEEDBACK", feedback);

                    //받은 피드백을 히스토리로 저장
                    HistoryItem item = new HistoryItem(
                            "testuser1",
                            "자기소개 해주세요",
                            "저는 책임감이 강하고 팀워크에 강합니다.",
                            feedback
                    );

                    api.saveHistory(item).enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            Log.d("SAVE", "히스토리 저장 완료");
                        }

                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {
                            Log.e("SAVE_ERR", t.getMessage());
                        }
                    });

                } else {
                    Log.e("GPT_ERR", response.message());
                }
            }

            @Override
            public void onFailure(Call<GptResponse> call, Throwable t) {
                Log.e("GPT_CALL_ERR", t.getMessage());
            }
        });

        // 게시판
        btnBoard1.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, BoardListActivity.class));
        });

//        printKeyHash();

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

    //API 테스트 함수
    private void runFeedbackTest() {
        Retrofit retrofit = RetrofitClient.getClient("http://10.0.2.2:8000/");
        GptApi api = retrofit.create(GptApi.class);

        AskRequest ask = new AskRequest("testuser1", "자기소개 해주세요");

        api.askGpt(ask).enqueue(new Callback<GptResponse>() {
            @Override
            public void onResponse(Call<GptResponse> call, Response<GptResponse> response) {
                if(response.isSuccessful()) {
                    String feedback = response.body().getContent();
                    Log.d("GPT_FEEDBACK", feedback);
                } else {
                    Log.e("GPT_ERROR", response.message());
                }
            }

            @Override
            public void onFailure(Call<GptResponse> call, Throwable t) {
                Log.e("GPT_FAILURE", t.getMessage());
            }
        });
    }
}
