package model;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.*;
import java.util.*;

public class UserManager {
    private Map<String, User> users = new HashMap<>();
    private static final String ALGORITHM = "AES";
    private final SecretKey masterKey;
    private final File masterKeyFile;

    public UserManager(SecretKey masterKey, File masterKeyFile) {
        this.masterKey = masterKey;
        this.masterKeyFile = masterKeyFile;
        loadUsers();
    }

    public UserManager(File masterKeyFile, String masterPassword) throws Exception {
        this.masterKeyFile = masterKeyFile;
        this.masterKey = KeyManager.loadKey(masterPassword, masterKeyFile);
        users = KeyManager.loadUsers(masterKey, masterKeyFile);
    }

    public void addUser(String username, String password) throws NoSuchAlgorithmException {
        if (users.containsKey(username)) {
            throw new IllegalArgumentException("User already exists!");
        }
        SecretKey userKey = KeyManager.generateKey();
        User user = new User(username, password, userKey);
        users.put(username, user);
        saveMasterKeyAndUsers();
    }

    public User authenticate(String username, String password) {
        User user = users.get(username);
        if (user != null && user.checkPassword(password)) {
            return user;
        }
        return null;
    }

    private void loadUsers() {
        try {
            users = KeyManager.loadUsers(masterKey, masterKeyFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveMasterKeyAndUsers() {
        try {
            KeyManager.saveKeyAndUsers(masterKey, masterKeyFile, users);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Cipher getCipher(int mode, String password) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(password != null ? password.getBytes() : masterKey.getEncoded(), ALGORITHM);
        cipher.init(mode, keySpec);
        return cipher;
    }
}