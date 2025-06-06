package com.seoja.aico;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

public interface ApiService {
    @Multipart
    @POST("api/images/upload")
    Call<ResponseBody> uploadImage(@Part MultipartBody.Part file);

    @DELETE("api/images/delete")
    Call<Void> deleteImage(@Query("url") String imageUrl);
}
