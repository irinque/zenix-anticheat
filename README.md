<div align="center">

# ZenixAC

### Passive anti-cheat monitoring for Bukkit-compatible Minecraft servers

Record unusual movement, review session history, and give staff configurable diagnostics while automatic punishments remain disabled.

</div>

---

## Key Features

- **Passive movement monitoring** — record unusually high horizontal movement rates for staff review.
- **Separate violation levels** — maintain a decaying level for each player and check.
- **Session history** — store player sessions and violation records in a local SQLite database.
- **Staff controls** — toggle alerts, inspect player profiles and history, reload settings, and check storage queue health.
- **Folia-aware player work** — schedule player-specific notifications on the entity's owning region.
- **English and Russian messages** — edit message templates and select a language in `config.yml`.
- **Optional client protocol details** — read the connecting client's protocol through ViaVersion when it is installed on the same server.

---

## Functionality

The first release includes one experimental movement check. It estimates horizontal speed across a configurable sample window and records a violation when the configured limit is exceeded. Its default limit is 18 blocks per second, with a 500 ms sample and an alert threshold of 3.0.

The check skips teleports, world changes, vehicles, flight, gliding, swimming, riptiding, several movement effects and blocks, recent damage or velocity, and long sample gaps. These filters reduce obvious false positives, but they do not make the heuristic proof of cheating. Server lag, custom mechanics, and other plugins can still produce unusual movement. Use the check in passive mode, review its records, and tune or disable it for the server's game modes.

There are no automatic kicks, commands, rollbacks, or other punishments in this MVP. Enabling punishments or adding punishment actions to `punishments.yml` is rejected with a clear configuration error.

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

The protocol adapter's own supported server versions and known client limitations apply. ZenixAC does not inspect or modify raw packets in this MVP.

---

## Requirements

- Bukkit-compatible server in the target range above.
- Java runtime suitable for the selected server version.
- Optional: ViaVersion on the same server when staff need the connecting client's protocol in history.
- Optional for cross-version connections: the required ViaVersion-family server or proxy plugins.

Folia support requires region-safe scheduling. Setting `folia-supported: true` alone is not enough; ZenixAC uses the entity scheduler for player notifications. The code avoids server entity and world access on its SQLite worker thread.

---

## Build and Install

Build with Maven:

```sh
mvn clean package
```

Copy `target/zenix-anticheat-1.0.0.jar` into the server's `plugins` directory and restart the server. SQLite JDBC is bundled in the plugin. ViaVersion remains an optional external plugin.

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
| `checks.yml` | Independent movement check limit, sampling interval, violation increase, decay, and alert threshold. |
| `notifications.yml` | Staff permissions and editable alert templates. |
| `punishments.yml` | Reserved for a later phase; punishments must remain disabled and the action list empty in the MVP. |
| `storage.yml` | SQLite selection and database filename. |
| `messages.yml` | Russian command messages. |
| `messages-en.yml` | English command messages. |

Set `language` to `en` or `ru`. Messages and notification formats use `&` color codes. A valid live reload swaps settings together. Database file, queue capacity, and retention changes require a server restart; an attempted live change is rejected without replacing the active settings.

`storage.retention-days: 0` disables automatic history cleanup. Otherwise, records older than the configured number of days are deleted when the database starts.

---

## Current Limits

- Movement is the only implemented check. Combat, packet-order, action-rate, block-interaction, vehicle, and flight checks are later work.
- The threshold is a starting value, not a measured accuracy claim. Each game mode needs legitimate and negative test scenarios before stricter settings or automated action are considered.
- The accepted server-version ranges and client adapter combinations need runtime testing on old, intermediate, and current versions of each listed platform.
- Folia scheduling has not yet been smoke-tested on a Folia server.
- The public plugin API, spectator workflow, external database adapters, and automated actions are not included in this MVP.

See [docs/architecture.md](docs/architecture.md) for the internal design and planned extension points.
See [docs/compatibility-matrix.md](docs/compatibility-matrix.md) for the runtime test plan and current results.

---

Licensed under GPL-3.0. See [LICENSE](LICENSE).
