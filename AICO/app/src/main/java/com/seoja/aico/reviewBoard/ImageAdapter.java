package com.seoja.aico.reviewBoard;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.seoja.aico.R;

import java.util.List;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {

    public interface OnDeleteClickListener {
        void onDelete(int position);
    }

    private final List<Uri> imageList;
    private final OnDeleteClickListener deleteClickListener;

    public ImageAdapter(List<Uri> imageList, OnDeleteClickListener listener) {
        this.imageList = imageList;
        this.deleteClickListener = listener;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image_with_delete, parent, false);
        return new ImageViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        Uri uri = imageList.get(position);

        // Glide로 이미지 로딩
        Glide.with(holder.imageView.getContext())
                .load(uri)
                .centerCrop()
                .into(holder.imageView);

        // 삭제 버튼 클릭 리스너
        holder.btnDelete.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                imageList.remove(pos);
                notifyItemRemoved(pos);
                notifyItemRangeChanged(pos, imageList.size());
                if (deleteClickListener != null) {
                    deleteClickListener.onDelete(pos);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return imageList.size();
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        ImageButton btnDelete;
        ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imgPreview);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
