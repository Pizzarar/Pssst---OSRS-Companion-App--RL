# Plan: LAN-first

Status: planned, nothing built. Written 2026-10-17. Covers all three repos (`PssstRunelite`, `PssstAndroid`, `PssstWorker`).

## 1. Summary

**Decision.** Same Wi-Fi becomes the default and only documented path: the phone pulls `GET /state` from the plugin's Local Server. Tailscale is a one-line note, since it is the same code path with a different IP. The Pssst Worker stays in the code but leaves the user guide. It is kept only as the fallback if the Hub rejects the listening socket (Branch B), and as the base for Discord group nags (`plans/discord-group-nags.md`). The direction is always phone pulls from PC.

**Hub answer: unclear** (§2). RuneLite's rejected list says *"Plugins which expose player information over HTTP"*, and a maintainer used that line to reject a local/LAN HTTP server in 2026. The same maintainer merged a `0.0.0.0` HTTP server that serves XP to a device on the LAN, and a loopback WebSocket that serves the bank was merged in August 2026.

**Recommendation right now: build the shared work, ask the maintainers, and hold the Branch A-only items until they answer.**
- 19 of the 35 items, including 4 of the 9 M items, are needed in both branches: snapshot persistence, `observedAt`, quiet unreachable handling, timers from cache, the staleness UI, and the docs that don't depend on the path. Start them now.
- Send the maintainer question in §2 this week, before the Hub PR.
- Branch A stays the target. Its own items (pairing panel, the `This PC Only` text, the LAN-first guide pages) wait for the answer. All are S or M except the uncommitted QR item (PL-9).
- Keep the outbound path (`Send to My Server`) working throughout. It is Branch B and it is the nags' base.

Verified facts this plan rests on:
- `:8788` is **plain HTTP/1.1**, one route (`GET /state`). There is no WebSocket. `LocalStateServer.java` uses a raw `ServerSocket`, one accept thread, an `Authorization: Bearer` check in constant time, an 8 KB / 3 s read cap, and `Connection: close`. There is no class called `LocalWebSocketServer`; the only trace of that name is the config section key `localWsSection`.
- The response envelope is `{ data, updated_at }`, with `updated_at` in ms set when the plugin serializes. `syncState()` runs about 2 s after any change, and every 1500 ticks (15 min) while logged in.
- Before login after a RuneLite start, the server answers **404**: `latestStateJson` is null until the first sync. The app shows "Connected, no state for this token yet" and keeps its cache.
- The app already caches the last snapshot in Room (`player_states`). Timers and reminders are computed from absolute `observedAt`/`readyAt` and `nowMs`, not from sync time. Gaps are in §3.B.

## 2. Hub socket research

### Rules
| Source | What it says | Link |
|---|---|---|
| Rejected or Rolled-Back Features (wiki), "Not currently being considered" | "Plugins which expose player information over HTTP." | https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features |
| Same page, forbidden functions | `Thread.interrupt`, `Thread.sleep`, and "use of `java.io` file APIs" are forbidden. File I/O must use `net.runelite.client.util.Filepath`. "Network IO" is a *sensitive* API: allowed, with extra review. | same |
| `example-plugin/AGENTS.md`, Data & Privacy | "No exposing player information over HTTP". It has no rule about sockets, ports or LAN. File I/O goes through `Filepath`, which needs `PluginDescriptor.internalName`. | https://github.com/runelite/example-plugin/blob/master/AGENTS.md |
| Plugin Hub README | Nothing on sockets. New dependencies need manual verification. | https://github.com/runelite/plugin-hub/blob/master/README.md |

