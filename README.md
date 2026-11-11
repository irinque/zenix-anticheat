<div align="center">

# ZenixAC

### Passive anti-cheat monitoring for Bukkit-compatible Minecraft servers

Record unusual movement, review session history, and give staff configurable diagnostics while automatic punishments remain disabled.

</div>

---

## Key Features

- **Passive behavior checks** — monitor movement, combat reach, action rates, block interaction rates, vehicle movement, flight signals, and digging packet order.
- **Optional packet integration** — packet-order monitoring uses an external PacketEvents installation; all Bukkit-event checks work without it.
- **Separate violation levels** — maintain a decaying level for each player and check.
- **Session history** — store player sessions and violation records in a local SQLite database.
- **Staff controls** — toggle alerts, inspect player profiles and history, reload settings, and check storage queue health.
- **Folia-aware player work** — schedule player-specific notifications on the entity's owning region.
- **Integration events** — observe violations, player sessions, configuration reloads, and command handling.
- **English and Russian messages** — edit message templates and select a language in `config.yml`.
- **Optional client protocol details** — read the connecting client's protocol through ViaVersion when it is installed on the same server.

---

## Functionality

All checks are passive: they record signals and notify staff, but never change or restrict player actions.

| Check | Signal | Default threshold |
| --- | --- | --- |
| `MOVE_RATE` | Horizontal movement speed across a sample window. | 18 blocks/s over at least 500 ms. |
| `COMBAT_REACH` | Distance from the attacker's eye to the nearest point of a target's bounding box on a server-accepted hit. Ping adds a bounded allowance. | 5.25 blocks plus ping compensation, capped at 2 blocks. |
| `ACTION_RATE_SWING` | Repeated arm-swing events. | More than 30 per second. |
| `ACTION_RATE_INTERACT` | Repeated non-physical player interactions. | More than 45 per second. |
| `BLOCK_ACTION_RATE` | Successful block-break and block-place events. Creative and spectator players are skipped. | More than 32 per second. |
| `VEHICLE_SPEED` | Sustained horizontal speed while riding a vehicle. | More than 80 blocks/s in consecutive samples. |
| `FLIGHT_ASCENT` | Repeated upward movement above the configured vertical-speed limit. | More than 9.5 blocks/s for four consecutive samples. |
| `FLIGHT_HOVER` | Nearly level, low-horizontal movement while airborne for a sustained period. | At least 2.5 seconds. |
| `PACKET_ORDER_DIG` | Repeated unmatched block-dig finish/cancel packets. | Three mismatches within 30 seconds. |

Thresholds are deliberately conservative starting values. They have not been calibrated against a broad set of server game modes, custom movement plugins, or client versions. A recorded signal is not proof of cheating; review history and tune or disable checks for the server's mechanics.

