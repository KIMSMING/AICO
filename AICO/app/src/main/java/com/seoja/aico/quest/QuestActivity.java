package com.seoja.aico.quest;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.seoja.aico.R;
import com.seoja.aico.gpt.GptApi;
import com.seoja.aico.gpt.GptRequest;
import com.seoja.aico.gpt.GptResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class QuestActivity extends AppCompatActivity implements View.OnClickListener {

    // Android 에뮬레이터에서 PC(호스트)의 localhost(127.0.0.1)를 가리키는 특수 주소
    private static final String BASE_URL = "http://10.0.2.2:8000/";
    private static final String TAG = "QuestActivity"; // 로그 태그 추가

    // 메인 UI 스레드에서 작업하기 위한 핸들러 추가
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView textRequest, textFeedback;
    private EditText textResponse;
    private Button btnRequest;
    private Button btnNextQuestion;
    private LinearLayout feedbackSection;

    private List<String> questionList = new ArrayList<>();

    // 셔플된 리스트에서 문제출력을 위한 인덱스
    private int currentQuestion = 0;

    private String selectedFirst = "";
    private String selectedSecond = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_quest);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        textRequest = findViewById(R.id.textRequest);
        textResponse = findViewById(R.id.textResponse);
        btnRequest = findViewById(R.id.btnRequest);
        textFeedback = findViewById(R.id.textFeedback);
        btnNextQuestion = findViewById(R.id.btnNextQuestion);
        feedbackSection = findViewById(R.id.feedbackSection);

        selectedFirst = getIntent().getStringExtra("selectedFirst");
        selectedSecond = getIntent().getStringExtra("selectedSecond");

        if (selectedSecond == null || selectedSecond.isEmpty()) {
            Toast.makeText(this, "소분류 정보가 없습니다", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        fetchJobQeustion();

        btnRequest.setOnClickListener(this);
        btnNextQuestion.setOnClickListener(v -> loadNewQuestion());

        // 초기 상태 설정
        textFeedback.setText("답변 후 피드백이 여기에 표시됩니다.");

        // 서버 연결 테스트
        testServerConnection();
    }

    // 서버 연결 테스트
    private void testServerConnection() {
        // 로깅 인터셉터 추가
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        // OkHttpClient 설정
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();

        // Retrofit 설정
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();

        // 루트 엔드포인트 호출
        retrofit.create(GptApi.class).testConnection().enqueue(new Callback<Object>() {
            @Override
            public void onResponse(Call<Object> call, Response<Object> response) {
                Log.d(TAG, "서버 연결 테스트 성공: " + response.code());
            }

            @Override
            public void onFailure(Call<Object> call, Throwable t) {
                Log.e(TAG, "서버 연결 테스트 실패: " + t.getMessage());
            }
        });
    }

    // Firebase에서 데이터 가져오기
    private void fetchJobQeustion() {
        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference("면접질문");

        rootRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // 1. 공통질문 가져오기
                DataSnapshot commonSnap = snapshot.child("공통질문");
                for (DataSnapshot questionSnap : commonSnap.getChildren()) {
                    String question = questionSnap.getValue(String.class);
                    if (question != null && !question.isEmpty()) {
                        questionList.add(question);
                    }
                }

                // 2. 인사질문 가져오기
                DataSnapshot hrSnap = snapshot.child("인사질문");
                for (DataSnapshot questionSnap : hrSnap.getChildren()) {
                    String question = questionSnap.getValue(String.class);
                    if (question != null && !question.isEmpty()) {
                        questionList.add(question);
                    }
                }

                // 3. 직업질문 가져오기 (selectedFirst, selectedSecond 기준)
                DataSnapshot jobSnap = snapshot.child("직업질문")
                        .child(selectedFirst)
                        .child(selectedSecond);
                for (DataSnapshot questionSnap : jobSnap.getChildren()) {
                    String question = questionSnap.getValue(String.class);
                    if (question != null && !question.isEmpty()) {
                        questionList.add(question);
                    }
                }

                // 리스트 셔플 후 첫 질문 출력
                if (!questionList.isEmpty()) {
                    Collections.shuffle(questionList);
                    currentQuestion = 0;
                    loadNewQuestion();
                } else {
                    Toast.makeText(QuestActivity.this, "질문이 없습니다.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(QuestActivity.this, "데이터 로딩 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadNewQuestion() {
        if (currentQuestion >= questionList.size()) {
            Toast.makeText(this, "더이상 낼 문제가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        String question = questionList.get(currentQuestion);
        textRequest.setText(question);
        textResponse.setText(""); // 답변 필드 초기화
        feedbackSection.setVisibility(View.GONE);
        textFeedback.setText("답변 후 피드백이 여기에 표시됩니다."); // 피드백 필드 초기화
        currentQuestion++;
    }

    public void sendGptRequest() {
        // 질문과 답변 가져오기
        String quest = textRequest.getText().toString();
        String answer = textResponse.getText().toString();
        feedbackSection.setVisibility(View.VISIBLE);

        // 질문과 답변 하나의 문자열로 만들기
        String requestMessage = "면접 질문 : " + quest + "\n사용자 답변 : " + answer;

        // 요청 중임을 표시
        textFeedback.setText("피드백을 요청 중입니다...");
        btnRequest.setEnabled(false); // 버튼 비활성화

        // 로깅 인터셉터 추가
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        // OkHttpClient 설정
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)  // 연결 타임아웃 증가
                .readTimeout(60, TimeUnit.SECONDS)     // 읽기 타임아웃 증가
                .writeTimeout(60, TimeUnit.SECONDS)    // 쓰기 타임아웃 증가
                .addInterceptor(logging)               // 로깅 추가
                .build();

        // Retrofit 설정
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)  // OkHttpClient 설정 추가
                .build();

        GptApi gptApi = retrofit.create(GptApi.class);

        // 요청 객체 만들기
        GptRequest request = new GptRequest(requestMessage);

        Log.d(TAG, "요청 시작: " + requestMessage);

        Call<GptResponse> call = gptApi.askGpt(request);
        call.enqueue(new Callback<GptResponse>() {
            @Override
            public void onResponse(@NonNull Call<GptResponse> call, @NonNull Response<GptResponse> response) {
                Log.d(TAG, "onResponse 호출됨, HTTP 코드: " + response.code());  // 콜백 진입 확인

                // UI 업데이트는 반드시 메인 스레드에서 실행
                mainHandler.post(() -> {
                    btnRequest.setEnabled(true); // 버튼 다시 활성화

                    if (response.isSuccessful()) {
                        Log.d(TAG, "응답 성공, HTTP 코드: " + response.code());

                        if (response.body() != null) {
                            String content = response.body().content;
                            Log.d(TAG, "응답 바디 있음: " + (content != null ? content.substring(0, Math.min(content.length(), 100)) : "null"));
                            textFeedback.setText(content);
                        } else {
                            Log.e(TAG, "응답 바디가 null임");
                            textFeedback.setText("응답을 받았으나 내용이 없습니다.");
                        }
                    } else {
                        Log.e(TAG, "응답 실패, HTTP 코드: " + response.code());
                        try {
                            // 에러 바디가 있으면 읽어서 출력
                            if (response.errorBody() != null) {
                                String errorBody = response.errorBody().string();
                                Log.e(TAG, "에러 바디: " + errorBody);
                                textFeedback.setText("오류 발생: " + response.code() + "\n" + errorBody);
                            } else {
                                textFeedback.setText("오류 발생: " + response.code());
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "에러 바디 읽기 실패: " + e.getMessage());
                            textFeedback.setText("오류 발생: " + response.code() + "\n" + e.getMessage());
                        }
                    }
                });
            }

            @Override
            public void onFailure(Call<GptResponse> call, Throwable t) {
                Log.e(TAG, "onFailure 호출됨, 서버 연결 실패: " + t.getMessage(), t);

                // UI 업데이트는 반드시 메인 스레드에서 실행
                mainHandler.post(() -> {
                    btnRequest.setEnabled(true); // 버튼 다시 활성화
                    textFeedback.setText("서버 연결 실패: " + t.getMessage());
                });
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btnRequest) {
            if (textResponse.getText().toString().isEmpty()) {
                textFeedback.setText("답변을 입력해주세요");
                return;
            }
            sendGptRequest();
        }

        if (v.getId() == R.id.btnNextQuestion) {
            loadNewQuestion();
        }
    }
}

