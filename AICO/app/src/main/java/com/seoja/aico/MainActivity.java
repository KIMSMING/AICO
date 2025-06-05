package com.seoja.aico;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.seoja.aico.reviewBoard.BoardActivity;
import com.seoja.aico.reviewBoard.BoardListActivity;
import com.seoja.aico.reviewBoard.BoardPost;
import com.seoja.aico.reviewBoard.MainBoardPreviewAdapter;
import com.seoja.aico.user.LoginActivity;
import com.seoja.aico.user.UserViewActivity;
import com.seoja.aico.QuestActivity;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    Button btnQuest, btnAddQuestion;
    ImageButton btnUserView, btnOption;
    private FirebaseAuth mAuth;
    TextView btnGoBoard;

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

        btnUserView = (ImageButton) findViewById(R.id.btnGoUserView);
        btnOption = (ImageButton) findViewById(R.id.btnOption);
        btnQuest = (Button) findViewById(R.id.btnQuest);
        btnGoBoard = (TextView) findViewById(R.id.btnGoBoard);
        btnAddQuestion = (Button) findViewById(R.id.btnAddQuestion);

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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("듣고 싶은 질문을 입력해주세요");

        final EditText input = new EditText(this);
        input.setHint("예: 이 직무에서 가장 도전적인 부분은?");
        builder.setView(input);

        builder.setPositiveButton("추가", (dialog, which) -> {
            String question = input.getText().toString().trim();
            if(!question.isEmpty()) {
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
        if(currentUser == null) return;

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
}
