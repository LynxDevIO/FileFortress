package view;

import config.ConfigManager;
import controller.ContainerManager;
import controller.ContextMenuManager;
import model.EncryptedContainer;
import model.KeyManager;
import model.User;
import model.UserManager;

import javax.crypto.SecretKey;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class FileManager {

    private final JFrame frame;
    private final JTree tree;
    private EncryptedContainer container;
    private final DefaultMutableTreeNode root;
    private Path tempDir;
    private UserManager userManager;
    private User currentUser;
    private final JProgressBar progressBar;
    private SecretKey masterKey;
    private final Map<String, String> config;
    private final ContextMenuManager contextMenuManager;
    private ContainerManager containerManager;
    private JButton createButton;
    private JButton importButton;
    private JButton importFile;
    private JButton exitCFM;

    public FileManager() {
        frame = new JFrame("FileFortress");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Prevent default close operation
        frame.setSize(600, 400);

        // Center the frame on the screen
        frame.setLocationRelativeTo(null);

        // Add window listener to handle closing
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                new Thread(FileManager.this::performExitTasksWithProgress).start();
            }
        });

        // Load config
        config = ConfigManager.loadConfig();

        // Check if it's the first run and show the tutorial window if needed
        if (ConfigManager.isFirstRun()) {
            showTutorialWindow();
            ConfigManager.setTutorialShown();
        }

        // Initial config to load master key
        if (!showKeyFileDialog()) {
            System.exit(0);
        }

        // UserManager config with a master key
        String lastKeyPath = config.get("lastKeyPath");
        if (lastKeyPath != null) {
            try {
                userManager = new UserManager(masterKey, new File(lastKeyPath));
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(frame, "Error while loading users: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
            }
        }

        // Show login screen and get master key
        if (!showLoginDialog()) {
            System.exit(0);
        }

        // Initialize tempDir once
        try {
            tempDir = Files.createTempDirectory("extracted");
            System.out.println("Temporary Directory created: " + tempDir.toString());
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Error while creating temporary directory: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }

        // Directory tree creation
        root = new DefaultMutableTreeNode("Root");
        tree = new JTree(root);
        DefaultTreeModel treeModel = (DefaultTreeModel) tree.getModel();
        JScrollPane treeScroll = new JScrollPane(tree);
        frame.add(treeScroll);

        // Initialize progress bar
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        frame.add(progressBar, BorderLayout.NORTH);

        // ContextMenuManager initialization
        contextMenuManager = new ContextMenuManager(tree, tempDir, treeModel, this, progressBar);

        // Control buttons
        JPanel panel = new JPanel();
        JButton createButton = new JButton("New Container");
        JButton importButton = new JButton("Open Container");
        JButton importFile = new JButton("Import File");
        JButton exitCFM = new JButton("Exit");

        panel.add(createButton);
        panel.add(importButton);
        panel.add(importFile);
        panel.add(exitCFM);
        frame.add(panel, BorderLayout.SOUTH);

        // Button actions
        createButton.addActionListener(_ -> createContainer());
        importButton.addActionListener(_ -> importContainer());
        importFile.addActionListener(_ -> importFilesOrDirectories());
        exitCFM.addActionListener(_ -> exitCFM());

        frame.setVisible(true);
    }

    private void showTutorialWindow() {
        TutorialWindow tutorialWindow = new TutorialWindow(frame);
        tutorialWindow.setVisible(true);
    }

    void importFilesOrDirectories() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Files or Directories to Import");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fileChooser.setMultiSelectionEnabled(true);

        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            new Thread(() -> {
                try {
                    contextMenuManager.resetProgressBar();
                    for (File file : selectedFiles) {
                        Path targetPath = tempDir.resolve(file.getName());
                        if (file.isDirectory()) {
                            copyDirectory(file.toPath(), targetPath);
                        } else {
                            Files.copy(file.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    SwingUtilities.invokeLater(() -> loadDirectory(tempDir, root));
                    saveCurrentContainerWithProgress(); // Save the container immediately after importing
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Files/Directories imported and container saved successfully."));
                } catch (IOException e) {
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Error while importing files/directories: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
                } finally {
                    contextMenuManager.updateProgressBarCompleted();
                    refreshTree();
                }
            }).start();
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path targetPath = target.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    if (!Files.exists(targetPath)) {
                        Files.createDirectory(targetPath);
                    }
                } else {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private boolean showKeyFileDialog() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select or Create Master Key File");

        // Set a custom file filter to show only .3f files
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".3f");
            }

            @Override
            public String getDescription() {
                return "Master Key Files (*.3f)";
            }
        });

        int result = fileChooser.showSaveDialog(frame);
        File masterKeyFile; // Null

        if (result == JFileChooser.APPROVE_OPTION) {
            masterKeyFile = fileChooser.getSelectedFile();
            if (!masterKeyFile.getName().endsWith(".3f")) {
                masterKeyFile = new File(masterKeyFile.getAbsolutePath() + ".3f");
            }
        } else {
            return false;
        }

        if (masterKeyFile.exists()) {
            JPasswordField passwordField = new JPasswordField();
            int option = JOptionPane.showConfirmDialog(frame, passwordField, "Type master password:", JOptionPane.OK_CANCEL_OPTION);
            if (option == JOptionPane.OK_OPTION) {
                String masterPassword = new String(passwordField.getPassword());
                try {
                    masterKey = KeyManager.loadKey(masterPassword, masterKeyFile);
                    userManager = new UserManager(masterKeyFile, masterPassword);
                    config.put("lastKeyPath", masterKeyFile.getAbsolutePath()); // Save the key path
                    ConfigManager.saveConfig(config);
                    return true;
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(frame, "Error while loading master key: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                return false;
            }
        } else {
            JPasswordField passwordField = new JPasswordField();
            int option = JOptionPane.showConfirmDialog(frame, passwordField, "Create a master password:", JOptionPane.OK_CANCEL_OPTION);
            if (option == JOptionPane.OK_OPTION) {
                String masterPassword = new String(passwordField.getPassword());
                try {
                    masterKey = KeyManager.generateKey();
                    KeyManager.saveKeyAndUsers(masterKey, masterKeyFile, new HashMap<>());
                    userManager = new UserManager(masterKey, masterKeyFile);
                    config.put("lastKeyPath", masterKeyFile.getAbsolutePath()); // Save the key path
                    ConfigManager.saveConfig(config);
                    return true;
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(frame, "Error while creating master key: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        return false;
    }

    private boolean showLoginDialog() {
        JDialog loginDialog = new JDialog(frame, "Login", true);
        loginDialog.setSize(300, 300);
        loginDialog.setLayout(new BorderLayout());

        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField usernameField = new JTextField();
        usernameField.setPreferredSize(new Dimension(200, 25)); // Set preferred size

        JPasswordField passwordField = new JPasswordField();
        passwordField.setPreferredSize(new Dimension(200, 25)); // Set preferred size

        JButton loginButton = new JButton("Login");
        JButton registerButton = new JButton("Register");

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(new JLabel("User:"), gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        panel.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        panel.add(new JLabel("Password:"), gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        panel.add(passwordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        panel.add(loginButton, gbc);

        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        panel.add(registerButton, gbc);

        loginDialog.add(panel, BorderLayout.CENTER);
        loginDialog.setLocationRelativeTo(frame);

        final boolean[] loggedIn = {false};

        loginButton.addActionListener(_ -> {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            User user = userManager.authenticate(username, password);
            if (user != null) {
                currentUser = user;
                // String lastContainerPath = config.get("lastContainerPath_" + username);
                loggedIn[0] = true;
                loginDialog.dispose();
            } else {
                JOptionPane.showMessageDialog(loginDialog, "Invalid user or password!");
            }
        });

        registerButton.addActionListener(_ -> {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            try {
                userManager.addUser(username, password);
                JOptionPane.showMessageDialog(loginDialog, "User registered successfully!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(loginDialog, "Error while registering user: " + ex.getMessage());
            }
        });

        loginDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                loginDialog.dispose();
            }
        });

        loginDialog.setVisible(true);
        return loggedIn[0];
    }

    void createContainer() {
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
                        contextMenuManager.resetProgressBar();
                        container = new EncryptedContainer(currentUser.getEncryptionKey());
                        container.saveContainerWithProgress(saveFile, selectedDir.toPath(), progressBar);
                        JOptionPane.showMessageDialog(frame, "Container created: " + saveFile.getAbsolutePath());
                        config.put("lastContainerPath_" + currentUser.getUsername(), saveFile.getAbsolutePath());
                        ConfigManager.saveConfig(config);
                        importContainer();  // Immediately open the container after creation
                        contextMenuManager.enableContextMenuItems(); // Enable context menu
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        contextMenuManager.updateProgressBarCompleted();
                    }
                }).start();
            }
        }
    }

    void importContainer() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Load Container");
        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            new Thread(() -> {
                try {
                    contextMenuManager.resetProgressBar();
                    SwingUtilities.invokeLater(() -> progressBar.setValue(0));
                    container = new EncryptedContainer(currentUser.getEncryptionKey());
                    container.loadContainerWithProgress(file, tempDir, progressBar);
                    SwingUtilities.invokeLater(() -> loadDirectory(tempDir, root));
                    config.put("lastContainerPath_" + currentUser.getUsername(), file.getAbsolutePath());
                    ConfigManager.saveConfig(config);
                    contextMenuManager.enableContextMenuItems(); // Enable context menu
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    contextMenuManager.updateProgressBarCompleted();
                }
            }).start();
        }
    }

    public void loadDirectory(Path path, DefaultMutableTreeNode parent) {
        try (Stream<Path> paths = Files.list(path)) {
            List<Path> fileList = paths.toList();

            for (Path file : fileList) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(file.getFileName().toString());
                parent.add(node);
                if (Files.isDirectory(file)) {
                    loadDirectory(file, node);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Update the tree model
        ((DefaultTreeModel) tree.getModel()).reload(parent);
    }

    public void saveCurrentContainer() {
        if (container != null && currentUser != null) {
            String lastContainerPath = config.get("lastContainerPath_" + currentUser.getUsername());
            if (lastContainerPath != null) {
                try {
                    File containerFile = new File(lastContainerPath);
                    container.saveContainerWithProgress(containerFile, tempDir, progressBar);
                    System.out.println("Container saved: " + lastContainerPath);
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(frame, "Error while saving container: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    void exitCFM() {
        new Thread(this::performExitTasksWithProgress).start();
    }

    void performExitTasksWithProgress() {
        // Save the current state if necessary
        saveCurrentContainerWithProgress();

        // Clean up resources if necessary
        if (tempDir != null) {
            try {
                contextMenuManager.resetProgressBar();
                Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                System.out.println("Temporary directory cleaned up: " + tempDir);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                contextMenuManager.updateProgressBarCompleted();
            }
        }

        // Exit the application
        System.out.println("Exiting the application...");
        System.exit(0);
    }

    public void saveCurrentContainerWithProgress() {
        if (container != null && currentUser != null) {
            String lastContainerPath = config.get("lastContainerPath_" + currentUser.getUsername());
            if (lastContainerPath != null) {
                try {
                    contextMenuManager.resetProgressBar();
                    File containerFile = new File(lastContainerPath);
                    container.saveContainerWithProgress(containerFile, tempDir, progressBar);
                    System.out.println("Container saved: " + lastContainerPath);
                } catch (Exception e) {
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(frame, "Error while saving container: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE)
                    );
                } finally {
                    contextMenuManager.updateProgressBarCompleted();
                }
            }
        }
    }

    private void updateUIState(boolean containerOpened) {
        createButton.setEnabled(!containerOpened);
        importButton.setEnabled(!containerOpened);
        importFile.setEnabled(containerOpened);
        exitCFM.setEnabled(true);
        if (containerOpened) {
            contextMenuManager.enableContextMenu();
        } else {
            contextMenuManager.disableContextMenu();
        }
    }

    public void refreshTree() {
        root.removeAllChildren();
        loadDirectory(tempDir, root);
    }
}