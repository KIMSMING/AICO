package com.seoja.aico.gpt;

public class NextInterviewRequest {
    private String user_id;
    private String session_id;
    private String last_question;
    private String user_answer;

    public NextInterviewRequest(String user_id, String session_id, String last_question, String user_answer) {
        this.user_id = user_id;
        this.session_id = session_id;
        this.last_question = last_question;
        this.user_answer = user_answer;
    }
}
