package com.seoja.aico.quest;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
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
// [수정] SttApi 인터페이스 import 추가
import com.seoja.aico.quest.SttApi;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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

    public static final String BASE_URL = "http://10.0.2.2:8000/";
    private static final String TAG = "QuestActivity";

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
    private MediaRecorder mediaRecorder;
    private String audioFilePath;
    private boolean isRecording = false;

    private List<String> questionList = new ArrayList<>();
    private List<String> tipList = new ArrayList<>();

    private int currentQuestion = 0;
    private int currentTip = 0;

    private String selectedFirst = "";
    private String selectedSecond = "";

    private MediaPlayer mediaPlayer;

    // [수정] 클래스 멤버 변수로 선언하여 앱 전체에서 공유
    private Retrofit retrofit;
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

        // UI 요소 초기화
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
        btnChangeMic = findViewById(R.id.btnChangeMic);
        micIcon = findViewById(R.id.micIcon);
        titleTextView = findViewById(R.id.header_title);
        titleTextView.setText("면접 연습");

        // Firebase 사용자 ID 초기화
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
        if (selectedSecond == null || selectedSecond.isEmpty()) {
            Toast.makeText(this, "소분류 정보가 없습니다", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // [수정 1] 사라진 네트워크 초기화 코드 복원 및 통합
        // 로깅 인터셉터 추가
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        // OkHttpClient 설정 (타임아웃 증가)
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();
        // Gson 커스터마이즈
        gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();

        // Retrofit 설정 (두 가지 Converter를 모두 추가)
        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                // ScalarsConverterFactory는 일반 String 응답을 처리할 때 필요 (STT)
                .addConverterFactory(ScalarsConverterFactory.create())
                // GsonConverterFactory는 JSON 응답을 처리할 때 필요 (GPT)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(client)
                .build();


        // 데이터 로딩 및 이벤트 리스너 설정
        fetchJobQeustion();
        setClickListeners();

        // 초기 상태 설정
        textFeedback.setText("답변 후 피드백이 여기에 표시됩니다.");

        // 서버 연결 테스트
        testServerConnection();
    }

    private void setClickListeners() {
        btnRequest.setOnClickListener(this);
        btnNextQuestion.setOnClickListener(v -> loadNewQuestion());
        btnBack.setOnClickListener(V -> finish());
        btnSoundplay.setOnClickListener(v -> sendTextToServer(question));
        btnChangeMic.setOnClickListener(v -> toggleInputMode());
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
        micIcon.setOnClickListener(v -> {
            if (!isMicMode) return;
            try {
                toggleRecording();
            } catch (IOException e) {
                Log.e(TAG, "Recording toggle failed", e);
                Toast.makeText(this, "녹음 시작/중지에 실패했습니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toggleInputMode() {
        if (isRecording) {
            stopRecording();
        }

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
        }
    }

    private void toggleRecording() throws IOException {
        if (isRecording) {
            stopRecording();
            micIcon.setImageResource(R.drawable.ic_mic);
        } else {
            startRecording();
            micIcon.setImageResource(R.drawable.ic_mic_on);
        }
    }

    private void startRecording() throws IOException {
        File outputDir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "recordings");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            Log.e(TAG, "Failed to create recording directory");
            return;
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

    private void stopRecording() {
        if (mediaRecorder != null && isRecording) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
            } catch (RuntimeException e) {
                Log.e(TAG, "stopRecording failed", e);
            } finally {
                mediaRecorder = null;
                isRecording = false;
            }

            File audioFile = new File(audioFilePath);
            if (audioFile.exists()) {
                RequestBody requestFile = RequestBody.create(MediaType.parse("audio/m4a"), audioFile);
                MultipartBody.Part body = MultipartBody.Part.createFormData("file", audioFile.getName(), requestFile);
                uploadAudioToServer(body);
            }
        }
    }

    private void uploadAudioToServer(MultipartBody.Part body) {
        // [수정 2] 새로 Retrofit을 만들지 않고, onCreate에서 만든 공통 객체 사용
        SttApi service = retrofit.create(SttApi.class);
        Call<String> call = service.uploadAudio(body);

        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String textResult = response.body();
                    // 따옴표가 포함된 경우 제거
                    textResult = textResult.replaceAll("^\"|\"$", "");
                    textResponse.setText(textResult);
                    Toast.makeText(QuestActivity.this, "음성이 텍스트로 변환되었습니다.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(QuestActivity.this, "서버 응답 오류: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                Toast.makeText(QuestActivity.this, "서버 전송 실패: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Audio Upload Failure", t);
            }
        });
    }

    private void testServerConnection() {
        if (retrofit == null) {
            Log.e(TAG, "Retrofit not initialized!");
            return;
        }
        retrofit.create(GptApi.class).testConnection().enqueue(new Callback<Object>() {
            @Override
            public void onResponse(@NonNull Call<Object> call, @NonNull Response<Object> response) {
                Log.d(TAG, "서버 연결 테스트 성공: " + response.code());
            }

            @Override
            public void onFailure(@NonNull Call<Object> call, @NonNull Throwable t) {
                Log.e(TAG, "서버 연결 테스트 실패: " + t.getMessage());
            }
        });
    }

    // [수정 3] 중복된 Firebase 리스너 하나 제거
    private void fetchJobQeustion() {
        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference("면접질문");
        DatabaseReference rootRef2 = FirebaseDatabase.getInstance().getReference("면접팁");

        // 두 비동기 작업의 완료를 추적하기 위한 카운터
        final int[] loadCounter = {2};

        ValueEventListener completionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                loadCounter[0]--;
                if (loadCounter[0] == 0) {
                    showFirstQuestion();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                loadCounter[0]--;
                Log.e(TAG, "Firebase load cancelled: " + error.getMessage());
                Toast.makeText(QuestActivity.this, "데이터 로딩 중 오류 발생", Toast.LENGTH_SHORT).show();
            }
        };

        rootRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                questionList.clear();
                // 공통질문, 인사질문, 직업질문 가져오기 로직 ... (기존과 동일)
                DataSnapshot commonSnap = snapshot.child("공통질문");
                for (DataSnapshot questionSnap : commonSnap.getChildren()) {
                    questionList.add(questionSnap.getValue(String.class));
                }
                DataSnapshot hrSnap = snapshot.child("인사질문");
                for (DataSnapshot questionSnap : hrSnap.getChildren()) {
                    questionList.add(questionSnap.getValue(String.class));
                }
                DataSnapshot jobSnap = snapshot.child("직업질문").child(selectedFirst).child(selectedSecond);
                for (DataSnapshot questionSnap : jobSnap.getChildren()) {
                    questionList.add(questionSnap.getValue(String.class));
                }
                completionListener.onDataChange(snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                completionListener.onCancelled(error);
            }
        });

        rootRef2.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                tipList.clear();
                for (DataSnapshot tipSnap : snapshot.getChildren()) {
                    tipList.add(tipSnap.getValue(String.class));
                }
                completionListener.onDataChange(snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                completionListener.onCancelled(error);
            }
        });
    }

    private void showFirstQuestion() {
        if (questionList.isEmpty()) {
            Toast.makeText(this, "질문 목록을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Collections.shuffle(questionList);
        currentQuestion = 0;
        loadNewQuestion();
    }

    private void loadNewQuestion() {
        if (currentQuestion >= questionList.size()) {
            Toast.makeText(this, "모든 질문에 답변하셨습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!tipList.isEmpty()) {
            Collections.shuffle(tipList);
            textTip.setText(tipList.get(0));
        }
        question = questionList.get(currentQuestion);
        textRequest.setText(question);
        textResponse.setText("");
        feedbackSection.setVisibility(View.GONE);
        textFeedback.setText("답변 후 피드백이 여기에 표시됩니다.");
        lastQuestion = question;
        currentQuestion++;
    }

    public void sendGptRequest() {
        String quest = textRequest.getText().toString();
        String answer = textResponse.getText().toString();
        feedbackSection.setVisibility(View.VISIBLE);
        textFeedback.setText("피드백을 요청 중입니다...");

        GptRequest request = new GptRequest(userId, "면접 질문 : " + quest + "\n사용자 답변 : " + answer);
        GptApi gptApi = retrofit.create(GptApi.class);

        gptApi.askGpt(request).enqueue(new Callback<GptResponse>() {
            @Override
            public void onResponse(@NonNull Call<GptResponse> call, @NonNull Response<GptResponse> response) {
                mainHandler.post(() -> {
                    if (response.isSuccessful() && response.body() != null) {
                        String content = response.body().content;
                        textFeedback.setText(content);
                        summarizeAndSaveHistory(quest, answer, content);
                    } else {
                        textFeedback.setText("응답 실패: " + response.code());
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<GptResponse> call, @NonNull Throwable t) {
                mainHandler.post(() -> textFeedback.setText("서버 연결 실패: " + t.getMessage()));
            }
        });
    }

    private void summarizeAndSaveHistory(String question, String answer, String feedback) {
        GptApi gptApi = retrofit.create(GptApi.class);
        SummaryRequest summaryReq = new SummaryRequest(feedback);
        gptApi.summarize(summaryReq).enqueue(new Callback<SummaryResponse>() {
            @Override
            public void onResponse(@NonNull Call<SummaryResponse> call, @NonNull Response<SummaryResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    saveHistoryToServer(question, answer, response.body().summary);
                } else {
                    saveHistoryToServer(question, answer, feedback); // 요약 실패 시 원본 저장
                }
            }
            @Override
            public void onFailure(@NonNull Call<SummaryResponse> call, @NonNull Throwable t) {
                saveHistoryToServer(question, answer, feedback); // 요약 실패 시 원본 저장
            }
        });
    }

    private void startInterview(String answer) {
        // [수정] 하드코딩된 직무 대신 selectedFirst 사용
        StartInterviewRequest req = new StartInterviewRequest(userId, selectedFirst, question);
        GptApi api = retrofit.create(GptApi.class);
        api.startInterview(req).enqueue(new Callback<StartInterviewResponse>() {
            @Override
            public void onResponse(@NonNull Call<StartInterviewResponse> call, @NonNull Response<StartInterviewResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    sessionId = response.body().getSession_id();
                    requestFollowup(answer);
                }
            }
            @Override
            public void onFailure(@NonNull Call<StartInterviewResponse> call, @NonNull Throwable t) {
                Toast.makeText(QuestActivity.this, "세션 시작 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void requestFollowup(String answer) {
        NextInterviewRequest req = new NextInterviewRequest(userId, sessionId, question, answer);
        GptApi api = retrofit.create(GptApi.class);
        api.nextInterview(req).enqueue(new Callback<NextInterviewResponse>() {
            @Override
            public void onResponse(@NonNull Call<NextInterviewResponse> call, @NonNull Response<NextInterviewResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    NextInterviewResponse body = response.body();
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
            public void onFailure(@NonNull Call<NextInterviewResponse> call, @NonNull Throwable t) {
                Toast.makeText(QuestActivity.this, "연관 질문 요청 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnRequest) {
            if (textResponse.getText().toString().isEmpty()) {
                textFeedback.setText("답변을 입력해주세요");
                return;
            }
            sendGptRequest();
        } else if (id == R.id.btnNextQuestion) {
            loadNewQuestion();
        }
    }

    private void sendTextToServer(String questionText) {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "interview/voice");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setDoOutput(true);

                JSONObject json = new JSONObject();
                json.put("text", questionText);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.toString().getBytes("utf-8"));
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }

                JSONObject responseJson = new JSONObject(responseBuilder.toString());
                String audioBase64 = responseJson.getString("audio_base64");
                byte[] audioBytes = Base64.decode(audioBase64, Base64.DEFAULT);

                File tempFile = File.createTempFile("tts", ".mp3", getCacheDir());
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(audioBytes);
                }

                runOnUiThread(() -> playAudio(tempFile.getAbsolutePath()));

            } catch (Exception e) {
                Log.e("TTS", "Error: " + e.getMessage(), e);
            }
        }).start();
    }

    private void playAudio(String filePath) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException e) {
            Log.e("TTS", "Playback Error: " + e.getMessage(), e);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    private void saveHistoryToServer(String question, String answer, String feedback) {
        if (userId == null) return;
        String generatedId = UUID.randomUUID().toString();
        HistoryItem item = new HistoryItem(generatedId, userId, question, answer, feedback);

        GptApi api = retrofit.create(GptApi.class);
        Call<Void> call = api.saveHistory(item);

        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d("HISTORY_SAVE", "히스토리 저장 성공");
                } else {
                    Log.e("HISTORY_SAVE", "저장 실패 : " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Log.e("HISTORY_SAVE", "저장 실패 : " + t.getMessage());
            }
        });
    }
}