package com.seoja.aico.gpt;

import com.google.gson.annotations.SerializedName;

public class DeleteRequest {
    @SerializedName("user_id")
    public String user_id;
    @SerializedName("history_id")
    public String history_id;

    public DeleteRequest(String user_id, String history_id) {
        this.user_id = user_id;
        this.history_id = history_id;
    }
}
