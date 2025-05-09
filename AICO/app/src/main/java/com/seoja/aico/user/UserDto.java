package com.seoja.aico.user;

public class UserDto{
    private String uid;
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

    public UserDto() {}

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public Integer getBirth() { return birth; }
    public void setBirth(Integer birth) { this.birth = birth; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Integer getTel() { return tel; }
    public void setTel(Integer tel) { this.tel = tel; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getProfileImage() { return profileImage; }
    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }
}
