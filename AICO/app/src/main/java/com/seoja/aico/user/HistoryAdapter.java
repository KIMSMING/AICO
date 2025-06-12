package com.seoja.aico.user;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.seoja.aico.R;
import com.seoja.aico.gpt.HistoryItem;

import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private List<HistoryItem> historyList;

    public HistoryAdapter(List<HistoryItem> historyList) {
        this.historyList = historyList;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        HistoryItem item = historyList.get(position);
        holder.textQuestion.setText("Q. " + item.getQuestion());
        holder.textAnswer.setText("A. " + item.getAnswer());
        holder.textFeedback.setText("피드백: " + item.getFeedback());
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    public static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView textQuestion;
        TextView textAnswer;
        TextView textFeedback;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            textQuestion = itemView.findViewById(R.id.textQuestion);
            textAnswer = itemView.findViewById(R.id.textAnswer);
            textFeedback = itemView.findViewById(R.id.textFeedback);
        }
    }
}
