package model;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class EncryptedContainer {
    private static final String ALGORITHM = "AES";
    private final SecretKey secretKey;

    public EncryptedContainer(SecretKey secretKey) {
        this.secretKey = secretKey;
    }

    public void saveContainerWithProgress(File file, Path directory, JProgressBar progressBar) throws Exception {
        List<Path> fileList = Files.walk(directory)
                .filter(path -> !Files.isDirectory(path))
                .toList();

        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             ZipOutputStream zipOut = new ZipOutputStream(byteStream)) {
            int totalFiles = fileList.size();
            int processedFiles = 0;

            for (Path filePath : fileList) {
                ZipEntry zipEntry = new ZipEntry(directory.relativize(filePath).toString());
                zipOut.putNextEntry(zipEntry);

                try (InputStream in = Files.newInputStream(filePath)) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        zipOut.write(buffer, 0, len);
                    }
                }

                zipOut.closeEntry();
                processedFiles++;
                final int progress = (int) (((double) processedFiles / totalFiles) * 100);
                SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
            }

            zipOut.finish();
            byte[] encryptedData = encrypt(byteStream.toByteArray());
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(encryptedData);
            }
        }
    }

    public void loadContainerWithProgress(File file, Path outputDirectory, JProgressBar progressBar) throws Exception {
        byte[] encryptedData = Files.readAllBytes(file.toPath());
        byte[] decryptedData = decrypt(encryptedData);

        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(decryptedData);
             ZipInputStream zipIn = new ZipInputStream(byteStream)) {
            long totalBytes = decryptedData.length;
            long processedBytes = 0;

            ZipEntry entry = zipIn.getNextEntry();
            while (entry != null) {
                Path filePath = outputDirectory.resolve(entry.getName());
                if (!entry.isDirectory()) {
                    Files.createDirectories(filePath.getParent());
                    try (BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(filePath))) {
                        byte[] bytesIn = new byte[1024];
                        int read;
                        while ((read = zipIn.read(bytesIn)) != -1) {
                            bos.write(bytesIn, 0, read);
                            processedBytes += read;
                            final int progress = (int) (((double) processedBytes / totalBytes) * 100);
                            SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
                        }
                    }
                } else {
                    Files.createDirectories(filePath);
                }
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
        }
    }

    private byte[] encrypt(byte[] data) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher.doFinal(data);
    }

    private byte[] decrypt(byte[] encryptedData) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        return cipher.doFinal(encryptedData);
    }
}