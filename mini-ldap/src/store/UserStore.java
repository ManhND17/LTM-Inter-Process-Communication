package com.minildap.store;

import com.minildap.models.User;

import java.io.*;
import java.util.*;

public class UserStore {
    private final Map<String, User> byUsername = new LinkedHashMap<>();
    private final File file;

    public UserStore(File file) {
        this.file = file;
    }

    public synchronized void load() throws IOException {
        byUsername.clear();
        if (!file.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while((line=br.readLine())!=null){
                if (line.trim().isEmpty()) continue;
                User u = User.fromCsv(line);
                if (u != null) byUsername.put(u.getUsername(), u);
            }
        }
    }

    public synchronized void save() throws IOException {
        file.getParentFile().mkdirs();
        try (PrintWriter pw = new PrintWriter(new FileWriter(file, false))) {
            for (User u : byUsername.values()) {
                pw.println(u.toCsv());
            }
        }
    }

    public synchronized User findByUsername(String username) {
        return byUsername.get(username);
    }

    public synchronized List<User> getAllUsers() {
        return new ArrayList<>(byUsername.values());
    }

    public synchronized void addUser(User u) {
        byUsername.put(u.getUsername(), u);
    }

    public synchronized void removeUser(String username) {
        byUsername.remove(username);
    }
}
