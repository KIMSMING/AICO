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
import com.seoja.aico.gpt.NextInterviewRequest;
import com.seoja.aico.gpt.NextInterviewResponse;
import com.seoja.aico.gpt.StartInterviewRequest;
import com.seoja.aico.gpt.StartInterviewResponse;
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
    private String sessionId = null;
    private String userId = null;
    private String lastQuestion = null;

    private TextView textRequest, textFeedback, textTip, titleTextView;
    private View textInputLayout;
    private EditText textResponse;
    private Button btnRequest, btnNextQuestion, btnFollowup;
    private ImageButton btnBack, btnSoundplay;
    private LinearLayout feedbackSection;

    private Button btnChangeMic;
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
        btnFollowup = findViewById(R.id.btnFollowup);

        //Firebase 사용자 ID 초기화
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userId = user.getUid();
        } else {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        selectedFirst = getIntent().getStringExtra("selectedFirst");
        selectedSecond = getIntent().getStringExtra("selectedSecond");
        btnChangeMic = findViewById(R.id.btnChangeMic);
        micIcon = findViewById(R.id.micIcon);
        titleTextView = findViewById(R.id.header_title);
        titleTextView.setText("면접 연습");

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
        btnSoundplay.setOnClickListener(v -> sendTextToServer(question));
        btnFollowup.setOnClickListener(v -> {
            String answer = textResponse.getText().toString().trim();
            if (answer.isEmpty() || question == null) {
                Toast.makeText(this, "답변 입력 후 눌러주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (sessionId == null) {
                startInterview(answer);
            } else {
                requestFollowup(answer);
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
        mediaRecorder.setAudioSamplingRate(16000);
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
                if (isQuestionListLoaded[0]) showFirstQuestion();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(QuestActivity.this, "면접 팁 로딩 실패", Toast.LENGTH_SHORT).show();
            }
        });

        rootRef2.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot tipSnap : snapshot.getChildren()) {
                    String tip = tipSnap.getValue(String.class);
                    if (tip != null && !tip.isEmpty()) tipList.add(tip);
                }
                isTipListLoaded[0] = true;
                if (isQuestionListLoaded[0]) showFirstQuestion();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(QuestActivity.this, "팁 로딩 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showFirstQuestion() {
        // 질문과 팁 모두 로드 완료
        if (!questionList.isEmpty()) {
            Collections.shuffle(questionList);
            currentQuestion = 0;
            loadNewQuestion();
        } else {
            Toast.makeText(this, "질문이 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadNewQuestion() {
        if (currentQuestion >= questionList.size()) {
            Toast.makeText(this, "더이상 낼 문제가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        textTip.setText("");
        Collections.shuffle(tipList);
        if (!tipList.isEmpty()) {
            String tip = tipList.get(currentTip);
            textTip.setText(tip);
            currentTip = (currentTip + 1) % tipList.size();
        }
        question = questionList.get(currentQuestion);
        textRequest.setText(question);
        textResponse.setText(""); // 답변 필드 초기화
        feedbackSection.setVisibility(View.GONE);
        textFeedback.setText("답변 후 피드백이 여기에 표시됩니다."); // 피드백 필드 초기화
        lastQuestion = question;
        currentQuestion = (currentQuestion + 1) % questionList.size();
    }

    public void sendGptRequest() {
        // 질문과 답변 가져오기
        String quest = textRequest.getText().toString();
        String answer = textResponse.getText().toString();
        feedbackSection.setVisibility(View.VISIBLE);

        if (userId == null) {
            textFeedback.setText("로그인 정보 없음 : 서버 요청 실패");
            return;
        }
        // 질문과 답변 하나의 문자열로 만들기
        String requestMessage = "면접 질문 : " + quest + "\n사용자 답변 : " + answer;

        // 요청 객체 만들기
        GptRequest request = new GptRequest(userId, requestMessage);

        // 요청 중임을 표시
        textFeedback.setText("피드백을 요청 중입니다...");

        GptApi gptApi = retrofit.create(GptApi.class);
        gptApi.askGpt(request).enqueue(new Callback<GptResponse>() {
            @Override
            public void onResponse(@NonNull Call<GptResponse> call, @NonNull Response<GptResponse> response) {
                // UI 업데이트는 반드시 메인 스레드에서 실행
                mainHandler.post(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        String content = response.body().content;
                        textFeedback.setText(content);

                        //요약 요청 보내기
                        SummaryRequest summaryReq = new SummaryRequest(content);
                        gptApi.summarize(summaryReq).enqueue(new Callback<SummaryResponse>() {
                            @Override
                            public void onResponse(Call<SummaryResponse> call, Response<SummaryResponse> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    saveHistoryToServer(quest, answer, response.body().summary);
                                } else {
                                    saveHistoryToServer(quest, answer, content);
                                }
                            }

                            @Override
                            public void onFailure(Call<SummaryResponse> call, Throwable t) {
                                saveHistoryToServer(quest, answer, content);
                            }
                        });
                    } else {
                        textFeedback.setText("응답 실패");
                    }
                });
            }

            @Override
            public void onFailure(Call<GptResponse> call, Throwable t) {
                mainHandler.post(() ->
                    textFeedback.setText("서버 연결 실패: " + t.getMessage()));
            }
        });
    }
    //연관 질문 세션 시작
    private void startInterview(String answer) {
        GptApi api = retrofit.create(GptApi.class);
        StartInterviewRequest req = new StartInterviewRequest(userId, "개발직무", question);
        api.startInterview(req).enqueue(new Callback<StartInterviewResponse>() {
            @Override
            public void onResponse(Call<StartInterviewResponse> call, Response<StartInterviewResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    sessionId = response.body().getSession_id();
                    requestFollowup(answer);
                }
            }
            @Override
            public void onFailure(Call<StartInterviewResponse> call, Throwable t) {
                Toast.makeText(QuestActivity.this, "세션 시작 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }
    //연관질문 요청
    private void requestFollowup (String answer) {
        GptApi api = retrofit.create(GptApi.class);
        NextInterviewRequest req = new NextInterviewRequest(userId, sessionId, question, answer);
        api.nextInterview(req).enqueue(new Callback<NextInterviewResponse>() {
            @Override
            public void onResponse(Call<NextInterviewResponse> call, Response<NextInterviewResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    NextInterviewResponse body = response.body();
                    Log.d("FOLLOWUP_API", "새 질문: " + body.getQuestion());
                    Log.d("FOLLOWUP_API", "피드백: " + body.getFeedback());
                    textRequest.setText(body.getQuestion());
                    textFeedback.setText(body.getFeedback());
                    question = body.getQuestion();
                    lastQuestion = question;
                    textResponse.setText("");
                } else {
                    Log.e("FOLLOWUP_API", "응답 실패: " + response.code());
                }
            }
            @Override
            public void onFailure(Call<NextInterviewResponse> call, Throwable t) {
                Toast.makeText(QuestActivity.this, "연관 질문 요청 실패", Toast.LENGTH_SHORT).show();
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
                    os.write(json.toString().getBytes("utf-8"));
                }

                // 응답 읽기
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
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
                        if (mediaPlayer != null) {
                            mediaPlayer.release(); // 이전 mediaPlayer 해제
                        }
                        mediaPlayer = new MediaPlayer(); // 새 인스턴스 생성
                        mediaPlayer.setDataSource(tempFile.getAbsolutePath()); // 파일 경로 설정
                        mediaPlayer.prepare(); // 준비
                        mediaPlayer.start(); // 재생 시작
                    } catch (Exception e) {
                        Log.e("TTS", "재생 에러 : " + e.getMessage(), e);
                    }
                });

            } catch (Exception e) {
                Log.e("TTS", "에러: " + e.getMessage(), e);
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
        if(userId == null) {
            Log.e("HISTORY_SAVE", "로그인 필요");
            return;
        }
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
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("HISTORY_SAVE", "저장 실패 : " + t.getMessage());
            }
        });
    }
}

