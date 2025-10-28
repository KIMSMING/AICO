package com.seoja.aico.reviewBoard;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
// SearchView import 추가
import androidx.appcompat.widget.SearchView;

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
import java.util.Locale;

public class BoardListActivity extends AppCompatActivity {

    private ImageButton btnBack, btnSearch;
    private SearchView searchView;
    private RecyclerView recyclerBoardList;
    private FloatingActionButton btnWritePost;
    private TextView titleTextView,  btnFilterAll, btnFilterTech, btnFilterPersonality, btnFilterGroup;
    private String currentCategoryFilter = "전체";

    private BoardListAdapter adapter;
    private List<BoardPost> displayedPostList = new ArrayList<>();
    private List<BoardPost> originalPostList = new ArrayList<>();
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
        btnSearch = findViewById(R.id.btnSearch);
        searchView = findViewById(R.id.searchView);

        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterTech = findViewById(R.id.btnFilterTech);
        btnFilterPersonality = findViewById(R.id.btnFilterPersonality);
        btnFilterGroup = findViewById(R.id.btnFilterGroup);

        titleTextView.setText("면접 후기");
        btnSearch.setVisibility(View.VISIBLE);

        View.OnClickListener filterClickListener = this::onFilterButtonClick;
        btnFilterAll.setOnClickListener(filterClickListener);
        btnFilterTech.setOnClickListener(filterClickListener);
        btnFilterPersonality.setOnClickListener(filterClickListener);
        btnFilterGroup.setOnClickListener(filterClickListener);

        btnBack.setOnClickListener(v -> handleBackButton());

        btnWritePost.setOnClickListener(v -> {
            startActivity(new Intent(this, AddBoardActivity.class));
        });

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String currentUserId = user != null ? user.getUid() : null;

        adapter = new BoardListAdapter(displayedPostList, currentUserId);
        recyclerBoardList.setLayoutManager(new LinearLayoutManager(this));
        recyclerBoardList.setAdapter(adapter);

        adapter.setOnItemClickListener(post -> {
            Intent intent = new Intent(this, BoardActivity.class);
            intent.putExtra("postKey", post.postId);
            startActivity(intent);
        });

        boardRef = FirebaseDatabase.getInstance().getReference("board");
        loadBoardPosts();

        setupSearchFunctionality();
    }

    private void loadBoardPosts() {
        boardRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                originalPostList.clear();
                for (DataSnapshot postSnap : snapshot.getChildren()) {
                    BoardPost post = postSnap.getValue(BoardPost.class);
                    if (post != null) {
                        post.postId = postSnap.getKey();
                        originalPostList.add(post);
                    }
                }
                originalPostList.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));

                // 현재 검색창에 텍스트가 있으면 그 텍스트로 필터링, 없으면 전체 목록 표시
                String currentQuery = searchView.getQuery().toString();
                filter(currentQuery);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setupSearchFunctionality() {
        btnSearch.setOnClickListener(v -> {
            titleTextView.setVisibility(View.GONE);
            btnSearch.setVisibility(View.GONE);
            searchView.setVisibility(View.VISIBLE);
            searchView.requestFocus(); // 검색창에 바로 포커스
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filter(newText);
                return true;
            }
        });
    }

    private void filter(String searchText) {
        List<BoardPost> categoryFilteredList = new ArrayList<>();

        // 1. 카테고리 필터링 먼저 적용
        if (currentCategoryFilter.equals("전체")) {
            categoryFilteredList.addAll(originalPostList);
        } else {
            for (BoardPost post : originalPostList) {
                // BoardPost 클래스에 category 필드가 있다고 가정합니다.
                if (post.category != null && post.category.equals(currentCategoryFilter)) {
                    categoryFilteredList.add(post);
                }
            }
        }

        // 2. 검색어 필터링 적용
        List<BoardPost> finalList = new ArrayList<>();
        String query = searchText.toLowerCase(Locale.getDefault()).trim();

        if (query.isEmpty()) {
            finalList.addAll(categoryFilteredList);
        } else {
            for (BoardPost post : categoryFilteredList) {
                if (post.title.toLowerCase(Locale.getDefault()).contains(query) ||
                        post.nickname.toLowerCase(Locale.getDefault()).contains(query)) {
                    finalList.add(post);
                }
            }
        }
        adapter.filterList(finalList);
    }

    private void handleBackButton() {
        if (searchView.getVisibility() == View.VISIBLE) {
            searchView.setQuery("", false); // 검색어 초기화
            searchView.setVisibility(View.GONE);
            titleTextView.setVisibility(View.VISIBLE);
            btnSearch.setVisibility(View.VISIBLE);
            adapter.filterList(originalPostList); // 전체 목록으로 복원
        } else {
            finish();
        }
    }

    private void onFilterButtonClick(View view) {
        TextView clickedButton = (TextView) view;
        currentCategoryFilter = clickedButton.getText().toString();
        updateFilterButtonsUI();
        filter(searchView.getQuery().toString());
    }

    private void updateFilterButtonsUI() {
        TextView[] buttons = {btnFilterAll, btnFilterTech, btnFilterPersonality, btnFilterGroup};
        for (TextView button : buttons) {
            boolean isSelected = button.getText().toString().equals(currentCategoryFilter);
            if (isSelected) {
                button.setTextAppearance(R.style.FilterButton_Selected);
                button.setBackgroundResource(R.drawable.professional_button_secondary);
            } else {
                button.setTextAppearance(R.style.FilterButton);
                button.setBackgroundResource(R.drawable.professional_button_tertiary);
            }
        }
    }

}