package view;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class FrameManager {
    private final JFrame frame;
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final JProgressBar progressBar;
    private final JButton createButton;
    private final JButton importButton;
    private final JButton importFile;
    private final JButton exitCFM;

    public FrameManager(FileManager fileManager) {
        frame = new JFrame("CFM");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Prevent default close operation
        frame.setSize(600, 400);

        // Center the frame on the screen
        frame.setLocationRelativeTo(null);

        // Add window listener to handle closing
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                new Thread(fileManager::performExitTasksWithProgress).start();
            }
        });

        // Initialize UI components
        root = new DefaultMutableTreeNode("Root");
        tree = new JTree(root);
        JScrollPane treeScroll = new JScrollPane(tree);
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);

        frame.add(treeScroll);
        frame.add(progressBar, BorderLayout.NORTH);

        // Control buttons
        JPanel panel = new JPanel();
        createButton = new JButton("New Container");
        importButton = new JButton("Open Container");
        importFile = new JButton("Import File");
        exitCFM = new JButton("Exit");

        panel.add(createButton);
        panel.add(importButton);
        panel.add(importFile);
        panel.add(exitCFM);
        frame.add(panel, BorderLayout.SOUTH);

        // Button actions
        createButton.addActionListener(_ -> fileManager.createContainer());
        importButton.addActionListener(_ -> fileManager.importContainer());
        importFile.addActionListener(_ -> fileManager.importFilesOrDirectories());
        exitCFM.addActionListener(_ -> fileManager.exitCFM());
    }

    public JFrame getFrame() {
        return frame;
    }

    public JTree getTree() {
        return tree;
    }

    public DefaultMutableTreeNode getRoot() {
        return root;
    }

    public JProgressBar getProgressBar() {
        return progressBar;
    }

    public void setButtonsEnabled(boolean enabled) {
        createButton.setEnabled(enabled);
        importButton.setEnabled(enabled);
        importFile.setEnabled(enabled);
        exitCFM.setEnabled(enabled);
    }
}