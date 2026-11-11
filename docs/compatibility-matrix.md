# Compatibility Matrix

This is the agreed target matrix and test plan. “Pending” means no runtime smoke test has been run for that combination yet. Compilation against Spigot API 1.18.2 does not verify runtime compatibility with every listed release or fork.

## Server Platforms

Run every passive check, staff commands, session lifecycle, SQLite write/query/shutdown, and Folia scheduler behavior on each representative version. Install PacketEvents 2.14.0+ on packet-order runs; also confirm that the other checks still start when it is absent.

| Server implementation | Old target | Intermediate target | Current target | Runtime status |
| --- | --- | --- | --- | --- |
| CraftBukkit / Bukkit-compatible | 1.18.2 | 1.21.11 | 26.3 | Pending |
| Spigot | 1.18.2 | 1.21.11 | 26.3 | Pending |
| Paper | 1.18.2 | 1.21.11 | 26.3 | Pending |
| Purpur | 1.18.2 | 1.21.11 | 26.3 | Pending |
| Folia | 1.19.4 | 1.21.11 | 26.3 | Pending |

For each row, record the exact server build, Java runtime, plugin artifact hash, startup result, command results, clean shutdown, and any warnings. The Folia run must include notifications to staff in another region and a player crossing region boundaries.

## Client Protocols

Test these representative clients against the chosen server versions. Confirm both connection and normal movement/play behavior; a successful login alone is insufficient.

| Client | Adapter path to Paper 26.3 | Protocol visible to ZenixAC | Runtime status |
| --- | --- | --- | --- |
| 1.7.10 | ViaVersion + ViaBackwards + ViaRewind | Expected through ViaVersion API | Pending |
| 1.12.2 | ViaVersion + ViaBackwards | Expected through ViaVersion API | Pending |
| 1.18.2 | ViaVersion-family adapter if required by selected build | Expected through ViaVersion API | Pending |
| 1.21.11 | ViaVersion-family adapter if required by selected build | Expected through ViaVersion API | Pending |
| 26.3 | Native Paper protocol | Expected through ViaVersion API when installed | Pending |

Also test a 26.3 client against a 1.18.2 Paper/Spigot server through the selected ViaVersion stack. Record the exact adapter versions and any version-specific limitations. If translation runs only on a proxy and the backend has no ViaVersion API, protocol reporting is expected to be `unknown`.

## Check Scenarios

For each supported runtime, compare at least these scenarios:

- Ordinary walking, sprinting, jumping, swimming, climbing, special movement blocks, and common movement effects do not produce staff alerts.
- Teleports, world changes, knockback, vehicles, flight, gliding, and liquids reset or skip the relevant movement samples.
- Ordinary melee hits at several ping levels do not produce reach alerts; repeated deliberately out-of-range accepted hits create passive records.
- Typical arm swings, hand interactions, building, and mining do not exceed action-rate thresholds during normal play.
- Fast boats, minecarts, and other supported vehicles on common surfaces do not exceed the vehicle limit; extreme sustained movement creates passive records.
- Normal jumps, levitation, slow falling, knockback, water movement, ladders, and elytra use do not trigger flight alerts; sustained vertical flight and airborne hovering create passive records.
- Normal digging sequences across representative clients do not trigger packet-order alerts; repeated unmatched finish/cancel sequences do.
- Deliberately extreme movement and action patterns create passive records but do not change or restrict the player's actions.
- A sustained test tick delay does not create a false-positive alert; if it does, update the heuristic or mark the affected environment unsupported.
- SQLite startup failure, temporary write failure, full queue, and shutdown with queued records do not stop live event processing or crash the server.
- Players without staff permissions never receive violation or debug details.

Do not call a server or client version verified until these scenarios have been run and the exact build and results are recorded here.
