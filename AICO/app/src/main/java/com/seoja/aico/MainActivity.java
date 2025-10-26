package com.seoja.aico;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.seoja.aico.quest.FieldActivity;
import com.seoja.aico.reviewBoard.BoardActivity;
import com.seoja.aico.reviewBoard.BoardListActivity;
import com.seoja.aico.reviewBoard.BoardPost;
import com.seoja.aico.reviewBoard.MainBoardPreviewAdapter;
import com.seoja.aico.user.LoginActivity;
import com.seoja.aico.user.UserViewActivity;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    ImageButton btnOption;
    private FirebaseAuth mAuth;
    TextView btnGoBoard, tvWelcomeMessage, tvCurrentLevel;
    ProgressBar progressLevel;
    LinearLayout btnUserView, btnTestSpeech, btnAddQuestion, btnQuest;
    
    private int currentLevel = 1;
    private int currentExperience = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();

        // 1. 로그인 상태 체크
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            redirectToLogin();
            return;
        }

        // 2. 로그인 되어 있으면 MainActivity 화면 세팅
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // printKeyHash(); // 해쉬 키 필요할 때만 쓰면 됨

        btnUserView = findViewById(R.id.btnGoUserView);
        btnOption = (ImageButton) findViewById(R.id.btnOption);
        btnQuest = findViewById(R.id.btnQuest);
        btnGoBoard = (TextView) findViewById(R.id.btnGoBoard);
        btnTestSpeech = findViewById(R.id.btnTestSpeech);
        btnAddQuestion = findViewById(R.id.btnAddQuestion);
        
        // 레벨 관련 UI 초기화
        progressLevel = findViewById(R.id.progressLevel);
        tvWelcomeMessage = findViewById(R.id.tvWelcomeMessage);
        tvCurrentLevel = findViewById(R.id.tvCurrentLevel);

        // 설정하기
        btnOption.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, OptionActivity.class));
        });

        // 마이크테스트
        btnTestSpeech.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, MiceTestActivity.class));
        });

        // 유저정보
        btnUserView.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, UserViewActivity.class));
        });

        // 퀘스트
        btnQuest.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, FieldActivity.class));
        });

        // 후기 게시판
        btnGoBoard.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, BoardListActivity.class));
        });

        //질문 추가하기
        btnAddQuestion.setOnClickListener(v -> {
            showQuestionDialog();
        });

