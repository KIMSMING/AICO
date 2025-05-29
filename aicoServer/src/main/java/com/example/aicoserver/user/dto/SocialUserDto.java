package com.example.aicoserver.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SocialUserDto {
    private String providerId;
    private String email;
    private String name;
    private String nickname;
    private String birth;
    private String gender;
    private String address;
    private String phone;
    private String photoUrl;
}
