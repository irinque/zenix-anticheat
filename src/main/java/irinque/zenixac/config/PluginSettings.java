package irinque.zenixac.config;

public record PluginSettings(
        int schemaVersion,
        int retentionDays,
        String databaseFile,
        int queueCapacity,
        int sampleWindowMillis,
        boolean notificationsEnabled,
        boolean movementCheckEnabled,
        double maximumHorizontalSpeed,
        int minimumMovementSampleMillis,
        double violationIncrease,
        double violationDecayPerMinute,
        double alertThreshold,
        boolean punishmentsEnabled,
        String alertPermission,
        String alertFormat,
        String debugPermission,
        String debugFormat
) {
}
