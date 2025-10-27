package com.minildap.models;

public class User {
    private String username;
    private String passwordHash; // SSHA
    private String email;
    private String fullName;
    private String role; // admin, developer, user

    public User(String username, String passwordHash, String email, String fullName, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }

    public void setEmail(String email) { this.email = email; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setRole(String role) { this.role = role; }

    // CSV persistence helpers (escape commas minimally)
    public String toCsv() {
        return username + "," + passwordHash + "," + (email==null?"":email) + "," + (fullName==null?"":fullName) + "," + (role==null?"":role);
    }

    public static User fromCsv(String line) {
        String[] p = line.split(",", -1);
        if (p.length < 5) return null;
        return new User(p[0], p[1], p[2], p[3], p[4]);
    }

    public String toJsonPublic() {
        return "{\"username\":\""+escape(username)+"\",\"email\":\""+escape(nullToEmpty(email))+"\",\"fullName\":\""+escape(nullToEmpty(fullName))+"\",\"role\":\""+escape(nullToEmpty(role))+"\"}";
    }

    private static String escape(String s){ return s.replace("\\","\\\\").replace("\"","\\\"");}
    private static String nullToEmpty(String s){return s==null?"":s;}
}
