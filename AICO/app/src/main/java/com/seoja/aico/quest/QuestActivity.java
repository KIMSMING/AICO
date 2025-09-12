package com.seoja.aico.quest;

import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Layout;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.seoja.aico.R;
import com.seoja.aico.gpt.GptApi;
import com.seoja.aico.gpt.GptRequest;
import com.seoja.aico.gpt.GptResponse;
import com.seoja.aico.gpt.HistoryItem;
import com.seoja.aico.gpt.SummaryRequest;
import com.seoja.aico.gpt.SummaryResponse;

import java.io.File;
import java.io.IOException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;


public class QuestActivity extends AppCompatActivity implements View.OnClickListener {

    // Android 에뮬레이터에서 PC(호스트)의 localhost(127.0.0.1)를 가리키는 특수 주소
    public static final String BASE_URL = "http://10.0.2.2:8000/";
    private static final String TAG = "QuestActivity"; // 로그 태그 추가

    // 메인 UI 스레드에서 작업하기 위한 핸들러 추가
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String question;
    private TextView textRequest, textFeedback, textTip;
    private View textInputLayout;
    private EditText textResponse;
    private Button btnRequest, btnNextQuestion;
    private ImageButton btnBack, btnSoundplay;
    private LinearLayout feedbackSection;

    private Button btnChangeMic, btnGoAnother;
    private ImageButton micIcon;
    private boolean isMicMode = false;
    private boolean isMicRecording = false;

//    private SpeechRecognizer speechRecognizer;
//    private Intent recognizerIntent;
//    private boolean isListening = false;

    private MediaRecorder mediaRecorder;
    private String audioFilePath;
    private boolean isRecording = false;

    private List<String> questionList = new ArrayList<>();
    private List<String> tipList = new ArrayList<>();

    // 셔플된 리스트에서 문제출력을 위한 인덱스
    private int currentQuestion = 0;
    private int currentTip = 0;

    private String selectedFirst = "";
    private String selectedSecond = "";

    // 오디오 재생을 위한 MediaPlayer 객체
    private MediaPlayer mediaPlayer;

    private Retrofit retrofit;
    private OkHttpClient client;
    private HttpLoggingInterceptor logging;
    private Gson gson;

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
        textInputLayout = findViewById(R.id.textInputLayout);
        textResponse = findViewById(R.id.textResponse);
        textTip = findViewById(R.id.textTip);
        btnRequest = findViewById(R.id.btnRequest);
        textFeedback = findViewById(R.id.textFeedback);
        btnNextQuestion = findViewById(R.id.btnNextQuestion);
        feedbackSection = findViewById(R.id.feedbackSection);
        btnBack = findViewById(R.id.btnBack);
        btnSoundplay = findViewById(R.id.btnSoundplay);

        selectedFirst = getIntent().getStringExtra("selectedFirst");
        selectedSecond = getIntent().getStringExtra("selectedSecond");
        btnChangeMic = findViewById(R.id.btnChangeMic);
        micIcon = findViewById(R.id.micIcon);

        btnGoAnother = findViewById(R.id.btnGoAnother);

        btnChangeMic.setOnClickListener(v -> toggleInputMode());

        if (selectedSecond == null || selectedSecond.isEmpty()) {
            Toast.makeText(this, "소분류 정보가 없습니다", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        fetchJobQeustion();

        btnRequest.setOnClickListener(this);
        btnNextQuestion.setOnClickListener(v -> loadNewQuestion());
        btnBack.setOnClickListener(V -> finish());
        btnSoundplay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendTextToServer(question);
            }
        });

        // 초기 상태 설정
        textFeedback.setText("답변 후 피드백이 여기에 표시됩니다.");

        // 마이크 연결
