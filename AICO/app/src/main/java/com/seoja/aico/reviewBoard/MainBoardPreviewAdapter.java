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
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
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
    private final String currentUserId;

    public MainBoardPreviewAdapter(List<BoardPost> postList,
                                   String currentUserId,
                                   OnItemClickListener listener) {
        this.postList = postList;
        this.currentUserId = currentUserId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PreviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_board_preview, parent, false);

        // 화면 너비의 1/3 크기로 고정 (최소 높이 설정 추가)
        ViewGroup.LayoutParams params = v.getLayoutParams();
        params.width = parent.getMeasuredWidth() / 3;
        params.height = (int) (params.width * 1.2); // 1:1.2 비율
        v.setLayoutParams(params);

        return new PreviewViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PreviewViewHolder holder, int position) {
        BoardPost post = postList.get(position);

        // 기본 정보 설정
        holder.tvTitle.setText(post.title != null ? post.title : "");
        holder.tvWriter.setText(post.nickname != null ? post.nickname : "익명");
        holder.tvDate.setText(formatDate(post.createdAt));
        holder.tvLikes.setText(String.valueOf(post.likes));

        // 좋아요 상태 표시 (비활성화)
        boolean isLiked = post.likedUsers != null
                && currentUserId != null
                && post.likedUsers.containsKey(currentUserId);
        holder.btnLike.setImageResource(isLiked ?
                R.drawable.ic_heart_fill : R.drawable.ic_heart);
        holder.btnLike.setClickable(false); // 프리뷰에서는 클릭 불가

        // 이미지 로딩 (Oracle Cloud 최적화)
        if (post.imageUrl != null && !post.imageUrl.isEmpty()) {
            holder.ivPreview.setVisibility(View.VISIBLE);
            Glide.with(holder.itemView.getContext())
                    .load(post.imageUrl)
                    .transition(DrawableTransitionOptions.withCrossFade(300))
                    .placeholder(R.drawable.ic_placeholder_img)
                    .error(R.drawable.ic_error_img)
                    .centerCrop()
                    .into(holder.ivPreview);
        } else {
            holder.ivPreview.setVisibility(View.GONE);
        }

        // 클릭 이벤트
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                listener.onItemClick(postList.get(pos));
            }
        });
    }

    @Override
    public int getItemCount() { return postList.size(); }

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
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.KOREAN);
        return sdf.format(new Date(timestamp));
    }
}
