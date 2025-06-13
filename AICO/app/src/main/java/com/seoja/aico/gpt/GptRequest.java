package com.seoja.aico.gpt;

public class GptRequest {
    private String user_id;
    private String message;

    public GptRequest(String user_id, String message){
        this.user_id = user_id;
        this.message = message;
    }

    public String getUser_id() {
        return user_id;
    }

    public String getMessage() {
        return message;
    }
}
