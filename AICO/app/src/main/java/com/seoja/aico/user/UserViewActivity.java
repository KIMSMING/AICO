package com.seoja.aico.user;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.*;
import com.google.firebase.database.*;

import com.seoja.aico.MainActivity;
import com.seoja.aico.R;
import com.seoja.aico.QuestActivity;

public class UserViewActivity extends AppCompatActivity {

    private ImageView imageProfile;
    private TextView textNickname, textEmail, textName, textBirth, textGender, textAddress, textPhone;
    private Button btnChangePassword, btnEdit, btnHistory, btnLogout, btnDeleteAccount;
    private ImageButton btnBack;

    private DatabaseReference database;
    private FirebaseUser currentUser;
    private FirebaseAuth mAuth;

    // 수정 화면 결과 받기
    private final ActivityResultLauncher<Intent> updateUserLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && currentUser != null) {
                    // 수정 완료 시 사용자 정보 즉시 새로고침
                    loadUserProfile(currentUser.getUid());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_view);

        // View 연결
        btnBack = (ImageButton) findViewById(R.id.btnBack);
        imageProfile = (ImageView) findViewById(R.id.imageProfile);
        textNickname = (TextView) findViewById(R.id.textNickname);
        textEmail = (TextView) findViewById(R.id.textEmail);
        textName = (TextView) findViewById(R.id.textName);
        textBirth = (TextView) findViewById(R.id.textBirth);
        textGender = (TextView) findViewById(R.id.textGender);
        textAddress = (TextView) findViewById(R.id.textAddress);
        textPhone = (TextView) findViewById(R.id.textPhone);
        btnEdit = (Button) findViewById(R.id.btnEdit);
        btnLogout = (Button) findViewById(R.id.btnLogout);
        btnChangePassword = (Button) findViewById(R.id.btnChangePassword);
        btnDeleteAccount = (Button) findViewById(R.id.btnDeleteAccount);
        btnHistory = (Button) findViewById(R.id.btnHistory);

        mAuth = FirebaseAuth.getInstance();

        // 뒤로가기
        btnBack.setOnClickListener(v -> finish());

        // 내 정보 수정 (registerForActivityResult 사용)
        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, UpdateUserActivity.class);
            updateUserLauncher.launch(intent);
        });

        // 히스토리 조회
        btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, QuestActivity.class);
            startActivity(intent);
        });

        // 비밀번호 변경 (모달창)
        btnChangePassword.setOnClickListener(v -> showPasswordResetDialog());

        // 로그아웃 처리
        btnLogout.setOnClickListener(v -> {
            // 1. Firebase 인증 로그아웃
            mAuth.signOut();

            // 2. Google 로그아웃 처리
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .build();

            GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, gso);

            googleSignInClient.signOut().addOnCompleteListener(task -> {
                // 3. 로그인 화면으로 이동
                redirectToLogin(); // 로그인 액티비티로 이동하는 메서드
            });
        });

        // 계정 삭제
        btnDeleteAccount.setOnClickListener(v -> showDeleteConfirmDialog());

        // Firebase 연결
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        database = FirebaseDatabase.getInstance().getReference("users");

        // 사용자 정보 불러오기
        loadUserProfile(currentUser.getUid());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            for (UserInfo info : user.getProviderData()) {
                if ("password".equals(info.getProviderId())) {
                    btnChangePassword.setVisibility(View.VISIBLE);
                    break;
                }
            }
        }
    }

    private void loadUserProfile(String uid) {
        database.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(UserViewActivity.this, "사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }
                String nickname = snapshot.child("nickname").getValue(String.class);
                String email = snapshot.child("email").getValue(String.class);
                String name = snapshot.child("name").getValue(String.class);
                String birth = snapshot.child("birth").getValue(String.class);
                String gender = snapshot.child("gender").getValue(String.class);
                String address = snapshot.child("address").getValue(String.class);
                String phone = snapshot.child("phone").getValue(String.class);
                String photoUrl = snapshot.child("photoUrl").getValue(String.class);

                textNickname.setText(nickname != null ? nickname : "-");
                textEmail.setText(email != null ? email : "-");
                textName.setText(name != null ? "이름: " + name : "이름: -");
                textBirth.setText(birth != null && !birth.isEmpty()
                        ? "생년월일: " + formatBirth(birth)
                        : "생년월일: -");
                textGender.setText(gender != null ?
                        ("성별: ".concat(gender.equals("M") ? "남성" : gender.equals("F") ? "여성" : gender)) : "성별: -");
                textAddress.setText(address != null ? "주소: " + address : "주소: -");
                textPhone.setText(phone != null && !phone.isEmpty()
                        ? "전화번호: " + formatPhone(phone)
                        : "전화번호: -");

                // Glide로 프로필 사진 표시 (photoUrl이 있으면)
                if (photoUrl != null && !photoUrl.isEmpty()) {
                    Glide.with(UserViewActivity.this)
                            .load(photoUrl)
                            .placeholder(R.drawable.ic_person)
                            .into(imageProfile);
                } else {
                    imageProfile.setImageResource(R.drawable.ic_person);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(UserViewActivity.this, "데이터를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 생년월일 포맷: 19991231 → 1999/12/31
    private String formatBirth(String birth) {
        if (birth == null || birth.length() != 8) return birth != null ? birth : "-";
        return birth.substring(0, 4) + "/" + birth.substring(4, 6) + "/" + birth.substring(6, 8);
    }

    // 전화번호 포맷: 01012345678 → 010-1234-5678, 0212345678 → 02-1234-5678
    private String formatPhone(String phone) {
        if (phone == null) return "-";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 11) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
        } else if (digits.length() == 10) {
            if (digits.startsWith("02")) {
                return digits.substring(0, 2) + "-" + digits.substring(2, 6) + "-" + digits.substring(6);
            } else {
                return digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
            }
        } else {
            return phone;
        }
    }

    // 비밀번호 재설정 이메일 모달
    private void showPasswordResetDialog() {
        TextView emailView = new TextView(this);
        emailView.setTextSize(16);
        emailView.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        emailView.setPadding(0, 24, 0, 24);
        emailView.setTypeface(null, android.graphics.Typeface.BOLD);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String email = (user != null && user.getEmail() != null) ? user.getEmail() : "-";
        emailView.setText(email);

        new AlertDialog.Builder(this)
                .setTitle("비밀번호 재설정")
                .setMessage("아래 이메일로 비밀번호 재설정 메일이 발송됩니다.")
                .setView(emailView)
                .setPositiveButton("전송", (dialog, which) -> {
                    if (email.equals("-")) {
                        Toast.makeText(this, "이메일 정보가 없습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    Toast.makeText(this, "비밀번호 재설정 메일을 전송했습니다.", Toast.LENGTH_LONG).show();
                                } else {
                                    String msg = "메일 전송 실패: ";
                                    if (task.getException() != null)
                                        msg += task.getException().getMessage();
                                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // 로그인 화면이동
    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // 삭제 경고 팝업
    private void showDeleteConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("계정 삭제")
                .setMessage("정말로 계정을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.")
                .setPositiveButton("네", (dialog, which) -> deleteAccount())
                .setNegativeButton("아니오", null)
                .show();
    }

    private void deleteAccount() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        // 1. Authentication에서 삭제
        user.delete().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // 2. Realtime Database에서 삭제
                database.child(user.getUid()).removeValue();

                Toast.makeText(this, "계정이 삭제되었습니다.", Toast.LENGTH_SHORT).show();

                // 스택 초기화 및 메인 이동
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "계정 삭제 실패: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
