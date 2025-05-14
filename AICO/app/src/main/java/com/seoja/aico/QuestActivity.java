package com.seoja.aico;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.seoja.aico.gpt.GptApi;
import com.seoja.aico.gpt.GptRequest;
import com.seoja.aico.gpt.GptResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class QuestActivity extends AppCompatActivity {

    // Android 에뮬레이터에서 PC(호스트)의 localhost(127.0.0.1)를 가리키는 특수 주소
    private static final String BASE_URL = "http://10.0.2.2:8000";

    TextView requestText, responseText;
    Button requestBtn;

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

        requestText = findViewById(R.id.request_text);
        responseText = findViewById(R.id.response_textView);
        requestBtn = findViewById(R.id.request_button);

        requestBtn.setOnClickListener(v -> sendGptRequest());
    }

    public void sendGptRequest(){
        String t = requestText.getText().toString();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        GptApi gptApi = retrofit.create(GptApi.class);

        // 요청 객체 만들기
        GptRequest request = new GptRequest(t);

        Call<GptResponse> call = gptApi.askGpt(request);
        call.enqueue(new Callback<GptResponse>() {
            @Override
            public void onResponse(@NonNull Call<GptResponse> call, @NonNull Response<GptResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d("GPT 응답", response.body().content); // 응답 성공시 로그 등록
                    responseText.setText(response.body().content); // 결과 텍스트에 출력
                } else {
                    Log.e("GPT 오류", "응답 실패 : " + response.code()); // 응답 실패시 로그 등록
                }
            }

            @Override
            public void onFailure(Call<GptResponse> call, Throwable t) {
                Log.e("GPT 실패", "서버 연결 실패: " + t.getMessage()); // 서버 연결 실패시 로그 등록
            }
        });
    }


}