package com.seoja.aico.gpt;

import com.google.gson.annotations.SerializedName;

public class DeleteRequest {
    @SerializedName("user_id")
    private String user_id;

    @SerializedName("history_id")
    private String history_id;

    public DeleteRequest(String user_id, String history_id) {
        this.user_id = user_id;
        this.history_id = history_id;
    }

    public String getUser_id() {
        return user_id;
    }
    public String getHistory_id() {
        return history_id;
    }
}
