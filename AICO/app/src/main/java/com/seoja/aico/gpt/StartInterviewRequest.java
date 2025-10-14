package com.seoja.aico.gpt;

public class StartInterviewRequest {
    private String user_id;
    private String role;
    private String seed_question;

    public StartInterviewRequest(String user_id, String role, String seed_question) {
        this.user_id = user_id;
        this.role = role;
        this.seed_question = seed_question;
    }
}
