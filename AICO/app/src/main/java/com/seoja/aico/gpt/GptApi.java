package com.seoja.aico.gpt;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface GptApi {
    @POST("/ask") // FastAPI의 POST 엔드포인트 경로
    Call<GptResponse> askGpt(@Body GptRequest request); // 요청 바디로 AskRequest 객체 전송

    @GET("/") // 루트 엔드포인트 - 서버 연결 테스트용
    Call<Object> testConnection();
}

