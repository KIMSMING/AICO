package com.seoja.aico.gpt;

public class HistoryItem {
    private String user_id;
    private String question;
    private String answer;
    private String feedback;

    public HistoryItem(String user_id, String question, String answer, String feedback) {
        this.user_id = user_id;
        this.question = question;
        this.answer = answer;
        this.feedback = feedback;
    }
}
