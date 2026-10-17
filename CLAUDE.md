# Pssst — RuneLite plugin

Companion to the Android app at `L:\Dev\Mobile\PssstAndroid` and the optional cloud relay at `L:\Dev\Plugins\PssstWorker` (see their `CLAUDE.md` files). This repo is the Plugin Hub submission, so it holds only the plugin.

## Layout
- `src/main/java/com/pssst/runelite/` — plugin (Java 11 target). `CompanionPlugin` collects state, `NetworkDispatcher` POSTs `/sync`, `LocalStateServer` serves `GET /state` on port 8788 (Authorization header only, no CORS/WebSocket, refuses to start without a token, loopback by default), `StateDTO` is the payload.
- `src/test/java/com/pssst/runelite/` — `CompanionPluginComplianceTest` (Hub rules: config defaults/warning, integer-only DTO, URL normalization, push/loopback defaults and warnings); `LocalStateServerTest` covers the auth and limits of the local server and `PssstPluginTest` (dev-client launcher).

## Commands (verified 2026-10-17)
| What | Command | Notes |
|---|---|---|
| Build + tests | `.\gradlew.bat build` | ~25s, 60 tests (compliance, noted-id rule, tracked-id pin, farming/birdhouse parity and trackers, slayer reader). Builds with `-Xlint:deprecation` and has no deprecation warnings (gameval only) |
| Regenerate farming tables | `.\gradlew.bat generateFarmingData` | Rewrites `farming/FarmingData.java` from RuneLite's timetracking classes. Run when `FarmingParityTest` fails after a RuneLite update, then re-check `FarmingBounds` by hand |
| Run dev client | `.\run-test.bat` | **User runs this in their own terminal.** Never launch the RuneLite GUI from an agent |

## Environment
- JDK: Temurin 17.0.20.1 at `C:/Users/aubro/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2`, set once in the user-level `~/.gradle/gradle.properties` (`org.gradle.java.home=...`). It is deliberately **not** in either repo's `gradle.properties`: it is machine-specific, and the Plugin Hub builds with its own JDK. A fresh clone needs that line (or `JAVA_HOME` pointing at a JDK 17).
- `compileOnly 'net.runelite:client:+'` (always latest).

## Cross-repo contract
Payload: `{ inventory[], bank[], potion_storage[], skills[], ge_offers[], varbits{}, varps{}, prices{}, slayer{}, farming[], birdhouses[] }`, with items as `{id, quantity}` (inventory entries for noted items also carry `baseId`, the unnoted item id that has the name and price). The Worker (`PssstWorker`), the mock server (`PssstAndroid/pssst-mock-server`) and the plugin's local server wrap it as `{ data, updated_at }`. `updated_at` is seconds from the Worker and ms from the local server and mock; Android normalizes to ms. Auth everywhere is `Authorization: Bearer <sync token>` (never a query parameter). The Worker accepts tokens of 16-128 chars of `A-Za-z0-9_-` and caps bodies at 256 KB.
`farming[]` entries are `{regionId, regionName, patchName, patchType, varbitId, value, observedAt, cropName, itemId, cropState, stage, stages, tickMinutes}`. `regionId + varbitId` identify a patch (the varbit alone is a per-region slot). `observedAt` is epoch **ms** and only moves when `value` changes. Crop fields are absent for unknown values. `tickMinutes` is 0 when nothing is advancing. ETA (Android side): `floor(observedAt / tick) * tick + (stages - 1 - stage) * tick`, with `tick = tickMinutes` in ms.
`prices{}` maps item id (the unnoted id for noted items) to its GE price in gp, taken from RuneLite's `ItemManager` on the client thread (`PriceBook`), so the app never asks a third party for prices. `varps{}` holds raw varp values (only NMZ points today); `slayer`, `birdhouses[]` and the full field lists are documented in the Android repo's `CLAUDE.md`, which is the contract's other half.
Change one side → update `StateDTO.java` **and** `StateDto.kt` together, plus both servers if the envelope changes.

## Guardrails
Read `GUIDELINES.md` (this repo) before touching networking, prices, assets or game logic. It holds the "not Mystix" rules, the five pre-merge questions, and the list of open deviations.

## Product rules (non-negotiable)
- Plugin Hub compliant: injected `OkHttpClient`/`Gson`/executor only, async `enqueue`, no reflection, no subprocesses, no dynamic code, never block the client thread.
- Free. Read-only and observational. No "do X now" instructions and no AI gear advice.
- Never hotlink or scrape the OSRS Wiki.
- Any third-party network feature is off by default and carries RuneLite's `ConfigItem.warning` IP disclosure.
- Local server binds loopback by default (`bindLoopbackOnly = true`, shown as "This PC Only"; turning it off warns), always requires the token, and the token is never logged. Pushing to a server (`enableByosPush`, shown as "Send to My Server") is off by default and carries the IP warning.

## Workflow
Feature branches named `claude/*`, one commit per logical change. Don't push or merge to `main` without asking.
