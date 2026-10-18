# Plan: the PC sends to the phone ("phone push")

Status: **built 2026-10-17** (plugin 82 tests, app 94 tests); not yet tried on a real phone and PC. Old Local Server code removed. Replaces the Local Server direction of `plans/lan-first.md` (see "What changes from LAN-first"). Decided by Aub after the Hub research below.

## Why
- **The Hub rule:** "Plugins which expose player information over HTTP" are rejected. The plugin's Local Server (the phone asks the PC) is exactly that. osrs-mcp, the closest match, was rejected in May 2026. No maintainer has approved a LAN server serving bank and inventory data.
- **The accepted pattern:** plugins *sending* the player's own data to something the player owns, opt-in with the IP warning. Verified on the Hub on 2026-10-17:
  - osrs-bank-sync: full bank to any URL, defaulting to a program on `127.0.0.1`.
  - ha-exporter: game data to the player's Home Assistant. Its review fixes were co-authored by riktenx.
  - ironman-path-sync, runeglass, ironwise: bank, skills and timers to companion services.
- **The new design:** the phone becomes the thing the plugin sends to. There is no server to host and nothing extra to install, and the plugin opens no listening socket at all.
- **Research notes:** two Gemini reports (prompt: `docs/research/hub-socket-gemini-prompt.md`) were checked against the plugin-hub repo. Corrections: ha-exporter is #10704 (not #10861), runeglass was added 2026-08-27, and ge-offer-webhook is disabled as unmaintained, not banned. Gemini could not see the RuneLite Discord, where informal guidance lives.

## Decisions (Aub, 2026-10-17)
| Topic | Decision |
|---|---|
| Direction | The plugin sends to the Pssst app on the phone. The plugin opens no port |
| Background | **Yes: short listen windows.** The app listens about 75 s every ~15 min (Android decides exactly when), plus whenever it is open |
| Bank | **Separate "Include Bank" toggle, on by default** once sending is enabled, with its own warning |
| Send rate | **On change, at most every 2 minutes** while playing, plus an immediate send on logout and when RuneLite closes |
| Phone address | **One address**: the phone's home Wi-Fi address, or its Tailscale address (never changes, and also works at home) |

## How it works
```
RuneLite plugin ──POST /sync──▶ Pssst app on your phone (listening while open, or in short background windows)
        │                                   │
   saves last-snapshot.json            saves the snapshot (Room), runs timers and notifications from it
```
1. **Pairing:** you copy the Sync Token from the plugin into the app (as today). The app shows the phone's address, which you type into the plugin's "Phone Address".
2. **Sending:** when the game state changes, the plugin sends at most every 2 minutes. If the phone isn't listening (connection refused, which is instant and carries no data), the plugin keeps the snapshot as **unsent** and retries every 30 s until it lands. Once delivered, it stays quiet until the next change.
3. **Receiving:** the phone listens while the app is open, and for ~75 s in each background window. A 30-second retry therefore always lands inside a 75-second window.
4. **Restart:** the snapshot file remembers what was delivered. If RuneLite closed with unsent data, it is delivered the next time RuneLite runs and the phone is listening.
5. **Contract:** the same `POST /sync` body the Worker already accepts (`StateDTO`, `Authorization: Bearer <sync token>`). The phone and the Worker are interchangeable targets for the plugin.

**Side effect: there is no Windows Firewall prompt any more.** The PC only makes outgoing connections, and the plugin no longer needs to know the PC's own IP.

