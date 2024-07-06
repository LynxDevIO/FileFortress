package controller;

import view.FileManager;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.stream.Stream;

public class ContextMenuManager {
    private final JTree tree;
    private final Path tempDir;
    private final DefaultTreeModel treeModel;
    private final FileManager fileManager;
    private final JProgressBar progressBar;
    private JPopupMenu popupMenu;
    private JMenuItem openItem;
    private JMenuItem renameItem;
    private JMenuItem copyItem;
    private JMenuItem deleteItem;
    private JMenuItem addFolderItem;
    private JMenuItem moveItem;

    public ContextMenuManager(JTree tree, Path tempDir, DefaultTreeModel treeModel, FileManager fileManager, JProgressBar progressBar) {
        this.tree = tree;
        this.tempDir = tempDir;
        this.treeModel = treeModel;
        this.fileManager = fileManager;
        this.progressBar = progressBar;
        configureContextMenu();
    }

    private void configureContextMenu() {
        popupMenu = new JPopupMenu();
        openItem = new JMenuItem("Open");
        renameItem = new JMenuItem("Rename");
        copyItem = new JMenuItem("Copy");
        deleteItem = new JMenuItem("Delete");
        addFolderItem = new JMenuItem("Add Folder");
        moveItem = new JMenuItem("Move");

        popupMenu.add(openItem);
        popupMenu.add(renameItem);
        popupMenu.add(copyItem);
        popupMenu.add(deleteItem);
        popupMenu.add(addFolderItem);
        popupMenu.add(moveItem);

        tree.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = tree.getClosestRowForLocation(e.getX(), e.getY());
                    tree.setSelectionRow(row);
                    DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
                    Path selectedPath = tempDir.resolve(getPathForNode(selectedNode));

                    // Show the context menu only if a valid node is selected
                    if (selectedNode != null) {
                        boolean isDirectory = Files.isDirectory(selectedPath);
                        addFolderItem.setVisible(isDirectory);
                        popupMenu.show(tree, e.getX(), e.getY());
                    }
                }
            }
        });

        openItem.addActionListener(this::openFile);
        renameItem.addActionListener(this::renameFile);
        copyItem.addActionListener(this::copyFile);
        deleteItem.addActionListener(this::deleteFile);
        addFolderItem.addActionListener(this::addFolder);
        moveItem.addActionListener(this::moveFile);
    }

    public void disableContextMenuItems() {
        openItem.setEnabled(false);
        renameItem.setEnabled(false);
        copyItem.setEnabled(false);
        deleteItem.setEnabled(false);
        addFolderItem.setEnabled(false);
        moveItem.setEnabled(false);
    }

    public void enableContextMenuItems() {
        openItem.setEnabled(true);
        renameItem.setEnabled(true);
        copyItem.setEnabled(true);
        deleteItem.setEnabled(true);
        addFolderItem.setEnabled(true);
        moveItem.setEnabled(true);
    }

    public void resetProgressBar() {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(0);
            progressBar.setString(null);
        });
    }

    public void updateProgressBarCompleted() {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(100);
            progressBar.setString("COMPLETED");
        });
    }

    private void openFile(ActionEvent e) {
        disableContextMenuItems();
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == null || tempDir == null) {
            enableContextMenuItems();
            return;
        }
        String nodeName = node.toString();
        Path filePath = tempDir.resolve(nodeName);
        if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
            try {
                Desktop.getDesktop().open(filePath.toFile());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        enableContextMenuItems();
    }

    private void renameFile(ActionEvent e) {
        disableContextMenuItems();
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == null || tempDir == null) {
            enableContextMenuItems();
            return;
        }
        String nodeName = node.toString();
        Path filePath = tempDir.resolve(nodeName);
        if (Files.exists(filePath)) {
            String newName = (String) JOptionPane.showInputDialog(
                    tree,
                    "Rename to:",
                    "Rename",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    nodeName); // pre-fill with current name
            if (newName != null && !newName.isEmpty()) {
                new Thread(() -> {
                    try {
                        resetProgressBar();
                        Files.move(filePath, filePath.resolveSibling(newName), StandardCopyOption.REPLACE_EXISTING);
                        node.setUserObject(newName);
                        treeModel.nodeChanged(node);
                        fileManager.saveCurrentContainerWithProgress();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    } finally {
                        updateProgressBarCompleted();
                        SwingUtilities.invokeLater(this::enableContextMenuItems);
                    }
                }).start();
            } else {
                enableContextMenuItems();
            }
        } else {
            enableContextMenuItems();
        }
    }

    private void copyFile(ActionEvent e) {
        disableContextMenuItems();
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == null || tempDir == null) {
            enableContextMenuItems();
            return;
        }
        String nodeName = node.toString();
        Path filePath = tempDir.resolve(nodeName);
        if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File(filePath.getFileName().toString()));
            int result = fileChooser.showSaveDialog(tree);
            if (result == JFileChooser.APPROVE_OPTION) {
                File saveFile = fileChooser.getSelectedFile();
                new Thread(() -> {
                    try {
                        resetProgressBar();
                        long fileSize = Files.size(filePath);
                        long[] bytesCopied = {0};
                        try (InputStream in = Files.newInputStream(filePath);
                             OutputStream out = Files.newOutputStream(saveFile.toPath())) {
                            byte[] buffer = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                                bytesCopied[0] += bytesRead;
                                int progress = (int) (100.0 * bytesCopied[0] / fileSize);
                                SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
                            }
                        }
                        fileManager.saveCurrentContainerWithProgress();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    } finally {
                        updateProgressBarCompleted();
                        SwingUtilities.invokeLater(this::enableContextMenuItems);
                    }
                }).start();
            } else {
                enableContextMenuItems();
            }
        } else {
            enableContextMenuItems();
        }
    }

    private void deleteFile(ActionEvent e) {
        disableContextMenuItems();
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == null || tempDir == null) {
            enableContextMenuItems();
            return;
        }
        String nodeName = node.toString();
        Path filePath = tempDir.resolve(nodeName);
        if (Files.exists(filePath)) {
            new Thread(() -> {
                try {
                    resetProgressBar();
                    BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
                    if (attrs.isDirectory()) {
                        deleteDirectoryRecursively(filePath);
                    } else {
                        Files.delete(filePath);
                    }
                    node.removeFromParent();
                    treeModel.reload();
                    fileManager.saveCurrentContainerWithProgress();
                } catch (IOException ex) {
                    ex.printStackTrace();
                } finally {
                    updateProgressBarCompleted();
                    SwingUtilities.invokeLater(this::enableContextMenuItems);
                }
            }).start();
        } else {
            enableContextMenuItems();
        }
    }

    private void deleteDirectoryRecursively(Path path) throws IOException {
        try (Stream<Path> paths = Files.walk(path)) {
            long totalSize = paths.mapToLong(p -> p.toFile().length()).sum();

            // Walk the path again to delete files
            try (Stream<Path> deletePaths = Files.walk(path)) {
                long[] processedSize = {0};

                deletePaths.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                                processedSize[0] += p.toFile().length();
                                int progress = (int) (100.0 * processedSize[0] / totalSize);
                                SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                        });
            }
        }
    }

    private void addFolder(ActionEvent e) {
        disableContextMenuItems();
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (node == null || tempDir == null) {
            enableContextMenuItems();
            return;
        }

        String newName = JOptionPane.showInputDialog("New folder name:");
        if (newName != null && !newName.isEmpty()) {
            new Thread(() -> {
                try {
                    resetProgressBar();
                    Path parentPath = getPathForNode(node);
                    Path newFolderPath = parentPath.resolve(newName);

                    // Create the new directory
                    Files.createDirectories(newFolderPath);

                    // Check if the directory was created successfully
                    if (Files.isDirectory(newFolderPath)) {
                        // Add the new node to the tree
                        DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(newName);
                        node.add(newNode);
                        treeModel.reload(node);

                        // Save the container with the new folder
                        fileManager.saveCurrentContainerWithProgress();

                        System.out.println("New folder created: " + newFolderPath);
                    } else {
                        System.out.println("Failed to create directory: " + newFolderPath);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                } finally {
                    updateProgressBarCompleted();
                    fileManager.refreshTree();
                    SwingUtilities.invokeLater(this::enableContextMenuItems);
                }
            }).start();
        } else {
            enableContextMenuItems();
        }
    }

    private void moveFile(ActionEvent e) {
        disableContextMenuItems();
        DefaultMutableTreeNode sourceNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (sourceNode == null || tempDir == null) {
            enableContextMenuItems();
            return;
        }
        String sourceNodeName = sourceNode.toString();
        Path sourceFilePath = tempDir.resolve(sourceNodeName);

        TreePath destinationPath = selectDestinationDirectory();
        if (destinationPath == null) {
            enableContextMenuItems();
            return;
        }

        DefaultMutableTreeNode destinationNode = (DefaultMutableTreeNode) destinationPath.getLastPathComponent();
        Path destinationDir;

        // Handle root directory case
        if (destinationNode.toString().equals("Root")) {
            destinationDir = tempDir;
        } else {
            destinationDir = tempDir.resolve(getPathForNode(destinationNode));
        }

        if (!Files.isDirectory(destinationDir)) {
            JOptionPane.showMessageDialog(tree, "Selected destination is not a directory", "Move", JOptionPane.ERROR_MESSAGE);
            enableContextMenuItems();
            return;
        }

        Path destinationFilePath = destinationDir.resolve(sourceFilePath.getFileName().toString());
        new Thread(() -> {
            try {
                resetProgressBar();
                Files.move(sourceFilePath, destinationFilePath, StandardCopyOption.REPLACE_EXISTING);
                treeModel.removeNodeFromParent(sourceNode);
                DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(destinationFilePath.getFileName().toString());
                destinationNode.add(newNode);
                treeModel.reload(destinationNode);
                fileManager.saveCurrentContainerWithProgress();
                JOptionPane.showMessageDialog(tree, "File moved successfully", "Move", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(tree, "Error moving file: " + ex.getMessage(), "Move", JOptionPane.ERROR_MESSAGE);
            } finally {
                updateProgressBarCompleted();
                SwingUtilities.invokeLater(this::enableContextMenuItems);
            }
        }).start();
    }

    private TreePath selectDestinationDirectory() {
        JDialog dialog = new JDialog((Frame) null, "Select Destination Directory", true);
        dialog.setSize(300, 400);
        dialog.setLocationRelativeTo(tree);

        // Create a copy of the current tree model to display in the dialog
        DefaultTreeModel treeModelCopy = (DefaultTreeModel) tree.getModel();
        JTree directoryTree = new JTree(treeModelCopy);
        directoryTree.setRootVisible(true);  // Ensure root is visible
        JScrollPane scrollPane = new JScrollPane(directoryTree);

        JButton selectButton = new JButton("Select");
        final TreePath[] selectedPath = new TreePath[1];
        selectButton.addActionListener(_ -> {
            selectedPath[0] = directoryTree.getSelectionPath();
            dialog.dispose();
        });

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(selectButton, BorderLayout.SOUTH);

        dialog.add(panel);
        dialog.setVisible(true);

        return selectedPath[0];
    }

    // Helper method to get the path for a node
    private Path getPathForNode(DefaultMutableTreeNode node) {
        TreeNode[] nodes = node.getPath();
        StringBuilder path = new StringBuilder(tempDir.toString());

        for (TreeNode n : nodes) {
            if (n.toString().equals("Root")) continue;
            path.append(File.separator).append(n);
        }
        return Paths.get(path.toString());
    }

    public void disableContextMenu() {
        tree.setEnabled(false);
    }

    public void enableContextMenu() {
        tree.setEnabled(true);
    }

    public void setContextMenuEnabled(boolean enabled) {
        openItem.setEnabled(enabled);
        renameItem.setEnabled(enabled);
        copyItem.setEnabled(enabled);
        deleteItem.setEnabled(enabled);
        addFolderItem.setEnabled(enabled);
        moveItem.setEnabled(enabled);
    }
}