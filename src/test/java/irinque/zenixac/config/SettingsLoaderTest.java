package irinque.zenixac.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsLoaderTest {
    private static final List<String> RESOURCE_FILES = List.of(
            "config.yml", "checks.yml", "notifications.yml", "storage.yml", "punishments.yml", "messages.yml", "messages-en.yml"
    );

    @TempDir
    Path tempDirectory;

    @BeforeEach
    void copyDefaultSettings() throws IOException {
        for (String fileName : RESOURCE_FILES) {
            try (var input = getClass().getResourceAsStream("/" + fileName)) {
                Files.copy(input, tempDirectory.resolve(fileName));
            }
        }
    }

    @Test
    void loadsPassiveDefaults() throws Exception {
        PluginSettings settings = new SettingsLoader(tempDirectory.toFile()).load();

        assertEquals(1, settings.schemaVersion());
        assertEquals("en", settings.language());
        assertEquals("history.db", settings.databaseFile());
        assertTrue(settings.movementCheckEnabled());
        assertFalse(settings.debugNotificationsEnabled());
        assertTrue(settings.messages().get("prefix").contains("ZenixAC"));
    }

    @Test
    void loadsRussianMessagesWhenSelected() throws Exception {
        Path configFile = tempDirectory.resolve("config.yml");
        Files.writeString(configFile, Files.readString(configFile).replace("language: en", "language: ru"));

        PluginSettings settings = new SettingsLoader(tempDirectory.toFile()).load();

        assertEquals("ru", settings.language());
        assertTrue(settings.messages().get("no-permission").contains("прав"));
    }

    @Test
    void reportsInvalidCheckSettingWithFileAndPath() throws Exception {
        Path checksFile = tempDirectory.resolve("checks.yml");
        String original = Files.readString(checksFile);
        Files.writeString(checksFile, original.replace("max-horizontal-speed: 18.0", "max-horizontal-speed: 0"));

        SettingsException exception = assertThrows(
                SettingsException.class,
                () -> new SettingsLoader(tempDirectory.toFile()).load()
        );

        assertTrue(exception.getMessage().contains("checks.yml"));
        assertTrue(exception.getMessage().contains("movement.max-horizontal-speed"));
    }

    @Test
    void failedReloadPreservesCurrentSettings() throws Exception {
        ConfigurationManager manager = new ConfigurationManager(tempDirectory.toFile());
        manager.load();
        Path checksFile = tempDirectory.resolve("checks.yml");
        String original = Files.readString(checksFile);
        Files.writeString(checksFile, original.replace("movement:\n  enabled: true", "movement:\n  enabled: false"));
        Files.writeString(tempDirectory.resolve("punishments.yml"), "enabled: true\nactions: []\n");

        assertThrows(SettingsException.class, manager::reload);
        assertTrue(manager.get().movementCheckEnabled());
    }
}
