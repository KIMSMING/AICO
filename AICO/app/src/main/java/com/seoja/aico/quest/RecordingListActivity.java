package com.seoja.aico.quest;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.seoja.aico.R;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class RecordingListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private RecordingAdapter adapter;
    private List<File> recordings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording_list);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Recordings");
        if (!dir.exists()) dir.mkdirs();

        recordings = Arrays.asList(dir.listFiles());

        adapter = new RecordingAdapter(recordings, new RecordingAdapter.OnItemClickListener() {
            @Override
            public void onPlayClick(File file) {
                MediaPlayer player = new MediaPlayer();
                try {
                    player.setDataSource(file.getPath());
                    player.prepare();
                    player.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onDeleteClick(File file) {
                file.delete();
                recordings = Arrays.asList(dir.listFiles());
                adapter.notifyDataSetChanged();
            }
        });

        recyclerView.setAdapter(adapter);
    }
}
