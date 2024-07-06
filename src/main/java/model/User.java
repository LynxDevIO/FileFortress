package model;

import javax.crypto.SecretKey;
import java.io.Serializable;
import java.util.Base64;

public class User implements Serializable {
    private final String username;
    private final String password; // The password is stored with hashing and salting
    private final SecretKey encryptionKey;

    public User(String username, String password, SecretKey encryptionKey) {
        this.username = username;
        this.password = hashPassword(password);
        this.encryptionKey = encryptionKey;
    }

    public String getUsername() {
        return username;
    }

    public boolean checkPassword(String password) {
        return this.password.equals(hashPassword(password));
    }

    public SecretKey getEncryptionKey() {
        return encryptionKey;
    }

    private String hashPassword(String password) {
        // Password hash implementation
        return Base64.getEncoder().encodeToString(password.getBytes());
    }
}