Packet-order monitoring requires [PacketEvents 2.14.0 or newer](https://github.com/retrooper/packetevents/releases) on the same server. It observes digging packet sequences only; it does not claim to validate every packet ordering pattern. If PacketEvents is absent or too old, ZenixAC logs that this one check is unavailable and keeps the other checks running.

There are no automatic kicks, commands, rollbacks, or other punishments. Enabling punishments or adding punishment actions to `punishments.yml` is rejected with a clear configuration error.

Each stored event includes the player's UUID and name, session ID, check code, timestamp, violation level, bounded diagnostic details, ping, and client protocol when available. Database work uses one bounded background queue. If SQLite is unavailable or the queue fills, live checks continue; the server log reports storage problems at a limited rate.

---

## Compatibility

These are the agreed target ranges. They are not a claim that every server and client combination has passed a runtime test.

| Server software | Target Minecraft server versions | Status |
| --- | --- | --- |
| CraftBukkit / Bukkit API implementations, Spigot, Paper, Purpur | 1.18.2–26.3 | Shared Bukkit API path; runtime matrix still needs testing. |
| Folia | 1.19.4–26.3 | Entity scheduler path is implemented; runtime matrix still needs testing. |

“Bukkit” is the plugin API, not a separate server distribution. CraftBukkit is included as a Bukkit-compatible server implementation.

The project compiles against Spigot API 1.18.2 and targets Java 17 bytecode. Minecraft 26.1 and newer server builds require Java 25; older server versions have their own Java requirements. Use the Java version required by the server you install.

The client target is Minecraft 1.7–26.3. To connect that range to one server version, install a compatible protocol stack on the server: ViaVersion, ViaBackwards, and ViaRewind as required by the client range. ZenixAC reads the client protocol through ViaVersion's API only when ViaVersion is installed on the same server. If version translation happens only on a proxy, ZenixAC records the protocol as unknown. Client connection support and ZenixAC's server-core compatibility are separate parts of the matrix.

| Client range | Adapter components |
| --- | --- |
| Newer clients joining an older server | ViaVersion |
| Older clients joining a newer server | ViaBackwards and ViaVersion |
| Minecraft 1.7/1.8 clients joining a newer server | ViaRewind, ViaBackwards, and ViaVersion |

The protocol adapter's own supported server versions and known client limitations apply. ZenixAC does not modify packets; the optional PacketEvents bridge reads block-dig packet state for `PACKET_ORDER_DIG`.

---

## Requirements

- Bukkit-compatible server in the target range above.
- Java runtime suitable for the selected server version.
- Optional: ViaVersion on the same server when staff need the connecting client's protocol in history.
- Optional for cross-version connections: the required ViaVersion-family server or proxy plugins.
- Optional for `PACKET_ORDER_DIG`: PacketEvents 2.14.0 or newer on the same backend server.

Folia support requires region-safe scheduling. Setting `folia-supported: true` alone is not enough; ZenixAC uses the entity scheduler for player notifications. The code avoids server entity and world access on its SQLite worker thread.

---

## Build and Install

Build with Maven:

```sh
mvn clean package
```

Copy `target/zenix-anticheat-1.0.0.jar` into the server's `plugins` directory and restart the server. SQLite JDBC is bundled in the plugin. ViaVersion and PacketEvents remain external optional plugins; PacketEvents is needed only for packet-order monitoring.

---

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/zenixac help` | Show available commands. | `zenixac.command` |
| `/zenixac alerts` | Toggle violation alerts for yourself. | `zenixac.alerts` |
| `/zenixac debug` | Toggle detailed alerts when enabled in configuration. | `zenixac.debug` |
| `/zenixac profile <player>` | Show a stored event count and highest recorded level. | `zenixac.profile` |
| `/zenixac history <player> [session-id]` | Show recent records, optionally limited to a session UUID. | `zenixac.history` |
| `/zenixac reload` | Validate and reload live settings and messages. | `zenixac.reload` |
| `/zenixac performance` | Show recorded, dropped, and queued history operations. | `zenixac.performance` |

The base `zenixac.command` permission and all command permissions are granted to operators by default. Alert and debug toggles are available to players; profile, history, reload, and performance commands can also be run from the console.

---

## Configuration

Settings are created in `plugins/ZenixAC/` on first start.

| File | Purpose |
| --- | --- |
| `config.yml` | Schema version, language, retention, queue capacity, and notification switches. |
| `checks.yml` | Enable flags, thresholds, and per-check violation increases; shared violation decay and alert threshold. |
| `notifications.yml` | Staff permissions and editable alert templates. |
| `punishments.yml` | Reserved for a later phase; punishments must remain disabled and the action list empty in the MVP. |
| `storage.yml` | SQLite selection and database filename. |
| `messages.yml` | Russian command messages. |
| `messages-en.yml` | English command messages. |

Set `language` to `en` or `ru`. Messages and notification formats use `&` color codes. A valid live reload swaps settings together. Database file, queue capacity, and retention changes require a server restart; an attempted live change is rejected without replacing the active settings. If PacketEvents was missing at startup, install it and restart to register packet-order monitoring; when it is already present, the packet-order setting can be changed through reload.

`storage.retention-days: 0` disables automatic history cleanup. Otherwise, records older than the configured number of days are deleted when the database starts.

---

## Current Limits

- These checks are first-pass passive heuristics, not accuracy claims. Each supported game mode needs legitimate and negative scenarios before thresholds are tightened or automated actions are considered.
- `PACKET_ORDER_DIG` covers repeated unmatched digging sequences only and is unavailable without PacketEvents 2.14.0 or newer.
- Custom movement, combat, world, and interaction plugins may produce patterns these checks cannot classify; the checks do not know every server-specific rule.
- The accepted server-version ranges and client adapter combinations need runtime testing on old, intermediate, and current versions of each listed platform.
- Folia scheduling has not yet been smoke-tested on a Folia server.
- The public plugin API, spectator workflow, external database adapters, and automated actions are not included in this MVP.

See [docs/architecture.md](docs/architecture.md) for the internal design and planned extension points.
See [docs/compatibility-matrix.md](docs/compatibility-matrix.md) for the runtime test plan and current results.
See [docs/public-api.md](docs/public-api.md) for plugin events exposed to integrations.

---

Licensed under GPL-3.0. See [LICENSE](LICENSE). Bundled dependency details are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and in the plugin JAR.