## Work items
### Plugin (`PssstRunelite`)
| ID | Change | Files | Size |
|---|---|---|---|
| PP-1 | Remove `LocalStateServer`, its test and the Local Server section (`enableLocalServer`, `localServerPort`, `bindLoopbackOnly`). Unset the old keys once on start-up so stale values don't linger. `HubRulesTest` also fails on `ServerSocket` | `LocalStateServer*`, `CompanionConfig`, `CompanionPlugin`, `HubRulesTest` | S |
| PP-2 | New "Phone" section (names are **proposals**): `Send to My Phone` (off by default, IP warning), `Phone Address` (IP or `IP:port`, default port **8789**; it must be a home-network, link-local or Tailscale address or the plugin refuses it and the panel says why), `Include Bank` (on by default, its own warning naming the bank and potion storage) | `CompanionConfig`, compliance test | S |
| PP-3 | Delivery loop: one schedule on the injected executor tracks each target (phone, and your own server if set). It sends when the state changed and 2 minutes have passed since the last send (immediately on logout and on `ClientShutdown`), and retries every 30 s while unsent. Connect timeout 3 s through the injected client's `newBuilder()` (shares RuneLite's pool; confirm reviewers accept it, otherwise use the default timeout). 401 stops and shows "phone rejected the token"; 403/429 keep today's back-off. Pure scheduling logic in a `DeliveryPolicy` class with a clock, for tests | `NetworkDispatcher` → `Delivery*`, `CompanionPlugin` | M |
| PP-4 | Remember deliveries: `last-snapshot.json` also stores the `observedAt` last delivered to each target, so start-up resumes unsent data | `SnapshotStore` | S |
| PP-5 | `Include Bank` off → `bank` and `potion_storage` (and their prices) are left out of the body; the app then shows "Bank sharing is off" instead of an empty bank | `CompanionPlugin`, `StateDTO` docs | S |
| PP-6 | Panel: status ("Sending to your phone", "Phone app not open: will deliver when it is", "Phone rejected the token", "Phone Address isn't a home address"), last delivered X ago, the masked token with Show/Copy. The PC address list goes (no longer needed) | `PairingPanel`, `LanAddresses` (delete from the plugin) | M |
| PP-7 | `Send to My Server` (Worker, under Advanced) uses the same delivery loop, so it gains the 2-minute pacing and the unsent retry | `CompanionPlugin` | S |
| PP-8 | Tests: `DeliveryPolicy` (pacing, retry, flush, 401 stop), Phone Address validation, config defaults and warnings, the Include Bank payload, and resume-after-restart via `SnapshotStore` | tests | M |
| PP-9 | Docs: `CLAUDE.md` (contract: the phone accepts `POST /sync` on 8789), `GUIDELINES.md` (the plugin opens no socket; Known risk resolved), README, and the Hub `warning=` text in `hub-submission.md` | docs | S |

### App (`PssstAndroid`)
| ID | Change | Files | Size |
|---|---|---|---|
| AP-1 | **Refactor first:** move everything `SyncWorker` does after fetching (monotonic guard, completion alerts, reminders, Room upsert, prices, status) into `SnapshotIngest`, used by both the receiver and the Worker pull. No behaviour change; existing tests stay green | `worker/SyncWorker.kt`, new `data/SnapshotIngest.kt` | M |
| AP-2 | `PhoneReceiver`: a small HTTP listener on 8789 (raw `ServerSocket` on a coroutine; no new library). `POST /sync` only, Bearer token compared in constant time against the saved token, body ≤ 256 KB, JSON object only, home-network/Tailscale peers only, one connection at a time, read deadline. Answers 200/400/401/404/405/413. Feeds `SnapshotIngest` with `updated_at` = receipt time | new `receiver/PhoneReceiver.kt` | M |
| AP-3 | When it listens: while the app is in the foreground (`ProcessLifecycleOwner` start/stop), plus a background window. The existing 15-minute periodic job becomes, in phone mode, "listen 75 s, then stop". A Wi-Fi/VPN join while the process is alive also opens a window (the existing `NetworkSyncTrigger`) | `SyncScheduler`, `SyncWorker` (or a new `ListenWorker`), `PssstApplication` | M |
| AP-4 | Address detection: the phone's IPv4 on the active Wi-Fi and on a VPN (`100.64.0.0/10` labelled Tailscale), from `ConnectivityManager`/`LinkProperties`. Port the ranking rules from the plugin's `LanAddresses` | new `util/PhoneAddresses.kt` | S |
| AP-5 | Setup and pairing: paste the Sync Token. The screen shows "Type this into RuneLite → Pssst Companion → Phone Address: `192.168.1.42`" (plus the Tailscale address if present) with Copy, then "Waiting for your PC…" until the first delivery. "Use my own server instead" (a Worker URL) moves under an Advanced link | `SetupScreen.kt`, `MainViewModel` | M |
| AP-6 | Modes: **phone mode** (no server URL: listen) or **server mode** (Worker URL: pull, as today). The drawer shows the phone address and "Last received from your PC X ago"; "Game state from X ago" is unchanged | `SyncScheduler`, `DashboardScreen`, prefs | S |
| AP-7 | Bank sharing off: no bank in the payload → the Bank and Potion tabs say "Bank sharing is off in RuneLite", and the cached bank is cleared | `MainViewModel`, `BankScreen`, `PotionStorageScreen` | S |
| AP-8 | Tests: the receiver (auth, wrong token, size, method, path, peer filter, a concurrent second client), ingest equivalence with the old pull, listen-window start/stop, address ranking, phone vs server mode | tests | M |
| AP-9 | Docs: Android `CLAUDE.md` (receiver contract, modes, the windows) | `CLAUDE.md` | S |

