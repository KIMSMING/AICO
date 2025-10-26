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
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import com.seoja.aico.MainActivity;
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
import retrofit2.converter.scalars.ScalarsConverterFactory;

public class QuestActivity extends AppCompatActivity implements View.OnClickListener {

    //    public static final String BASE_URL = "http://192.168.56.1:8000/"; // 본인 컴퓨터
    public static final String BASE_URL = "http://172.20.10.4:8000/"; // 핫스팟 주소

    private static final String TAG = "QuestActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    // --- UI 컴포넌트 ---
    private TextView textRequest, textFeedback, textTip, titleTextView, introText, presentationScoreText;
    private View textInputLayout;
    private EditText textResponse;
    private Button btnRequest, btnNextQuestion, btnFollowup;
    private Button introCameraBtn, introCameraStopBtn, btnStartCamera, btnStopCamera;
    private ImageButton btnBack, btnSoundplay;
    private LinearLayout feedbackSection;

    private LinearLayout introSection;
    private LinearLayout presentationSection;
    private LinearLayout textResponseSection;

    private Button btnIntroAnalysis;
    private Button btnPresentationAnalysis;
    private Button btnTextResponse;

    // --- 상태 변수 및 데이터 ---
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String question;
    private String sessionId = null;
    private String userId = null;
    private String lastQuestion = null;
    private boolean isMicMode = false;
    private boolean isRecording = false;
    private boolean isIntroAnalyzing = false;
    private boolean isCameraAnalyzing = false;
    private List<String> questionList = new ArrayList<>();
    private List<String> tipList = new ArrayList<>();
    private int currentQuestion = 0;
    private int currentTip = 0;
    private String selectedFirst = "";
    private String selectedSecond = "";

    // --- 미디어 및 파일 관련 ---
    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private String audioFilePath; // STT용 오디오 파일 경로
    private File currentRecordingFile; // 영상 분석용 오디오 파일
    private Uri videoUri;
    private File currentVideoFile;
    private SpeechRecognizer speechRecognizer;

    // --- 네트워크 관련 ---
    private Retrofit retrofit;
    private Gson gson;

    // --- ActivityResultLauncher ---
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

        introSection = findViewById(R.id.introSection);
        presentationSection = findViewById(R.id.presentationSection);
        textResponseSection = findViewById(R.id.textResponseSection);

        btnIntroAnalysis = findViewById(R.id.btnIntroAnalysis);
        btnPresentationAnalysis = findViewById(R.id.btnPresentationAnalysis);
        btnTextResponse = findViewById(R.id.btnTextResponse);

        // 섹션 선택 버튼 클릭 이벤트
        btnIntroAnalysis.setOnClickListener(v -> onSectionButtonClick(btnIntroAnalysis));
        btnPresentationAnalysis.setOnClickListener(v -> onSectionButtonClick(btnPresentationAnalysis));
        btnTextResponse.setOnClickListener(v -> onSectionButtonClick(btnTextResponse));

        // 초기 상태 설정 (첫 번째 버튼 선택)
        onSectionButtonClick(btnIntroAnalysis);

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

