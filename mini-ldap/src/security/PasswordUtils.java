package com.minildap.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordUtils {

    // {SSHA} base64( SHA1(password + salt) + salt )
    public static String hashSSHA(String password) {
        try {
            byte[] salt = new byte[8];
            new SecureRandom().nextBytes(salt);

            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(password.getBytes("UTF-8"));
            sha1.update(salt);
            byte[] digest = sha1.digest();

            byte[] combo = new byte[digest.length + salt.length];
            System.arraycopy(digest, 0, combo, 0, digest.length);
            System.arraycopy(salt, 0, combo, digest.length, salt.length);

            return "{SSHA}" + Base64.getEncoder().encodeToString(combo);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean verifySSHA(String rawPassword, String stored) {
        try {
            if (stored == null || !stored.startsWith("{SSHA}")) return false;
            byte[] combo = Base64.getDecoder().decode(stored.substring(6));
            if (combo.length < 20) return false; // SHA1 length

            byte[] digest = new byte[20];
            System.arraycopy(combo, 0, digest, 0, 20);
            byte[] salt = new byte[combo.length - 20];
            System.arraycopy(combo, 20, salt, 0, salt.length);

            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(rawPassword.getBytes("UTF-8"));
            sha1.update(salt);
            byte[] test = sha1.digest();

            if (test.length != digest.length) return false;
            for (int i=0;i<test.length;i++) if (test[i]!=digest[i]) return false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
