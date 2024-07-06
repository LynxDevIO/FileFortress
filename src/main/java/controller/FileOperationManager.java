package controller;

import view.FileManager;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

public class FileOperationManager {
    private final Path tempDir;
    private final JProgressBar progressBar;
    private final DefaultTreeModel treeModel;
    private final JTree tree;
    private final FileManager fileManager;

    public FileOperationManager(Path tempDir, JProgressBar progressBar, DefaultTreeModel treeModel, JTree tree, FileManager fileManager) {
        this.tempDir = tempDir;
        this.progressBar = progressBar;
        this.treeModel = treeModel;
        this.tree = tree;
        this.fileManager = fileManager;
    }

    public void loadDirectory(Path path, DefaultMutableTreeNode parent) {
        parent.removeAllChildren(); // Clear the parent node first
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
        treeModel.reload(parent);
    }

    public void importFilesOrDirectories(JFrame frame) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Files or Directories to Import");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fileChooser.setMultiSelectionEnabled(true);

        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            new Thread(() -> {
                try {
                    progressBar.setValue(0);
                    progressBar.setString(null);
                    for (File file : selectedFiles) {
                        Path targetPath = tempDir.resolve(file.getName());
                        if (file.isDirectory()) {
                            copyDirectory(file.toPath(), targetPath);
                        } else {
                            Files.copy(file.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    SwingUtilities.invokeLater(() -> loadDirectory(tempDir, (DefaultMutableTreeNode) treeModel.getRoot()));
                } catch (IOException e) {
                    e.printStackTrace();
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
}