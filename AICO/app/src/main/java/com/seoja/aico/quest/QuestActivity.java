package com.seoja.aico.quest;

import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
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

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.seoja.aico.R;
import com.seoja.aico.gpt.GptApi;
import com.seoja.aico.gpt.GptRequest;
import com.seoja.aico.gpt.GptResponse;

import java.io.File;
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

    private TextView textRequest, textFeedback, textTip;
    private EditText textResponse;
    private Button btnRequest, btnNextQuestion;
    private ImageButton btnBack;
    private LinearLayout feedbackSection;

    private Button btnChangeMic;
    private ImageButton micIcon;
    private boolean isMicMode = false;
    private boolean isMicRecording = false;

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isListening = false;

    private MediaRecorder recorder;
    private boolean isRecording = false;
    private File audioFile;

    private MediaRecorder mediaRecorder;
    private String audioFilePath;


    private List<String> questionList = new ArrayList<>();
    private List<String> tipList = new ArrayList<>();

    // 셔플된 리스트에서 문제출력을 위한 인덱스
    private int currentQuestion = 0;
    private int currentTip = 0;

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
        textTip = findViewById(R.id.textTip);
        btnRequest = findViewById(R.id.btnRequest);
        textFeedback = findViewById(R.id.textFeedback);
        btnNextQuestion = findViewById(R.id.btnNextQuestion);
        feedbackSection = findViewById(R.id.feedbackSection);
        btnBack = findViewById(R.id.btnBack);
        selectedFirst = getIntent().getStringExtra("selectedFirst");
        selectedSecond = getIntent().getStringExtra("selectedSecond");
        btnChangeMic = findViewById(R.id.btnChangeMic);
        micIcon = findViewById(R.id.micIcon);

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

        // 초기 상태 설정
        textFeedback.setText("답변 후 피드백이 여기에 표시됩니다.");

        // 마이크 연결
        initSpeechRecognizer();
        micIcon.setOnClickListener(v -> toggleMicRecording());
        // 서버 연결 테스트
        testServerConnection();
    }

    private void toggleInputMode() {
        isMicMode = !isMicMode;
        micIcon.setImageResource(R.drawable.ic_mic_on);

        if (isMicMode) {
            textResponse.setVisibility(View.GONE);
            textResponse.setHint("");
            micIcon.setVisibility(View.VISIBLE);
            btnChangeMic.setText("텍스트 전환");

            startListening();
        } else {
            textResponse.setVisibility(View.VISIBLE);
            textResponse.setHint("답변을 입력해주세요");
            micIcon.setVisibility(View.GONE);
            btnChangeMic.setText("음성 전환");
        }
    }

    private void toggleMicRecording() {
        if (isMicRecording) {
            stopListening();
            micIcon.setImageResource(R.drawable.ic_mic);
            isMicRecording = false;
        } else {
            startListening();
            micIcon.setImageResource(R.drawable.ic_mic_on);
            isMicRecording = true;
        }
    }

    // STT 및 녹음 객체 초기화
    private void initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onError(int error) {
                stopListening();
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    textResponse.setText(matches.get(0)); // 결과를 EditText에 삽입
                }
                stopListening();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partial != null && !partial.isEmpty()) {
                    textResponse.setText(partial.get(0));
                }
            }

            @Override public void onEvent(int eventType, Bundle params) { }
        });
    }

    // STT 시작
    private void startListening() {
        if (speechRecognizer == null) {
            initSpeechRecognizer();
        }
        isListening = true;

        // 음성 파일 저장
        startRecording();
        // STT
        speechRecognizer.startListening(recognizerIntent);
    }

    // STT 중지
    private void stopListening() {
        if (speechRecognizer != null && isListening) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        isListening = false;
        // 상태 초기화
        // 녹음 정지
        stopRecording();
        micIcon.setImageResource(R.drawable.ic_mic);
        isMicRecording = false;
    }

    // 녹음
    private void startRecording() {
        try {
            String fileName = "recorded_" + System.currentTimeMillis() + ".m4a";
            File outputDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC); // /storage/emulated/0/Android/data/패키지명/files/Music/
            if (outputDir != null) {
                File audioFile = new File(outputDir, fileName);
                audioFilePath = audioFile.getAbsolutePath();
            } else {
                Toast.makeText(this, "오디오 파일 디렉토리를 찾을 수 없습니다", Toast.LENGTH_SHORT).show();
                return;
            }

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(audioFilePath);
            mediaRecorder.prepare();
            mediaRecorder.start();
            Log.d(TAG, "녹음 시작됨: " + audioFilePath);
        } catch (Exception e) {
            Log.e(TAG, "녹음 실패: " + e.getMessage(), e);
            Toast.makeText(this, "녹음 시작에 실패했습니다", Toast.LENGTH_SHORT).show();
        }
    }

    // 녹음 정지
    private void stopRecording() {
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
                Log.d(TAG, "녹음 저장됨: " + audioFilePath);
            }
        } catch (Exception e) {
            Log.e(TAG, "녹음 정지 실패: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopListening();
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
        String question = questionList.get(currentQuestion);
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

