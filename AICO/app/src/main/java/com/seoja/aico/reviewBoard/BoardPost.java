package com.seoja.aico.reviewBoard;

import java.util.HashMap;
import java.util.Map;

public class BoardPost {
    public String postId;
    public String title;
    public String content;
    public String category;
    public String authorUid;
    public String authorName;
    public String nickname;
    public String company;
    public long createdAt;
    public String imageUrl;
    public int likes;
    public Map<String, Boolean> likedUsers;

    public BoardPost() {
        likedUsers = new HashMap<>();
    }

    public BoardPost(String postId, String title, String content, String category, String authorUid, String authorName, String company, String nickname, long createdAt, String imageUrl, int likes, Map<String, Boolean> likedUsers) {
        this.postId = postId;
        this.title = title;
        this.content = content;
        this.category = category;
        this.authorUid = authorUid;
        this.authorName = authorName;
        this.nickname = nickname;
        this.company = company;
        this.createdAt = createdAt;
        this.imageUrl = imageUrl;
        this.likes = likes;
        this.likedUsers = likedUsers != null ? likedUsers : new HashMap<>();
    }
}
