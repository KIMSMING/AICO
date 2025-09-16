package com.seoja.aico;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.seoja.aico.quest.QuestActivity;
import com.seoja.aico.quest.SttApi;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

public class MainActivity2 extends AppCompatActivity {

    private TextView statusTextView, textRequest;
    private Button convertButton;
    private Button playButton;
    private ExecutorService executorService;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<String[]> filePickerLauncher;
    private MediaPlayer mediaPlayer;
    private String audioFilePath;
    private String extractedAudioPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main2);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        textRequest = findViewById(R.id.textRequest);
        statusTextView = findViewById(R.id.statusTextView);
        convertButton = findViewById(R.id.convertButton);
        playButton = findViewById(R.id.playButton);
        executorService = Executors.newSingleThreadExecutor();

        playButton.setEnabled(false); // 초기 상태는 비활성화

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        runOnUiThread(() -> statusTextView.setText("파일 선택 완료. 오디오 추출을 시작합니다..."));
                        playButton.setEnabled(false);
                        executorService.execute(() -> extractAudioFromVideo(uri));
                    }
                });

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        openFilePicker();
                    } else {
                        runOnUiThread(() -> statusTextView.setText("오류: 권한이 거부되어 파일에 접근할 수 없습니다."));
                    }
                }
        );

        convertButton.setOnClickListener(v -> checkAndRequestPermission());

        playButton.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    playButton.setText("오디오 재생");
                    statusTextView.setText("재생 일시정지.");
                } else {
                    mediaPlayer.start();
                    playButton.setText("일시정지");
                    statusTextView.setText("오디오 재생 중...");
                }
            } else if (extractedAudioPath != null) {
                // MediaPlayer가 초기화되지 않았을 경우
                setupMediaPlayer(extractedAudioPath);
                mediaPlayer.start();
                playButton.setText("일시정지");
                statusTextView.setText("오디오 재생 중...");
            }
        });
    }

    private void checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED) {
                openFilePicker();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                openFilePicker();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
    }

    private void openFilePicker() {
        filePickerLauncher.launch(new String[]{"video/mp4", "video/quicktime"});
    }

    private void extractAudioFromVideo(Uri inputUri) {
        MediaExtractor extractor = null;
        MediaMuxer muxer = null;

        try {
            runOnUiThread(() -> statusTextView.setText("오디오 추출 중..."));

            extractor = new MediaExtractor();
            extractor.setDataSource(this, inputUri, null);

            int audioTrackIndex = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                    break;
                }
            }

            if (audioTrackIndex == -1) {
                runOnUiThread(() -> statusTextView.setText("오디오 트랙을 찾을 수 없습니다."));
                Log.e("AudioExtractor", "비디오 파일에서 오디오 트랙을 찾을 수 없습니다.");
                return;
            }

            extractor.selectTrack(audioTrackIndex);
            MediaFormat audioFormat = extractor.getTrackFormat(audioTrackIndex);

            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            String outputPath = new File(downloadDir, "extracted_audio.m4a").getAbsolutePath();

            muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int muxerAudioTrackIndex = muxer.addTrack(audioFormat);
            muxer.start();

            ByteBuffer buffer = ByteBuffer.allocate(audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            while (true) {
                int sampleSize = extractor.readSampleData(buffer, 0);
                if (sampleSize < 0) {
                    break;
                }

                info.size = sampleSize;
                info.offset = 0;
                info.flags = extractor.getSampleFlags();
                info.presentationTimeUs = extractor.getSampleTime();

                muxer.writeSampleData(muxerAudioTrackIndex, buffer, info);
                extractor.advance();
            }

            // **Changed section starts here**
            extractedAudioPath = outputPath;
            runOnUiThread(() -> {
                statusTextView.setText("오디오 추출 성공. 서버로 전송합니다...");
                Log.d("AudioExtractor", "오디오 추출 성공: " + extractedAudioPath);

                // Use the newly created file path for both MediaPlayer setup and server upload
                setupMediaPlayer(extractedAudioPath);

                File audioFile = new File(extractedAudioPath);

                // Ensure the file exists before trying to upload it
                if (audioFile.exists()) {
                    RequestBody requestFile = RequestBody.create(
                            MediaType.parse("audio/m4a"),
                            audioFile
                    );
                    MultipartBody.Part body = MultipartBody.Part.createFormData(
                            "file",
                            audioFile.getName(),
                            requestFile
                    );
                    uploadAudioToServer(body);
                } else {
                    Toast.makeText(this, "오디오 파일이 존재하지 않습니다. 서버 업로드 실패.", Toast.LENGTH_LONG).show();
                    Log.e("STT_UPLOAD", "File does not exist at path: " + extractedAudioPath);
                }
            });
            // **Changed section ends here**

        } catch (Exception e) {
            runOnUiThread(() -> statusTextView.setText("오디오 추출 실패: " + e.getMessage()));
            Log.e("AudioExtractor", "오디오 추출 실패", e);
        } finally {
            if (extractor != null) {
                extractor.release();
            }
            if (muxer != null) {
                muxer.stop();
                muxer.release();
            }
        }
    }

    // Retrofit 서버 업로드
    private void uploadAudioToServer(MultipartBody.Part body) {
        // OkHttpClient Builder를 사용하여 타임아웃 설정
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                // 연결 타임아웃 (서버와 연결되는 시간) - 기본값 10초
                .connectTimeout(60, TimeUnit.SECONDS)
                // 읽기 타임아웃 (서버로부터 응답을 받는 시간) - 기본값 10초
                .readTimeout(60, TimeUnit.SECONDS)
                // 쓰기 타임아웃 (서버에 데이터를 보내는 시간) - 기본값 10초
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://" + getString(R.string.server_url))
                // OkHttp 클라이언트를 Retrofit에 연결
                .client(okHttpClient)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build();

        SttApi service = retrofit.create(SttApi.class);
        Call<String> call = service.uploadAudio(body);
        Context context = this;

        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if (response.isSuccessful()) {
                    String textResult = response.body();
                    Toast.makeText(context, "STT 결과: " + textResult, Toast.LENGTH_LONG).show();
                    runOnUiThread(() -> {
                        textRequest.setText(textResult);
                    });
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "No error body";
                        Log.e("STT_API_ERROR", "서버 응답 오류. 코드: " + response.code() + ", 메시지: " + response.message() + ", 에러 바디: " + errorBody);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Toast.makeText(context, "서버 응답 오류: " + response.message(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                Log.e("STT_API_FAILURE", "서버 전송 실패", t);
                if (t instanceof java.net.SocketTimeoutException) {
                    Toast.makeText(context, "시간 초과 오류: 서버가 너무 느리거나 응답이 없습니다.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(context, "서버 전송 실패: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void setupMediaPlayer(String audioFilePath) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(audioFilePath);
            mediaPlayer.prepare();
            playButton.setEnabled(true);
            playButton.setText("오디오 재생");
        } catch (IOException e) {
            statusTextView.setText("재생 오류: " + e.getMessage());
            Log.e("MediaPlayer", "재생 오류", e);
            playButton.setEnabled(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
    }
}
