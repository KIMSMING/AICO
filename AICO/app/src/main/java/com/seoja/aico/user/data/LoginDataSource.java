package com.seoja.aico.user.data;

import com.seoja.aico.user.data.model.LoggedInUser;

import java.io.IOException;

/**
 * Class that handles authentication w/ login credentials and retrieves user information.
 */
public class LoginDataSource {

    public Result<LoggedInUser> login(String id, String password) {

        try {
            // TODO: handle loggedInUser authentication
            Integer uid = new LoggedInUser().getUid(id);
            LoggedInUser fakeUser =
                    new LoggedInUser(
                            //java.util.UUID.randomUUID().toString(),
                            uid,
                            id,
                            password,
                            name,
                            nickname,
                            birth,
                            email,
                            tel,
                            gender,
                            address,
                            profileImage
                            );
            return new Result.Success<>(fakeUser);
        } catch (Exception e) {
            return new Result.Error(new IOException("Error logging in", e));
        }
    }

    public void logout() {
        // TODO: revoke authentication
    }
}