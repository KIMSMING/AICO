package com.seoja.aico.user;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.*;

import com.kakao.sdk.auth.model.OAuthToken;
import com.kakao.sdk.common.KakaoSdk;
import com.kakao.sdk.user.UserApiClient;
import com.navercorp.nid.NaverIdLoginSDK;
import com.navercorp.nid.oauth.OAuthLoginCallback;
import com.seoja.aico.MainActivity;
import com.seoja.aico.R;

import java.io.IOException;

import kotlin.Unit;
import kotlin.jvm.functions.Function2;

import okhttp3.*;

public class LoginActivity extends AppCompatActivity {

    private EditText editTextId, editTextPassword;
    private Button btnLogin, btnSignUp;
    private ImageButton btnGoogleLogin, btnNaverLogin, btnKakaoLogin;

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    // Google 로그인 결과 처리
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Kakao SDK 초기화
        KakaoSdk.init(this, getString(R.string.kakao_app_key));

        // View 바인딩
        editTextId = findViewById(R.id.editTextId);
        editTextPassword = findViewById(R.id.editTextPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnSignUp = findViewById(R.id.btnSignUp);
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);
        btnNaverLogin = findViewById(R.id.btnNaverLogin);
        btnKakaoLogin = findViewById(R.id.btnKakaoLogin);

        // Google 로그인 초기화
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Google 로그인 결과 처리
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            firebaseAuthWithGoogle(account.getIdToken());
                        } catch (ApiException e) {
                            Toast.makeText(this, "구글 로그인 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        // 네이버 SDK 초기화
        NaverIdLoginSDK.INSTANCE.initialize(
                this,
                getString(R.string.naver_client_id),
                getString(R.string.naver_client_secret),
                getString(R.string.naver_client_name)
        );

        // 버튼 리스너
        btnLogin.setOnClickListener(v -> signInWithEmail());
        btnSignUp.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        btnGoogleLogin.setOnClickListener(v -> signInWithGoogle());
        btnNaverLogin.setOnClickListener(v -> signInWithNaver());
        btnKakaoLogin.setOnClickListener(v -> signInWithKakao());
    }

    // 이메일 로그인
    private void signInWithEmail() {
        String email = editTextId.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "이메일과 비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "로그인 성공", Toast.LENGTH_SHORT).show();
                        goToMain();
                    } else {
                        Toast.makeText(this, "로그인 실패: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Google 로그인
    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        checkFirstSocialLogin();
                    } else {
                        Toast.makeText(this, "Firebase 인증 실패: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // 네이버 로그인
    private void signInWithNaver() {
        NaverIdLoginSDK.INSTANCE.authenticate(this, new OAuthLoginCallback() {
            @Override
            public void onSuccess() {
                String accessToken = NaverIdLoginSDK.INSTANCE.getAccessToken();
                // TODO: 서버에 accessToken 전달, customToken 받아오기
                Toast.makeText(LoginActivity.this, "네이버 로그인은 서버 연동 후 사용 가능합니다.", Toast.LENGTH_SHORT).show();
                getFirebaseCustomTokenFromServer(accessToken, "naver");
            }
            @Override
            public void onFailure(int httpStatus, String message) {
                Toast.makeText(LoginActivity.this, "네이버 로그인 실패: " + message, Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onError(int errorCode, String message) {
                onFailure(errorCode, message);
            }
        });
    }

    // 카카오 로그인
    private void signInWithKakao() {
        if (UserApiClient.getInstance().isKakaoTalkLoginAvailable(this)) {
            UserApiClient.getInstance().loginWithKakaoTalk(this, kakaoCallback);
        } else {
            UserApiClient.getInstance().loginWithKakaoAccount(this, kakaoCallback);
        }
    }

    private final Function2<OAuthToken, Throwable, Unit> kakaoCallback = (token, error) -> {
        if (error != null) {
            Toast.makeText(this, "카카오 로그인 실패: " + error.getMessage(), Toast.LENGTH_SHORT).show();
        } else if (token != null) {
            String accessToken = token.getAccessToken();
            // TODO: 서버에 accessToken 전달, customToken 받아오기
            Toast.makeText(this, "카카오 로그인은 서버 연동 후 사용 가능합니다.", Toast.LENGTH_SHORT).show();
            getFirebaseCustomTokenFromServer(accessToken, "kakao");
        }
        return null;
    };

    // 서버 연동 후 사용할 함수 예시 (현재는 미구현)
    private final OkHttpClient httpClient = new OkHttpClient();

    private void getFirebaseCustomTokenFromServer(String accessToken, String provider) {
        // TODO: 실제 서버 주소로 변경
        String url = "https://YOUR_SERVER_URL/api/socialCustomToken";

        RequestBody body = new FormBody.Builder()
                .add("accessToken", accessToken)
                .add("provider", provider) // "naver" 또는 "kakao"
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(LoginActivity.this, "서버 통신 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String customToken = response.body().string().trim();
                    signInWithFirebaseCustomToken(customToken);
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(LoginActivity.this, "커스텀 토큰 발급 실패", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }

    private void signInWithFirebaseCustomToken(String customToken) {
        mAuth.signInWithCustomToken(customToken)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        checkFirstSocialLogin();
                    } else {
                        Toast.makeText(this, "Firebase 커스텀 인증 실패: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // 최초 로그인(회원가입) 여부 체크 및 DB 저장
    private void checkFirstSocialLogin() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && user.getMetadata() != null) {
            long creation = user.getMetadata().getCreationTimestamp();
            long lastSignIn = user.getMetadata().getLastSignInTimestamp();
            if (creation == lastSignIn) {
                // 최초 로그인(회원가입)
                SocialRegisterImpl.registerUser(user, new SocialRegisterImpl.RegisterCallback() {
                    @Override
                    public void onSuccess() {
                        goToMain();
                    }
                    @Override
                    public void onFailure(String errorMsg) {
                        runOnUiThread(() -> Toast.makeText(LoginActivity.this, "회원가입 실패: " + errorMsg, Toast.LENGTH_SHORT).show());
                    }
                });
            } else {
                // 기존 회원
                goToMain();
            }
        } else {
            Toast.makeText(this, "사용자 정보를 확인할 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    // 메인화면 이동
    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
