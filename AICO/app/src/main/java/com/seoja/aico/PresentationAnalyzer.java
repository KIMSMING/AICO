package com.seoja.aico;

import android.content.Context;
import android.util.Log;

import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PresentationAnalyzer {
    private static final String TAG = "PresentationAnalyzer";

    // 분석 타입 상수
    public static final String TYPE_INTRO = "INTRO";
    public static final String TYPE_QUESTION = "QUESTION";

    // ML Kit 얼굴 감지기
    private FaceDetector detector;

    // 카메라 관련
    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private ExecutorService cameraExecutor;

    // 분석 콜백 인터페이스
    public interface AnalysisCallback {
        void onScoreUpdate(PresentationScores scores);

        void onError(String error);
    }

    // 분석 데이터
    private PresentationScores currentScores;
    private AnalysisCallback callback;
    private boolean isAnalyzing = false;
    private String analysisType = TYPE_QUESTION; // 기본값

    // 통계 데이터
    private int totalFrames = 0;
    private int facesDetectedFrames = 0;
    private int eyesOpenFrames = 0;
    private int smilingFrames = 0;
    private int speakingFrames = 0; // 음성 대신 입 움직임 감지
    private long analysisStartTime = 0;
    private List<Float> confidenceScores = new ArrayList<>();

    // 에뮬레이터 최적화 변수
    private int frameSkipCount = 0;
    private final int FRAME_SKIP_INTERVAL = 3; // 3프레임마다 1번만 처리

    public PresentationAnalyzer() {
        // ML Kit 얼굴 감지 설정 - 에뮬레이터 최적화
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE) // 성능 향상을 위해 랜드마크 비활성화
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.1f) // 더 작은 얼굴도 감지
                .enableTracking()
                .build();

        detector = FaceDetection.getClient(options);
        cameraExecutor = Executors.newSingleThreadExecutor();
        currentScores = new PresentationScores();
    }

    public void startAnalysis(Context context, String type, AnalysisCallback callback) {
        this.callback = callback;
        this.isAnalyzing = true;
        this.analysisType = type;
        this.analysisStartTime = System.currentTimeMillis();

        // 분석 데이터 초기화
        resetAnalysisData();

        // 카메라 설정
        setupCamera(context);

        Log.d(TAG, "분석 시작: " + type);
    }

    private void setupCamera(Context context) {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(context);
            } catch (Exception e) {
                Log.e(TAG, "카메라 설정 실패", e);
                if (callback != null) {
                    callback.onError("카메라 설정 실패: " + e.getMessage());
                }
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindCameraUseCases(Context context) {
        // 전면 카메라 선택
        CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

        // 이미지 분석 설정 - 에뮬레이터 최적화
        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new android.util.Size(320, 240)) // 해상도 낮춤
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(
                    (LifecycleOwner) context,
                    cameraSelector,
                    imageAnalysis
            );
            Log.d(TAG, "카메라 바인딩 성공");
        } catch (Exception e) {
            Log.e(TAG, "카메라 바인딩 실패", e);
            if (callback != null) {
                callback.onError("카메라 바인딩 실패: " + e.getMessage());
            }
        }
    }

    private void analyzeImage(@androidx.annotation.NonNull ImageProxy imageProxy) {
        if (!isAnalyzing) {
            imageProxy.close();
            return;
        }

        // 프레임 스킵으로 성능 최적화
        frameSkipCount++;
        if (frameSkipCount % FRAME_SKIP_INTERVAL != 0) {
            imageProxy.close();
            return;
        }
        try {
            // ImageProxy에서 Image 가져오기 (null일 수 있음)
            android.media.Image mediaImage = imageProxy.getImage();
            if (mediaImage == null) {
                Log.w(TAG, "MediaImage is null");
                imageProxy.close();
                return;
            }

            // InputImage 생성
            InputImage image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.getImageInfo().getRotationDegrees()
            );

            detector.process(image)
                    .addOnSuccessListener(faces -> {
                        processFaceResults(faces);
                        imageProxy.close();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "얼굴 감지 실패", e);
                        imageProxy.close();
                    });

        } catch (Exception e) {
            Log.e(TAG, "이미지 처리 오류: " + e.getMessage());
            imageProxy.close();
        }
    }

    private void processFaceResults(List<Face> faces) {
        totalFrames++;

        if (!faces.isEmpty()) {
            facesDetectedFrames++;

            // 첫 번째 얼굴만 분석
            Face face = faces.get(0);
            analyzeFaceData(face);
        }

        // 5프레임마다 점수 업데이트 (성능 최적화)
        if (totalFrames % 5 == 0) {
            calculateScores();
            if (callback != null) {
                callback.onScoreUpdate(currentScores);
            }
        }
    }

    private void analyzeFaceData(Face face) {
        // 1. 시선 접촉 분석 (눈 열림 상태)
        if (face.getLeftEyeOpenProbability() != null && face.getRightEyeOpenProbability() != null) {
            float leftEyeOpen = face.getLeftEyeOpenProbability();
            float rightEyeOpen = face.getRightEyeOpenProbability();

            // 더 관대한 기준 적용 (에뮬레이터 환경 고려)
            if (leftEyeOpen > 0.3f && rightEyeOpen > 0.3f) {
                eyesOpenFrames++;
            }

            Log.d(TAG, String.format("눈 열림: L=%.2f, R=%.2f", leftEyeOpen, rightEyeOpen));
        } else {
            // ML Kit에서 확률을 제공하지 않는 경우 얼굴이 감지되었다면 눈이 열린 것으로 간주
            eyesOpenFrames++;
            Log.d(TAG, "눈 확률 정보 없음, 얼굴 감지됨으로 간주");
        }

        // 2. 표정 다양성 분석 - 향상된 로직
        if (face.getSmilingProbability() != null) {
            float smilingProb = face.getSmilingProbability();
            if (smilingProb > 0.3f) { // 더 관대한 기준
                smilingFrames++;
            }
            confidenceScores.add(smilingProb);
            Log.d(TAG, String.format("미소 확률: %.2f", smilingProb));
        } else {
            // 미소 확률이 없는 경우 랜덤하게 표정 변화 시뮬레이션
            if (Math.random() > 0.7) {
                smilingFrames++;
            }
            Log.d(TAG, "미소 확률 정보 없음, 시뮬레이션 적용");
        }

        // 3. 음성 분석 대신 입 움직임이나 시간 기반 추정
        // 실제 음성 분석은 별도 라이브러리가 필요하므로 시간 기반으로 추정
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - analysisStartTime;

        // 분석 시간이 5초 이상이면 말하고 있다고 가정 (간단한 휴리스틱)
        if (elapsedTime > 5000 && Math.random() > 0.4) {
            speakingFrames++;
        }
    }

    private void calculateScores() {
        if (totalFrames == 0) return;

        // 1. 시선 접촉 점수 - 개선된 계산
        float eyeContactRatio = (float) eyesOpenFrames / totalFrames;
        currentScores.eyeContact = Math.min(100f, Math.max(10f, eyeContactRatio * 100f));

        // 2. 표정 다양성 점수 - 개선된 계산
        float smilingRatio = (float) smilingFrames / totalFrames;
        float expressionVariance = confidenceScores.size() > 1 ? calculateVariance(confidenceScores) : 0.1f;
        currentScores.expressionVariety = Math.min(100f, Math.max(5f,
                (smilingRatio * 50f) + (expressionVariance * 200f)));

        // 3. 음성 일관성 - 시간과 분석 타입 기반
        long elapsedSeconds = (System.currentTimeMillis() - analysisStartTime) / 1000;
        float speakingRatio = totalFrames > 0 ? (float) speakingFrames / totalFrames : 0;

        if (TYPE_INTRO.equals(analysisType)) {
            // 자기소개: 지속적으로 말해야 하므로 시간 기반 점수
            currentScores.voiceConsistency = Math.min(100f, Math.max(60f,
                    70f + (elapsedSeconds * 2f) + (speakingRatio * 20f)));
        } else {
            // 질문답변: 간헐적 답변이므로 다른 기준
            currentScores.voiceConsistency = Math.min(100f, Math.max(50f,
                    65f + (elapsedSeconds * 1.5f) + (speakingRatio * 30f)));
        }

        // 4. 자연스러움 점수 - 다른 점수들의 조합
        float faceDetectionRatio = (float) facesDetectedFrames / totalFrames;
        currentScores.naturalness = Math.min(100f, Math.max(20f,
                (currentScores.eyeContact * 0.3f) +
                        (currentScores.expressionVariety * 0.3f) +
                        (faceDetectionRatio * 40f)));

        Log.d(TAG, String.format("점수 업데이트: 시선=%.1f, 표정=%.1f, 음성=%.1f, 자연=%.1f",
                currentScores.eyeContact, currentScores.expressionVariety,
                currentScores.voiceConsistency, currentScores.naturalness));
    }

    private float calculateVariance(List<Float> values) {
        if (values.size() < 2) return 0.1f; // 기본값

        float sum = 0f;
        for (float value : values) {
            sum += value;
        }
        float mean = sum / values.size();

        float variance = 0f;
        for (float value : values) {
            variance += (value - mean) * (value - mean);
        }

        return variance / values.size();
    }

    public PresentationScores stopAnalysis() {
        isAnalyzing = false;

        // 최종 점수 계산
        calculateScores();

        // 최소 점수 보장 (0점 방지)
        currentScores.eyeContact = Math.max(10f, currentScores.eyeContact);
        currentScores.expressionVariety = Math.max(10f, currentScores.expressionVariety);
        currentScores.voiceConsistency = Math.max(50f, currentScores.voiceConsistency);
        currentScores.naturalness = Math.max(15f, currentScores.naturalness);

        Log.d(TAG, "분석 종료 - 최종 점수: " + currentScores.toString());
        return currentScores.clone();
    }

    public void cleanup() {
        isAnalyzing = false;

        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }

        if (detector != null) {
            detector.close();
        }

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }

        Log.d(TAG, "리소스 정리 완료");
    }

    private void resetAnalysisData() {
        totalFrames = 0;
        facesDetectedFrames = 0;
        eyesOpenFrames = 0;
        smilingFrames = 0;
        speakingFrames = 0;
        frameSkipCount = 0;
        confidenceScores.clear();
        currentScores = new PresentationScores();
    }
}