### Precedents (plugin-hub PRs)
| PR | What | Outcome | Relevance |
|---|---|---|---|
| [#11453 osrs-mcp](https://github.com/runelite/plugin-hub/pull/11453) | Local HTTP MCP server on `127.0.0.1:8282` with Local, **LAN** and relay modes; exposes stats, gear, inventory and location | **Rejected** 2026-05-15 by riktenx, citing the rule | Closest match to Pssst. **Against.** |
| [#10181 streamdeck](https://github.com/runelite/plugin-hub/pull/10181) | Moved from hosting an HTTP server to pushing state to a local Stream Deck server | **Rejected**. riktenx: "The direction (served vs. sent) is irrelevant." | **Against**, and it also threatens Branch B's push |
| [#10300 / #10357 esp32-tracker](https://github.com/runelite/plugin-hub/pull/10300) | Pushes XP to an ESP32 | Challenged under the rule. Adam (RuneLite's author): the rule is "for all of the people who want generic inv/stats/action endpoints for their bots". Then merged. | **For.** States the rule's intent. |
| [#10539 esp32-tracker update](https://github.com/runelite/plugin-hub/pull/10539) | Replaced push with `HttpServer` on **`0.0.0.0`**, no auth, XP polled by a LAN device | **Merged** 2026-02-15 by riktenx | **For.** A LAN-bound server is live on the Hub. |
| [#14607 bank-bridge](https://github.com/runelite/plugin-hub/pull/14607) | Loopback WebSocket (`127.0.0.1:37767-37776`), origin allowlist, serves bank, inventory, equipment and levels | **Merged** 2026-08-25. Only change asked: remove `Thread.interrupt`. Hub manifest carries a `warning=`. | **For**, loopback only |
| [WikiSync](https://github.com/weirdgloop/WikiSync) (`dps/ws/WSWebsocketServer.java`) | Loopback WebSocket with an origin allowlist (`dps.osrs.wiki`, `tools.runescape.wiki`, `localhost`) | On the Hub | Loopback only, run by the Wiki |
| [#10595 expose-bank-state](https://github.com/runelite/plugin-hub/pull/10595) | Loopback HTTP, `{bankOpen}` only | Merged 2026-02-24 | Loopback, tiny payload |
| [#15610 expose-interface-state](https://github.com/runelite/plugin-hub/pull/15610) | Loopback HTTP, interface state | Closed: "no legitimate use outside of botting" | Shows the review asks about purpose |

### Classification: **unclear**
- Against: the rule is explicit. Pssst serves a broad state document (bank, inventory, skills, GE). The closest analog (osrs-mcp, which also had a LAN mode) was rejected. One maintainer says pushing the data out is no different from serving it.
- For: the rule's stated intent is generic endpoints for bots. A LAN-bound server was merged eight months ago. Pssst is opt-in twice (Enable Sync, then `This PC Only` off), authenticated with a 128-bit token, read-only, has one route, carries no location or interface state, updates on change rather than every tick, and serves one named app.
- Loopback-only gives Pssst nothing: the phone can't reach `127.0.0.1` on the PC. So "loopback allowed, LAN not" means **Branch B**.

### Question for the maintainers
Post in the RuneLite Discord. In #10181 the maintainers asked for policy follow-ups there rather than in repeat PRs.

> Pssst Companion (not submitted yet) is a read-only companion for an Android app. With Enable Sync on (off by default, IP warning), it serves one JSON document: the player's own bank, inventory, skills, GE offers, Slayer task and farming/birdhouse timers. It has no location or interface state, updates on change (2 s debounce), and lives at `GET /state` on port 8788. Every request needs a 128-bit token in the `Authorization` header. It binds 127.0.0.1 by default; a second opt-in with its own warning binds 0.0.0.0, so the player's phone can read it over home Wi-Fi or Tailscale. Given "Plugins which expose player information over HTTP", would you accept this? If not, is it acceptable to push the same document to a server the player runs themselves (opt-in, IP warning)? We looked at esp32-tracker (#10539, merged), bank-bridge (#14607, merged) and osrs-mcp (#11453, rejected).

- Answer "yes": Branch A.
- Answer "no, but push is fine": Branch B.
- Answer "neither": the plugin can't sync anything, and the project needs a rethink. That is out of scope here.

### Branch B options
| Option | What | Cost | Verdict |
|---|---|---|---|
| B1. Worker only, with less friction | Plugin pushes (existing `NetworkDispatcher`). Worker gets a Deploy to Cloudflare button with D1 provisioned, and the token is set as a secret without the `hash-token` step. App unchanged. | Plugin M (PL-11), Worker M+S (WK-3, WK-4), docs M (DOC-7), app S (AN-10) | **Recommended.** Every user needs a free Cloudflare account, which strains guardrail Q5 (see Open questions). |
| B2. Home relay | Productize `pssst-mock-server` (Node or Docker on a NAS, Pi or the PC). Plugin pushes to it; phone pulls from it on the LAN. | Mock server M (allowlist, caps, persistence), docs M | Document as a tinkerer option only. Needs an always-on box. |
| B3. PC helper program | A separate program the plugin pushes to over loopback, which then serves the LAN | M | **Rejected.** It works around the rule's spirit, and #10181 says direction doesn't matter. |
| B4. Sideload outside the Hub | — | — | **Rejected.** Not viable for normal users. |

## 3. Work items

IDs: PL plugin, AN Android, WK Worker, DOC docs, DG Discord. Branch: A = only if LAN is allowed, B = only if it is not, both = needed either way. All setting names are the current ones; every rename is marked **proposal**.

### A. Plugin (`PssstRunelite`)
| ID | Change | Files | Branch | Size |
|---|---|---|---|---|
| PL-1 | Persist the last snapshot to disk: `getPluginDirectory()` (set `internalName`, e.g. `pssst-companion`, matching the Hub manifest name), write `last-snapshot.json` = `{profileKey, observedAt, data}` atomically (`createTempFile` then `moveTo(ATOMIC_MOVE)`). Write on the injected executor only when the JSON changed. Flush on `GameStateChanged(LOGIN_SCREEN)`, and on exit via `ClientShutdown.waitFor(future)`. No `java.io.File`/`Files`. Delete the file when `Enable Sync` is turned off. | `CompanionPlugin`, new `SnapshotStore`, test | both | M |
| PL-2 | Serve the persisted snapshot as soon as the Local Server starts, before login, so the phone gets the last session's final state the next time RuneLite runs. `updated_at` = serve time, `observedAt` = from the file. | `CompanionPlugin`, `LocalStateServer` | A | S |
| PL-3 | Add `observedAt` (epoch ms) to `StateDTO`: the time of the last client-thread refresh while `LOGGED_IN` that the snapshot reflects. It sits inside `data`, so the Worker and mock pass it through unchanged. Optional `bankObservedAt` for PL-4. | `StateDTO`, `CompanionPlugin`, compliance test, `StateDto.kt` | both | S |
| PL-4 | Carry the bank and potion storage forward from the snapshot for the **same RS profile** until the bank is opened. Today `shutDown()` clears `currentBank`, and after login the bank container is null until opened, so the first publish sends `bank: []` and the app overwrites its cached bank (verify in game). Clear all per-account state when the profile key changes. Today another account's bank survives a relog. | `CompanionPlugin`, test | both | M |
| PL-5 | Heartbeat off the client thread. `onGameTick` calls `syncState()` directly, so the full state is serialized on the client thread every 15 min. Call `triggerDebouncedSync()` instead. A prerequisite for PL-1's disk write. | `CompanionPlugin` | both | S |
| PL-6 | Remove `acceptThread.interrupt()` from `LocalStateServer.stop()`; closing the socket already ends `accept()`. `Thread.interrupt` is on the forbidden list, and bank-bridge was asked to remove exactly this. | `LocalStateServer`, `LocalStateServerTest` | A | S |
| PL-7 | Fate of `Send to My Server` / `Server URL`: **keep both, in a section closed by default** (`closedByDefault = true`). **Proposal:** rename the section display name "Your Own Server" to "Advanced: Your Own Server". Keys (`enableByosPush`, `serverUrl`, `byosSection`) stay unchanged, so no migration is needed. Warnings stay. Why not remove them: they are Branch B's whole path and G1's transport, and removing then re-adding network code costs two extra Hub reviews. | `CompanionConfig` | A | S |
| PL-8 | Pairing panel: a side panel (`NavigationButton` + `PluginPanel`) showing the phone address(es) `http://<IPv4>:<port>`, found with `NetworkInterface` (up, not loopback, not virtual; site-local IPv4 first; a `100.64.0.0/10` address labelled Tailscale). Also the server status (off / This PC only / on your network), and the Sync Token masked with Show and Copy. No subprocess (`ipconfig`), no network calls. | new `PairingPanel`, `CompanionPlugin` | A | M |
| PL-9 | QR code in the panel (evaluate after PL-8, **not committed**): encodes `{url, token}`. RuneLite has no QR encoder (no ZXing in `client-1.13.3`), so it needs a new dependency (manual Hub verification) or a hand-written encoder. Hidden behind a click, auto-hidden after 60 s (streamers). | `PairingPanel` | A | L |
| PL-10 | `This PC Only`: keep the name and the default **on**. Flipping it off by default would bind 0.0.0.0 as soon as Enable Sync is on, and that can't be defended in review. **Proposal** text: description "On: only this PC can connect. Turn it off so the Pssst app on your phone can connect over your Wi-Fi or Tailscale." Warning: "Turning this off lets devices on your network reach this PC on the chosen port. They still need your Sync Token, and the connection is not encrypted. Only do this on a network you trust, such as your home Wi-Fi. If Windows asks, allow Java on Private networks only." Plus: in LAN mode, answer only private-range clients (RFC 1918, `100.64/10`, link-local, loopback) and close any other connection. | `CompanionConfig`, `LocalStateServer`, tests | A | S |
| PL-11 | Branch B: delete `LocalStateServer` and the Local Server section, unset its keys on start (`configManager.unsetConfiguration`), and make push the main path: section open by default, named "Your Server" (**proposal**). PL-1 still applies (bank carry-forward); PL-2 does not. | `CompanionConfig`, `CompanionPlugin`, `LocalStateServer*`, tests | B | M |
| PL-12 | Hub submission: plugin-hub manifest `warning=` (bank-bridge precedent) stating that the plugin serves the player's own state on port 8788 to the paired app, token-protected, LAN only if This PC Only is off. The PR text answers the precedents in §2. | plugin-hub PR | A | S |
| PL-13 | Guardrails: `GUIDELINES.md` gets the HTTP rule and the precedents, `Filepath`-only I/O, and no `Thread.interrupt`. "Known risks" points here. `CLAUDE.md` contract gets `observedAt` and the snapshot file. | `GUIDELINES.md`, `CLAUDE.md` | both | S |

Pairing ranked by user value against Hub risk:
1. **PL-8 (address panel).** High value: it removes the `ipconfig` step, the most common failure in a LAN guide. Low risk: plain Java and Swing.
2. **PL-9 (QR).** Highest value, because the phone no longer types a 32-character token. Medium risk and cost: a dependency or hand-written encoder, a scanner in the app, and the token on screen. Defer.
3. **mDNS/NSD.** Medium value, since the token is still needed. **High risk:** a second listening socket (multicast 5353), a dependency (jmDNS starts its own threads) or a hand-rolled responder, and it announces the PC to the whole LAN. **Not planned.**

### B. Android (`PssstAndroid`)
| ID | Change | Files | Branch | Size |
|---|---|---|---|---|
| AN-1 | Quiet unreachable. A connect failure (`ConnectException`, connect timeout, `NoRouteToHostException`, `UnknownHostException`) returns `Result.success()` with **no retry** and a neutral status: "PC not reachable, showing saved data" (proposal). It does not count toward "Server unreachable". Keep retry with backoff only for 5xx and mid-transfer drops. Keep 401/403/429 exactly as today. Use a 5 s connect timeout for private-range hosts. Today any exception means `Result.retry()`, and the one-off `syncNow` has no constraints, so every app open away from home starts a backoff chain. | `SyncWorker`, `StateApiService`, `SyncWorkerTest` | both | M |
| AN-2 | Sync triggers: app open (`MainActivity.onResume` → `syncNow`, **exists**); network regained via a `ConnectivityManager.NetworkCallback` registered while the process is alive, which runs `syncNow` on a validated Wi-Fi network, at most once per 60 s; periodic every 15 min (**exists**, `CONNECTED`). `syncNow` gains the `CONNECTED` constraint. Android has no manifest broadcast for connectivity since API 24, so with the process dead the periodic job is the catch-up. Documented, not worked around. | `MainActivity` or a new `PssstApp`, `SyncScheduler` | both | S |
| AN-3 | Reminders from cache. `syncReminders` runs only after a **successful** sync, so turning notifications on while away from the PC queues nothing. Also re-plan from the cached Room snapshot on app start and when notifications are toggled on. | `SyncScheduler`, `MainViewModel`, test | both | S |
| AN-4 | Reminder lateness. Reminders are WorkManager one-off jobs with `setInitialDelay`, which Doze can defer to maintenance windows. Measure lateness on Aub's phone first. If it is over about 15 min, move only the reminder firing to `AlarmManager.setAndAllowWhileIdle` (inexact, no exact-alarm permission), keeping WorkManager for sync. Optional. | `SyncScheduler`, `ReminderWorker` | both | M |
| AN-5 | Staleness UI. Main line: "Game state from X ago", from `observedAt` (fallback `updated_at`). Secondary: "Checked X ago" (`lastSyncedAt`). Today the top bar shows only `lastSyncedAt`, and `lastUpdated` is parsed but never shown. No colour alarm or nag: just the age, plus "PC not reachable" from AN-1. | `DashboardScreen`, `MainUiState`, `MainViewModel` | both | S |
| AN-6 | Read `observedAt`. Ignore a snapshot older than the cached one (a monotonic guard, e.g. a stale PC or a mis-set URL). Stamp prices with `observedAt`, not `updated_at`, because PL-2 serves an old snapshot with a fresh `updated_at`. Store `observedAt` in the entity (Room migration v5→v6) or read it from `rawJson`. | `StateDto.kt`, `SyncWorker`, `PlayerStateEntity`, migration test | both | S |
| AN-7 | Setup screen, LAN-first. It already defaults to LAN (`http://192.168.1.20:8788` placeholder, loopback warning). Change the help line to "Find this address in the Pssst panel in RuneLite" once PL-8 exists, and add the Tailscale one-liner. An https Worker URL keeps working without being mentioned. | `SetupScreen.kt` | A | S |
| AN-8 | QR scan button, only if PL-9 ships. Use ZXing-embedded (needs CAMERA) rather than Play services' code scanner (a Google dependency). | `SetupScreen.kt`, Gradle | A | M |
| AN-9 | Keep Worker support honest: tests for an https URL, `updated_at` in seconds, and `observedAt` present or absent. | `SyncWorkerTest` | both | S |
| AN-10 | Branch B: setup copy points to "the URL of your Pssst Worker". The LAN hint goes. | `SetupScreen.kt` | B | S |

### C. Worker (`PssstWorker`): freeze
| ID | Change | Files | Branch | Size |
|---|---|---|---|---|
| WK-1 | Freeze. These must keep working: `POST /sync`, `GET /state` (`{data, updated_at}` in seconds), `ALLOWED_TOKEN_HASHES` failing closed, the `guard.ts` limits (256 KB, 1 write/s/token, 90-day retention, token format), and the 10 tests. Add one test that `data.observedAt` round-trips (the payload is opaque, so no code change is expected). | `test/` | both | S |
| WK-2 | The README takes over the developer docs that leave the user guide: deploy, `hash-token`, the WAF rule, local dev, and the 8787 port clash with the mock. | `README.md` | both | S |
| WK-3 | Branch B: Deploy to Cloudflare button with the D1 binding and schema, so there is no `wrangler d1 create` or placeholder `database_id`. | `README.md`, `wrangler.toml`, `package.json` | B | M |
| WK-4 | Branch B: simpler token setup, e.g. accept a `SYNC_TOKEN` secret and hash it at startup (the secret store is already encrypted), keeping `ALLOWED_TOKEN_HASHES` for multiple tokens. | `src/`, tests | B | S |

Can be deprioritized in both branches: WAF and retention tuning, any UI, multi-user features (beyond G2's group mode), and dashboards.

### D. Docs
The handoff (`setup-docs-handoff.md`), the wiki prompt (`setup-wiki-prompt.md`) and the code review are **not on disk** in any of the three repos or worktrees (ROADMAP N4 calls the handoff a "project doc"). The edits below are written against those documents' names and against the draft wiki in the `claude/pssst-setup-wiki-99bed1` worktree (`docs/wiki/`, uncommitted).

| ID | Change | Files | Branch | Size |
|---|---|---|---|---|
| DOC-1 | `setup-docs-handoff.md`: the one path is Same Wi-Fi, and Tailscale is a note on that page. Remove the Worker screenshots from the 8-screenshot list. Add the pairing panel (PL-8) and the Windows Firewall prompt. Add the residual-gap sentence (§6). | project doc | A (Branch B variant: DOC-7) | S |
| DOC-2 | `setup-wiki-prompt.md` and the wiki: **drop `4c-Your-Own-Server.md`** from the guide and the sidebar. Its content moves to the Worker README (WK-2), with one "Advanced: run your own relay" link from the FAQ. **`4-Choose-a-Connection.md` becomes "Connect your phone"**: no three-path table; Same Wi-Fi steps (Enable Sync on, This PC Only off, address from the panel or `ipconfig`); one line: "Away from home? Install Tailscale on both and use the PC's `100.x` address instead." `4a`/`4b` fold into it. Home and Quick Start lose the "Advanced" path line. | `docs/wiki/*` | A | S |
| DOC-3 | Privacy: keep "What is sent / not sent". "Where it goes" becomes "Your phone reads it from your PC over your own network. It isn't sent anywhere else." New: "A copy of the last snapshot is saved on your PC, in RuneLite's folder for this plugin, so the phone can read it after a restart. It holds no Sync Token. Turning Enable Sync off deletes it." The Worker sentence goes; "If you change the Sync Token" drops the Worker step. The Discord group line stays as planned. | `Privacy.md` | both | S |
| DOC-4 | Troubleshooting, "Data looks old": replace the single cause with a table of cause and fix. (1) RuneLite hasn't run since: open RuneLite (no login needed once PL-2 ships). (2) The PC shut down before the phone fetched: the §6 sentence; open the app before turning off the PC. (3) The phone isn't on the same Wi-Fi: it updates when you're home. (4) The PC's IP changed: copy the new address from the panel; optionally reserve the IP in your router. (5) Another account logged in: the snapshot belongs to the last account. Add a row for "PC not reachable" (AN-1: normal when away). | `Troubleshooting.md` | both | S |
| DOC-5 | FAQ "Does it work with my PC off?": "Timers and notifications, yes. They run on the phone from the last snapshot. New data, no. It arrives the next time RuneLite runs while your phone is on the same Wi-Fi." Update `Timers.md` to match, and add the §6 sentence. | `FAQ.md`, `Timers.md`, `Home.md` | both | S |
| DOC-6 | `ROADMAP.md`: edits proposed below. **Not applied.** | `docs/ROADMAP.md` | both | S |
| DOC-7 | Branch B: rewrite the guide around the Worker. `4c` becomes the main page (with WK-3/WK-4), Same Wi-Fi and Tailscale are removed, and Privacy says the data goes to *your* Cloudflare account. | `docs/wiki/*`, Worker README | B | M |

Proposed `ROADMAP.md` edits (DOC-6):
- **N3** becomes: "Ask the Hub maintainers about the Local Server (question in `plans/lan-first.md` §2), then pick Branch A or B."
- **N4** becomes: "Setup docs, LAN-first (`plans/lan-first.md` §3.D). Worker pages leave the guide."
- **N5** adds: "Windows Firewall prompt on the first LAN bind, reminder lateness under Doze (AN-4), PC shutdown mid-session."
- New Now rows: **L1** "Shared plugin work: PL-1, PL-3, PL-4, PL-5, PL-13"; **L2** "Shared app work: AN-1 to AN-6, AN-9"; **L3** "Branch items after the Hub answer: PL-6 to PL-12 or PL-11, AN-7/AN-10, WK-3/WK-4, DOC-2/DOC-7".
- Later, G: add "Depends on the outbound path kept by PL-7 (or PL-11)."
- Done: unchanged.

### E. Discord group nags
| ID | Change | Files | Branch | Size |
|---|---|---|---|---|
| DG-1 | No blockers. G0 (`readyAt` on `farming[]`) is independent of `observedAt` and can land in the same contract change. G1 needs `NetworkDispatcher` and its 403/429 back-off, which PL-7 keeps (and PL-11 promotes). G2–G7 are new Worker code on top of the frozen WK-1 surface. G8's pages stay "advanced" and outside the main guide, which matches DOC-2. One note for G1's PR: #10181's "direction is irrelevant" means even the timers-only push may be questioned. Its narrow payload (nickname + `{kind, patch, readyAt}`) is the answer. | `plans/discord-group-nags.md` (add the note) | both | S |

**Count:** 35 items. 25 S, 9 M, 1 L (PL-9, not committed). Both branches: 19. A only: 11. B only: 5 (PL-11, AN-10, WK-3, WK-4, DOC-7). DOC-1 has a B variant inside DOC-7.

## 4. Acceptance criteria
- **PL-1:** After a session, `last-snapshot.json` exists under the plugin's `getPluginDirectory()`, and no other path is written. A test shows no `java.io.File`, `java.nio.file.Files` or `Paths` import in `src/main`. The write never runs on the client thread (assertion or test seam). Closing RuneLite from the login screen and from in game both leave a file whose `observedAt` equals the last in-game refresh. The file contains no token. Enable Sync off deletes it.
- **PL-2:** Restart RuneLite without logging in, then `curl -H "Authorization: Bearer …" :8788/state` returns 200 with the previous session's `data` and `observedAt`. Before PL-2 it returns 404.
- **PL-3:** `observedAt` is present and in ms. It doesn't move while logged out, even when config changes trigger a sync. `CompanionPluginComplianceTest` is updated. `StateDto.kt` parses it.
- **PL-4:** Restart, log in, don't open the bank: the served `bank` equals the persisted bank. Log into a second account: its served `bank` is `[]` (or that account's own persisted bank), never the first account's.
- **PL-5:** No `syncState()` call is reachable from a `@Subscribe` handler (code review plus a unit test on the heartbeat path).
- **PL-6:** `grep interrupt src/main` finds nothing. `LocalStateServerTest` still passes; `stop()` frees the port within 1 s.
- **PL-7:** The section is collapsed on a fresh profile. Existing `enableByosPush`/`serverUrl` values survive the upgrade. Pushes are unchanged (`NetworkDispatcherBackoffTest` green).
- **PL-8:** On a PC with Wi-Fi, Hyper-V and Tailscale adapters, the panel lists the Wi-Fi IPv4 first, labels the 100.x address as Tailscale, and hides virtual adapters. The token is masked until Show is clicked, and Copy puts it on the clipboard. The panel says "This PC only: your phone can't connect" while that setting is on.
- **PL-9:** Decision recorded (ship or not, and which encoder). If shipped: the phone scans it and pairs without typing, and the code hides itself after 60 s.
- **PL-10:** The new texts are in place, and the keys and default are unchanged. A connection from a public-range source address is closed without a response (unit test with a mocked remote address).
- **PL-11:** No `ServerSocket` remains in `src/main`. Old Local Server keys are unset after the first start. The push path works with Enable Sync, Send to My Server and Server URL alone.
- **PL-12:** The Hub PR includes the manifest warning and links the §2 answer.
- **PL-13:** `GUIDELINES.md` Status and Known risks match the code. `CLAUDE.md` (all three repos) lists `observedAt`.
- **AN-1:** With the URL pointing at a powered-off PC: one attempt per trigger, no `RETRY` state visible in WorkManager's job list, status reads "PC not reachable, showing saved data", the failure counter is unchanged, and no notification appears. 401/403/429 behave as today (existing tests green).
- **AN-2:** Joining home Wi-Fi with the app process alive triggers one sync within 60 s. Toggling Wi-Fi twice in 10 s triggers one. Opening the app away from home leaves no pending sync job afterwards.
- **AN-3:** Pair, sync once, go offline, turn notifications off then on: the reminders for growing patches are queued again from cache.
- **AN-4:** Lateness measured overnight on Aub's phone and recorded in this plan. If the alarm path ships, a reminder fires within 15 min of its time with the phone in Doze.
- **AN-5:** The top bar shows "Game state from 2 h ago" when `observedAt` is 2 h old, even right after a successful sync of a persisted snapshot.
- **AN-6:** A snapshot whose `observedAt` is older than the cached one is not stored, and the status says so. Prices are stamped with `observedAt`. Migration test green.
- **AN-7/AN-10:** The setup copy matches the branch. An https Worker URL still pairs (AN-9).
- **AN-8:** Scanning PL-9's code fills both fields. CAMERA is requested only on tap.
- **AN-9:** Tests cover https, seconds vs ms, and `observedAt` absent.
- **WK-1:** `npm test` green, with the new round-trip test.
- **WK-2:** The README alone gets a developer from clone to a working `/state`.
- **WK-3/WK-4:** A fresh Cloudflare account reaches a working Worker without a terminal, or with one command.
- **DOC-1 to DOC-7:** Every setting name in the docs matches `CompanionConfig` exactly. The §6 sentence appears in FAQ, Timers and Troubleshooting. The guide has no Worker link outside the FAQ's advanced line (Branch A).
- **DG-1:** The note is added to `plans/discord-group-nags.md`, and G0–G8 need no change.

## 5. Test plan
Aub runs everything that needs the game or a real phone (`run-test.bat` in their own terminal). Agents run `.\gradlew.bat build`, `:app:testDebugUnitTest` and `npm test`.

1. **Real phone, same Wi-Fi.** Fresh RuneLite profile: Enable Sync on, This PC Only off. Note whether Windows Firewall prompts, for which executable, and for which network profile. Pair with the address from the panel (PL-8), or from `ipconfig` before PL-8. Check bank, inventory, timers and the "Game state from" line. Plant a patch and confirm the reminder is queued (WorkManager inspector or a log line).
2. **PC shutdown mid-session.** Play, then change the inventory and plant a patch. Shut the PC down within 1 minute, with the phone idle so it hasn't pulled. Expected: the phone shows the older state, and AN-5 shows its age. Boot the PC and open RuneLite **without logging in**. Open the app: the new state and the planted patch arrive (PL-1, PL-2), and the bank isn't empty (PL-4).
3. **Phone away, then back.** Leave home on mobile data. Open the app: it shows "PC not reachable", there is no retry chain, and existing reminders still fire on time (AN-1, AN-3). Come home: one sync within 60 s of joining Wi-Fi while the app process is alive (AN-2), or within the next periodic run otherwise.
4. **Tailscale.** Phone on mobile data with Tailscale on, URL set to the PC's `100.x` address: syncs. The PL-10 source filter allows `100.64/10`. Tailscale off: behaves as in test 3.
5. **Server unreachable.** RuneLite closed with the PC on; then Enable Sync off; then a wrong port. Each gives "PC not reachable" with no retries and no notifications. A wrong token gives an immediate "Unauthorized".
6. **Restart without login** (curl, from the PC and from another LAN device): 200 with the persisted snapshot. Same with `This PC Only` on from another device: connection refused.
7. **Account switch:** log into account 2 after account 1. Account 1's bank never appears in account 2's served state.
8. **Branch B smoke test** (if chosen): push to a local `wrangler dev`, app pointed at it, then tests 2, 3 and 5 against the Worker URL.

## 6. Risks and the residual gap
| Risk | Likelihood | Mitigation |
|---|---|---|
| Hub rejects the Local Server | Medium | Ask first (§2). Branch B is planned, with its shared items already built. |
| Hub also questions push ("direction is irrelevant", #10181) | Low to medium | Push goes only to the user's own server, opt-in with the IP warning (the common Hub pattern). Ask it in the same message. |
| PC's DHCP address changes and the app breaks | Medium | The panel shows the current address (PL-8). Troubleshooting suggests a router reservation. |
| Firewall prompt confuses users or lands on a Public profile | Medium | The PL-10 warning text and a screenshot in DOC-1 (N5). |
| Doze delays reminders | Medium (unmeasured) | AN-4: measure, then switch firing to the alarm path. |
| Snapshot file is personal data on disk | Low | Inside RuneLite's own plugin folder, no token, deleted when Enable Sync is off. Stated in Privacy (DOC-3). |
| Bank carry-forward shows a bank that changed on another device (mobile OSRS) | Low | `bankObservedAt` lets the app show the bank's own age (PL-3/PL-4). |
| Plain HTTP on the LAN exposes the token to the network | Low on home Wi-Fi | Warning text. Tailscale encrypts. Private-source filter (PL-10). |

### The residual gap
The phone can only show what it last pulled. If the PC shuts down before the phone pulls, the phone keeps the previous state **until RuneLite next runs while the phone is on the same Wi-Fi**. Persistence (PL-1/PL-2) and the extra triggers (AN-2) shrink the gap but can't close it.

- **What is lost:** everything that changed between the phone's last successful pull and the PC shutting down.
- **How big that window is:**
  - Phone awake on the same Wi-Fi, or the app opened after the last change: ≤ 15 min, or 0.
  - Phone idle in Doze: periodic jobs move to maintenance windows, so **hours**.
  - App in the "rare" or "restricted" standby bucket: up to about **24 h**, which in practice is the **whole last session**.
- **How long it stays stale:** unbounded. It lasts until the next RuneLite start plus one pull. With PL-2 no login is needed; without it, until the next login.
- **Effect on timers:** patches planted inside the window get no reminder. Patches harvested inside it still get one (a false "grown"). Everything observed before the window keeps working from cache.
- **Branch B comparison:** with push, the window is about 2 s (debounce) while the PC has internet. That is the one real thing the Worker buys a solo player.

**User-facing sentence** (Troubleshooting, FAQ, Timers): *"Pssst shows what your phone last fetched from your PC: if your PC shuts down before your phone catches up, the app shows your previous state and timers until RuneLite is next open on the same Wi-Fi, so open the app once before you switch your PC off."*

## 7. Open questions for Aub
1. **Ask the maintainers before submitting, or submit and let review decide?** Recommended: ask first, with the §2 text. Rejected PRs (#11453) took a month, and a policy answer decides ten items.
2. **Firewall on first LAN bind (real device):** does Windows prompt, for `java.exe` or `RuneLite.exe`, and is your home network Private? Recommended: record it during N5. It decides the PL-10 text and a DOC-1 screenshot.
3. **Reminder lateness on your phone:** plant something with about 1 h left, lock the phone, and note how late the notification is. Recommended: do it before AN-4. Under 15 min late means AN-4 is dropped.
4. **Branch B only: every user needing a free Cloudflare account.** Is that acceptable under guardrail Q5 ("Is anything behind payment or an account?")? Recommended: yes. It is the user's own free third-party account, not a Pssst account, and the nags plan already accepts it for admins. State it plainly on the setup page.
5. **QR pairing (PL-9) worth a dependency or a hand-written encoder?** Recommended: no for now. Ship PL-8, then revisit if pairing is still the top setup complaint.

## Code-vs-doc mismatches found
- The `:8788` server is plain HTTP `GET /state` with no WebSocket. The name `LocalWebSocketServer` (from the planning prompt) doesn't exist, the config section key is still `localWsSection`, and a `SyncWorker` comment says "plugin WS".
- `LocalStateServer.stop()` calls `Thread.interrupt()`, which is on the Hub's forbidden list. `GUIDELINES.md` Status calls the local server compliant.
- `GUIDELINES.md` Known risks says reviewers "may object to any listening socket". The rejected list has an explicit rule ("expose player information over HTTP"), which neither `GUIDELINES.md` nor `CLAUDE.md` mentions.
- `onGameTick` calls `syncState()` directly, so the heartbeat serializes on the client thread. The comment in `triggerDebouncedSync` implies sync runs on the executor.
- After a RuneLite restart the plugin publishes `bank: []` until the bank is opened, and the app replaces its cached bank. Not in any doc; verify in game.
- The app's top bar shows `lastSyncedAt` (phone fetch time). `updatedAt` is parsed into `lastUpdated` but never shown. Prices are stamped with `updated_at`, which PL-2 would make wrong.
- `SyncWorker` retries every exception, and the one-off `syncNow` has no network constraint. Android `CLAUDE.md` describes this as "rides out drops", which is true but doesn't cover the normal away-from-home case.
- The wiki FAQ (draft) says the PC-off case works "only with your own server". Timers already work from cache on every path.
- `CompanionPlugin`'s `@PluginDescriptor` has no `internalName`, which `Filepath` (`getPluginDirectory()`) requires.
- `setup-docs-handoff.md`, `setup-wiki-prompt.md` and `code-review-2026-10-17.md` aren't in any repo or worktree on disk. ROADMAP N4 calls the handoff a "project doc", and the planning prompt places it under `docs/handoff/`.
