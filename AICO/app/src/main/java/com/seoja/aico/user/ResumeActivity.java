package com.seoja.aico.user;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.seoja.aico.R;
import com.seoja.aico.gpt.ApiClient;
import com.seoja.aico.gpt.GptApi;
import com.seoja.aico.gpt.GptResponse;
import com.seoja.aico.quest.QuestActivity;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ResumeActivity extends AppCompatActivity {

    // 서버주소(values/strings.xml, py_server_url) 이거사용 -> getString(R.string.py_server_url);

    private TextView titleTextView;
    private ImageButton btnBack;

    private TextWatcher resumeTextWatcher;
    private TextInputEditText inputJobRole, inputProject, inputStrength, inputWeakness, inputMotivation;
    private Button btnSaveResume, btnReset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resume);

        inputJobRole = findViewById(R.id.inputJobRole);
        inputProject = findViewById(R.id.inputProject);
        inputStrength = findViewById(R.id.inputStrength);
        inputWeakness = findViewById(R.id.inputWeakness);
        inputMotivation = findViewById(R.id.inputMotivation);
        btnSaveResume = findViewById(R.id.btnSaveResume);
        btnReset = findViewById(R.id.btnReset);
        titleTextView = findViewById(R.id.header_title);
        btnBack = findViewById(R.id.btnBack);

        initTextWatcher();
        inputJobRole.addTextChangedListener(resumeTextWatcher);
        inputProject.addTextChangedListener(resumeTextWatcher);
        inputStrength.addTextChangedListener(resumeTextWatcher);
        inputWeakness.addTextChangedListener(resumeTextWatcher);
        inputMotivation.addTextChangedListener(resumeTextWatcher);

        titleTextView.setText("자기소개서 작성");

        btnBack.setOnClickListener(v -> finish());

        btnSaveResume.setOnClickListener(v -> saveResumeToFirebase());
        btnReset.setOnClickListener(v -> clearInputFields());

        btnSaveResume.setEnabled(false);
        btnReset.setEnabled(false);

        loadSavedResume();

    }

    private void saveResumeToFirebase() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "로그인 후 이용해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = user.getUid();
        String jobRole = inputJobRole.getText().toString().trim();
        String project = inputProject.getText().toString().trim();
        String strength = inputStrength.getText().toString().trim();
        String weakness = inputWeakness.getText().toString().trim();
        String motivation = inputMotivation.getText().toString().trim();

        if (jobRole.isEmpty() || project.isEmpty() || strength.isEmpty() || weakness.isEmpty() || motivation.isEmpty()) {
            Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("resumes").child(userId);

        Map<String, Object> resumeData = new HashMap<>();
        resumeData.put("job_role", jobRole);
        resumeData.put("project_experience", project);
        resumeData.put("strength", strength);
        resumeData.put("weakness", weakness);
        resumeData.put("motivation", motivation);

        //Firebase 저장
        ref.setValue(resumeData)
                .addOnSuccessListener(aVoid -> {
                    Log.d("ResumeActivity", "1. Firebase 저장 성공");
                    Toast.makeText(this, "자기소개서가 제출되었습니다.", Toast.LENGTH_SHORT).show();
                    startInterviewWithResume(userId); //FastAPI 호출 추가
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "저장 실패 : " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("ResumeActivity", "Firebase 저장 실패", e);
                });
    }

    private void initTextWatcher() {
        resumeTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 아무것도 안 함
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 아무것도 안 함
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 텍스트가 변경된 후, 버튼 상태를 업데이트합니다.
                updateButtonStates();
            }
        };
    }

    private void updateButtonStates() {
        String jobRole = inputJobRole.getText().toString().trim();
        String project = inputProject.getText().toString().trim();
        String strength = inputStrength.getText().toString().trim();
        String weakness = inputWeakness.getText().toString().trim();
        String motivation = inputMotivation.getText().toString().trim();

        // 모든 필드가 비어있으면 true
        boolean allEmpty = jobRole.isEmpty() && project.isEmpty() && strength.isEmpty() &&
                weakness.isEmpty() && motivation.isEmpty();

        btnReset.setEnabled(!allEmpty);

        boolean allFieldsFilled = !jobRole.isEmpty() && !project.isEmpty() && !strength.isEmpty() &&
                !weakness.isEmpty() && !motivation.isEmpty();

        btnSaveResume.setEnabled(allFieldsFilled);
    }

    private void clearInputFields() {
        inputJobRole.setText(null);
        inputProject.setText(null);
        inputStrength.setText(null);
        inputWeakness.setText(null);
        inputMotivation.setText(null);
    }

    private void loadSavedResume() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            updateButtonStates();
            return;
        }

        String userId = user.getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("resumes")
                .child(userId);

        ref.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                String jobRole = snapshot.child("job_role").getValue(String.class);
                String project = snapshot.child("project_experience").getValue(String.class);
                String strength = snapshot.child("strength").getValue(String.class);
                String weakness = snapshot.child("weakness").getValue(String.class);
                String motivation = snapshot.child("motivation").getValue(String.class);

                inputJobRole.setText(jobRole != null ? jobRole : "");
                inputProject.setText(project != null ? project : "");
                inputStrength.setText(strength != null ? strength : "");
                inputWeakness.setText(weakness != null ? weakness : "");
                inputMotivation.setText(motivation != null ? motivation : "");
            }
            updateButtonStates();
        }).addOnFailureListener(e -> {
            Log.e("ResumeActivity", "자기소개서 불러오기 실패: " + e.getMessage());
            updateButtonStates();
        });
    }

    //FastAPI에 자기소개서 기반 질문 요청
    private void startInterviewWithResume(String userId) {
        Log.d("ResumeActivity", "2. FastAPI 호출 시작");
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(getString(R.string.py_server_url))
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        GptApi api = retrofit.create(GptApi.class);
        Call<GptResponse> call = api.matchResumeQuestion(userId);
        call.enqueue(new Callback<GptResponse>() {
            @Override
            public void onResponse(Call<GptResponse> call, Response<GptResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String firstQuestion = response.body().getContent();
                    String matchedCategory = response.body().getMatch();
                    Log.d("ResumeActivity", "3. FastAPI 응답 성공: " + firstQuestion);
                    Log.d("ResumeActivity", "4. matchedCategory = " + matchedCategory);

                    //Toast.makeText(ResumeActivity.this, "첫 질문: " + firstQuestion, Toast.LENGTH_SHORT).show();

                    if (matchedCategory != null && matchedCategory.contains("/")) {
                        String[] parts = matchedCategory.split("/");
                        String selectedFirst = parts[0].trim();
                        String selectedSecond = parts[1].trim();

                        //QuestActivity로 이동 + 첫 질문 전달
                        Intent intent = new Intent(ResumeActivity.this, QuestActivity.class);
                        intent.putExtra("selectedFirst", selectedFirst);
                        intent.putExtra("selectedSecond", selectedSecond);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(ResumeActivity.this, "카테고리 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e("ResumeActivity", "FastAPI 응답 실패: " + response.code());
                    Toast.makeText(ResumeActivity.this, "FastAPI 응답 실패: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<GptResponse> call, Throwable t) {
                Log.e("ResumeActivity", "FastAPI 호출 실패: ", t);
                Toast.makeText(ResumeActivity.this, "서버 연결 실패: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
