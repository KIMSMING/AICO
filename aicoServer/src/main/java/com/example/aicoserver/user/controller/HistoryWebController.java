package com.example.aicoserver.user.controller;

import com.google.firebase.database.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Controller
public class HistoryWebController {

    @GetMapping("/history/{userId}/{historyId}")
    public String getHistory(@PathVariable String userId,
                             @PathVariable String historyId,
                             Model model) {

        if (userId == null || userId.isEmpty() || historyId == null || historyId.isEmpty()) {
            model.addAttribute("error", "Invalid IDs");
            return "history";
        }

        DatabaseReference databaseRef = FirebaseDatabase.getInstance().getReference();

        // userRef 경로 ../{userId}
        DatabaseReference userRef = databaseRef.child("users").child(userId);

        // 가져오려는 객체가 2개이므로 String -> DataSnapshot
        CompletableFuture<DataSnapshot> userFuture = new CompletableFuture<>();

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                userFuture.complete(snapshot);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                userFuture.completeExceptionally(new RuntimeException(error.getMessage()));
            }
        });

        String nickname;
        String photoUrl;

        try {
            // 스냅샷에 nickname과 photoUrl 분리
            DataSnapshot userSnapshot = userFuture.get(10, TimeUnit.SECONDS);

            if (userSnapshot.exists()) {
                nickname = userSnapshot.child("nickname").getValue(String.class);
                photoUrl = userSnapshot.child("photoUrl").getValue(String.class); // 6. photoUrl 값 추출

                if (nickname == null) {
                    nickname = "Unknown";
                }

            } else {
                // users/{userId} 노드가 없는 경우
                nickname = "Unknown";
                photoUrl = null;
            }

        } catch (TimeoutException te) {
            model.addAttribute("error", "Firebase timeout while fetching user data"); // 에러 메시지 업데이트
            return "history";
        } catch (Exception e) {
            model.addAttribute("error", "Firebase error while fetching user data: " + e.getMessage()); // 에러 메시지 업데이트
            return "history";
        }

        // history/{userId}/{historyId}
        DatabaseReference historyRef = databaseRef.child("history").child(userId).child(historyId);
        CompletableFuture<DataSnapshot> historyFuture = new CompletableFuture<>();

        historyRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                historyFuture.complete(snapshot);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                historyFuture.completeExceptionally(new RuntimeException(error.getMessage()));
            }
        });

        try {
            DataSnapshot snapshot = historyFuture.get(10, TimeUnit.SECONDS);

            if (snapshot.exists()) {
                model.addAttribute("photoUrl", photoUrl);
                model.addAttribute("nickname", nickname);
                model.addAttribute("question", snapshot.child("question").getValue(String.class));
                model.addAttribute("answer", snapshot.child("answer").getValue(String.class));
                model.addAttribute("feedback", snapshot.child("feedback").getValue(String.class));
            } else {
                model.addAttribute("error", "No history found");
            }
        } catch (TimeoutException te) {
            model.addAttribute("error", "Firebase timeout while fetching history");
        } catch (Exception e) {
            model.addAttribute("error", "Firebase error while fetching history: " + e.getMessage());
        }

        return "history";
    }
}