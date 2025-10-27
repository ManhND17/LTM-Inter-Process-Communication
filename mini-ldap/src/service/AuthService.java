package com.minildap.service;

import com.minildap.models.User;
import com.minildap.security.PasswordUtils;
import com.minildap.store.UserStore;

public class AuthService {
    private final UserStore userStore;

    public AuthService(UserStore userStore) { this.userStore = userStore; }

    public User authenticate(String username, String password) throws Exception {
        User u = userStore.findByUsername(username);
        if (u == null) throw new Exception("User not found");
        if (!PasswordUtils.verifySSHA(password, u.getPasswordHash()))
            throw new Exception("Invalid password");
        return u;
    }
}
