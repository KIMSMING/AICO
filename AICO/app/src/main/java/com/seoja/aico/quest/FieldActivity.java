package com.seoja.aico.quest;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.seoja.aico.R;

import java.util.*;

public class FieldActivity extends AppCompatActivity implements View.OnClickListener {

    Spinner firstSpinner, secondSpinner; // 직업 대분류, 소분류 Spinner

    Button btnQuest; // 질문으로 이동 버튼

    ImageView imageBack; // 뒤로가기 버튼

    Map<String, List<String>> jobMap = new LinkedHashMap<>(); // 직업 대분류 → 소분류 맵

    TextView firstText, secondText; // 텍스트 라벨

    String selectedFirst = "";  // 대분류
    String selectedSecond = ""; // 사용자가 선택한 소분류 값 저장

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_field); // 레이아웃 연결

        // View 객체 연결
        firstSpinner = findViewById(R.id.firstSpinner);
        secondSpinner = findViewById(R.id.secondSpinner);
        firstText = findViewById(R.id.firstText);
        secondText = findViewById(R.id.secondText);
        btnQuest = findViewById(R.id.btnQuest);
        imageBack = findViewById(R.id.imageBack);

        // 클릭 리스너 등록
        btnQuest.setOnClickListener(this);
        imageBack.setOnClickListener(this);

        // Firebase Realtime Database에서 "면접질문/직업질문" 데이터 읽기
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("면접질문/직업질문");

        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                jobMap.clear(); // 이전 데이터 초기화

                // 대분류 순회
                for (DataSnapshot categorySnapshot : snapshot.getChildren()) {
                    String key = categorySnapshot.getKey(); // 대분류 키 건설, 채굴직
                    List<String> subList = new ArrayList<>();

                    // 해당 대분류의 소분류 값들 읽기
                    for (DataSnapshot subSnapshot : categorySnapshot.getChildren()) {
                        String subKey = subSnapshot.getKey();
                        if (subKey != null) {
                            subList.add(subKey);
                        }
                    }
                    if (!subList.isEmpty()) {
                        jobMap.put(key, subList); // 맵에 추가
                    }
                }

                Log.d("Firebase", "데이터 로딩 완료: " + jobMap.toString());
                setupSpinner(); // Spinner UI 설정
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "데이터 로딩 실패: " + error.getMessage());
            }
        });
    }

    // Spinner들을 설정하는 메서드
    private void setupSpinner() {
        List<String> firstList = new ArrayList<>(jobMap.keySet()); // 대분류 리스트
        firstList.add(0, "직업분류 선택하기");

        // 첫 번째 Spinner에 어댑터 연결
        ArrayAdapter<String> firstAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, firstList);
        firstSpinner.setAdapter(firstAdapter);

        // 첫 번째 Spinner 선택 리스너 설정
        firstSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedFirst = firstList.get(position); // 선택한 대분류

                if (position == 0 || !jobMap.containsKey(selectedFirst)) {
                    // "직업분류 선택하기"가 선택된 경우 소분류 숨김
                    secondSpinner.setVisibility(View.GONE);
                    secondText.setVisibility(View.GONE);
                    selectedSecond = "";
                    return;
                }

                // 해당 대분류의 소분류 리스트 가져오기
                List<String> rawSecondList = jobMap.get(selectedFirst);
                if (rawSecondList == null || rawSecondList.isEmpty()) {
                    secondSpinner.setVisibility(View.GONE);
                    secondText.setVisibility(View.GONE);
                    selectedSecond = "";
                    return;
                }
                List<String> secondList = new ArrayList<>(rawSecondList);
                ArrayAdapter<String> secondAdapter = new ArrayAdapter<>(FieldActivity.this, android.R.layout.simple_spinner_dropdown_item, secondList);
                secondSpinner.setAdapter(secondAdapter);

                // 소분류 표시
                secondSpinner.setVisibility(View.VISIBLE);
                secondText.setVisibility(View.VISIBLE);

                // 두 번째 Spinner 선택 리스너
                secondSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        selectedSecond = rawSecondList.get(position); // 선택된 소분류 저장
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        selectedSecond = "";
                    }
                });
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedSecond = "";
            }
        });
    }

    // 버튼 클릭 이벤트 처리
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.imageBack) {
            finish(); // 뒤로가기
        }

        if (v.getId() == R.id.btnQuest) {
            Intent intent = new Intent(FieldActivity.this, QuestActivity.class);
            intent.putExtra("selectedFirst", selectedFirst);
            intent.putExtra("selectedSecond", selectedSecond);
            startActivity(intent);
        }
    }
}
