package com.seoja.aico.user.data.model;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Data class that captures user information for logged in users retrieved from LoginRepository
 */
public class LoggedInUser extends AppCompatActivity {

    private Integer uid;
    private String id;
    private String password;
    private String name;
    private String nickname;
    private Integer birth;
    private String email;
    private Integer tel;
    private String gender;
    private String address;
    private String profileImage;

    public LoggedInUser(Integer uid, String id, String password, String name, String nickname, Integer birth, String email, Integer tel, String gender, String address, String profileImage) {
        this.uid = uid;
        this.id = id;
        this.password = password;
        this.name = name;
        this.nickname = nickname;
        this.birth = birth;
        this.email = email;
        this.tel = tel;
        this.gender = gender;
        this.address = address;
        this.profileImage = profileImage;
    }

    public String getUid(String id) {
        Integer uid = findViewById(R.id.e);
        return uid;
    }

    public String getDisplayName() {
        return nickname;
    }
}