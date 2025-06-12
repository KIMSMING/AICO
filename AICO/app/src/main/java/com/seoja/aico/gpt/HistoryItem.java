package com.seoja.aico.gpt;

public class HistoryItem {
    private String user_id;
    private String question;
    private String answer;
    private String feedback;

    public HistoryItem() {} //Firebase 용 기본 생성자

    public HistoryItem(String user_id, String question, String answer, String feedback) {
        this.user_id = user_id;
        this.question = question;
        this.answer = answer;
        this.feedback = feedback;
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
}
