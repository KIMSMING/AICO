package com.seoja.aico.quest;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface SttApi {
    @Multipart
    @POST("api/stt/stt")
    Call<String> uploadAudio(@Part MultipartBody.Part file);
}
