package com.seoja.aico;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuestActivity extends AppCompatActivity {

    // Android 에뮬레이터에서 PC(호스트)의 localhost(127.0.0.1)를 가리키는 특수 주소
//    private static final String BASE_URL = "http://10.0.2.2:8000";

    private TextView textRequest, textFeedback;

    private EditText textResponse;

    private Button btnRequest;

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

        selectedFirst = getIntent().getStringExtra("selectedFirst");
        selectedSecond = getIntent().getStringExtra("selectedSecond");

        if (selectedSecond == null || selectedSecond.isEmpty()) {
            Toast.makeText(this, "소분류 정보가 없습니다", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        fetchJobQeustion();

//        btnRequest.setOnClickListener(v -> sendGptRequest());
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
        currentQuestion++;
    }

//    public void sendGptRequest() {
//        String t = textRequest.getText().toString();
//        Retrofit retrofit = new Retrofit.Builder()
//                .baseUrl(BASE_URL)
//                .addConverterFactory(GsonConverterFactory.create())
//                .build();
//
//        GptApi gptApi = retrofit.create(GptApi.class);
//
//        // 요청 객체 만들기
//        GptRequest request = new GptRequest(t);
//
//        Call<GptResponse> call = gptApi.askGpt(request);
//        call.enqueue(new Callback<GptResponse>() {
//            @Override
//            public void onResponse(@NonNull Call<GptResponse> call, @NonNull Response<GptResponse> response) {
//                if (response.isSuccessful() && response.body() != null) {
//
//                    // 응답 성공시 로그 등록
//                    Log.d("GPT 응답", response.body().content);
//
//                    // 결과 텍스트에 출력
//                    textResponse.setText(response.body().content);
//                } else {
//                    // 응답 실패시 로그 등록
//                    Log.e("GPT 오류", "응답 실패 : " + response.code());
//                }
//            }
//
//            @Override
//            public void onFailure(Call<GptResponse> call, Throwable t) {
//                // 서버 연결 실패시 로그 등록
//                Log.e("GPT 실패", "서버 연결 실패: " + t.getMessage());
//            }
//        });
//    }


}