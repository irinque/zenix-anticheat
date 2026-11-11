# Public Plugin API

ZenixAC exposes Bukkit events as nested classes of `irinque.zenixac.ZenixEvents`. Add the ZenixAC plugin JAR to the compile classpath of an integration plugin and declare a soft dependency on `ZenixAC` when the integration is optional.

| Event | Fields | Fired when |
| --- | --- | --- |
| `ZenixEvents.ViolationDetected` | Violation record and whether the staff-alert threshold was crossed. | A movement violation record passes the per-check record throttle. |
| `ZenixEvents.PlayerSessionStart` | Started player session. | A tracked player joins or is registered. |
| `ZenixEvents.PlayerSessionEnd` | Session and end timestamp. | A tracked player leaves or ZenixAC shuts down. |
| `ZenixEvents.ConfigurationReload` | Success flag and safe error reason on failure. | Settings reload succeeds or fails. |
| `ZenixEvents.CommandCompleted` | Subcommand and player UUID; `null` player UUID means console. | The command dispatcher finishes handling a subcommand. |

All events are observation-only and are not cancellable. They are dispatched synchronously on the thread that handled the triggering action. On Folia, an event caused by a player action may run on that player's region thread. Integrations must not assume every event runs on one global server thread, and must schedule entity or world work on the appropriate Folia scheduler.

`ViolationRecord` contains player UUID/name, session UUID, stable check code, timestamp in epoch milliseconds, violation level, bounded diagnostic text, optional client protocol, and ping. The MVP emits only the `MOVE_RATE` check code. Records are throttled per player and check; the event does not fire for every low-level movement sample.

The API is currently shipped inside the plugin JAR and has no separate artifact or compatibility guarantee yet. Consumers should compile against the released plugin version they support and treat new event types or record fields as additive API changes.
