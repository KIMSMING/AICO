package com.seoja.aico.reviewBoard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.seoja.aico.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BoardListAdapter extends RecyclerView.Adapter<BoardListAdapter.BoardViewHolder> {

    private final List<BoardPost> postList;
    private final String currentUserId; // 현재 로그인한 사용자

    public interface OnItemClickListener {
        void onItemClick(BoardPost post);
    }

    private OnItemClickListener listener;

    public BoardListAdapter(List<BoardPost> postList, String currentUserId) {
        this.postList = postList;
        this.currentUserId = currentUserId;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public BoardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_board, parent, false);
        return new BoardViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull BoardViewHolder holder, int position) {
        BoardPost post = postList.get(position);
        holder.tvTitle.setText(post.title);
        holder.tvWriter.setText(post.nickname);
        holder.tvDate.setText(formatDate(post.createdAt));
        holder.tvLikes.setText(String.valueOf(post.likes));

        // 좋아요(하트) 상태만 표시
        boolean isLiked = false;
        if (post.likedUsers != null && currentUserId != null) {
            isLiked = post.likedUsers.containsKey(currentUserId);
        }
        holder.btnLike.setImageResource(isLiked ? R.drawable.ic_heart_fill : R.drawable.ic_heart);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(post);
        });
    }

    @Override
    public int getItemCount() {
        return postList.size();
    }

    static class BoardViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvWriter, tvDate, tvLikes;
        ImageButton btnLike;
        BoardViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvWriter = itemView.findViewById(R.id.tvWriter);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvLikes = itemView.findViewById(R.id.tvLikes);
            btnLike = itemView.findViewById(R.id.btnLike);
        }
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
