package com.seoja.aico.reviewBoard;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
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

    private ImageButton btnBack;
    private RecyclerView recyclerBoardList;
    private FloatingActionButton btnWritePost;
    private BoardListAdapter adapter;
    private List<BoardPost> postList = new ArrayList<>();
    private DatabaseReference boardRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_board_list);

        // 초기화
        btnBack = findViewById(R.id.btnBack);
        recyclerBoardList = findViewById(R.id.recyclerBoardList);
        btnWritePost = findViewById(R.id.btnWritePost);

        // RecyclerView 설정
        adapter = new BoardListAdapter(postList, getCurrentUserId());
        recyclerBoardList.setLayoutManager(new LinearLayoutManager(this));
        recyclerBoardList.setAdapter(adapter);

        // 클릭 이벤트
        btnBack.setOnClickListener(v -> finish());
        btnWritePost.setOnClickListener(v -> startActivity(new Intent(this, AddBoardActivity.class)));
        adapter.setOnItemClickListener(post -> openPostDetail(post.postId));

        // Firebase 참조
        boardRef = FirebaseDatabase.getInstance().getReference("board");
        loadBoardPosts();
    }

    private String getCurrentUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    private void openPostDetail(String postId) {
        Intent intent = new Intent(this, BoardActivity.class);
        intent.putExtra("postKey", postId);
        startActivity(intent);
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
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(BoardListActivity.this, "데이터 불러오기 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
