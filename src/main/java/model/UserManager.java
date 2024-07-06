package model;

import javax.crypto.*;
import java.io.*;
import java.security.*;
import java.util.*;

public class UserManager {
    private Map<String, User> users = new HashMap<>();
    private static final String ALGORITHM = "AES";
    private final SecretKey masterKey;

    public UserManager(SecretKey masterKey, File lastKeyPath) {
        this.masterKey = masterKey;
        loadUsers();
    }

    public void addUser(String username, String password) throws NoSuchAlgorithmException {
        if (users.containsKey(username)) {
            throw new IllegalArgumentException("Usuário já existe!");
        }
        SecretKey userKey = KeyManager.generateKey();
        User user = new User(username, password, userKey);
        users.put(username, user);
        saveUsers();
    }

    public User authenticate(String username, String password) {
        User user = users.get(username);
        if (user != null && user.checkPassword(password)) {
            return user;
        }
        return null;
    }

    private void loadUsers() {
        File file = getUsersFile();
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new CipherInputStream(new FileInputStream(file), getCipher(Cipher.DECRYPT_MODE)))) {
                users = (Map<String, User>) ois.readObject();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void saveUsers() {
        File file = getUsersFile();
        try (ObjectOutputStream oos = new ObjectOutputStream(new CipherOutputStream(new FileOutputStream(file), getCipher(Cipher.ENCRYPT_MODE)))) {
            oos.writeObject(users);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Cipher getCipher(int mode) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(mode, masterKey);
        return cipher;
    }

    private File getUsersFile() {
        // Get the user's home directory
        String userHome = System.getProperty("user.home");

        // Construct the path to the Documents folder
        File documentsFolder = new File(userHome, "Documents");

        // Construct the path to the CFM folder inside Documents
        File cfmFolder = new File(documentsFolder, "CFM");

        // Create the CFM directory if it doesn't exist
        if (!cfmFolder.exists()) {
            cfmFolder.mkdirs();
        }

        // Return the file object for users.enc inside the CFM folder
        return new File(cfmFolder, "users.enc");
    }
}