package com.seoja.aico.gpt;

import com.google.gson.annotations.SerializedName;

public class HistoryItem {
    private String id;         //Firebase 고유 키

    @SerializedName("user_id")
    private String user_id;

    @SerializedName("question")
    private String question;

    @SerializedName("answer")
    private String answer;

    @SerializedName("feedback")
    private String feedback;

    public HistoryItem() {} //Firebase 용 기본 생성자

    public HistoryItem(String id, String user_id, String question, String answer, String feedback) {
        this.id = id;
        this.user_id = user_id;
        this.question = question;
        this.answer = answer;
        this.feedback = feedback;
    }
    public String getId() {
        return id;
    }
    public String getUser_id() {
        return user_id;
    }
    public String getQuestion() {
        return question;
    }
    public String getAnswer() {
        return answer;
    }
    public String getFeedback() {
        return feedback;
    }

    public void setId(String id) {
        this.id = id;
    }
    public void setUser_id(String user_id) {
        this.user_id = user_id;
    }
}
