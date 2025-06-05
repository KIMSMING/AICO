package com.seoja.aico;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class OptionActivity extends AppCompatActivity {

    ImageButton btnBack;
    private SeekBar seekBarVolume;
    private Switch switchVibration, switchNotification;
    private AudioManager audioManager;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_option);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // SharedPreferences
        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        btnBack = findViewById(R.id.btnBack);
        // 소리(SeekBar) 설정
        seekBarVolume = findViewById(R.id.seekBarVolume);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int curVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        seekBarVolume.setMax(maxVolume);
        seekBarVolume.setProgress(curVolume);

        seekBarVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 진동 ON/OFF
        switchVibration = findViewById(R.id.switchVibration);
        boolean vibrationOn = prefs.getBoolean("vibration", true);
        switchVibration.setChecked(vibrationOn);
        switchVibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("vibration", isChecked).apply();
        });

        // 알림 ON/OFF
        switchNotification = findViewById(R.id.switchNotification);
        boolean notificationOn = prefs.getBoolean("notification", true);
        switchNotification.setChecked(notificationOn);
        switchNotification.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("notification", isChecked).apply();
        });

        // 개인정보 처리방침 모달
        findViewById(R.id.btnPrivacy).setOnClickListener(v -> showDetailDialog(
                "개인정보 처리방침",
                "본 앱은 이용자의 개인정보를 중요하게 여기며, 「개인정보 보호법」 등 관련 법령을 준수합니다.\n\n" +
                        "1. 수집하는 개인정보 항목\n" +
                        "- 회원가입, 서비스 이용 과정에서 이름, 이메일, 휴대전화번호 등 최소한의 개인정보를 수집할 수 있습니다.\n" +
                        "- 서비스 이용 중 생성되는 로그, 기기 정보, 쿠키 등도 자동으로 수집될 수 있습니다.\n\n" +
                        "2. 개인정보의 이용 목적\n" +
                        "- 회원 식별 및 관리, 서비스 제공 및 개선, 문의 응대, 공지사항 전달, 맞춤형 서비스 제공을 위해 개인정보를 활용합니다.\n\n" +
                        "3. 개인정보의 보관 및 파기\n" +
                        "- 개인정보는 수집 및 이용 목적이 달성된 후 즉시 파기됩니다. 단, 관련 법령에 따라 일정 기간 보관이 필요한 경우 해당 기간 동안 안전하게 보관됩니다.\n\n" +
                        "4. 개인정보의 제3자 제공\n" +
                        "- 이용자의 동의 없이 개인정보를 외부에 제공하지 않습니다. 단, 법령에 따라 요청이 있는 경우에는 예외로 할 수 있습니다.\n\n" +
                        "5. 이용자의 권리\n" +
                        "- 이용자는 언제든지 자신의 개인정보를 조회, 수정, 삭제 요청할 수 있습니다. 또한 개인정보 처리에 대한 동의 철회도 가능합니다.\n\n" +
                        "6. 개인정보 보호를 위한 노력\n" +
                        "- 본 앱은 개인정보 보호를 위해 암호화, 접근제어 등 기술적·관리적 보호조치를 시행하고 있습니다.\n\n" +
                        "자세한 내용은 앱 내 고객센터 또는 이메일 문의를 통해 확인하실 수 있습니다."
        ));

        // 이용약관 모달
        findViewById(R.id.btnTerms).setOnClickListener(v -> showDetailDialog(
                "이용약관",
                "본 약관은 본 앱(이하 \"서비스\")의 이용과 관련하여 회사와 이용자 간의 권리, 의무 및 책임사항을 규정합니다.\n\n" +
                        "1. 서비스의 이용\n" +
                        "- 이용자는 본 약관 및 관련 법령을 준수하여 서비스를 이용해야 하며, 타인의 권리를 침해하거나 서비스 운영에 지장을 주는 행위를 해서는 안 됩니다.\n\n" +
                        "2. 회원가입 및 계정 관리\n" +
                        "- 회원가입 시 정확한 정보를 입력해야 하며, 타인의 정보를 도용할 경우 서비스 이용이 제한될 수 있습니다.\n" +
                        "- 계정 정보는 본인이 직접 관리해야 하며, 타인에게 양도 또는 공유할 수 없습니다.\n\n" +
                        "3. 서비스의 변경 및 중단\n" +
                        "- 회사는 서비스의 일부 또는 전부를 사전 고지 후 변경하거나 중단할 수 있습니다. 단, 불가피한 사정이 있는 경우 사전 고지 없이 변경/중단될 수 있습니다.\n\n" +
                        "4. 게시물 및 콘텐츠\n" +
                        "- 이용자가 서비스에 게시한 글, 사진 등 모든 콘텐츠의 저작권은 이용자에게 있으며, 회사는 서비스 운영, 홍보 등을 위해 합리적인 범위 내에서 이를 사용할 수 있습니다.\n" +
                        "- 타인의 권리를 침해하는 게시물은 사전 통보 없이 삭제될 수 있습니다.\n\n" +
                        "5. 책임과 면책\n" +
                        "- 회사는 천재지변, 불가항력적 사유 등으로 인한 서비스 장애에 대해 책임을 지지 않습니다.\n" +
                        "- 이용자의 귀책사유로 인한 문제에 대해서는 회사가 책임지지 않습니다.\n\n" +
                        "6. 약관의 변경\n" +
                        "- 본 약관은 관련 법령 및 회사 정책에 따라 변경될 수 있으며, 변경 시 사전에 공지합니다.\n\n" +
                        "기타 문의사항은 고객센터를 통해 안내받으실 수 있습니다."
        ));

        // 뒤로가기
        btnBack.setOnClickListener(v -> finish());
    }

    private void showDetailDialog(String title, String content) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_detail, null, false);
        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvContent = dialogView.findViewById(R.id.tvDialogContent);

        tvTitle.setText(title);
        tvContent.setText(content);

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("닫기", null)
                .show();
    }

    // 앱 내에서 진동/알림 설정을 사용할 때 예시
    public static boolean isVibrationOn(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("settings", MODE_PRIVATE);
        return prefs.getBoolean("vibration", true);
    }
    public static boolean isNotificationOn(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("settings", MODE_PRIVATE);
        return prefs.getBoolean("notification", true);
    }
}
