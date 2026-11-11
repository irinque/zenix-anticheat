# ZenixAC Architecture

## Agreed MVP

- Product name: ZenixAC.
- Audience: broad Bukkit server audience; no game mode is assumed to be safe for automatic punishment.
- Server targets: CraftBukkit/Bukkit-compatible implementations, Spigot, Paper, and Purpur from 1.18.2 through 26.3; Folia from 1.19.4 through 26.3.
- Client target: 1.7 through 26.3 through a separately installed ViaVersion-family adapter where needed.
- First release: passive movement observation, session history, staff alerts, English/Russian messages, and SQLite.
- Automatic punishments and product-specific innovations are deferred.

## Runtime Boundaries

The shared implementation compiles against the Bukkit API and avoids NMS and server packet classes. Platform-specific behavior stays behind small adapters:

- `PlatformScheduler` uses Bukkit scheduling on non-Folia servers and the entity scheduler through reflection on Folia. This keeps the Spigot 1.18.2 compile baseline while allowing region-safe player notifications on Folia.
- `ClientProtocolProvider` discovers ViaVersion at runtime and reads a client's protocol when that API is available. It is optional and does not prevent startup.
- Checks consume Bukkit events and create domain records. New checks should have their own class and code instead of adding all detection logic to one event listener.

## Event Flow

```text
PlayerMoveEvent
  -> MovementCheck filters and samples horizontal movement
  -> ViolationManager updates per-player, per-check level and decay
  -> ViolationRecord is sent to storage and staff notification paths
  -> bounded single-worker queue -> SQLite
  -> entity scheduler -> permission-checked staff message
```

The movement sampler resets after teleports, world changes, exclusions, damage/velocity grace periods, or long gaps. It records protocol as unknown when ViaVersion is absent. A five-second per-player/check record cooldown and an eight-message-per-second recipient limit reduce duplicate records and notification bursts.

## Configuration and Reload

`SettingsLoader` loads all selected files into a candidate snapshot, checks required values and ranges, and reports the source file and key for errors. `ConfigurationManager` swaps the snapshot only after a complete successful load. Storage file, queue capacity, and retention are fixed until restart; changing them during reload is rejected. Schema version 1 is explicit. Unknown schema versions fail loudly rather than being silently interpreted.

English and Russian message catalogs are validated before activation. Staff formats and messages are kept outside Java code.

## Storage

SQLite stores two tables:

- `player_sessions`: session UUID, player UUID/name, start, and optional end time.
- `violations`: session/player identifiers, check code, event time, violation level, bounded diagnostic text, optional client protocol, and ping.

One background worker owns the SQLite connection and processes session, write, and query tasks. Its queue is bounded. A database failure or queue overflow is reported and counted; live movement checks continue. Retention runs during database initialization. Shutdown drains the queue for a bounded time and reports any unsaved operations.

## Extension Points

- Add future independent check classes that call `ViolationManager` with a stable check code and safe diagnostic fields.
- Extend settings with new versioned sections and migrations when a later release needs schema changes.
- Add optional platform or protocol adapters without making them required for the core Bukkit event path.
- Add public violation/session/reload events and external storage adapters in a later API phase.
- Add reactions only behind explicit per-check configuration and after validation on legitimate and negative scenarios.

## Known Risks

The movement speed heuristic uses elapsed wall time between Bukkit movement samples. Lag spikes and server-specific movement mechanics can distort the rate. It skips common special movement states, but the current filters cannot cover every plugin, item, effect, or custom world. Therefore the MVP records and alerts only; accuracy remains unverified until per-platform gameplay trials are run.

Client connection translation, protocol reporting, and server implementation support are separate. A proxy-only ViaVersion installation can translate connections while leaving the backend ZenixAC unable to identify the original client protocol.
