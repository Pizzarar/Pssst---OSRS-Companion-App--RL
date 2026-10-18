# Pssst — RuneLite plugin

Companion to the Android app at `L:\Dev\Mobile\PssstAndroid` and the optional cloud relay at `L:\Dev\Plugins\PssstWorker` (see their `CLAUDE.md` files). This repo is the Plugin Hub submission, so it holds only the plugin.

## Layout
- `src/main/java/com/pssst/runelite/` — plugin (Java 11 target). `CompanionPlugin` collects state; it **sends** and never listens (no socket; `HubRulesTest` fails on `ServerSocket`/`HttpServer`). `NetworkDispatcher` POSTs `/sync` to the Pssst app on the phone; `DeliveryPolicy` decides when (on change, at most every 2 min, flush on logout/exit, retry every 30 s while the phone isn't listening, stop on 401, honour 403/429); `PhoneAddress` validates the Phone Address (an IP or a name such as a Tailscale MagicDNS or router name, resolved at send time; every address must be home network, Tailscale or loopback; default port 8789); `PayloadFilter` drops the bank when Include Bank is off. `IconBook`/`IconEncoder` send item icons: the phone's reply lists the ids it has no icon for, the client thread loads them through `ItemManager.getImage` and encodes PNGs in memory, and up to 100 ride along with the next send (15 s catch-up pacing while some wait); they are never saved to disk. `StateDTO` is the payload. `SnapshotStore` keeps `last-snapshot.json` (via `Filepath`) with the delivered marks, so unsent data goes out after a restart; `BankCarry` keeps the same profile's bank across a restart; `PairingPanel` shows whether the phone is up to date and edits the Sync Token and Phone Address.
- `src/test/java/com/pssst/runelite/` — `CompanionPluginComplianceTest` (Hub rules: config defaults/warning, integer-only DTO, URL normalization, push/loopback defaults and warnings); `DeliveryPolicyTest`, `PhoneAddressTest` (also the Include Bank filter) and `NetworkDispatcherBackoffTest` cover sending, `SnapshotStoreTest`/`BankCarryTest`/`PairingPanelTest` the snapshot and panel, `HubRulesTest` scans for forbidden APIs, and `PssstPluginTest` is the dev-client launcher.

## Commands (verified 2026-10-17)
| What | Command | Notes |
|---|---|---|
| Build + tests | `.\gradlew.bat build` | ~25s, 99 tests (compliance, noted-id rule, tracked-id pin, farming/birdhouse parity and trackers, slayer reader, snapshot store, bank carry, delivery policy, phone address, Hub-rule scan). Builds with `-Xlint:deprecation` and has no deprecation warnings (gameval only) |
| Regenerate farming tables | `.\gradlew.bat generateFarmingData` | Rewrites `farming/FarmingData.java` from RuneLite's timetracking classes. Run when `FarmingParityTest` fails after a RuneLite update, then re-check `FarmingBounds` by hand |
| Run dev client | `.\run-test.bat` | **User runs this in their own terminal.** Never launch the RuneLite GUI from an agent |

## Environment
- JDK: Temurin 17.0.20.1 at `C:/Users/aubro/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2`, set once in the user-level `~/.gradle/gradle.properties` (`org.gradle.java.home=...`). It is deliberately **not** in either repo's `gradle.properties`: it is machine-specific, and the Plugin Hub builds with its own JDK. A fresh clone needs that line (or `JAVA_HOME` pointing at a JDK 17).
- `compileOnly 'net.runelite:client:+'` (always latest).

## Cross-repo contract
Payload: `{ observedAt, bankObservedAt, inventory[], bank[], potion_storage[], skills[], ge_offers[], varbits{}, varps{}, prices{}, slayer{}, farming[], birdhouses[] }`, with items as `{id, quantity}` (inventory entries for noted items also carry `baseId`, the unnoted item id that has the name and price). The plugin POSTs the bare payload to `/sync` on each target: the Pssst app's receiver (port 8789 on the phone) or the Worker. The Worker and the mock server return it from `GET /state` as `{ data, updated_at }` (seconds from the Worker, ms from the mock); the app stamps a received snapshot with its arrival time in ms. Auth everywhere is `Authorization: Bearer <sync token>` (never a query parameter). The Worker accepts tokens of 16-128 chars of `A-Za-z0-9_-` and caps bodies at 256 KB.
`farming[]` entries are `{regionId, regionName, patchName, patchType, varbitId, value, observedAt, cropName, itemId, cropState, stage, stages, tickMinutes}`. `regionId + varbitId` identify a patch (the varbit alone is a per-region slot). `observedAt` is epoch **ms** and only moves when `value` changes. Crop fields are absent for unknown values. `tickMinutes` is 0 when nothing is advancing. ETA (Android side): `floor(observedAt / tick) * tick + (stages - 1 - stage) * tick`, with `tick = tickMinutes` in ms.
`prices{}` maps item id (the unnoted id for noted items) to its GE price in gp, taken from RuneLite's `ItemManager` on the client thread (`PriceBook`), so the app never asks a third party for prices. `varps{}` holds raw varp values (only NMZ points today); `slayer`, `birdhouses[]` and the full field lists are documented in the Android repo's `CLAUDE.md`, which is the contract's other half.
`observedAt` (epoch ms) is when the game last fed the snapshot (a game event or the 15-minute heartbeat while logged in); `bankObservedAt` is when the bank was last read from the open bank (carried over from the saved snapshot after a restart). Both are separate from `updated_at`, which is when it arrived or was stored: a snapshot saved by an earlier session is delivered later with its original `observedAt`. Android shows `observedAt` as "Game state from X ago" and ignores a snapshot older than the one it holds.
Change one side → update `StateDTO.java` **and** `StateDto.kt` together, plus both servers if the envelope changes.

## Guardrails
Read `GUIDELINES.md` (this repo) before touching networking, prices, assets or game logic. It holds the "not Mystix" rules, the five pre-merge questions, and the list of open deviations.

## Product rules (non-negotiable)
- Plugin Hub compliant: injected `OkHttpClient`/`Gson`/executor only, async `enqueue`, no reflection, no subprocesses, no dynamic code, never block the client thread.
- Free. Read-only and observational. No "do X now" instructions and no AI gear advice.
- Never hotlink or scrape the OSRS Wiki.
- Any third-party network feature is off by default and carries RuneLite's `ConfigItem.warning` IP disclosure.
- **The plugin never listens on a port** ("Plugins which expose player information over HTTP" are rejected). It only sends, to the Pssst app on the phone: `sendToPhone` ("Send to My Phone") is off by default with the IP warning; `includeBank` ("Include Bank") is its own toggle with its own warning. The token goes in the Authorization header only and is never logged. Retired keys (Local Server, Send to My Server; `CompanionConfig.RETIRED_KEYS`) are unset on start-up. The side panel edits the Sync Token and Phone Address with explicit Save buttons; any token works, short ones get a note.

## Roadmap
Open work and plans: `docs/ROADMAP.md` (index) and `docs/plans/`. The sync direction (the plugin sends to the phone) is `docs/plans/phone-push.md`; `docs/plans/lan-first.md` is partly superseded by it.

## Workflow
Feature branches named `claude/*`, one commit per logical change. Don't push or merge to `main` without asking.
