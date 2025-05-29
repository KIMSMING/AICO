package com.example.aicoserver.user;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String uid;
    private String email;
    private String name;
    private String nickname;
    private String birth;
    private String gender;
    private String address;
    private String phone;
    private String photoUrl;
    private String provider;    // "kakao" or "naver"
    private String providerId;
}
