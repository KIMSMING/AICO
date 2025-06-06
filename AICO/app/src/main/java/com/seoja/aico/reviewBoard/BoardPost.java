package com.seoja.aico.reviewBoard;

import java.util.HashMap;
import java.util.Map;

public class BoardPost {
    // 기본 정보
    public String postId;
    public String title;
    public String content;

    // 작성자 정보
    public String authorUid;
    public String authorName;
    public String nickname;

    // 메타데이터
    public long createdAt;
    public int likes;

    // 이미지 정보 (Oracle Cloud URL)
    public String imageUrl;

    // 좋아요 사용자 목록
    public Map<String, Boolean> likedUsers;

    // Firebase 역직렬화를 위한 기본 생성자
    public BoardPost() {
        this.likedUsers = new HashMap<>();
    }

    // 모든 필드 초기화 생성자
    public BoardPost(String postId, String title, String content,
                     String authorUid, String authorName, String nickname,
                     long createdAt, String imageUrl, int likes,
                     Map<String, Boolean> likedUsers) {
        this.postId = postId;
        this.title = title;
        this.content = content;
        this.authorUid = authorUid;
        this.authorName = authorName;
        this.nickname = nickname;
        this.createdAt = createdAt;
        this.imageUrl = imageUrl != null ? imageUrl : "";  // null 방지
        this.likes = likes;
        this.likedUsers = likedUsers != null ? likedUsers : new HashMap<>();
    }

    // 좋아요 상태 확인 메서드
    public boolean isLikedByUser(String userId) {
        return likedUsers != null && likedUsers.containsKey(userId);
    }

    // 좋아요 수 업데이트 메서드
    public void updateLike(String userId) {
        if (likedUsers == null) {
            likedUsers = new HashMap<>();
        }

        if (likedUsers.containsKey(userId)) {
            likedUsers.remove(userId);
            likes = Math.max(likes - 1, 0);
        } else {
            likedUsers.put(userId, true);
            likes += 1;
        }
    }

    @Override
    public String toString() {
        return "BoardPost{" +
                "postId='" + postId + '\'' +
                ", title='" + title + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", likes=" + likes +
                '}';
    }
}