### Worker
No change. It remains a valid "Send to My Server" target, and the nags base.

### Count
18 items: 9 plugin, 9 app. 9 S, 9 M, 0 L.

## What changes from LAN-first (`plans/lan-first.md`)
| LAN-first item | Now |
|---|---|
| PL-1 snapshot on disk, PL-3 `observedAt`, PL-4 bank carry, PL-5 heartbeat off the client thread, PL-13 Hub-rule scan | **Kept** (PL-1 gains delivery marks, PP-4) |
| PL-2 serve the snapshot before login, PL-6 no interrupt, PL-10 This PC Only, peer filter on the server | **Removed** with the server (PP-1) |
| PL-7 own server under Advanced | **Kept**; it uses the new delivery loop (PP-7) |
| PL-8 pairing panel | **Reworked**: phone status instead of PC addresses (PP-6) |
| AN-1 quiet unreachable, AN-2 network trigger | **Server mode only**; in phone mode the trigger opens a listen window |
| AN-3 reminders from cache, AN-5 game-state age, AN-6 monotonic guard and prices | **Kept** (moved into `SnapshotIngest`) |
| Branch A/B question | **Closed**: no listening socket in the plugin |

## Acceptance
- No `ServerSocket` in the plugin (`HubRulesTest`). The plugin's only network activity is POSTs to the configured phone and (optionally) server.
- With the app open on the same Wi-Fi: a change in game reaches the phone within 2 min while playing, and immediately on logout.
- With the app closed: unsent data arrives within one background window (typically ≤ 15 min, longer under Doze), with no notification shown for listening.
- App not reachable: the plugin makes one connection attempt every 30 s and stops once delivered. Nothing is logged at warn level for a refused connection.
- RuneLite closed with unsent data: delivered the next time RuneLite runs and the phone listens.
- A wrong token is refused (401), and the plugin panel says so. A non-home Phone Address is refused with a clear message.
- Include Bank off: no bank in the body, and the app says sharing is off.
- Worker mode still works end to end (existing tests plus `SnapshotIngest`).

## Test plan (real devices)
1. Same Wi-Fi, app open: pair, play, and see updates arrive within 2 min; log out and see an immediate update.
2. App closed, phone idle: play 20 min, then check that "Last received" moved without opening the app. Note how late the windows run.
3. PC shutdown with unsent data: change something, close the app, shut the PC down within 30 s. Start RuneLite (no login) and open the app: the data arrives.
4. Away from home, then back: no errors in the plugin panel beyond "phone app not open"; delivery resumes at home.
5. Tailscale: the Phone Address is the phone's `100.x`; it works on mobile data.
6. Wrong token, wrong address, Include Bank off.

## Risks
| Risk | Mitigation |
|---|---|
| Doze stretches background windows to hours | Opening the app always works within 30 s; timers run from cache regardless. Measure in test 2 |
| The phone's home address changes (DHCP) | The app shows the current address; the plugin panel says "phone not reachable". Recommend Tailscale or a router reservation in the guide |
| A guest or "client isolation" Wi-Fi blocks phone↔PC | Same as before; Troubleshooting row |
| Reviewers still dislike any player data over HTTP | It is now exactly the accepted pattern (send to your own device, opt-in, IP warning, bank opt-in, 2-min pacing). The Hub warning text says so |
| `newBuilder()` on the injected OkHttp client questioned | Fall back to the default timeout; refused connections are instant anyway |
| A listening port on the phone | Token required, home-network peers only, only while open or in a 75 s window, POST-only, size-capped |

## Residual gap (updated user-facing sentence)
*"Your phone gets new data when the Pssst app is open, or every so often in the background, while your phone can reach your PC. If your PC shuts down before that, the phone shows your previous state and timers until RuneLite and the app next meet."*

## Order
1. AP-1 (refactor, no behaviour change), then AP-2 and PP-1 to PP-3 in parallel: the contract (`POST /sync` + `StateDTO`) is already fixed.
2. PP-4 to PP-7, then AP-3 to AP-7.
3. PP-8 and AP-8 alongside, PP-9 and AP-9 last, then the real-device run.
4. The Hub-submission plan (`plans/hub-submission.md`) continues on top. Its guide pages lose the firewall step and gain "type your phone's address into RuneLite".
