package irinque.zenixac.config;

import java.util.Map;

public record PluginSettings(
        int schemaVersion,
        String language,
        int retentionDays,
        String databaseFile,
        int queueCapacity,
        boolean notificationsEnabled,
        boolean debugNotificationsEnabled,
        boolean movementCheckEnabled,
        double maximumHorizontalSpeed,
        int minimumMovementSampleMillis,
        double violationIncrease,
        double violationDecayPerMinute,
        double alertThreshold,
        String alertPermission,
        String alertFormat,
        String debugPermission,
        String debugFormat,
        Map<String, String> messages
) {
    public PluginSettings {
        messages = Map.copyOf(messages);
    }
}
