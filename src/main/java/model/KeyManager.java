package model;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.*;
import java.util.Map;

public class KeyManager {
    private static final String ALGORITHM = "AES";
    private static final int KEY_SIZE = 256;

    public static SecretKey generateKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(KEY_SIZE);
        return keyGen.generateKey();
    }

    public static void saveKeyAndUsers(SecretKey key, String password, File file, Map<String, String> users) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(users);
        oos.close();

        byte[] usersData = baos.toByteArray();
        byte[] encryptedUsersData = cipher.doFinal(usersData);

        try (FileOutputStream fos = new FileOutputStream(file);
             ObjectOutputStream oos2 = new ObjectOutputStream(fos)) {
            oos2.writeObject(key.getEncoded());
            oos2.writeObject(encryptedUsersData);
        }
    }

    public static SecretKey loadKey(String password, File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file);
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            byte[] keyData = (byte[]) ois.readObject();
            return new SecretKeySpec(keyData, ALGORITHM);
        }
    }

    public static Map<String, String> loadUsers(SecretKey key, File file) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);

        try (FileInputStream fis = new FileInputStream(file);
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            ois.readObject(); // Skip the key data
            byte[] encryptedUsersData = (byte[]) ois.readObject();

            byte[] usersData = cipher.doFinal(encryptedUsersData);
            ByteArrayInputStream bais = new ByteArrayInputStream(usersData);
            ObjectInputStream ois2 = new ObjectInputStream(bais);

            return (Map<String, String>) ois2.readObject();
        }
    }
}