package com.seoja.aico;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 3000; // 3초
    private static final int ANIMATION_DURATION = 800; // 애니메이션 지속시간

    private ImageView ivAIBrain;
    private TextView tvAICO;
    private TextView tvSubtitle;
    private LinearLayout loadingSection;
    private TextView tvVersion;
    private TextView tvCopyright;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // 상태바 숨기기
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        initViews();
        startSplashAnimation();

        // 일정 시간 후 메인 액티비티로 이동
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        }, SPLASH_DURATION);
    }

    private void initViews() {
        ivAIBrain = findViewById(R.id.ivAIBrain);
        tvAICO = findViewById(R.id.tvAICO);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        loadingSection = findViewById(R.id.loadingSection);
        tvVersion = findViewById(R.id.tvVersion);
        tvCopyright = findViewById(R.id.tvCopyright);
    }

    private void startSplashAnimation() {
        // 1단계: AI 브레인 아이콘 애니메이션
        ObjectAnimator brainAlpha = ObjectAnimator.ofFloat(ivAIBrain, "alpha", 0f, 1f);
        ObjectAnimator brainScaleX = ObjectAnimator.ofFloat(ivAIBrain, "scaleX", 0.8f, 1f);
        ObjectAnimator brainScaleY = ObjectAnimator.ofFloat(ivAIBrain, "scaleY", 0.8f, 1f);

        AnimatorSet brainAnimSet = new AnimatorSet();
        brainAnimSet.playTogether(brainAlpha, brainScaleX, brainScaleY);
        brainAnimSet.setDuration(ANIMATION_DURATION);
        brainAnimSet.setInterpolator(new AccelerateDecelerateInterpolator());

        // 2단계: AICO 텍스트 애니메이션
        ObjectAnimator aicoAlpha = ObjectAnimator.ofFloat(tvAICO, "alpha", 0f, 1f);
        ObjectAnimator aicoTranslation = ObjectAnimator.ofFloat(tvAICO, "translationY", 20f, 0f);

        AnimatorSet aicoAnimSet = new AnimatorSet();
        aicoAnimSet.playTogether(aicoAlpha, aicoTranslation);
        aicoAnimSet.setDuration(ANIMATION_DURATION);
        aicoAnimSet.setStartDelay(300);
        aicoAnimSet.setInterpolator(new AccelerateDecelerateInterpolator());

        // 3단계: 서브타이틀 애니메이션
        ObjectAnimator subtitleAlpha = ObjectAnimator.ofFloat(tvSubtitle, "alpha", 0f, 1f);
        ObjectAnimator subtitleTranslation = ObjectAnimator.ofFloat(tvSubtitle, "translationY", 20f, 0f);

        AnimatorSet subtitleAnimSet = new AnimatorSet();
        subtitleAnimSet.playTogether(subtitleAlpha, subtitleTranslation);
        subtitleAnimSet.setDuration(ANIMATION_DURATION);
        subtitleAnimSet.setStartDelay(600);
        subtitleAnimSet.setInterpolator(new AccelerateDecelerateInterpolator());

        // 4단계: 로딩 섹션 애니메이션
        ObjectAnimator loadingAlpha = ObjectAnimator.ofFloat(loadingSection, "alpha", 0f, 1f);
        loadingAlpha.setDuration(ANIMATION_DURATION);
        loadingAlpha.setStartDelay(1000);
        loadingAlpha.setInterpolator(new AccelerateDecelerateInterpolator());

        // 5단계: 하단 정보 애니메이션
        ObjectAnimator versionAlpha = ObjectAnimator.ofFloat(tvVersion, "alpha", 0f, 1f);
        ObjectAnimator copyrightAlpha = ObjectAnimator.ofFloat(tvCopyright, "alpha", 0f, 1f);

        AnimatorSet bottomAnimSet = new AnimatorSet();
        bottomAnimSet.playTogether(versionAlpha, copyrightAlpha);
        bottomAnimSet.setDuration(ANIMATION_DURATION);
        bottomAnimSet.setStartDelay(1300);
        bottomAnimSet.setInterpolator(new AccelerateDecelerateInterpolator());

        // 모든 애니메이션 시작
        brainAnimSet.start();
        aicoAnimSet.start();
        subtitleAnimSet.start();
        loadingAlpha.start();
        bottomAnimSet.start();
    }
}

