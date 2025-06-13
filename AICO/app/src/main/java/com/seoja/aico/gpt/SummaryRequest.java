package com.seoja.aico.gpt;

public class SummaryRequest {
    public String content;
    public String role = "내용";

    public SummaryRequest(String content) {
        this.content = content;
    }
}
