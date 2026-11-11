# ZenixAC Architecture

## Agreed MVP

- Product name: ZenixAC.
- Audience: broad Bukkit server audience; no game mode is assumed to be safe for automatic punishment.
- Server targets: CraftBukkit/Bukkit-compatible implementations, Spigot, Paper, and Purpur from 1.18.2 through 26.3; Folia from 1.19.4 through 26.3.
- Client target: 1.7 through 26.3 through a separately installed ViaVersion-family adapter where needed.
- First release: passive movement, combat, action-rate, block-interaction, vehicle, flight, and packet-order observations, with session history, staff alerts, English/Russian messages, and SQLite.
- Automatic punishments and product-specific innovations are deferred.

## Runtime Boundaries

The shared checks compile against the Bukkit API and avoid NMS. Packet-order support is isolated behind an optional PacketEvents bridge and does not add a compile-time packet-library dependency. Platform-specific behavior stays behind small adapters:

- `PlatformScheduler` uses Bukkit scheduling on non-Folia servers and the entity scheduler through reflection on Folia. This keeps the Spigot 1.18.2 compile baseline while allowing region-safe player notifications on Folia.
- `ClientProtocolProvider` discovers ViaVersion at runtime and reads a client's protocol when that API is available. It is optional and does not prevent startup.
- `PacketEventsBridge` discovers PacketEvents 2.14.0+ at runtime and registers one packet listener through reflection. If the dependency is absent or incompatible, only packet-order monitoring is unavailable.
- Bukkit-event checks each have their own class and stable check code. Packet-order state is updated from packet data and the PacketEvents user UUID. If a threshold is reached, the bridge hands the provided player handle to `PlatformScheduler`; player-dependent reads and violation creation then run on the player's scheduler.

## Event Flow

Bukkit event checks follow the shared flow:

```text
Bukkit event
  -> independent check filters and measures the event
  -> ViolationManager updates per-player, per-check level and decay
  -> ViolationRecord is sent to storage, the public violation event, and staff notifications
  -> bounded single-worker queue -> SQLite
  -> entity scheduler -> permission-checked staff message
```

The movement sampler resets after teleports, world changes, exclusions, damage/velocity grace periods, or long gaps. Combat reach is measured to a target's bounding box and allows bounded ping compensation. Action and block checks count accepted Bukkit events within one-second windows. Vehicle and flight checks use conservative speed, sample, and state filters.

When PacketEvents is present, its monitor-priority callback ignores cancelled events and tracks block-dig start/change/finish/cancel order using only packet data. Repeated unmatched finish/cancel actions are handed to the player's scheduler before a violation is created. There is no packet cancellation or modification. A five-second per-player/check record cooldown and an eight-message-per-second recipient limit reduce duplicate records and notification bursts.

## Configuration and Reload

`SettingsLoader` loads all selected files into a candidate snapshot, checks required values and ranges, and reports the source file and key for errors. `ConfigurationManager` swaps the snapshot only after a complete successful load. Storage file, queue capacity, and retention are fixed until restart; changing them during reload is rejected. Schema version 1 is explicit. Unknown schema versions fail loudly rather than being silently interpreted.

English and Russian message catalogs are validated before activation. Staff formats and messages are kept outside Java code. Check settings are validated as one snapshot; if PacketEvents is absent or incompatible at startup, install a supported version and restart. When PacketEvents is already present, the packet-order setting can be toggled through reload.

## Storage

SQLite stores two tables:

- `player_sessions`: session UUID, player UUID/name, start, and optional end time.
- `violations`: session/player identifiers, check code, event time, violation level, bounded diagnostic text, optional client protocol, and ping.

One background worker owns the SQLite connection and processes session, write, and query tasks. Its queue is bounded. A database failure or queue overflow is reported and counted; live checks continue. Retention runs during database initialization. Shutdown drains the queue for a bounded time and reports any unsaved operations.

## Extension Points

- Add future independent check classes that call `ViolationManager` with a stable check code and safe diagnostic fields. Existing signals are experimental starting points; extend them only after platform-specific legitimate and negative scenarios are measured.
- Extend settings with new versioned sections and migrations when a later release needs schema changes.
- Add optional platform or protocol adapters without making them required for the core Bukkit event path.
- Extend the shipped public violation/session/reload/command events and add external storage adapters.
- Add reactions only behind explicit per-check configuration and after validation on legitimate and negative scenarios.

## Known Risks

All detection is passive and heuristic. Movement timing can be distorted by lag or custom mechanics. Reach can be affected by server-side combat plugins and old-position handling. Action counts include accepted Bukkit events and may not map one-to-one to physical clicks. Block and vehicle rates depend on game mode, surface, and plugins. Flight samples depend on movement events and server ground-state updates. Packet-order detection only covers repeated unmatched block-dig finish/cancel packets and requires a compatible PacketEvents installation.

None of these thresholds has been validated on the full target matrix. A check signal should be reviewed in context; the checks do not automatically punish or alter players. A proxy-only ViaVersion installation can translate connections while leaving backend protocol reporting unknown.
