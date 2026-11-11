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
        CombatSettings combat,
        ActionRateSettings actionRate,
        BlockInteractionSettings blockInteraction,
        VehicleSettings vehicle,
        FlightSettings flight,
        PacketOrderSettings packetOrder,
        String alertPermission,
        String alertFormat,
        String debugPermission,
        String debugFormat,
        Map<String, String> messages
) {
    public PluginSettings {
        messages = Map.copyOf(messages);
    }

    public record CombatSettings(boolean enabled, double maxReach, double pingCompensation, double maxPingCompensation,
                                 double violationIncrease) {
    }

    public record ActionRateSettings(boolean enabled, int maxSwingsPerSecond, int maxInteractionsPerSecond,
                                     double violationIncrease) {
    }

    public record BlockInteractionSettings(boolean enabled, int maxActionsPerSecond, double violationIncrease) {
    }

    public record VehicleSettings(boolean enabled, double maxHorizontalSpeed, int consecutiveSamples,
                                  double violationIncrease) {
    }

    public record FlightSettings(boolean enabled, double maxVerticalSpeed, int consecutiveSamples,
                                 double violationIncrease) {
    }

    public record PacketOrderSettings(boolean enabled, int mismatchLimit, int mismatchWindowSeconds,
                                      double violationIncrease) {
    }
}
