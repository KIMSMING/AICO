package com.seoja.aico.user;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.seoja.aico.R;
import com.seoja.aico.gpt.DeleteRequest;
import com.seoja.aico.gpt.GptApi;
import com.seoja.aico.gpt.HistoryItem;

import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private List<HistoryItem> historyList;
    private GptApi api;

    public HistoryAdapter(List<HistoryItem> historyList) {
        this.historyList = historyList;

        // Retrofit 설정
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://10.0.2.2:8000/") // 주소는 환경에 맞게 수정
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(client)
                .build();

        api = retrofit.create(GptApi.class);
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
        String feedback = item.getFeedback();

        holder.textQuestion.setText("Q. " + item.getQuestion());
        holder.textAnswer.setText("A. " + item.getAnswer());

        if (!feedback.startsWith("피드백:")) {
            feedback = "피드백: " + feedback;
        }
        holder.textFeedback.setText(feedback);

        //삭제 버튼 클릭 리스너
        holder.btnDelete.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                HistoryItem itemToDelete = historyList.get(currentPos);
                String userId = itemToDelete.getUser_id();
                String historyId = itemToDelete.getId(); //Firebase key

//                DatabaseReference ref = FirebaseDatabase.getInstance()
//                        .getReference("history")
//                        .child(userId)
//                        .child(historyId);
//
//                ref.removeValue().addOnSuccessListener(unused -> {
//                    //리스트에도 제거하고 리사이클러뷰 갱신
//                    historyList.remove(currentPos);
//                    notifyItemRemoved(currentPos);
//                }).addOnFailureListener(e -> {
//                    Log.e("DELETE_FAIL", "히스토리 삭제 실패: " + e.getMessage());
//                });

            DeleteRequest deleteRequest = new DeleteRequest(userId, historyId);
//            Log.d("DELETE_BODY", new Gson().toJson(deleteRequest));
            Log.d("DELETE_BODY", "보낼 user_id: " + userId + ", history_id: " + historyId);

            api.deleteHistory(new DeleteRequest(userId, historyId)).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (response.isSuccessful()) {
                        historyList.remove(currentPos);
                        notifyItemRemoved(currentPos);
                        Toast.makeText(holder.itemView.getContext(), "삭제 완료", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(holder.itemView.getContext(), "삭제 실패: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    Toast.makeText(holder.itemView.getContext(), "서버 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
            }
        });
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    public static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView textQuestion;
        TextView textAnswer;
        TextView textFeedback;
        Button btnDelete;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            textQuestion = itemView.findViewById(R.id.textQuestion);
            textAnswer = itemView.findViewById(R.id.textAnswer);
            textFeedback = itemView.findViewById(R.id.textFeedback);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
