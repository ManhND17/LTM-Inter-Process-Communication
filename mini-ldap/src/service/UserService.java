package com.minildap.service;

import com.minildap.models.User;
import com.minildap.security.PasswordUtils;
import com.minildap.store.UserStore;

import java.util.List;

public class UserService {
    private final UserStore userStore;

    public UserService(UserStore userStore) { this.userStore = userStore; }

    public void createUser(String username, String rawPassword, String email, String fullName, String role) throws Exception {
        if (userStore.findByUsername(username) != null) throw new Exception("User exists");
        String hash = PasswordUtils.hashSSHA(rawPassword);
        userStore.addUser(new User(username, hash, email, fullName, role));
        userStore.save();
    }

    public User readUser(String username) throws Exception {
        User u = userStore.findByUsername(username);
        if (u == null) throw new Exception("User not found");
        return u;
    }

    public void updateUser(String username, String email, String fullName) throws Exception {
        User u = userStore.findByUsername(username);
        if (u == null) throw new Exception("User not found");
        u.setEmail(email);
        u.setFullName(fullName);
        userStore.save();
    }

    public void deleteUser(String username) throws Exception {
        if (userStore.findByUsername(username) == null) throw new Exception("User not found");
        userStore.removeUser(username);
        userStore.save();
    }

    public List<User> listAllUsers() {
        return userStore.getAllUsers();
    }
}