        fetchJobQuestion();
        setupClickListeners();
        checkPermissions();
        testServerConnection();
    }

    // --- 초기화 메서드 ---

    private void initializeViews() {
        // 공통 UI
        textRequest = findViewById(R.id.textRequest);
        textResponse = findViewById(R.id.textResponse);
        textTip = findViewById(R.id.textTip);
        btnRequest = findViewById(R.id.btnRequest);
        textFeedback = findViewById(R.id.textFeedback);
        btnNextQuestion = findViewById(R.id.btnNextQuestion);
        feedbackSection = findViewById(R.id.feedbackSection);
        btnBack = findViewById(R.id.btnBack);
        btnSoundplay = findViewById(R.id.btnSoundplay);
        titleTextView = findViewById(R.id.header_title);
        titleTextView.setText("면접 연습");

        // STT 및 꼬리질문 UI
        textInputLayout = findViewById(R.id.textInputLayout);
        btnFollowup = findViewById(R.id.btnFollowup);
//        btnChangeMic = findViewById(R.id.btnChangeMic);
//        micIcon = findViewById(R.id.micIcon);

        // 영상 분석 UI
        introCameraBtn = findViewById(R.id.introCameraBtn);
        introCameraStopBtn = findViewById(R.id.introCameraStopBtn);
        introText = findViewById(R.id.introText);
        btnStartCamera = findViewById(R.id.btnStartCamera);
        btnStopCamera = findViewById(R.id.btnStopCamera);
        presentationScoreText = findViewById(R.id.presentationScoreText);

        // 초기 상태 설정
        textFeedback.setText("답변 후 피드백이 여기에 표시됩니다.");
        introCameraStopBtn.setEnabled(false);
        introText.setText("자기소개 영상을 촬영하여 분석을 시작해주세요");
        btnStopCamera.setEnabled(false);
        presentationScoreText.setText("질문 답변 영상을 촬영하여 분석을 시작해주세요");
        
        // 입력창 포커스 시 자동 스크롤
        setupAutoScroll();
    }

    private void setupAutoScroll() {
        textResponse.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                // 포커스가 되면 약간의 딜레이 후 스크롤
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    ScrollView scrollView = findViewById(R.id.scrollViewMain);
                    if (scrollView != null) {
                        View view = v;
                        // 입력창의 위치를 계산하여 스크롤
                        int[] location = new int[2];
                        view.getLocationOnScreen(location);
                        int scrollY = location[1] - 200;
                        if (scrollY > 0) {
                            scrollView.smoothScrollTo(0, scrollView.getScrollY() + scrollY);
                        }
                    }
                }, 300);
            }
        });
    }

    private void initializeNetworking() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
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
                .addConverterFactory(ScalarsConverterFactory.create()) // for STT (String response)
                .addConverterFactory(GsonConverterFactory.create(gson)) // for GPT (JSON response)
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
        // 공통
        btnRequest.setOnClickListener(this);
        btnNextQuestion.setOnClickListener(this);
        btnBack.setOnClickListener(v -> finish());
        btnSoundplay.setOnClickListener(v -> sendTextToServer(question));
        btnFollowup.setOnClickListener(this);
        // STT 및 꼬리질문
//        btnChangeMic.setOnClickListener(v -> toggleInputMode());
//        micIcon.setOnClickListener(v -> {
//            if (!isMicMode) return;
//            try {
//                toggleRecording();
//            } catch (IOException e) {
//                Log.e(TAG, "Recording toggle failed", e);
//                Toast.makeText(this, "녹음 시작/중지에 실패했습니다.", Toast.LENGTH_SHORT).show();
//            }
//        });

        // 영상 분석
        introCameraBtn.setOnClickListener(this);
        introCameraStopBtn.setOnClickListener(this);
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
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "앱 사용을 위해 모든 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // --- OnClickListener ---
    @Override
    public void onClick(View v) {
        int id = v.getId();
        // 텍스트 피드백 요청
        if (id == R.id.btnRequest) {
            if (textResponse.getText().toString().isEmpty()) {
                textFeedback.setText("답변을 입력해주세요");
                return;
            }
            sendGptRequest();
        }
        // 다음 질문
        else if (id == R.id.btnNextQuestion) {
            loadNewQuestion();
        }
        // 꼬리 질문
        else if (id == R.id.btnFollowup) {
            handleFollowupClick();
        }
        // 자기소개 영상 분석 시작
        else if (id == R.id.introCameraBtn) {
            startIntroVideoRecording();
        }
        // 자기소개 영상 분석 중지
        else if (id == R.id.introCameraStopBtn) {
            stopCurrentAnalysis("intro");
        }
        // 질문답변 영상 분석 시작
        else if (id == R.id.btnStartCamera) {
            startQuestionVideoRecording();
        }
        // 질문답변 영상 분석 중지
        else if (id == R.id.btnStopCamera) {
            stopCurrentAnalysis("question");
        }
    }

    // --- 기능별 로직 (질문 로딩, STT, 꼬리질문, 영상분석 등) ---

    private void fetchJobQuestion() {
        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference("면접질문");
        DatabaseReference rootRef2 = FirebaseDatabase.getInstance().getReference("면접팁");
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
                // 공통, 인사, 직업 질문 로드
                DataSnapshot commonSnap = snapshot.child("공통질문");
                for (DataSnapshot questionSnap : commonSnap.getChildren())
                    questionList.add(questionSnap.getValue(String.class));

                DataSnapshot hrSnap = snapshot.child("인사질문");
                for (DataSnapshot questionSnap : hrSnap.getChildren())
                    questionList.add(questionSnap.getValue(String.class));

                DataSnapshot jobSnap = snapshot.child("직업질문").child(selectedFirst).child(selectedSecond);
                for (DataSnapshot questionSnap : jobSnap.getChildren())
                    questionList.add(questionSnap.getValue(String.class));

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
                for (DataSnapshot tipSnap : snapshot.getChildren())
                    tipList.add(tipSnap.getValue(String.class));
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
        // 꼬리질문 세션 초기화
        sessionId = null;
    }

    // --- STT 및 꼬리질문 관련 메서드 ---