//        printKeyHash();

        // 게시판 글 출력
        RecyclerView rvMainPreview = findViewById(R.id.rvMainPreview);
        rvMainPreview.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        List<BoardPost> previewList = new ArrayList<>();
        MainBoardPreviewAdapter previewAdapter = new MainBoardPreviewAdapter(
                previewList, currentUserId, post -> {
            Intent intent = new Intent(MainActivity.this, BoardActivity.class);
            intent.putExtra("postKey", post.postId);
            startActivity(intent);
        });
        rvMainPreview.setAdapter(previewAdapter);

        // 후기보기 > 클릭 시 게시판 이동
        findViewById(R.id.btnGoBoard).setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, BoardListActivity.class));
        });

        // 데이터 로딩 (좋아요순 4개만)
        DatabaseReference boardRef = FirebaseDatabase.getInstance().getReference("board");
        boardRef.get().addOnSuccessListener(snapshot -> {
            List<BoardPost> allPosts = new ArrayList<>();
            for (DataSnapshot postSnap : snapshot.getChildren()) {
                BoardPost post = postSnap.getValue(BoardPost.class);
                if (post != null) {
                    post.postId = postSnap.getKey();
                    allPosts.add(post);
                }
            }
            Collections.sort(allPosts, (a, b) -> Integer.compare(b.likes, a.likes));
            previewList.clear();
            for (int i = 0; i < Math.min(4, allPosts.size()); i++) {
                previewList.add(allPosts.get(i));
            }
            previewAdapter.notifyDataSetChanged();
        });

        // 사용자 이름 로드 및 환영 메시지 설정
        loadUserName();
        
        // 레벨 데이터 로드
        loadUserLevelData();
        
        // 알람권한
        checkAndRequestPermissions();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 앱이 백그라운드에서 복귀할 때마다 로그인 상태 확인
        if (mAuth.getCurrentUser() == null) {
            redirectToLogin();
        }
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void printKeyHash() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                String keyHash = Base64.encodeToString(md.digest(), Base64.NO_WRAP);
                Log.d("KeyHash", keyHash);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //질문 추가하기 다이얼로그 함수
    private void showQuestionDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("듣고 싶은 질문을 입력해주세요");
        LayoutInflater inflater = this.getLayoutInflater();

        View dialogView = inflater.inflate(R.layout.dialog_add_question, null);

        final TextInputEditText input = dialogView.findViewById(R.id.text_input_question);

        builder.setView(dialogView);

        builder.setPositiveButton("추가", (dialog, which) -> {
            String question = String.valueOf(input.getText()).trim();
            if (!question.isEmpty()) {
                saveQuestionToFirebase(question);
            } else {
                Toast.makeText(this, "질문을 입력해주세요", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("취소", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    //Firebase에 질문 저장 함수
    private void saveQuestionToFirebase(String question) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String uid = currentUser.getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid)
                .child("custom_questions");

        String key = ref.push().getKey(); //고유 키 자동 생성
        ref.child(key).setValue(question)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "질문이 추가되었습니다!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "저장 실패 : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void checkAndRequestPermissions() {
        // 첫 번째: 알림 권한 확인 및 요청 (API 33 이상)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
                return;
            }
        }
        checkAndRequestMediaPermission();
    }

    // 2. 미디어(오디오) 권한 확인 및 요청 함수
    private void checkAndRequestMediaPermission() {
        String permission;
        // 버전에 따라 다른 권한을 요청
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permission = android.Manifest.permission.READ_MEDIA_AUDIO;
        } else {
            permission = android.Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission}, 3001);
            return;
        }
        checkAndRequestRecordAudioPermission();
    }

    // 3. 마이크 권한 확인 및 요청 함수
    private void checkAndRequestRecordAudioPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 2001);
            }
        }
    }

    // 4. 모든 권한 요청 결과를 처리하는 콜백 메소드
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1001: // 알림 권한 결과
                checkAndRequestMediaPermission();
                break;
            case 3001: // 미디어(오디오) 권한 결과
                checkAndRequestRecordAudioPermission();
                break;
            case 2001: // 마이크 권한 결과
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "마이크 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "마이크 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }
    
    // 사용자 이름 로드
    private void loadUserName() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        
        String uid = currentUser.getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid);
        
        userRef.child("name").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String userName = snapshot.getValue(String.class);
                if (userName != null && !userName.isEmpty()) {
                    tvWelcomeMessage.setText(userName + "님 환영합니다");
                } else {
                    tvWelcomeMessage.setText("환영합니다");
                }
            }
            
            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("MainActivity", "사용자 이름 로드 실패: " + error.getMessage());
                tvWelcomeMessage.setText("환영합니다");
            }
        });
    }
    
    // 레벨 데이터 로드
    private void loadUserLevelData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        
        String uid = currentUser.getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid);
        
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Log.d("MainActivity", "Firebase 데이터 로드: " + snapshot.toString());
                
                if (snapshot.exists()) {
                    // 기존 데이터가 있으면 로드
                    Integer level = snapshot.child("level").getValue(Integer.class);
                    Integer exp = snapshot.child("experience").getValue(Integer.class);
                    
                    Log.d("MainActivity", "로드된 레벨: " + level + ", 경험치: " + exp);
                    
                    if (level != null) {
                        currentLevel = level;
                    } else {
                        userRef.child("level").setValue(1);
                        currentLevel = 1;
                    }
                    
                    if (exp != null) {
                        currentExperience = exp;
                    } else {
                        userRef.child("experience").setValue(0);
                        currentExperience = 0;
                    }
                    
                    // 레벨업 체크 및 처리
                    checkAndProcessLevelUp();
                } else {
                    // 신규 사용자면 기본값 설정
                    Log.d("MainActivity", "신규 사용자 - 기본값 설정");
                    userRef.child("level").setValue(1);
                    userRef.child("experience").setValue(0);
                    currentLevel = 1;
                    currentExperience = 0;
                }
                updateLevelUI();
            }
            
            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("MainActivity", "레벨 데이터 로드 실패: " + error.getMessage());
            }
        });
    }
    
    // 레벨 UI 업데이트
    private void updateLevelUI() {
        int requiredExp = calculateRequiredExp(currentLevel);
        int currentLevelExp = currentExperience - calculateTotalExpForLevel(currentLevel - 1);
        // 레벨 텍스트 업데이트 (레벨 숫자만 밝게)
        setLevelTextWithHighlight(currentLevel);
        
        int progress = (int) ((float) currentLevelExp / requiredExp * 100);
        progressLevel.setProgress(progress);
        
        Log.d("MainActivity", "프로그레스바 진행률: " + progress + "%");
    }
    
    // 레벨업 체크 및 처리
    private void checkAndProcessLevelUp() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        
        int oldLevel = currentLevel;
        int newLevel = currentLevel;
        
        // 레벨업 체크
        while (currentExperience >= calculateTotalExpForLevel(newLevel)) {
            newLevel++;
        }
        
        // 레벨업이 발생한 경우
        if (newLevel > oldLevel) {
            // Firebase에 업데이트
            String uid = currentUser.getUid();
            DatabaseReference userRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(uid);
            
            userRef.child("level").setValue(newLevel);
            
            // 로컬 변수 업데이트
            currentLevel = newLevel;
            
            // 레벨업 알림
            Toast.makeText(this, "레벨업! 레벨 " + newLevel + " 달성!", Toast.LENGTH_LONG).show();
        }
    }
    
    // 레벨 텍스트 스타일링 (레벨 숫자만 밝게)
    private void setLevelTextWithHighlight(int level) {
        String text = "당신의 레벨은 " + level + " 입니다";
        SpannableString spannableString = new SpannableString(text);
        
        // 레벨 숫자 부분만 밝은 색으로 설정
        String levelStr = String.valueOf(level);
        int startIndex = text.indexOf(levelStr);
        int endIndex = startIndex + levelStr.length();
        
        if (startIndex >= 0) {
            spannableString.setSpan(
                new ForegroundColorSpan(Color.parseColor("#A5E6A8")),
                startIndex,
                endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        
        tvCurrentLevel.setText(spannableString);
    }
    
    // 레벨업에 필요한 경험치 계산
    private int calculateRequiredExp(int level) {
        return 20 + (level - 1) * 5;
    }
    
    // 특정 레벨까지의 총 필요 경험치 계산
    private int calculateTotalExpForLevel(int level) {
        int totalExp = 0;
        for (int i = 1; i <= level; i++) {
            totalExp += calculateRequiredExp(i);
        }
        return totalExp;
    }
    
    // 경험치 추가 및 레벨업 처리
    public void addExperience(int exp) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        
        int oldLevel = currentLevel;
        int newExperience = currentExperience + exp;
        int newLevel = currentLevel;
        
        // 레벨업 체크
        while (newExperience >= calculateTotalExpForLevel(newLevel)) {
            newLevel++;
        }
        
        // Firebase에 업데이트
        String uid = currentUser.getUid();
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(uid);
        
        userRef.child("level").setValue(newLevel);
        userRef.child("experience").setValue(newExperience);
        
        // 로컬 변수 업데이트
        currentLevel = newLevel;
        currentExperience = newExperience;
        
        // UI 업데이트
        updateLevelUI();
        
        // 레벨업 알림
        if (newLevel > oldLevel) {
            Toast.makeText(this, "레벨업! 레벨 " + newLevel + " 달성!", Toast.LENGTH_LONG).show();
        }
    }
}
