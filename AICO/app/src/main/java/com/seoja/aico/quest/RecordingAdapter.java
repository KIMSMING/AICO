package com.seoja.aico.quest;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.seoja.aico.R;

import java.io.File;
import java.util.List;

public class RecordingAdapter extends RecyclerView.Adapter<RecordingAdapter.RecordingViewHolder> {

    private List<File> recordings;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onPlayClick(File file);
        void onDeleteClick(File file);
    }

    public RecordingAdapter(List<File> recordings, OnItemClickListener listener) {
        this.recordings = recordings;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RecordingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recording, parent, false);
        return new RecordingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordingViewHolder holder, int position) {
        File file = recordings.get(position);
        holder.textFileName.setText(file.getName());
        holder.btnPlay.setOnClickListener(v -> listener.onPlayClick(file));
        holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(file));
    }

    @Override
    public int getItemCount() {
        return recordings.size();
    }

    static class RecordingViewHolder extends RecyclerView.ViewHolder {
        TextView textFileName;
        ImageButton btnPlay, btnDelete;

        RecordingViewHolder(View itemView) {
            super(itemView);
            textFileName = itemView.findViewById(R.id.textFileName);
            btnPlay = itemView.findViewById(R.id.btnPlay);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}

