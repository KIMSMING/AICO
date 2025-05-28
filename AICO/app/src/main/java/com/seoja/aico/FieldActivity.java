package com.seoja.aico;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.*;

public class FieldActivity extends AppCompatActivity implements View.OnClickListener {

    Spinner firstSpinner, secondSpinner;

    Button btnQuest;

    ImageView imageBack;

    Map<String, List<String>> jobMap = new LinkedHashMap<>();

    TextView firstText, secondText;

    String selectedSecond = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_field);

        firstSpinner = findViewById(R.id.firstSpinner);
        secondSpinner = findViewById(R.id.secondSpinner);
        firstText = findViewById(R.id.firstText);
        secondText = findViewById(R.id.secondText);
        btnQuest = findViewById(R.id.btnQuest);
        imageBack = findViewById(R.id.imageBack);

        btnQuest.setOnClickListener(this);
        imageBack.setOnClickListener(this);

        setupJobData();

        // 첫 번째 Spinner에 들어갈 항목들 (Map의 key 값들) 리스트로 변환
        List<String> firstList = new ArrayList<>(jobMap.keySet());

        // 첫 번째 Spinner에 사용할 어댑터 생성
        ArrayAdapter<String> firstAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, firstList);

        // 드롭다운 스타일 설정
        firstAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // 첫 번째 Spinner에 어댑터 설정
        firstSpinner.setAdapter(firstAdapter);

        // 첫 번째 Spinner에서 선택 항목이 바뀌었을 때 실행될 리스너 등록
        firstSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                // 두 번째 Spinner와 EditText를 보이도록 설정
                secondSpinner.setVisibility(View.VISIBLE);
                secondText.setVisibility(View.VISIBLE);

                // 선택된 항목(직업 대분류)을 가져옴
                String selectedFirst = firstList.get(position);

                if (position == 0) {
                    secondSpinner.setVisibility(View.GONE);
                    secondText.setVisibility(View.GONE);
                }
                else{
                    // 해당 항목에 대응되는 소분류 리스트 가져오기
                    List<String> secondList = jobMap.get(selectedFirst);

                    // 두 번째 Spinner에 사용할 어댑터 생성
                    ArrayAdapter<String> secondAdapter = new ArrayAdapter<>(FieldActivity.this,
                            android.R.layout.simple_spinner_item, secondList);
                    secondAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    secondSpinner.setAdapter(secondAdapter);

                    secondSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position2, long id) {
                            selectedSecond = secondList.get(position2);
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {
                            selectedSecond = "";
                        }
                    });
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.imageBack) {
            finish();
        }

        if (v.getId() == R.id.btnQuest) {
            Intent intent = new Intent(FieldActivity.this, QuestActivity.class);
            startActivity(intent);
        }
    }

    // 직업 분야별 데이터를 Map 형태로 초기화하는 함수
    private void setupJobData() {
        jobMap = new LinkedHashMap<>();

        // 각 대분류 항목에 대한 소분류 항목들을 Map에 넣음
        jobMap.put("직업분류 선택하기", Arrays.asList("세부직업 선택하기"));
        jobMap.put("경영·사무·금융·보험직"
                , Arrays.asList("관리직", "경영·행정·사무직", "금융·보험직"));
        jobMap.put("연구직 및 공학 기술직"
                , Arrays.asList("인문·사회과학연구직", "자연·생명 과학 연구직"
                        , "정보통신 연구 개발직 및 공학기술직", "건설·채굴 연구개발직 및 공학기술직"
                        , "제조 연구개발직 및 공학기술직"));
        jobMap.put("교육·법률·사회복지·경찰·소방직 및 군인"
                , Arrays.asList("사회복지·종교직", "교육직"
                        , "법률직", "경찰·소방·교도직", "군인"));
        jobMap.put("보건·의료직"
                , Arrays.asList("보건·의료직"));
        jobMap.put("예술·디자인·방송·스포츠직"
                , Arrays.asList("예술·디자인·방송직", "스포츠·레크리에이션직"));
        jobMap.put("미용·여행·숙박·음식·경비·청소직"
                , Arrays.asList("경호·경비직", "돌봄 서비스직"
                        , "청소 및 기타 개인서비스직", "마용·예식 서비스직"
                        , "여행·숙박·오락 서비스직", "음식 서비스직"));
        jobMap.put("영업·판매·운전·운송직"
                , Arrays.asList("영업·판매직", "운전·운송직"));
        jobMap.put("건설·채굴직"
                , Arrays.asList("건설·채굴직"));
        jobMap.put("설치·정비·생산직"
                , Arrays.asList("식품 가공·생산직", "인쇄·목재·공예 및 설치·정비·생산작"
                        , "제조 단순직", "기계 설치·정비·생산직"
                        , "금속·재료 설치·정비·생산직", "전기·전자 설치·정비·생산직"
                        , "정보통신 설치·정비직", "화학·환경 설치·정비·생산직"
                        , "섬유·의복 생산직"));
        jobMap.put("농림어업직", Arrays.asList("농림어업직"));
    }
}
