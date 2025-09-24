package com.seoja.aico.quest;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
import com.seoja.aico.PresentationAnalyzer;
import com.seoja.aico.PresentationScores;
import com.seoja.aico.R;
import com.seoja.aico.gpt.GptApi;
import com.seoja.aico.gpt.GptRequest;
import com.seoja.aico.gpt.GptResponse;
import com.seoja.aico.gpt.HistoryItem;
import com.seoja.aico.gpt.SummaryRequest;
import com.seoja.aico.gpt.SummaryResponse;

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

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class QuestActivity extends AppCompatActivity implements View.OnClickListener {

    // Android 에뮬레이터에서 PC(호스트)의 localhost(127.0.0.1)를 가리키는 특수 주소
    public static final String BASE_URL = "http://10.0.2.2:8000/";
    private static final String TAG = "QuestActivity";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;

    // 파이썬 프로세스 실행용
    private Process pythonProcess;


    // 메인 UI 스레드에서 작업하기 위한 핸들러 추가
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String question;
    private TextView textRequest, textFeedback, textTip;
    private EditText textResponse;
    private Button btnRequest, btnNextQuestion;
    private ImageButton btnBack, btnSoundplay;
    private LinearLayout feedbackSection;

    // 자기소개 분석 관련
    private Button introCameraBtn, introCameraStopBtn;
    private TextView introText;
    private PresentationAnalyzer introAnalyzer;
    private boolean isIntroAnalyzing = false;

    // 질문답변 분석 관련
    private Button btnStartCamera, btnStopCamera;
    private TextView presentationScoreText;
    private PresentationAnalyzer presentationAnalyzer;
    private boolean isCameraAnalyzing = false;

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


        introCameraBtn = findViewById(R.id.introCameraBtn);
        introCameraStopBtn = findViewById(R.id.introCameraStopBtn);
        btnStartCamera = findViewById(R.id.btnStartCamera);
        btnStopCamera = findViewById(R.id.btnStopCamera);
        introText = findViewById(R.id.introText);
        presentationScoreText = findViewById(R.id.presentationScoreText);

        // 자기소개 분석 버튼
        introCameraBtn.setOnClickListener(v -> runPythonScript("intro"));

        // 질문답변 분석 버튼
        btnStartCamera.setOnClickListener(v -> runPythonScript("question"));

        // 중지 버튼
        introCameraStopBtn.setOnClickListener(v -> stopPythonScript());
        btnStopCamera.setOnClickListener(v -> stopPythonScript());

        initializeViews();
        initializePresentationAnalyzers();
        initializeNetworking();

        selectedFirst = getIntent().getStringExtra("selectedFirst");
        selectedSecond = getIntent().getStringExtra("selectedSecond");

        if (selectedSecond == null || selectedSecond.isEmpty()) {
            Toast.makeText(this, "소분류 정보가 없습니다", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        fetchJobQuestion();
        setupClickListeners();
        checkCameraPermissions();
        testServerConnection();

    }

    private void initializeViews() {
        textRequest = findViewById(R.id.textRequest);
        textResponse = findViewById(R.id.textResponse);
        textTip = findViewById(R.id.textTip);
        btnRequest = findViewById(R.id.btnRequest);
        textFeedback = findViewById(R.id.textFeedback);
        btnNextQuestion = findViewById(R.id.btnNextQuestion);
        feedbackSection = findViewById(R.id.feedbackSection);
        btnBack = findViewById(R.id.btnBack);
        btnSoundplay = findViewById(R.id.btnSoundplay);

        // 자기소개 분석 관련
        introCameraBtn = findViewById(R.id.introCameraBtn);
        introCameraStopBtn = findViewById(R.id.introCameraStopBtn);
        introText = findViewById(R.id.introText);

        // 질문답변 분석 관련
        btnStartCamera = findViewById(R.id.btnStartCamera);
        btnStopCamera = findViewById(R.id.btnStopCamera);
        presentationScoreText = findViewById(R.id.presentationScoreText);

        // 초기 상태 설정
        textFeedback.setText("답변 후 피드백이 여기에 표시됩니다.");
        introCameraStopBtn.setEnabled(false);
        introText.setText("자기소개 분석을 시작해주세요");
        btnStopCamera.setEnabled(false);
        presentationScoreText.setText("카메라 분석을 시작해주세요");
    }

    private void initializePresentationAnalyzers() {
        introAnalyzer = new PresentationAnalyzer();
        presentationAnalyzer = new PresentationAnalyzer();
    }

    private void initializeNetworking() {
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
    }

    private void setupClickListeners() {
        btnRequest.setOnClickListener(this);
        btnNextQuestion.setOnClickListener(v -> loadNewQuestion());
        btnBack.setOnClickListener(v -> finish());
        btnSoundplay.setOnClickListener(v -> sendTextToServer(question));

        // 자기소개 분석 버튼
        introCameraBtn.setOnClickListener(this);
        introCameraStopBtn.setOnClickListener(this);

        // 질문답변 분석 버튼
        btnStartCamera.setOnClickListener(this);
        btnStopCamera.setOnClickListener(this);
    }

    private void checkCameraPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "카메라 권한 승인됨");
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_LONG).show();
            }
        }
    }

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
    private void fetchJobQuestion() {
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
                    initializeQuestions();
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
                    initializeQuestions();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(QuestActivity.this, "면접 팁 로딩 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initializeQuestions() {
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

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btnRequest) {
            if (textResponse.getText().toString().isEmpty()) {
                textFeedback.setText("답변을 입력해주세요");
                return;
            }
            sendGptRequest();
        }

        // 자기소개 분석 버튼들
        if (v.getId() == R.id.introCameraBtn) {
            startIntroAnalysis();
        }
        if (v.getId() == R.id.introCameraStopBtn) {
            stopIntroAnalysis();
        }

        // 질문답변 분석 버튼들
        if (v.getId() == R.id.btnStartCamera) {
            startQuestionAnalysis();
        }
        if (v.getId() == R.id.btnStopCamera) {
            stopQuestionAnalysis();
        }
    }

    // 자기소개 분석 시작
    private void startIntroAnalysis() {
        if (!isIntroAnalyzing) {
            isIntroAnalyzing = true;
            introCameraBtn.setEnabled(false);
            introCameraStopBtn.setEnabled(true);

            introText.setText("자기소개 분석 중... 카메라를 바라보며 자기소개를 해주세요");

            introAnalyzer.startAnalysis(this, "INTRO", new PresentationAnalyzer.AnalysisCallback() {
                @Override
                public void onScoreUpdate(PresentationScores scores) {
                    runOnUiThread(() -> updateIntroScores(scores));
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(QuestActivity.this, "자기소개 분석 오류: " + error, Toast.LENGTH_SHORT).show();
                        stopIntroAnalysis();
                    });
                }
            });
        }
    }

    // 자기소개 분석 중지
    private void stopIntroAnalysis() {
        if (isIntroAnalyzing) {
            isIntroAnalyzing = false;
            introCameraBtn.setEnabled(true);
            introCameraStopBtn.setEnabled(false);

            PresentationScores finalScores = introAnalyzer.stopAnalysis();
            showIntroResults(finalScores);
        }
    }

    // 질문답변 분석 시작
    private void startQuestionAnalysis() {
        if (!isCameraAnalyzing) {
            isCameraAnalyzing = true;
            btnStartCamera.setEnabled(false);
            btnStopCamera.setEnabled(true);

            presentationScoreText.setText("질문답변 분석 중... 카메라를 바라보며 답변해주세요");

            presentationAnalyzer.startAnalysis(this, "QUESTION", new PresentationAnalyzer.AnalysisCallback() {
                @Override
                public void onScoreUpdate(PresentationScores scores) {
                    runOnUiThread(() -> updatePresentationScores(scores));
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(QuestActivity.this, "질문답변 분석 오류: " + error, Toast.LENGTH_SHORT).show();
                        stopQuestionAnalysis();
                    });
                }
            });
        }
    }

    // 질문답변 분석 중지
    private void stopQuestionAnalysis() {
        if (isCameraAnalyzing) {
            isCameraAnalyzing = false;
            btnStartCamera.setEnabled(true);
            btnStopCamera.setEnabled(false);

            PresentationScores finalScores = presentationAnalyzer.stopAnalysis();
            showQuestionResults(finalScores);
        }
    }

    // 자기소개 점수 업데이트
    private void updateIntroScores(PresentationScores scores) {
        String scoreText = String.format(
                "자기소개 분석\n\n" +
                        "👁️ 시선 접촉: %.0f점\n" +
                        "😊 표정 다양성: %.0f점\n" +
                        "🎤 음성 일관성: %.0f점\n" +
                        "✨ 자연스러움: %.0f점\n\n" +
                        "총점: %.0f점",
                scores.eyeContact,
                scores.expressionVariety,
                scores.voiceConsistency,
                scores.naturalness,
                scores.getTotalScore()
        );

        introText.setText(scoreText);
    }

    // 질문답변 점수 업데이트
    private void updatePresentationScores(PresentationScores scores) {
        String scoreText = String.format(
                "질문답변 분석\n\n" +
                        "👁️ 시선 접촉: %.0f점\n" +
                        "😊 표정 다양성: %.0f점\n" +
                        "🎤 음성 일관성: %.0f점\n" +
                        "✨ 자연스러움: %.0f점\n\n" +
                        "총점: %.0f점",
                scores.eyeContact,
                scores.expressionVariety,
                scores.voiceConsistency,
                scores.naturalness,
                scores.getTotalScore()
        );

        presentationScoreText.setText(scoreText);
    }

    // 자기소개 결과 표시
    private void showIntroResults(PresentationScores scores) {
        String grade = getGrade(scores.getTotalScore());
        String suggestions = generateSuggestions(scores);

        String message = String.format(
                "자기소개 분석 완료!\n\n" +
                        "총점: %.0f점 (%s)\n\n" +
                        "세부 점수:\n" +
                        "• 시선 접촉: %.0f점\n" +
                        "• 표정 다양성: %.0f점\n" +
                        "• 음성 일관성: %.0f점\n" +
                        "• 자연스러움: %.0f점\n\n" +
                        "개선 제안:\n%s",
                scores.getTotalScore(), grade,
                scores.eyeContact, scores.expressionVariety,
                scores.voiceConsistency, scores.naturalness,
                suggestions
        );

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("자기소개 분석 결과")
                .setMessage(message)
                .setPositiveButton("확인", null)
                .show();

        introText.setText("자기소개 분석 완료. 다시 시작하려면 분석 시작을 눌러주세요");
    }

    // 질문답변 결과 표시
    private void showQuestionResults(PresentationScores scores) {
        String grade = getGrade(scores.getTotalScore());
        String suggestions = generateSuggestions(scores);

        String message = String.format(
                "질문답변 분석 완료!\n\n" +
                        "총점: %.0f점 (%s)\n\n" +
                        "세부 점수:\n" +
                        "• 시선 접촉: %.0f점\n" +
                        "• 표정 다양성: %.0f점\n" +
                        "• 음성 일관성: %.0f점\n" +
                        "• 자연스러움: %.0f점\n\n" +
                        "개선 제안:\n%s",
                scores.getTotalScore(), grade,
                scores.eyeContact, scores.expressionVariety,
                scores.voiceConsistency, scores.naturalness,
                suggestions
        );

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("질문답변 분석 결과")
                .setMessage(message)
                .setPositiveButton("확인", null)
                .show();

        presentationScoreText.setText("질문답변 분석 완료. 다시 시작하려면 분석 시작을 눌러주세요");
    }

    private String getGrade(float score) {
        if (score >= 80) return "우수";
        else if (score >= 60) return "보통";
        else if (score >= 40) return "개선 필요";
        else return "많은 연습 필요";
    }

    private String generateSuggestions(PresentationScores scores) {
        StringBuilder suggestions = new StringBuilder();

        if (scores.eyeContact < 60) {
            suggestions.append("• 카메라(청중)를 더 자주 바라보세요\n");
        }
        if (scores.expressionVariety < 60) {
            suggestions.append("• 더 다양한 표정으로 감정을 표현해보세요\n");
        }
        if (scores.voiceConsistency < 60) {
            suggestions.append("• 목소리 톤과 속도를 일정하게 유지하세요\n");
        }
        if (scores.naturalness < 60) {
            suggestions.append("• 자연스러운 제스처를 더 많이 사용하세요\n");
        }

        if (suggestions.length() == 0) {
            suggestions.append("• 전반적으로 훌륭한 프레젠테이션입니다!");
        }

        return suggestions.toString();
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
                                    saveHistoryToServer(quest, answer, summary);
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

    private void saveHistoryToServer(String question, String answer, String feedback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e("HISTORY_SAVE", "Firebase 사용자 정보 없음 (로그인 필요)");
            return;
        }

        String userId = user.getUid();
        Log.d("HISTORY_SAVE", "저장 시도 : userId = " + userId);

        String generatedId = UUID.randomUUID().toString(); //고유 ID 생성
        HistoryItem item = new HistoryItem(generatedId, userId, question, answer, feedback);

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

    @Override
    protected void onDestroy() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }

        if (introAnalyzer != null) {
            introAnalyzer.cleanup();
        }

        if (presentationAnalyzer != null) {
            presentationAnalyzer.cleanup();
        }

        super.onDestroy();
    }

    private void runPythonScript(String mode) {
        try {
            // pythonExample.py 실행 (가상환경 or python 경로 직접 지정 필요)
            String pythonPath = "/usr/bin/python3"; // 또는 C:\Python312\python.exe
            String scriptPath = getFilesDir().getAbsolutePath() + "/pythonExample.py";

            // intro / question 모드 전달
            ProcessBuilder pb = new ProcessBuilder(
                    pythonPath, scriptPath, mode
            );
            pb.redirectErrorStream(true);
            pythonProcess = pb.start();

            // 실행 결과 읽기
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(pythonProcess.getInputStream()))) {
                    String line;
                    StringBuilder output = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        Log.d("PythonOutput", line);
                    }

                    String finalOutput = output.toString();
                    runOnUiThread(() -> {
                        if (mode.equals("intro")) {
                            introText.setText(finalOutput);
                        } else {
                            presentationScoreText.setText(finalOutput);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            Toast.makeText(this, "분석을 시작합니다 (" + mode + ")", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Python 실행 실패", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPythonScript() {
        if (pythonProcess != null) {
            pythonProcess.destroy();
            pythonProcess = null;
            Toast.makeText(this, "분석이 중지되었습니다", Toast.LENGTH_SHORT).show();
        }
    }
}