//        initSpeechRecognizer();
        micIcon.setOnClickListener(v -> {
            if (!isMicMode) return;
            try {
                toggleRecording();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        btnGoAnother.setOnClickListener(v -> {
            Intent intent = new Intent(QuestActivity.this, RecordingListActivity.class);
            startActivity(intent);
        });

        // 서버 연결 테스트
        testServerConnection();
    }

    private void toggleInputMode() {
        if (isRecording) stopRecording();

        isMicMode = !isMicMode;
        if (isMicMode) {
            textInputLayout.setVisibility(View.GONE);
            micIcon.setVisibility(View.VISIBLE);
            btnChangeMic.setText("텍스트 전환");
            micIcon.setImageResource(R.drawable.ic_mic);
        } else {
            textInputLayout.setVisibility(View.VISIBLE);
            micIcon.setVisibility(View.GONE);
            btnChangeMic.setText("음성 전환");
            micIcon.setImageResource(R.drawable.ic_mic);
        }
    }

    // 녹음 토글
    private void toggleRecording() throws IOException {
        if (isRecording) {
            stopRecording();
            micIcon.setImageResource(R.drawable.ic_mic); // 대기 상태
        } else {
            startRecording();
            micIcon.setImageResource(R.drawable.ic_mic_on); // 녹음중
        }
    }

    // 녹음 시작
    private void startRecording() throws IOException {
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }

        // 디렉토리 저장
        File outputDir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "recordings");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String fileName = "recorded_" + System.currentTimeMillis() + ".m4a";
        File audioFile = new File(outputDir, fileName);
        audioFilePath = audioFile.getAbsolutePath();

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setOutputFile(audioFilePath);
        mediaRecorder.prepare();
        mediaRecorder.start();
        isRecording = true;
    }


    // 녹음 중지
    private void stopRecording() {
        if (mediaRecorder != null && isRecording) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
        }

        File audioFile = new File(audioFilePath);
        RequestBody requestFile = RequestBody.create(
                MediaType.parse("audio/m4a"),
                audioFile
        );
        MultipartBody.Part body = MultipartBody.Part.createFormData(
                "file",
                audioFile.getName(),
                requestFile
        );

        uploadAudioToServer(body);
    }

    // Retrofit 서버 업로드
    private void uploadAudioToServer(MultipartBody.Part body) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://" + getString(R.string.server_url)) // 서버 주소
                .addConverterFactory(ScalarsConverterFactory.create())
                .build();

        SttApi service = retrofit.create(SttApi.class);
        Call<String> call = service.uploadAudio(body);
        Context context = QuestActivity.this;

        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if (response.isSuccessful()) {
                    String textResult = response.body();
                    Toast.makeText(context, "STT 결과: " + textResult, Toast.LENGTH_LONG).show();

                    runOnUiThread(() -> {
                        textResponse.setText(textResult);
                    });

                } else {
                    Toast.makeText(context, "서버 응답 오류", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                Toast.makeText(context, "서버 전송 실패: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 서버 연결 테스트
    private void testServerConnection() {
        // 로깅 인터셉터 추가
        logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        // OkHttpClient 설정
        client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();
        //Gson 커스터마이즈
        gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();

        // Retrofit 설정
        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(client)
                .build();

        // 서버 연결 테스트
        testServerConnection();
    }

    // Firebase에서 데이터 가져오기
    private void fetchJobQeustion() {
        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference("면접질문");
        DatabaseReference rootRef2 = FirebaseDatabase.getInstance().getReference("면접팁");

        final boolean[] isQuestionListLoaded = {false};
        final boolean[] isTipListLoaded = {false};

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
                isQuestionListLoaded[0] = true;
                if (isTipListLoaded[0]) {
                    // 질문과 팁 모두 로드 완료
                    if (!questionList.isEmpty()) {
                        Collections.shuffle(questionList);
                        currentQuestion = 0;
                        loadNewQuestion();
                    } else {
                        Toast.makeText(QuestActivity.this, "질문이 없습니다.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(QuestActivity.this, "데이터 로딩 실패", Toast.LENGTH_SHORT).show();
            }
        });

        rootRef2.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot tipSnap : snapshot.getChildren()) {
                    String tip = tipSnap.getValue(String.class);
                    if (tip != null && !tip.isEmpty()) {
                        tipList.add(tip);
                    }
                }

                isTipListLoaded[0] = true;
                if (isQuestionListLoaded[0]) {
                    // 질문과 팁 모두 로드 완료
                    if (!questionList.isEmpty()) {
                        Collections.shuffle(questionList);
                        currentQuestion = 0;
                        loadNewQuestion();
                    } else {
                        Toast.makeText(QuestActivity.this, "질문이 없습니다.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(QuestActivity.this, "면접 팁 로딩 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadNewQuestion() {
        if (currentQuestion >= questionList.size()) {
            Toast.makeText(this, "더이상 낼 문제가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        textTip.setText("");
        Collections.shuffle(tipList);
        String tip = tipList.get(currentTip);
        textTip.setText(tip);
        question = questionList.get(currentQuestion);
        textRequest.setText(question);
        textResponse.setText(""); // 답변 필드 초기화
        feedbackSection.setVisibility(View.GONE);
        textFeedback.setText("답변 후 피드백이 여기에 표시됩니다."); // 피드백 필드 초기화
        currentQuestion = (currentQuestion + 1) % questionList.size();
        currentTip = (currentTip + 1) % tipList.size();
    }

    public void sendGptRequest() {
        // 질문과 답변 가져오기
        String quest = textRequest.getText().toString();
        String answer = textResponse.getText().toString();
        feedbackSection.setVisibility(View.VISIBLE);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            textFeedback.setText("로그인 정보 없음 : 서버 요청 실패");
            return;
        }

        String userId = user.getUid();
        // 질문과 답변 하나의 문자열로 만들기
        String requestMessage = "면접 질문 : " + quest + "\n사용자 답변 : " + answer;

        // 요청 객체 만들기
        GptRequest request = new GptRequest(userId, requestMessage);

        // 요청 중임을 표시
        textFeedback.setText("피드백을 요청 중입니다...");
//        btnRequest.setEnabled(false); // 버튼 비활성화

        // 로깅 인터셉터 추가
//        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
//        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        // OkHttpClient 설정
//        OkHttpClient client = new OkHttpClient.Builder()
//                .connectTimeout(60, TimeUnit.SECONDS)  // 연결 타임아웃 증가
//                .readTimeout(60, TimeUnit.SECONDS)     // 읽기 타임아웃 증가
//                .writeTimeout(60, TimeUnit.SECONDS)    // 쓰기 타임아웃 증가
//                .addInterceptor(logging)               // 로깅 추가
//                .build();

        // Retrofit 설정
//        Retrofit retrofit = new Retrofit.Builder()
//                .baseUrl(BASE_URL)
//                .addConverterFactory(GsonConverterFactory.create())
//                .client(client)  // OkHttpClient 설정 추가
//                .build();

        GptApi gptApi = retrofit.create(GptApi.class);

        Log.d(TAG, "요청 시작: " + requestMessage);

        gptApi.askGpt(request).enqueue(new Callback<GptResponse>() {
            @Override
            public void onResponse(@NonNull Call<GptResponse> call, @NonNull Response<GptResponse> response) {
                Log.d(TAG, "onResponse 호출됨, HTTP 코드: " + response.code());  // 콜백 진입 확인

                // UI 업데이트는 반드시 메인 스레드에서 실행
                mainHandler.post(() -> {
                    btnRequest.setEnabled(true);

                    if (response.isSuccessful() && response.body() != null) {
                        String content = response.body().content;
                        textFeedback.setText(content);

                        //요약 요청 보내기
                        SummaryRequest summaryReq = new SummaryRequest(content);
                        gptApi.summarize(summaryReq).enqueue(new Callback<SummaryResponse>() {
                            @Override
                            public void onResponse(Call<SummaryResponse> call, Response<SummaryResponse> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    String summary = response.body().summary;
                                    Log.d("SUMMARY", "요약 성공: " + summary);

                                    saveHistoryToServer(
                                            quest,
                                            answer,
                                            summary
                                    );
                                } else {
                                    Log.e("SUMMARY", "요약 실패, 원문 저장");
                                    saveHistoryToServer(quest, answer, content);
                                }
                            }

                            @Override
                            public void onFailure(Call<SummaryResponse> call, Throwable t) {
                                Log.e("SUMMARY_ERR", "요약 실패: " + t.getMessage());
                                saveHistoryToServer(quest, answer, content);
                            }
                        });
                    } else {
                        String errorMsg = "응답 실패: " + response.code();
                        try {
                            // 에러 바디가 있으면 읽어서 출력
                            if (response.errorBody() != null) {
                                errorMsg += "\n" + response.errorBody().string();
                            }
                        } catch (Exception e) {
                            errorMsg += "\n" + e.getMessage();
                        }
                        Log.e(TAG, errorMsg);
                        textFeedback.setText(errorMsg);
                    }
                });
            }

            @Override
            public void onFailure(Call<GptResponse> call, Throwable t) {
                Log.e(TAG, "서버 연결 실패: " + t.getMessage());
                mainHandler.post(() -> {
                    btnRequest.setEnabled(true);
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

    // 질문 텍스트를 백엔드 서버에 전송하고, 응답받은 Base64 음성을 재생하는 함수
    private void sendTextToServer(String questionText) {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "interview/voice");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setDoOutput(true);

                // JSON으로 질문 전송
                JSONObject json = new JSONObject();
                json.put("text", questionText);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                // 응답 읽기
                InputStream responseStream = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream));
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }

                JSONObject responseJson = new JSONObject(responseBuilder.toString());
                String audioBase64 = responseJson.getString("audio_base64");

                // Base64 디코딩
                byte[] audioBytes = Base64.decode(audioBase64, Base64.DEFAULT);

                // 임시 mp3 파일 저장
                File tempFile = File.createTempFile("tts", ".mp3", getCacheDir());
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(audioBytes);
                }

                // UI 쓰레드에서 MediaPlayer 재생
                runOnUiThread(() -> {
                    try {
                        Log.d("TTS", "UI 스레드 진입");

                        if (mediaPlayer != null) {
                            mediaPlayer.release(); // 이전 mediaPlayer 해제
                        }

                        mediaPlayer = new MediaPlayer(); // 새 인스턴스 생성

                        Log.d("TTS", "오디오 파일 경로: " + tempFile.getAbsolutePath());
                        Log.d("TTS", "파일 존재 여부: " + tempFile.exists());
                        Log.d("TTS", "파일 크기: " + tempFile.length());

                        mediaPlayer.setDataSource(tempFile.getAbsolutePath()); // 파일 경로 설정

                        mediaPlayer.prepare(); // 준비

                        mediaPlayer.start(); // 재생 시작

                    } catch (Exception e) {
                        Log.e("TTS", "UI 쓰레드 내 에러: " + e.getMessage(), e);
                    }
                });


            } catch (Exception e) {
                Log.e("TTS", "Error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        if (mediaPlayer != null) {
            mediaPlayer.release(); // 앱 종료 시 재생기 해제
        }
        if (isRecording) stopRecording();
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
        super.onDestroy();
    }
    //히스토리 저장 함수
    private void saveHistoryToServer(String question, String answer, String feedback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if(user == null) {
            Log.e("HISTORY_SAVE", "Firebase 사용자 정보 없음 (로그인 필요)");
            return;
        }

        String userId = user.getUid();
        Log.d("HISTORY_SAVE", "저장 시도 : userId = " + userId);

        String generatedId = UUID.randomUUID().toString(); //고유 ID 생성
        HistoryItem item = new HistoryItem  (generatedId, userId, question, answer, feedback);

        GptApi api = retrofit.create(GptApi.class);
        Call<Void> call = api.saveHistory(item);

        Log.d("HISTORY_JSON", gson.toJson(item));

        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d("HISTORY_SAVE", "히스토리 저장 성공");
                } else {
                    Log.e("HISTORY_SAVE", "저장 실패 : " + response.code());
                    try {
                        Log.e("HISTORY_SAVE", "에러 바디: " + response.errorBody().string());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("HISTORY_SAVE", "저장 실패 : " + t.getMessage());
            }
        });
    }
}

