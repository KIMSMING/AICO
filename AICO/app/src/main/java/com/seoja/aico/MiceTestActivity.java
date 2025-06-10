package com.seoja.aico;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;

public class MiceTestActivity extends AppCompatActivity {

    private TextView tvHint, tvResult;
    private boolean isHintHidden = false;
    private Button imgMic;
    private boolean isListening = false;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private Handler silenceHandler = new Handler();
    private Runnable silenceRunnable;
    private float baseMicSizeDp = 180f;
    private float maxMicSizeDp = 260f;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mice_test);

        imgMic = findViewById(R.id.imgMic);
        tvResult = findViewById(R.id.tvResult);
        tvHint = findViewById(R.id.tvHint);

        // 뒤로가기
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        if (!isAudioPermissionGranted()) {
            imgMic.setEnabled(false);
            tvHint.setText("마이크 권한이 필요합니다.\n설정에서 권한을 허용해주세요.");
            tvHint.setVisibility(View.VISIBLE);
            Toast.makeText(this, "마이크 권한이 없어 음성 테스트를 사용할 수 없습니다.", Toast.LENGTH_LONG).show();
            return;
        }

        // STT 준비
        createSpeechRecognizer();

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        imgMic.setOnClickListener(v -> {
            // 힌트는 액티비티가 살아있는 동안 한 번만 사라지게
            if (!isHintHidden) {
                tvHint.setVisibility(View.GONE);
                isHintHidden = true;
            }
            if (isListening) {
                stopListening();
            } else {
                startListening();
            }
        });
    }

    private boolean isAudioPermissionGranted() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void createSpeechRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
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
                // 크기 변화: 0.7~1.2배
                float scale = 0.7f + Math.min(Math.max(rmsdB, 0) / 10f, 0.5f); // 0.7~1.2
                float newSize = baseMicSizeDp * scale;
                if (newSize > maxMicSizeDp) newSize = maxMicSizeDp;
                animateMicSize(newSize);
                resetSilenceTimer();
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
            }

            @Override
            public void onError(int error) {
                stopListening();
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    tvResult.setText(matches.get(0));
                }
                stopListening();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partial != null && !partial.isEmpty()) {
                    tvResult.setText(partial.get(0));
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });
    }

    private void startListening() {
        isListening = true;
        tvResult.setText("");
//        imgMic.setImageResource(R.drawable.ic_mic_on); // 활성화 마이크 아이콘
        animateMicSize(baseMicSizeDp); // 초기 크기
        createSpeechRecognizer(); // 항상 새로 생성
        speechRecognizer.startListening(recognizerIntent);
        resetSilenceTimer();
    }

    private void stopListening() {
        isListening = false;
        try {
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
                speechRecognizer.destroy();
            }
        } catch (Exception ignored) {
        }
//        imgMic.setImageResource(R.drawable.ic_mic); // 비활성화 마이크 아이콘
        animateMicSize(baseMicSizeDp);
        clearSilenceTimer();
        createSpeechRecognizer(); // 다음 녹음을 위해 새 인스턴스 준비
    }

    private void animateMicSize(float targetDp) {
        float scale = targetDp / baseMicSizeDp;
        imgMic.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(100)
                .start();
    }

    private void resetSilenceTimer() {
        clearSilenceTimer();
        silenceRunnable = () -> {
            // 2초 무음 시 STT 종료 및 결과 표시
            stopListening();
        };
        silenceHandler.postDelayed(silenceRunnable, 2000);
    }

    private void clearSilenceTimer() {
        if (silenceRunnable != null) {
            silenceHandler.removeCallbacks(silenceRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
}
