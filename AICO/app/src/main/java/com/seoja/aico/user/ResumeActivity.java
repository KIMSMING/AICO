package com.seoja.aico.user;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.seoja.aico.R;

import java.util.HashMap;
import java.util.Map;

public class ResumeActivity extends AppCompatActivity {

    private TextInputEditText inputJobRole, inputProject, inputStrength, inputWeakness, inputMotivation;
    private Button btnSubmitResume;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resume);

        inputJobRole = findViewById(R.id.inputJobRole);
        inputProject = findViewById(R.id.inputProject);
        inputStrength = findViewById(R.id.inputStrength);
        inputWeakness = findViewById(R.id.inputWeakness);
        inputMotivation = findViewById(R.id.inputMotivation);
        btnSubmitResume = findViewById(R.id.btnSubmitResume);

        btnSubmitResume.setOnClickListener(v -> saveResumeToFirebase());
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

        ref.setValue(resumeData)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "자기소개서가 제출되었습니다.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "저장 실패 : " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
