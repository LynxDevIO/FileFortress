package controller;

import model.EncryptedContainer;
import model.User;
import model.UserManager;
import view.FrameManager;
import view.FileManager;

import javax.crypto.SecretKey;
import javax.swing.*;
import java.io.File;
import java.nio.file.Path;

public class ContainerManager {
    private EncryptedContainer container;
    private final SecretKey masterKey;
    private final UserManager userManager;
    private final Path tempDir;
    private final JProgressBar progressBar;
    private final FrameManager frameManager;
    private final FileManager fileManager;

    public ContainerManager(SecretKey masterKey, UserManager userManager, Path tempDir, JProgressBar progressBar, FrameManager frameManager, FileManager fileManager) {
        this.masterKey = masterKey;
        this.userManager = userManager;
        this.tempDir = tempDir;
        this.progressBar = progressBar;
        this.frameManager = frameManager;
        this.fileManager = fileManager;
    }

    public void createContainer(JFrame frame, User currentUser) {
        JFileChooser directoryChooser = new JFileChooser();
        directoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = directoryChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedDir = directoryChooser.getSelectedFile();
            JFileChooser saveChooser = new JFileChooser();
            saveChooser.setDialogTitle("Save container");
            result = saveChooser.showSaveDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                File saveFile = saveChooser.getSelectedFile();
                new Thread(() -> {
                    try {
                        frameManager.setButtonsEnabled(false);
                        progressBar.setValue(0);
                        progressBar.setString(null);
                        container = new EncryptedContainer(currentUser.getEncryptionKey());
                        container.saveContainerWithProgress(saveFile, selectedDir.toPath(), progressBar);
                        JOptionPane.showMessageDialog(frame, "Container created: " + saveFile.getAbsolutePath());
                        importContainer(frame, currentUser, saveFile);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        frameManager.setButtonsEnabled(true);
                    }
                }).start();
            }
        }
    }

    public void importContainer(JFrame frame, User currentUser) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Load Container");
        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            importContainer(frame, currentUser, file);
        }
    }

    public void importContainer(JFrame frame, User currentUser, File file) {
        new Thread(() -> {
            try {
                frameManager.setButtonsEnabled(false);
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(0);
                    progressBar.setString("COMPLETED");
                });
                container = new EncryptedContainer(currentUser.getEncryptionKey());
                container.loadContainerWithProgress(file, tempDir, progressBar);
                SwingUtilities.invokeLater(() -> fileManager.loadDirectory(tempDir, frameManager.getRoot()));
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                frameManager.setButtonsEnabled(true);
            }
        }).start();
    }

    public EncryptedContainer getContainer() {
        return container;
    }

    public void setContainer(EncryptedContainer container) {
        this.container = container;
    }
}