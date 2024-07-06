package config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ConfigManager {
    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".file_manager_app");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.properties");

    public static Map<String, String> loadConfig() {
        Map<String, String> config = new HashMap<>();
        if (Files.exists(CONFIG_FILE)) {
            Properties props = new Properties();
            try {
                props.load(Files.newInputStream(CONFIG_FILE));
                for (String name : props.stringPropertyNames()) {
                    config.put(name, props.getProperty(name));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return config;
    }

    public static void saveConfig(Map<String, String> config) {
        Properties props = new Properties();
        props.putAll(config);
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }
            props.store(Files.newOutputStream(CONFIG_FILE), null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}