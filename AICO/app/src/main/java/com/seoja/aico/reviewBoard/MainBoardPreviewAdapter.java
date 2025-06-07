package com.seoja.aico.reviewBoard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.seoja.aico.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainBoardPreviewAdapter extends RecyclerView.Adapter<MainBoardPreviewAdapter.PreviewViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(BoardPost post);
    }

    private final List<BoardPost> postList;
    private final OnItemClickListener listener;
    private final String currentUserId; // 현재 로그인한 사용자 UID

    public MainBoardPreviewAdapter(List<BoardPost> postList, String currentUserId, OnItemClickListener listener) {
        this.postList = postList;
        this.currentUserId = currentUserId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PreviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_board_preview, parent, false);
        // 카드의 폭을 항상 화면의 1/3로 맞춤
        int width = parent.getMeasuredWidth() / 3;
        ViewGroup.LayoutParams params = v.getLayoutParams();
        params.width = width;
        v.setLayoutParams(params);
        return new PreviewViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PreviewViewHolder holder, int position) {
        BoardPost post = postList.get(position);
        holder.tvTitle.setText(post.title);
        holder.tvWriter.setText(post.nickname);
        holder.tvDate.setText(formatDate(post.createdAt));
        holder.tvLikes.setText(String.valueOf(post.likes));

        // 좋아요(하트) 상태만 표시 (클릭 기능 없음)
        boolean isLiked = post.likedUsers != null && currentUserId != null && post.likedUsers.containsKey(currentUserId);
        holder.btnLike.setImageResource(isLiked ? R.drawable.ic_heart_fill : R.drawable.ic_heart);

        // 대표 이미지
        if (post.imageUrl != null && !post.imageUrl.isEmpty()) {
            holder.ivPreview.setVisibility(View.VISIBLE);
            Glide.with(holder.itemView.getContext()).load(post.imageUrl).into(holder.ivPreview);
        } else {
            holder.ivPreview.setImageDrawable(null);
            holder.ivPreview.setVisibility(View.INVISIBLE);
        }

        // 카드 전체 클릭 시 상세 이동
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(post);
        });

        // 좋아요 버튼 클릭 리스너는 필요 없으므로 생략
    }

    @Override
    public int getItemCount() {
        return postList.size();
    }

    static class PreviewViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPreview;
        TextView tvTitle, tvWriter, tvDate, tvLikes;
        ImageButton btnLike;
        PreviewViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPreview = itemView.findViewById(R.id.ivPreview);
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
