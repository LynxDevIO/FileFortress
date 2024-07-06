package view;

import javax.swing.*;
import java.awt.*;

public class TutorialWindow extends JDialog {
    public TutorialWindow(JFrame parent) {
        super(parent, "Tutorial", true);
        setLayout(new BorderLayout());
        setSize(400, 300);
        setLocationRelativeTo(parent);

        JTextArea tutorialText = new JTextArea(
                "Welcome to FileFortress!\n\n" +
                        "This application helps you manage and secure your files.\n\n" +
                        "Setup Guide:\n" +
                        "1. Master Key: You'll be prompted to create or load a master key file. This key is essential for encrypting and decrypting your files. Keep it secure.\n" +
                        "2. User Registration: Register a new user account with a username and password.\n" +
                        "       Different users can't access the same container. It belongs to one user only." +
                        "3. Login: Login with your credentials to access the application.\n\n" +
                        "Usage Guide:\n" +
                        "1. Create a Container: Click 'New Container' to create a new secure container for your files.\n" +
                        "2. Open Container: Click 'Open Container' to load an existing container.\n" +
                        "3. Import Files/Directories: Use 'Import File' to add files or directories into your container.\n" +
                        "4. Context Menu: Right-click on files or directories to access options like open, rename, copy, delete, and move.\n" +
                        "5. Save and Exit: Ensure to exit the application safely to save your data and clean up temporary files.\n\n" +
                        "Enjoy using FileFortress!"
        );
        tutorialText.setEditable(false);
        tutorialText.setLineWrap(true);
        tutorialText.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(tutorialText);
        add(scrollPane, BorderLayout.CENTER);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        add(closeButton, BorderLayout.SOUTH);
    }
}