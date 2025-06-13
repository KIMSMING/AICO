package com.seoja.aico.user;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.*;
import com.google.firebase.auth.*;
import com.google.firebase.database.*;

import com.navercorp.nid.NaverIdLoginSDK; // 네이버 SDK 최신 import
import com.kakao.sdk.user.UserApiClient;

import com.seoja.aico.OptionActivity;
import com.seoja.aico.R;

public class UserViewActivity extends AppCompatActivity {

    private ImageView btnOption, imageProfile;
    private TextView textNickname, textEmail, textName, textBirth, textGender, textAddress, textPhone;
    private Button btnChangePassword, btnEdit, btnHistory, btnLogout, btnDeleteAccount;
    private ImageButton btnBack;

    private DatabaseReference database;
    private FirebaseUser currentUser;
    private FirebaseAuth mAuth;

    private GoogleSignInClient googleSignInClient;

    // 수정 화면 결과 받기
    private final ActivityResultLauncher<Intent> updateUserLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && currentUser != null) {
                    loadUserProfile(currentUser.getUid());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_view);

        // View 연결
        btnBack = findViewById(R.id.btnBack);
        imageProfile = findViewById(R.id.imageProfile);
        textNickname = findViewById(R.id.textNickname);
        textEmail = findViewById(R.id.textEmail);
        textName = findViewById(R.id.textName);
        textBirth = findViewById(R.id.textBirth);
        textGender = findViewById(R.id.textGender);
        textAddress = findViewById(R.id.textAddress);
        textPhone = findViewById(R.id.textPhone);
        btnEdit = findViewById(R.id.btnEdit);
        btnLogout = findViewById(R.id.btnLogout);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount);
        btnHistory = findViewById(R.id.btnHistory);
        btnOption = findViewById(R.id.btnOption);

        mAuth = FirebaseAuth.getInstance();

        // 구글 로그인 클라이언트 준비
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // 뒤로가기
        btnBack.setOnClickListener(v -> finish());

        // 내 정보 수정
        btnOption.setOnClickListener(v -> {
            Intent intent = new Intent(this, OptionActivity.class);
            updateUserLauncher.launch(intent);
        });

        // 내 정보 수정
        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, UpdateUserActivity.class);
            updateUserLauncher.launch(intent);
        });

        // 히스토리 조회
        btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoryActivity.class);
            startActivity(intent);
        });

        // 비밀번호 변경
        btnChangePassword.setOnClickListener(v -> showPasswordResetDialog());

        // 로그아웃 (버튼 하나로 통합)
        btnLogout.setOnClickListener(v -> handleLogoutOrDelete(false));

        // 계정 삭제 (버튼 하나로 통합)
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

    // 로그아웃/계정삭제 통합 처리
    private void handleLogoutOrDelete(boolean isDelete) {
        SharedPreferences prefs = getSharedPreferences("aico_prefs", MODE_PRIVATE);
        String socialType = prefs.getString("social_type", ""); // "google", "kakao", "naver", "email" 등
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        Runnable redirect = () -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        };

        if (isDelete && user != null) {
            user.delete().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // DB에서 유저 정보 삭제
                    database.child(user.getUid()).removeValue();
                    Toast.makeText(this, "계정이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                    doSocialLogout(socialType, redirect);
                } else {
                    Toast.makeText(this, "계정 삭제 실패: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            doSocialLogout(socialType, redirect);
        }
    }

    // 실제 소셜 로그아웃/연결해제 + Firebase 로그아웃
    private void doSocialLogout(String socialType, Runnable redirect) {
        switch (socialType) {
            case "google":
                googleSignInClient.signOut().addOnCompleteListener(task -> {
                    FirebaseAuth.getInstance().signOut();
                    redirect.run();
                });
                break;
            case "kakao":
                UserApiClient.getInstance().logout(error -> {
                    FirebaseAuth.getInstance().signOut();
                    redirect.run();
                    return null;
                });
                break;
            case "naver":
                NaverIdLoginSDK.INSTANCE.logout();
                FirebaseAuth.getInstance().signOut();
                redirect.run();
                break;
            default:
                googleSignInClient.signOut().addOnCompleteListener(task1 -> {
                    UserApiClient.getInstance().logout(error -> {
                        NaverIdLoginSDK.INSTANCE.logout();
                        FirebaseAuth.getInstance().signOut();
                        redirect.run();
                        return null;
                    });
                });
                break;
        }
    }

    // 계정 삭제 확인 다이얼로그
    private void showDeleteConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("계정 삭제")
                .setMessage("정말로 계정을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.")
                .setPositiveButton("네", (dialog, which) -> handleLogoutOrDelete(true))
                .setNegativeButton("아니오", null)
                .show();
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

                textNickname.setText(nickname != null ? nickname : "---");
                textEmail.setText(email != null ? email : "---");
                textName.setText(name != null ? name : "---");
                textBirth.setText(birth != null && !birth.isEmpty()
                        ? formatBirth(birth)
                        : "---");
                textGender.setText(
                        gender != null && !gender.isEmpty()
                                ? (gender.equals("M") ? "남성" : gender.equals("F") ? "여성" : gender)
                                : "---"
                );
                textAddress.setText(
                        address != null && !address.isEmpty()
                                ? address
                                : "---"
                );
                textPhone.setText(phone != null && !phone.isEmpty()
                        ? formatPhone(phone)
                        : "---");

                // Glide로 프로필 사진 표시
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

    private String formatBirth(String birth) {
        if (birth == null || birth.length() != 8) return birth != null ? birth : "-";
        return birth.substring(0, 4) + "/" + birth.substring(4, 6) + "/" + birth.substring(6, 8);
    }

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

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
