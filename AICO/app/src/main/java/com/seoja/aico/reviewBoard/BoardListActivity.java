package com.seoja.aico.reviewBoard;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.seoja.aico.R;

import java.util.ArrayList;
import java.util.List;

public class BoardListActivity extends AppCompatActivity {

    private ImageButton btnBack, btnSearsh;
    private RecyclerView recyclerBoardList;
    private FloatingActionButton btnWritePost;
    private TextView titleTextView;

    private BoardListAdapter adapter;
    private List<BoardPost> postList = new ArrayList<>();
    private DatabaseReference boardRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_board_list);

        btnBack = findViewById(R.id.btnBack);
        recyclerBoardList = findViewById(R.id.recyclerBoardList);
        btnWritePost = findViewById(R.id.btnWritePost);
        titleTextView = findViewById(R.id.header_title);
        btnSearsh = findViewById(R.id.btnSearch);

        titleTextView.setText("면접 후기");
        btnSearsh.setVisibility(View.VISIBLE);

        // 뒤로가기
        btnBack.setOnClickListener(v -> finish());

        // 글 작성 버튼
        btnWritePost.setOnClickListener(v -> {
            startActivity(new Intent(this, AddBoardActivity.class));
        });

        // 현재 로그인한 사용자 UID
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String currentUserId = user != null ? user.getUid() : null;

        // RecyclerView 설정
        adapter = new BoardListAdapter(postList, currentUserId);
        recyclerBoardList.setLayoutManager(new LinearLayoutManager(this));
        recyclerBoardList.setAdapter(adapter);

        // 게시글 클릭 이벤트 예시
        adapter.setOnItemClickListener(post -> {
            Intent intent = new Intent(this, BoardActivity.class);
            intent.putExtra("postKey", post.postId);
            startActivity(intent);
        });

        // Firebase board 경로 참조
        boardRef = FirebaseDatabase.getInstance().getReference("board");

        // 게시글 불러오기
        loadBoardPosts();
    }

    private void loadBoardPosts() {
        boardRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                postList.clear();
                for (DataSnapshot postSnap : snapshot.getChildren()) {
                    BoardPost post = postSnap.getValue(BoardPost.class);
                    if (post != null) {
                        post.postId = postSnap.getKey();
                        postList.add(post);
                    }
                }
                postList.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
                adapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}
