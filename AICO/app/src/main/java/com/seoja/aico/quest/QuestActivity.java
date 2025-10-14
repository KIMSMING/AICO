package com.seoja.aico.quest;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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

public class QuestActivity extends AppCompatActivity implements View.OnClickListener {

//    public static final String BASE_URL = "http://192.168.56.1:8000/"; // 본인 컴퓨터
    public static final String BASE_URL = "http://172.20.10.4:8000/"; // 핫스팟 주소
    private static final String TAG = "QuestActivity";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;
    private static final int AUDIO_PERMISSION_REQUEST_CODE = 1002;

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
    private boolean isIntroAnalyzing = false;

    // 질문답변 분석 관련
    private Button btnStartCamera, btnStopCamera;
    private TextView presentationScoreText;
    private boolean isCameraAnalyzing = false;

    // 녹음 관련
    private MediaRecorder mediaRecorder;
    private File currentRecordingFile;
    private boolean isRecording = false;
    private SpeechRecognizer speechRecognizer;

    // 카메라 관련
    private Uri videoUri;
    private File currentVideoFile;

    private List<String> questionList = new ArrayList<>();
    private List<String> tipList = new ArrayList<>();

    private int currentQuestion = 0;
    private int currentTip = 0;

    private String selectedFirst = "";
    private String selectedSecond = "";

    private MediaPlayer mediaPlayer;

    private Retrofit retrofit;
    private OkHttpClient client;
    private HttpLoggingInterceptor logging;
    private Gson gson;