//    private void toggleInputMode() {
//        if (isRecording) {
//            stopSttRecording();
//        }
//        isMicMode = !isMicMode;
//        if (isMicMode) {
//            textInputLayout.setVisibility(View.GONE);
//            micIcon.setVisibility(View.VISIBLE);
//            btnChangeMic.setText("텍스트 전환");
//            micIcon.setImageResource(R.drawable.ic_mic);
//        } else {
//            textInputLayout.setVisibility(View.VISIBLE);
//            micIcon.setVisibility(View.GONE);
//            btnChangeMic.setText("음성 전환");
//        }
//    }
//
//    private void toggleRecording() throws IOException {
//        if (isRecording) {
//            stopSttRecording();
//            micIcon.setImageResource(R.drawable.ic_mic);
//        } else {
//            startSttRecording();
//            micIcon.setImageResource(R.drawable.ic_mic_on);
//        }
//    }

    private void startSttRecording() throws IOException {
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

    private void stopSttRecording() {
        if (mediaRecorder != null && isRecording) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
            } catch (RuntimeException e) {
                Log.e(TAG, "stopSttRecording failed", e);
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
        SttApi service = retrofit.create(SttApi.class);
        Call<String> call = service.uploadAudio(body);
        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String textResult = response.body().replaceAll("^\"|\"$", "");
                    textResponse.setText(textResult);
                    Toast.makeText(QuestActivity.this, "음성이 텍스트로 변환되었습니다.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(QuestActivity.this, "STT 서버 응답 오류: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                Toast.makeText(QuestActivity.this, "STT 서버 전송 실패: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Audio Upload Failure", t);
            }
        });
    }

    private void handleFollowupClick() {
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
    }

    private void startInterview(String answer) {
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
                    feedbackSection.setVisibility(View.VISIBLE);
                    question = body.getQuestion();
                    lastQuestion = question;
                    textResponse.setText("");
                } else {
                    Log.e("FOLLOWUP_API", "응답 실패: " + response.code());
                    Toast.makeText(QuestActivity.this, "꼬리질문 응답 실패", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<NextInterviewResponse> call, @NonNull Throwable t) {
                Toast.makeText(QuestActivity.this, "연관 질문 요청 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }


    // --- 영상 분석 관련 메서드 ---

    private void startIntroVideoRecording() {
        if (!isIntroAnalyzing) {
            isIntroAnalyzing = true;
            isCameraAnalyzing = false; // 다른 분석 모드 비활성화
            introCameraBtn.setEnabled(false);
            introCameraStopBtn.setEnabled(true);
            introText.setText("자기소개 영상을 촬영 중입니다...");
            startVideoRecording("intro", 60);
        }
    }

    private void startQuestionVideoRecording() {
        if (!isCameraAnalyzing) {
            isCameraAnalyzing = true;
            isIntroAnalyzing = false; // 다른 분석 모드 비활성화
            btnStartCamera.setEnabled(false);
            btnStopCamera.setEnabled(true);
            presentationScoreText.setText("질문답변 영상을 촬영 중입니다...");
            startVideoRecording("question", 120);
        }
    }

    private void startVideoRecording(String analysisType, int duration) {
        try {
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            currentVideoFile = new File(storageDir, analysisType + "_" + timeStamp + ".mp4");
            videoUri = FileProvider.getUriForFile(this, "com.seoja.aico.fileprovider", currentVideoFile);

            Intent cameraIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);
            cameraIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, duration);
            cameraIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
            cameraIntent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startAnalysisAudioRecording(analysisType);
            cameraLauncher.launch(cameraIntent);

        } catch (Exception e) {
            Log.e(TAG, "카메라 시작 오류: " + e.getMessage(), e);
            showErrorMessage("카메라 시작 실패: " + e.getMessage());
        }
    }

    private void startAnalysisAudioRecording(String analysisType) {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = analysisType + "_audio_" + timeStamp + ".3gp";
            File audioDir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "InterviewAudios");
            if (!audioDir.exists()) audioDir.mkdirs();
            currentRecordingFile = new File(audioDir, fileName);

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setOutputFile(currentRecordingFile.getAbsolutePath());
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            Log.d(TAG, "영상 분석용 오디오 녹음 시작: " + currentRecordingFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "오디오 녹음 시작 오류: " + e.getMessage());
            showErrorMessage("오디오 녹음을 시작할 수 없습니다: " + e.getMessage());
        }
    }

    private void handleVideoResult() {
        stopAnalysisAudioRecording();
        if (currentVideoFile == null || !currentVideoFile.exists()) {
            Log.e(TAG, "비디오 파일을 찾을 수 없습니다. 오디오만 분석합니다.");
            if (currentRecordingFile != null && currentRecordingFile.exists()) {
                if (isIntroAnalyzing) analyzeIntroAudioOnly();
                else if (isCameraAnalyzing) analyzeQuestionAudioOnly();
            } else {
                showErrorMessage("녹화/녹음된 파일을 찾을 수 없습니다.");
            }
            return;
        }

        if (isIntroAnalyzing) analyzeIntroVideo();
        else if (isCameraAnalyzing) analyzeQuestionVideo();
    }

    private void stopAnalysisAudioRecording() {
        if (mediaRecorder != null && isRecording) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "오디오 녹음 중지 오류: " + e.getMessage());
            } finally {
                mediaRecorder = null;
                isRecording = false;
                Log.d(TAG, "영상 분석용 오디오 녹음 완료");
            }
        }
    }

    private void analyzeIntroVideo() {
        introText.setText("자기소개 영상을 분석 중입니다...");
        new Thread(() -> {
            try {
                String transcribedText = performSTT(currentRecordingFile);
                JSONObject result = analyzeWithServer("intro", currentVideoFile, currentRecordingFile, transcribedText);
                runOnUiThread(() -> updateIntroResults(result));
            } catch (Exception e) {
                Log.e(TAG, "자기소개 영상 분석 오류", e);
                runOnUiThread(() -> showErrorMessage("자기소개 영상 분석 실패: " + e.getMessage()));
            }
        }).start();
    }

    private void analyzeQuestionVideo() {
        presentationScoreText.setText("질문답변 영상을 분석 중입니다...");
        new Thread(() -> {
            try {
                String transcribedText = performSTT(currentRecordingFile);
                JSONObject result = analyzeWithServer("question", currentVideoFile, currentRecordingFile, transcribedText);
                runOnUiThread(() -> updateQuestionResults(result));
            } catch (Exception e) {
                Log.e(TAG, "질문답변 영상 분석 오류", e);
                runOnUiThread(() -> showErrorMessage("질문답변 영상 분석 실패: " + e.getMessage()));
            }
        }).start();
    }

    private void analyzeIntroAudioOnly() {
        introText.setText("자기소개 음성을 분석 중입니다...");
        new Thread(() -> {
            try {
                String transcribedText = performSTT(currentRecordingFile);
                JSONObject result = analyzeWithServer("intro", null, currentRecordingFile, transcribedText);
                runOnUiThread(() -> updateIntroResults(result));
            } catch (Exception e) {
                Log.e(TAG, "자기소개 음성 분석 오류", e);
                runOnUiThread(() -> showErrorMessage("자기소개 음성 분석 실패: " + e.getMessage()));
            }
        }).start();
    }

    private void analyzeQuestionAudioOnly() {
        presentationScoreText.setText("질문답변 음성을 분석 중입니다...");
        new Thread(() -> {
            try {
                String transcribedText = performSTT(currentRecordingFile);
                JSONObject result = analyzeWithServer("question", null, currentRecordingFile, transcribedText);
                runOnUiThread(() -> updateQuestionResults(result));
            } catch (Exception e) {
                Log.e(TAG, "질문답변 음성 분석 오류", e);
                runOnUiThread(() -> showErrorMessage("질문답변 음성 분석 실패: " + e.getMessage()));
            }
        }).start();
    }

    // STT 더미 구현 (실제 API 연동 필요)
    private String performSTT(File audioFile) {
        Log.d(TAG, "STT 처리 시도 (현재는 더미 텍스트 반환)");
        return "음성을 텍스트로 변환한 내용입니다."; // 실제 STT API 결과로 대체 필요
    }

    private JSONObject analyzeWithServer(String analysisType, File videoFile, File audioFile, String transcribedText) throws Exception {
        URL url = new URL(BASE_URL + "analyze_video");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream os = conn.getOutputStream()) {
            writeFormField(os, boundary, "analysis_type", analysisType);
            if ("question".equals(analysisType)) {
                writeFormField(os, boundary, "question", question);
            }
            writeFormField(os, boundary, "transcribed_text", transcribedText);

            if (videoFile != null) writeFileField(os, boundary, "video_file", videoFile);
            if (audioFile != null) writeFileField(os, boundary, "audio_file", audioFile);

            os.write(("--" + boundary + "--\r\n").getBytes());
        }

        int responseCode = conn.getResponseCode();
        InputStream inputStream = (responseCode == 200) ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) response.append(line);

        if (responseCode == 200) {
            return new JSONObject(response.toString());
        } else {
            throw new Exception("서버 오류: " + responseCode + " " + response.toString());
        }
    }

    private void writeFormField(OutputStream os, String boundary, String fieldName, String value) throws IOException {
        os.write(("--" + boundary + "\r\n").getBytes());
        os.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"\r\n\r\n").getBytes());
        os.write(value.getBytes());
        os.write("\r\n".getBytes());
    }

    private void writeFileField(OutputStream os, String boundary, String fieldName, File file) throws IOException {
        os.write(("--" + boundary + "\r\n").getBytes());
        os.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + file.getName() + "\"\r\n").getBytes());
        os.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes());
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) os.write(buffer, 0, bytesRead);
        }
        os.write("\r\n".getBytes());
    }

    private void updateIntroResults(JSONObject result) {
        try {
            if (!result.getBoolean("success")) {
                showErrorMessage("자기소개 분석 실패");
                return;
            }
            JSONObject scores = result.getJSONObject("scores");
            StringBuilder suggestionText = new StringBuilder();
            JSONArray suggestions = result.getJSONArray("suggestions");
            for (int i = 0; i < suggestions.length(); i++)
                suggestionText.append("• ").append(suggestions.getString(i)).append("\n");

            String scoreText = String.format(Locale.getDefault(),
                    "자기소개 분석 결과\n\n자신감 표현: %.1f점\n목소리 톤: %.1f점\n내용 구성: %.1f점\n자세와 표정: %.1f점\n\n총점: %.1f점 (%s)\n\n개선 제안:\n%s",
                    scores.getDouble("confidence_expression"), scores.getDouble("voice_tone"),
                    scores.getDouble("content_structure"), scores.getDouble("posture_expression"),
                    result.getDouble("total_score"), result.getString("grade"), suggestionText.toString());

            introText.setText(scoreText);
            resetAnalysisState();
            showResultsDialog(scoreText, "자기소개");
            addExperience(5); // 자기소개 분석 성공 시 5점 경험치
        } catch (Exception e) {
            Log.e(TAG, "자기소개 결과 파싱 오류", e);
            showErrorMessage("자기소개 결과 처리 오류");
        }
    }

    private void updateQuestionResults(JSONObject result) {
        try {
            if (!result.getBoolean("success")) {
                showErrorMessage("질문답변 분석 실패");
                return;
            }
            JSONObject scores = result.getJSONObject("scores");
            StringBuilder suggestionText = new StringBuilder();
            JSONArray suggestions = result.getJSONArray("suggestions");
            for (int i = 0; i < suggestions.length(); i++)
                suggestionText.append("• ").append(suggestions.getString(i)).append("\n");

            String scoreText = String.format(Locale.getDefault(),
                    "질문답변 분석 결과\n\n답변 정확성: %.1f점\n논리적 구성: %.1f점\n말하기 자연스러움: %.1f점\n집중도: %.1f점\n\n총점: %.1f점 (%s)\n\n개선 제안:\n%s",
                    scores.getDouble("answer_accuracy"), scores.getDouble("logical_structure"),
                    scores.getDouble("speaking_naturalness"), scores.getDouble("focus_level"),
                    result.getDouble("total_score"), result.getString("grade"), suggestionText.toString());

            presentationScoreText.setText(scoreText);
            resetAnalysisState();
            showResultsDialog(scoreText, "질문답변");
            addExperience(4); // 질문답변 분석 성공 시 4점 경험치
        } catch (Exception e) {
            Log.e(TAG, "질문답변 결과 파싱 오류", e);
            showErrorMessage("질문답변 결과 처리 오류");
        }
    }

    private void stopCurrentAnalysis(String analysisType) {
        if ("intro".equals(analysisType) && isIntroAnalyzing) {
            introText.setText("자기소개 분석이 중지되었습니다.");
        } else if ("question".equals(analysisType) && isCameraAnalyzing) {
            presentationScoreText.setText("질문답변 분석이 중지되었습니다.");
        }
        resetAnalysisState();
    }

    private void resetAnalysisState() {
        stopAnalysisAudioRecording(); // 진행 중인 녹음 중지
        isIntroAnalyzing = false;
        isCameraAnalyzing = false;
        introCameraBtn.setEnabled(true);
        introCameraStopBtn.setEnabled(false);
        btnStartCamera.setEnabled(true);
        btnStopCamera.setEnabled(false);
    }

    private void showResultsDialog(String scoreText, String analysisType) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(analysisType + " 분석 결과")
                .setMessage(scoreText)
                .setPositiveButton("확인", null)
                .setNeutralButton("결과 저장", (dialog, which) -> saveAnalysisResult(scoreText, analysisType))
                .show();
    }

    private void saveAnalysisResult(String scoreText, String analysisType) {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = analysisType + "_result_" + timeStamp + ".txt";
            File resultDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "AnalysisResults");
            if (!resultDir.exists()) resultDir.mkdirs();
            File resultFile = new File(resultDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(resultFile)) {
                fos.write(scoreText.getBytes());
            }
            Toast.makeText(this, "결과가 저장되었습니다: " + fileName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "결과 저장 오류", e);
            Toast.makeText(this, "결과 저장에 실패했습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    // --- 공통 유틸리티 메서드 ---

    private void testServerConnection() {
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
                if (response.isSuccessful() && response.body() != null) {
                    String content = response.body().content;
                    textFeedback.setText(content);
                    summarizeAndSaveHistory(quest, answer, content);
                    addExperience(2); // 답변 제출 성공 시 2점 경험치
                } else {
                    textFeedback.setText("응답 실패: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<GptResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "GPT 요청 실패: " + t.getMessage());
                // mainHandler를 사용하여 UI 스레드에서 Toast 메시지를 표시합니다.
                mainHandler.post(() -> {
                    textFeedback.setText("서버에 연결할 수 없습니다. 네트워크를 확인해주세요.");
                    Toast.makeText(QuestActivity.this, "피드백 요청 실패", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void summarizeAndSaveHistory(String question, String answer, String feedback) {
        GptApi gptApi = retrofit.create(GptApi.class);
        SummaryRequest summaryReq = new SummaryRequest(feedback);
        gptApi.summarize(summaryReq).enqueue(new Callback<SummaryResponse>() {
            @Override
            public void onResponse(@NonNull Call<SummaryResponse> call, @NonNull Response<SummaryResponse> response) {
                String summary = (response.isSuccessful() && response.body() != null) ? response.body().summary : feedback;
                saveHistoryToServer(question, answer, summary);
            }

            @Override
            public void onFailure(@NonNull Call<SummaryResponse> call, @NonNull Throwable t) {
                saveHistoryToServer(question, answer, feedback);
            }
        });
    }

    private void saveHistoryToServer(String question, String answer, String feedback) {
        if (userId == null) return;
        String generatedId = UUID.randomUUID().toString();
        HistoryItem item = new HistoryItem(generatedId, userId, question, answer, feedback);
        GptApi api = retrofit.create(GptApi.class);
        api.saveHistory(item).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) Log.d("HISTORY_SAVE", "히스토리 저장 성공");
                else Log.e("HISTORY_SAVE", "저장 실패 : " + response.code());
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Log.e("HISTORY_SAVE", "저장 실패 : " + t.getMessage());
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
                    os.write(json.toString().getBytes("utf-8"));
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) responseBuilder.append(line);

                JSONObject responseJson = new JSONObject(responseBuilder.toString());
                byte[] audioBytes = Base64.decode(responseJson.getString("audio_base64"), Base64.DEFAULT);

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
            if (mediaPlayer != null) mediaPlayer.release();
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException e) {
            Log.e("TTS", "Playback Error: " + e.getMessage(), e);
        }
    }

    private void showErrorMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        resetAnalysisState();
    }

    @Override
    protected void onDestroy() {
        if (mediaPlayer != null) mediaPlayer.release();
        if (mediaRecorder != null) mediaRecorder.release();
        if (speechRecognizer != null) speechRecognizer.destroy();
        super.onDestroy();
    }
    private void onSectionButtonClick(Button clickedButton) {
        // 모든 버튼을 기본 상태로 설정
        updateButtonStyle(btnIntroAnalysis, false);
        updateButtonStyle(btnPresentationAnalysis, false);
        updateButtonStyle(btnTextResponse, false);
        
        // 클릭된 버튼을 선택 상태로 설정
        updateButtonStyle(clickedButton, true);
        
        // 레이아웃 전환
        if (clickedButton == btnIntroAnalysis) {
            introSection.setVisibility(View.VISIBLE);
            presentationSection.setVisibility(View.GONE);
            textResponseSection.setVisibility(View.GONE);
        } else if (clickedButton == btnPresentationAnalysis) {
            introSection.setVisibility(View.GONE);
            presentationSection.setVisibility(View.VISIBLE);
            textResponseSection.setVisibility(View.GONE);
        } else if (clickedButton == btnTextResponse) {
            introSection.setVisibility(View.GONE);
            presentationSection.setVisibility(View.GONE);
            textResponseSection.setVisibility(View.VISIBLE);
        }
    }

    private void updateButtonStyle(Button button, boolean isSelected) {
        if (isSelected) {
            button.setTextAppearance(this, R.style.SectionButton_Selected);
            button.setBackgroundResource(R.drawable.professional_button_primary);
        } else {
            button.setTextAppearance(this, R.style.SectionButton);
            button.setBackgroundResource(R.drawable.professional_button_tertiary);
        }
    }
    
    // 경험치 추가 메서드
    private void addExperience(int exp) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        
        String uid = currentUser.getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid);
        
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Integer currentLevel = snapshot.child("level").getValue(Integer.class);
                    Integer currentExp = snapshot.child("experience").getValue(Integer.class);
                    
                    if (currentLevel == null) currentLevel = 1;
                    if (currentExp == null) currentExp = 0;
                    
                    int oldLevel = currentLevel;
                    int newExp = currentExp + exp;
                    int newLevel = currentLevel;
                    
                    // 레벨업 체크
                    while (newExp >= calculateTotalExpForLevel(newLevel)) {
                        newLevel++;
                    }
                    
                    // Firebase 업데이트
                    userRef.child("level").setValue(newLevel);
                    userRef.child("experience").setValue(newExp);
                    
                    // 레벨업 알림
                    if (newLevel > oldLevel) {
                        Toast.makeText(QuestActivity.this, "레벨업! 레벨 " + newLevel + " 달성!", Toast.LENGTH_LONG).show();
                    }
                    
                    // 경험치 획득 알림
                    Toast.makeText(QuestActivity.this, "+" + exp + " 경험치 획득!", Toast.LENGTH_SHORT).show();
                }
            }
            
            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "경험치 추가 실패: " + error.getMessage());
            }
        });
    }
    
    // 특정 레벨까지의 총 필요 경험치 계산 (MainActivity와 동일한 로직)
    private int calculateTotalExpForLevel(int level) {
        int totalExp = 0;
        for (int i = 1; i <= level; i++) {
            totalExp += calculateRequiredExp(i);
        }
        return totalExp;
    }
    
    // 레벨업에 필요한 경험치 계산 (MainActivity와 동일한 로직)
    private int calculateRequiredExp(int level) {
        return 20 + (level - 1) * 5;
    }
}