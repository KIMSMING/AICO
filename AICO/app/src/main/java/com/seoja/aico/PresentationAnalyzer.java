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
    private List<Float> smileProbabilities = new ArrayList<>();
    private int expressionChanges = 0;
    private float previousSmileProb = 0f;

    // 자기소개와 질문답변에 따른 다른 가중치
    private AnalysisWeights weights;

    public PresentationAnalyzer() {
        // ML Kit 얼굴 감지 설정
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.15f)
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

        // 분석 타입에 따른 가중치 설정
        setWeightsByType(type);

        // 분석 데이터 초기화
        resetAnalysisData();

        // 카메라 설정
        setupCamera(context);
    }

    // 분석 타입별 가중치 설정
    private void setWeightsByType(String type) {
        if (TYPE_INTRO.equals(type)) {
            // 자기소개: 자연스러움과 표정을 더 중시
            weights = new AnalysisWeights(0.25f, 0.35f, 0.15f, 0.25f);
        } else {
            // 질문답변: 시선접촉과 음성을 더 중시
            weights = new AnalysisWeights(0.35f, 0.20f, 0.30f, 0.15f);
        }
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

        // 이미지 분석 설정
        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new android.util.Size(640, 480))
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

        try {
            // ImageProxy에서 Image 가져오기 (null일 수 있음)
            android.media.Image mediaImage = imageProxy.getImage();
            if (mediaImage == null) {
                Log.w(TAG, "MediaImage is null - ImageProxy doesn't wrap an android Image");
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

        // 10프레임마다 점수 업데이트
        if (totalFrames % 10 == 0) {
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

            if (leftEyeOpen > 0.5f && rightEyeOpen > 0.5f) {
                eyesOpenFrames++;
            }
        }

        // 2. 표정 다양성 분석 (미소 확률)
        if (face.getSmilingProbability() != null) {
            float currentSmileProb = face.getSmilingProbability();
            smileProbabilities.add(currentSmileProb);

            // 표정 변화 감지
            if (Math.abs(currentSmileProb - previousSmileProb) > 0.2f) {
                expressionChanges++;
            }
            previousSmileProb = currentSmileProb;
        }
    }

    private void calculateScores() {
        if (totalFrames == 0) return;

        // 1. 시선 접촉 점수
        float eyeContactRatio = (float) eyesOpenFrames / totalFrames;
        currentScores.eyeContact = Math.min(100f, eyeContactRatio * getEyeContactMultiplier());

        // 2. 표정 다양성 점수
        if (totalFrames > 10) {
            float changeRatio = (float) expressionChanges / totalFrames;
            currentScores.expressionVariety = Math.min(100f, changeRatio * getExpressionMultiplier());

            // 미소 확률의 변화량 추가 고려
            if (smileProbabilities.size() > 1) {
                float variance = calculateVariance(smileProbabilities);
                currentScores.expressionVariety = Math.min(100f,
                        currentScores.expressionVariety + variance * 50f);
            }
        }

        // 3. 음성 일관성 (분석 타입에 따라 다른 기본값)
        if (TYPE_INTRO.equals(analysisType)) {
            // 자기소개: 약간 더 높은 기본값 (준비된 내용)
            currentScores.voiceConsistency = 80f + (float) (Math.random() * 10 - 5);
        } else {
            // 질문답변: 일반적인 기본값
            currentScores.voiceConsistency = 75f + (float) (Math.random() * 10 - 5);
        }

        // 4. 자연스러움 (가중 평균 사용)
        currentScores.naturalness = (
                currentScores.eyeContact * weights.eyeContactWeight +
                        currentScores.expressionVariety * weights.expressionWeight +
                        currentScores.voiceConsistency * weights.voiceWeight
        );
    }

    // 분석 타입에 따른 다른 승수
    private float getEyeContactMultiplier() {
        return TYPE_INTRO.equals(analysisType) ? 110f : 120f;
    }

    private float getExpressionMultiplier() {
        return TYPE_INTRO.equals(analysisType) ? 350f : 300f;
    }

    private float calculateVariance(List<Float> values) {
        if (values.size() < 2) return 0f;

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
    }

    private void resetAnalysisData() {
        totalFrames = 0;
        facesDetectedFrames = 0;
        eyesOpenFrames = 0;
        smileProbabilities.clear();
        expressionChanges = 0;
        previousSmileProb = 0f;
        currentScores = new PresentationScores();
    }

    // 가중치 클래스
    private static class AnalysisWeights {
        float eyeContactWeight;
        float expressionWeight;
        float voiceWeight;
        float naturalnessWeight;

        AnalysisWeights(float eyeContact, float expression, float voice, float naturalness) {
            this.eyeContactWeight = eyeContact;
            this.expressionWeight = expression;
            this.voiceWeight = voice;
            this.naturalnessWeight = naturalness;
        }
    }
}