    // ActivityResultLauncher for camera
    private ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    handleVideoResult();
                } else {
                    resetAnalysisState();
                }
            });

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

        initializeViews();
        initializeNetworking();
        initializeSpeechRecognizer();

        selectedFirst = getIntent().getStringExtra("selectedFirst");
        selectedSecond = getIntent().getStringExtra("selectedSecond");

        if (selectedSecond == null || selectedSecond.isEmpty()) {
            Toast.makeText(this, "소분류 정보가 없습니다", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        fetchJobQuestion();
        setupClickListeners();
        checkPermissions();
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
        introText.setText("자기소개 영상을 촬영하여 분석을 시작해주세요");
        btnStopCamera.setEnabled(false);
        presentationScoreText.setText("질문 답변 영상을 촬영하여 분석을 시작해주세요");
    }

    private void initializeNetworking() {
        logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();

        gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();

        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(client)
                .build();
    }

    private void initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
            }

            @Override
            public void onBeginningOfSpeech() {
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
            }

            @Override
            public void onError(int error) {
                Log.e(TAG, "Speech recognition error: " + error);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    Log.d(TAG, "Recognized text: " + recognizedText);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });
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

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    CAMERA_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                Log.d(TAG, "모든 권한 승인됨");
            } else {
                Log.d(TAG, "카메라와 오디오 권한이 필요합니다");
            }
        }
    }

    private void testServerConnection() {
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

    private void fetchJobQuestion() {
        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference("면접질문");
        DatabaseReference rootRef2 = FirebaseDatabase.getInstance().getReference("면접팁");

        final boolean[] isQuestionListLoaded = {false};
        final boolean[] isTipListLoaded = {false};

        rootRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // 공통질문 가져오기
                DataSnapshot commonSnap = snapshot.child("공통질문");
                for (DataSnapshot questionSnap : commonSnap.getChildren()) {
                    String question = questionSnap.getValue(String.class);
                    if (question != null && !question.isEmpty()) {
                        questionList.add(question);
                    }
                }

                // 인사질문 가져오기
                DataSnapshot hrSnap = snapshot.child("인사질문");
                for (DataSnapshot questionSnap : hrSnap.getChildren()) {
                    String question = questionSnap.getValue(String.class);
                    if (question != null && !question.isEmpty()) {
                        questionList.add(question);
                    }
                }

                // 직업질문 가져오기
                DataSnapshot jobSnap = snapshot.child("직업질문")
                        .child(selectedFirst)
                        .child(selectedSecond);
                for (DataSnapshot questionSnap : jobSnap.getChildren()) {
                    String question = questionSnap.getValue(String.class);
                    if (question != null && !question.isEmpty()) {
                        questionList.add(question);
                    }
                }

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
        textResponse.setText("");
        feedbackSection.setVisibility(View.GONE);
        textFeedback.setText("답변 후 피드백이 여기에 표시됩니다.");
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

        // 자기소개 분석
        if (v.getId() == R.id.introCameraBtn) {
            startIntroVideoRecording();
        }
        if (v.getId() == R.id.introCameraStopBtn) {
            stopCurrentAnalysis("intro");
        }

        // 질문답변 분석
        if (v.getId() == R.id.btnStartCamera) {
            startQuestionVideoRecording();
        }
        if (v.getId() == R.id.btnStopCamera) {
            stopCurrentAnalysis("question");
        }
    }

    // 자기소개 영상 촬영 시작
    private void startIntroVideoRecording() {
        if (!isIntroAnalyzing) {
            isIntroAnalyzing = true;
            introCameraBtn.setEnabled(false);
            introCameraStopBtn.setEnabled(true);
            introText.setText("자기소개 영상을 촬영 중입니다...");

            startVideoRecording("intro", 60);
        }
    }

    // 질문답변 영상 촬영 시작
    private void startQuestionVideoRecording() {
        if (!isCameraAnalyzing) {
            isCameraAnalyzing = true;
            btnStartCamera.setEnabled(false);
            btnStopCamera.setEnabled(true);
            presentationScoreText.setText("질문답변 영상을 촬영 중입니다...");

            startVideoRecording("question", 120);
        }
    }

    // 영상 촬영 시작
    private void startVideoRecording(String analysisType, int duration) {
        // 권한 확인
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show();
            checkPermissions();
            return;
        }

        try {
            // 저장 경로 (간단하게 변경)
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if (storageDir == null) {
                showErrorMessage("저장소에 접근할 수 없습니다");
                return;
            }

            // 파일명
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            currentVideoFile = new File(storageDir, analysisType + "_" + timeStamp + ".mp4");

            Log.d(TAG, "비디오 저장 경로: " + currentVideoFile.getAbsolutePath());

            // FileProvider URI
            videoUri = FileProvider.getUriForFile(
                    this,
                    "com.seoja.aico.fileprovider",  // 패키지명 확인
                    currentVideoFile
            );

            Log.d(TAG, "VideoURI: " + videoUri.toString());

            // 카메라 Intent
            Intent cameraIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);
            cameraIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, duration);
            cameraIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);

            // 권한 플래그 - 매우 중요!
            cameraIntent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // 오디오 녹음
            startAudioRecording(analysisType);

            // 카메라 실행
            cameraLauncher.launch(cameraIntent);

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "FileProvider 오류: " + e.getMessage());
            showErrorMessage("파일 경로 설정 오류. file_paths.xml을 확인하세요.");
            resetAnalysisState();
        } catch (Exception e) {
            Log.e(TAG, "카메라 시작 오류: " + e.getMessage());
            e.printStackTrace();
            showErrorMessage("카메라 시작 실패: " + e.getMessage());
            resetAnalysisState();
        }
    }

    // 오디오 녹음 시작
    private void startAudioRecording(String analysisType) {
        try {
            // 오디오 파일 생성
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = analysisType + "_audio_" + timeStamp + ".3gp";
            File audioDir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "InterviewAudios");
            if (!audioDir.exists()) {
                audioDir.mkdirs();
            }
            currentRecordingFile = new File(audioDir, fileName);

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setOutputFile(currentRecordingFile.getAbsolutePath());
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;

            Log.d(TAG, "오디오 녹음 시작: " + currentRecordingFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "오디오 녹음 시작 오류: " + e.getMessage());
            showErrorMessage("오디오 녹음을 시작할 수 없습니다: " + e.getMessage());
        }
    }

    // 영상 촬영 결과 처리
    private void handleVideoResult() {
        // 오디오 녹음 중지
        stopAudioRecording();

        Log.d(TAG, "비디오 촬영 완료");
        Log.d(TAG, "currentVideoFile: " + (currentVideoFile != null ? currentVideoFile.getAbsolutePath() : "null"));
        Log.d(TAG, "파일 존재 여부: " + (currentVideoFile != null && currentVideoFile.exists()));

        // 파일 존재 확인
        if (currentVideoFile == null || !currentVideoFile.exists()) {
            Log.e(TAG, "비디오 파일을 찾을 수 없습니다");

            // 파일이 없어도 오디오는 있으니 오디오만 분석
            if (currentRecordingFile != null && currentRecordingFile.exists()) {
                Log.d(TAG, "오디오 파일만 분석 시도");

                if (isIntroAnalyzing) {
                    analyzeIntroAudioOnly();
                } else if (isCameraAnalyzing) {
                    analyzeQuestionAudioOnly();
                }
            } else {
                showErrorMessage("녹화 파일을 찾을 수 없습니다.");
                resetAnalysisState();
            }
            return;
        }

        Log.d(TAG, "비디오 파일 크기: " + currentVideoFile.length() + " bytes");

        // 분석 시작
        if (isIntroAnalyzing) {
            analyzeIntroVideo();
        } else if (isCameraAnalyzing) {
            analyzeQuestionVideo();
        }
    }

    // 오디오 녹음 중지
    private void stopAudioRecording() {
        if (mediaRecorder != null && isRecording) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
                isRecording = false;
                Log.d(TAG, "오디오 녹음 완료");
            } catch (Exception e) {
                Log.e(TAG, "오디오 녹음 중지 오류: " + e.getMessage());
            }
        }
    }

    // 자기소개 비디오 분석
    private void analyzeIntroVideo() {
        introText.setText("자기소개 영상을 분석 중입니다... 잠시만 기다려주세요");

        new Thread(() -> {
            try {
                // STT 처리
                String transcribedText = performSTT(currentRecordingFile);

                // 영상과 음성 파일을 서버로 전송하여 분석
                JSONObject analysisResult = analyzeVideoWithServer("intro", currentVideoFile, currentRecordingFile, transcribedText);

                runOnUiThread(() -> updateIntroResults(analysisResult));

            } catch (Exception e) {
                Log.e(TAG, "자기소개 분석 오류: " + e.getMessage());
                runOnUiThread(() -> showErrorMessage("자기소개 분석 중 오류가 발생했습니다: " + e.getMessage()));
            }
        }).start();
    }

    // 질문답변 비디오 분석
    private void analyzeQuestionVideo() {
        presentationScoreText.setText("질문답변 영상을 분석 중입니다... 잠시만 기다려주세요");

        new Thread(() -> {
            try {
                // STT 처리
                String transcribedText = performSTT(currentRecordingFile);

                // 영상과 음성 파일을 서버로 전송하여 분석
                JSONObject analysisResult = analyzeVideoWithServer("question", currentVideoFile, currentRecordingFile, transcribedText);

                runOnUiThread(() -> updateQuestionResults(analysisResult));

            } catch (Exception e) {
                Log.e(TAG, "질문답변 분석 오류: " + e.getMessage());
                runOnUiThread(() -> showErrorMessage("질문답변 분석 중 오류가 발생했습니다: " + e.getMessage()));
            }
        }).start();
    }

    // 자기소개 오디오만 분석
    private void analyzeIntroAudioOnly() {
        introText.setText("자기소개 음성을 분석 중입니다... 잠시만 기다려주세요");

        new Thread(() -> {
            try {
                // STT 처리
                String transcribedText = performSTT(currentRecordingFile);

                Log.d(TAG, "STT 결과: " + transcribedText);

                // 오디오만 서버로 전송하여 분석
                JSONObject analysisResult = analyzeAudioWithServer("intro", currentRecordingFile, transcribedText);

                runOnUiThread(() -> updateIntroResults(analysisResult));

            } catch (Exception e) {
                Log.e(TAG, "자기소개 분석 오류: " + e.getMessage());
                runOnUiThread(() -> showErrorMessage("자기소개 분석 중 오류가 발생했습니다: " + e.getMessage()));
            }
        }).start();
    }

    // 질문답변 오디오만 분석
    private void analyzeQuestionAudioOnly() {
        presentationScoreText.setText("질문답변 음성을 분석 중입니다... 잠시만 기다려주세요");

        new Thread(() -> {
            try {
                // STT 처리
                String transcribedText = performSTT(currentRecordingFile);

                Log.d(TAG, "STT 결과: " + transcribedText);

                // 오디오만 서버로 전송하여 분석
                JSONObject analysisResult = analyzeAudioWithServer("question", currentRecordingFile, transcribedText);

                runOnUiThread(() -> updateQuestionResults(analysisResult));

            } catch (Exception e) {
                Log.e(TAG, "질문답변 분석 오류: " + e.getMessage());
                runOnUiThread(() -> showErrorMessage("질문답변 분석 중 오류가 발생했습니다: " + e.getMessage()));
            }
        }).start();
    }

    // 오디오만 서버로 전송
    private JSONObject analyzeAudioWithServer(String analysisType, File audioFile, String transcribedText) throws Exception {
        URL url = new URL(BASE_URL + "analyze_video");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setDoOutput(true);

        // Multipart 요청 생성
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream os = conn.getOutputStream()) {
            // 분석 타입
            writeFormField(os, boundary, "analysis_type", analysisType);

            // 현재 질문 (질문답변 분석의 경우)
            if ("question".equals(analysisType)) {
                writeFormField(os, boundary, "question", question);
            }

            // 음성 인식 텍스트
            writeFormField(os, boundary, "transcribed_text", transcribedText);

            // 오디오 파일
            writeFileField(os, boundary, "audio_file", audioFile);

            // 마지막 boundary
            os.write(("--" + boundary + "--\r\n").getBytes());
        }

        // 응답 받기
        int responseCode = conn.getResponseCode();
        InputStream inputStream = responseCode == 200 ? conn.getInputStream() : conn.getErrorStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        if (responseCode == 200) {
            return new JSONObject(response.toString());
        } else {
            throw new Exception("서버 오류: " + responseCode + " " + response.toString());
        }
    }

    // STT 처리 (간단한 더미 구현 - 실제로는 Google Speech API 등을 사용해야 함)
    private String performSTT(File audioFile) {
        // 실제 구현에서는 Google Speech-to-Text API나 다른 STT 서비스를 사용
        // 여기서는 더미 텍스트 반환
        try {
            // 음성 인식을 위한 Intent 사용 (오프라인)
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

            // 실제 구현에서는 음성 파일을 텍스트로 변환하는 로직 구현 필요
            Log.d(TAG, "STT 처리 완료 (더미)");
            return "음성을 텍스트로 변환한 내용입니다."; // 더미 텍스트

        } catch (Exception e) {
            Log.e(TAG, "STT 처리 오류: " + e.getMessage());
            return "음성 인식을 처리할 수 없습니다.";
        }
    }

    // 서버에서 영상 분석
    private JSONObject analyzeVideoWithServer(String analysisType, File videoFile, File audioFile, String transcribedText) throws Exception {
        URL url = new URL(BASE_URL + "analyze_video");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setDoOutput(true);

        // Multipart 요청 생성
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream os = conn.getOutputStream()) {
            // 분석 타입
            writeFormField(os, boundary, "analysis_type", analysisType);

            // 현재 질문 (질문답변 분석의 경우)
            if ("question".equals(analysisType)) {
                writeFormField(os, boundary, "question", question);
            }

            // 음성 인식 텍스트
            writeFormField(os, boundary, "transcribed_text", transcribedText);

            // 영상 파일
            writeFileField(os, boundary, "video_file", videoFile);

            // 오디오 파일
            writeFileField(os, boundary, "audio_file", audioFile);

            // 마지막 boundary
            os.write(("--" + boundary + "--\r\n").getBytes());
        }

        // 응답 받기
        int responseCode = conn.getResponseCode();
        InputStream inputStream = responseCode == 200 ? conn.getInputStream() : conn.getErrorStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        if (responseCode == 200) {
            return new JSONObject(response.toString());
        } else {
            throw new Exception("서버 오류: " + responseCode + " " + response.toString());
        }
    }

    private void writeFormField(OutputStream os, String boundary, String fieldName, String value) throws IOException {
        os.write(("--" + boundary + "\r\n").getBytes());
        os.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"\r\n").getBytes());
        os.write("\r\n".getBytes());
        os.write(value.getBytes());
        os.write("\r\n".getBytes());
    }

    private void writeFileField(OutputStream os, String boundary, String fieldName, File file) throws IOException {
        os.write(("--" + boundary + "\r\n").getBytes());
        os.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + file.getName() + "\"\r\n").getBytes());
        os.write(("Content-Type: application/octet-stream\r\n").getBytes());
        os.write("\r\n".getBytes());

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }
        os.write("\r\n".getBytes());
    }

    // 자기소개 결과 업데이트
    private void updateIntroResults(JSONObject result) {
        try {
            boolean success = result.getBoolean("success");

            if (!success) {
                showErrorMessage("자기소개 분석 실패");
                return;
            }

            JSONObject scores = result.getJSONObject("scores");
            double totalScore = result.getDouble("total_score");
            String grade = result.getString("grade");
            JSONArray suggestions = result.getJSONArray("suggestions");

            // 제안사항 문자열 생성
            StringBuilder suggestionText = new StringBuilder();
            for (int i = 0; i < suggestions.length(); i++) {
                suggestionText.append("• ").append(suggestions.getString(i)).append("\n");
            }

            String scoreText = String.format(
                    "자기소개 분석 결과\n\n" +
                            "자신감 표현: %.1f점\n" +
                            "목소리 톤: %.1f점\n" +
                            "내용 구성: %.1f점\n" +
                            "자세와 표정: %.1f점\n\n" +
                            "총점: %.1f점 (%s)\n\n" +
                            "개선 제안:\n%s",
                    scores.getDouble("confidence_expression"),
                    scores.getDouble("voice_tone"),
                    scores.getDouble("content_structure"),
                    scores.getDouble("posture_expression"),
                    totalScore, grade,
                    suggestionText.toString()
            );

            introText.setText(scoreText);
            isIntroAnalyzing = false;
            introCameraBtn.setEnabled(true);
            introCameraStopBtn.setEnabled(false);

            // 결과 팝업 표시
            showResultsDialog(scoreText, "자기소개");

        } catch (Exception e) {
            Log.e(TAG, "자기소개 결과 파싱 오류: " + e.getMessage());
            showErrorMessage("자기소개 결과 처리 오류");
        }
    }

    // 질문답변 결과 업데이트
    private void updateQuestionResults(JSONObject result) {
        try {
            boolean success = result.getBoolean("success");

            if (!success) {
                showErrorMessage("질문답변 분석 실패");
                return;
            }

            JSONObject scores = result.getJSONObject("scores");
            double totalScore = result.getDouble("total_score");
            String grade = result.getString("grade");
            JSONArray suggestions = result.getJSONArray("suggestions");

            // 제안사항 문자열 생성
            StringBuilder suggestionText = new StringBuilder();
            for (int i = 0; i < suggestions.length(); i++) {
                suggestionText.append("• ").append(suggestions.getString(i)).append("\n");
            }

            String scoreText = String.format(
                    "질문답변 분석 결과\n\n" +
                            "답변 정확성: %.1f점\n" +
                            "논리적 구성: %.1f점\n" +
                            "말하기 자연스러움: %.1f점\n" +
                            "집중도: %.1f점\n\n" +
                            "총점: %.1f점 (%s)\n\n" +
                            "개선 제안:\n%s",
                    scores.getDouble("answer_accuracy"),
                    scores.getDouble("logical_structure"),
                    scores.getDouble("speaking_naturalness"),
                    scores.getDouble("focus_level"),
                    totalScore, grade,
                    suggestionText.toString()
            );

            presentationScoreText.setText(scoreText);
            isCameraAnalyzing = false;
            btnStartCamera.setEnabled(true);
            btnStopCamera.setEnabled(false);

            // 결과 팝업 표시
            showResultsDialog(scoreText, "질문답변");

        } catch (Exception e) {
            Log.e(TAG, "질문답변 결과 파싱 오류: " + e.getMessage());
            showErrorMessage("질문답변 결과 처리 오류");
        }
    }

    // 분석 중지
    private void stopCurrentAnalysis(String analysisType) {
        if ("intro".equals(analysisType) && isIntroAnalyzing) {
            isIntroAnalyzing = false;
            introCameraBtn.setEnabled(true);
            introCameraStopBtn.setEnabled(false);
            introText.setText("자기소개 분석이 중지되었습니다.");

            // 진행 중인 녹음/촬영 중지
            stopAudioRecording();

        } else if ("question".equals(analysisType) && isCameraAnalyzing) {
            isCameraAnalyzing = false;
            btnStartCamera.setEnabled(true);
            btnStopCamera.setEnabled(false);
            presentationScoreText.setText("질문답변 분석이 중지되었습니다.");

            // 진행 중인 녹음/촬영 중지
            stopAudioRecording();
        }
    }

    // 분석 상태 초기화
    private void resetAnalysisState() {
        if (isIntroAnalyzing) {
            isIntroAnalyzing = false;
            introCameraBtn.setEnabled(true);
            introCameraStopBtn.setEnabled(false);
            introText.setText("분석을 다시 시도해주세요.");
        }

        if (isCameraAnalyzing) {
            isCameraAnalyzing = false;
            btnStartCamera.setEnabled(true);
            btnStopCamera.setEnabled(false);
            presentationScoreText.setText("분석을 다시 시도해주세요.");
        }

        stopAudioRecording();
    }

    // 결과 다이얼로그 표시
    private void showResultsDialog(String scoreText, String analysisType) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(analysisType + " 분석 결과")
                .setMessage(scoreText)
                .setPositiveButton("확인", null)
                .setNeutralButton("결과 저장", (dialog, which) -> saveAnalysisResult(scoreText, analysisType))
                .show();
    }

    // 분석 결과 저장
    private void saveAnalysisResult(String scoreText, String analysisType) {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = analysisType + "_result_" + timeStamp + ".txt";
            File resultDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "AnalysisResults");
            if (!resultDir.exists()) {
                resultDir.mkdirs();
            }

            File resultFile = new File(resultDir, fileName);

            try (FileOutputStream fos = new FileOutputStream(resultFile)) {
                fos.write(scoreText.getBytes());
            }

            Toast.makeText(this, "결과가 저장되었습니다: " + fileName, Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "결과 저장 오류: " + e.getMessage());
            Toast.makeText(this, "결과 저장에 실패했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    // 오류 메시지 표시
    private void showErrorMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        resetAnalysisState();
    }

    public void sendGptRequest() {
        String quest = textRequest.getText().toString();
        String answer = textResponse.getText().toString();
        feedbackSection.setVisibility(View.VISIBLE);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            textFeedback.setText("로그인 정보 없음 : 서버 요청 실패");
            return;
        }

        String userId = user.getUid();
        String requestMessage = "면접 질문 : " + quest + "\n사용자 답변 : " + answer;
        GptRequest request = new GptRequest(userId, requestMessage);

        textFeedback.setText("피드백을 요청 중입니다...");

        GptApi gptApi = retrofit.create(GptApi.class);
        Log.d(TAG, "요청 시작: " + requestMessage);

        gptApi.askGpt(request).enqueue(new Callback<GptResponse>() {
            @Override
            public void onResponse(@NonNull Call<GptResponse> call, @NonNull Response<GptResponse> response) {
                Log.d(TAG, "onResponse 호출됨, HTTP 코드: " + response.code());

                mainHandler.post(() -> {
                    btnRequest.setEnabled(true);

                    if (response.isSuccessful() && response.body() != null) {
                        String content = response.body().content;
                        textFeedback.setText(content);

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

                JSONObject json = new JSONObject();
                json.put("text", questionText);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                InputStream responseStream = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream));
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

                runOnUiThread(() -> {
                    try {
                        Log.d("TTS", "UI 스레드 진입");

                        if (mediaPlayer != null) {
                            mediaPlayer.release();
                        }

                        mediaPlayer = new MediaPlayer();

                        Log.d("TTS", "오디오 파일 경로: " + tempFile.getAbsolutePath());
                        Log.d("TTS", "파일 존재 여부: " + tempFile.exists());
                        Log.d("TTS", "파일 크기: " + tempFile.length());

                        mediaPlayer.setDataSource(tempFile.getAbsolutePath());
                        mediaPlayer.prepare();
                        mediaPlayer.start();

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

        String generatedId = UUID.randomUUID().toString();
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

        if (mediaRecorder != null) {
            try {
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "MediaRecorder 해제 오류: " + e.getMessage());
            }
        }

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }

        super.onDestroy();
    }
}