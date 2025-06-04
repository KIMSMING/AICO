package com.seoja.aico.gpt;

public class AskRequest {
    private String user_id;
    private String message;

    public AskRequest(String user_id, String message) {
        this.user_id = user_id;
        this.message = message;
    }
    // Gson이라는 애가 자동 처리 해줘서 Getter/Setter는 생략 가능하대요 ㄷㄷ;
}
