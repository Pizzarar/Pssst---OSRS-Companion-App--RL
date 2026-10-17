# Pssst — RuneLite plugin + Cloudflare Worker

Companion to the Android app at `L:\Dev\Mobile\PssstAndroid` (see its `CLAUDE.md`).

## Layout
- `src/main/java/com/pssst/runelite/` — plugin (Java 11 target). `CompanionPlugin` collects state, `NetworkDispatcher` POSTs `/sync`, `LocalWebSocketServer` serves `GET /state` + WS on port 8788, `StateDTO` is the payload.
- `src/test/java/com/pssst/runelite/` — `CompanionPluginComplianceTest` (Hub rules: config defaults/warning, integer-only DTO, URL normalization, local server auth) and `PssstPluginTest` (dev-client launcher).
- `src/index.ts`, `schema.sql`, `wrangler.toml` — Cloudflare Worker + D1. Lives here for now; must move out before Hub submission.

## Commands (verified 2026-10-17)
| What | Command | Notes |
|---|---|---|
| Build + tests | `.\gradlew.bat build` | ~25s, 5 tests. Deprecation notes from `Varbits`/`VarPlayer` etc. are known |
| Run dev client | `.\run-test.bat` | **User runs this in their own terminal.** Never launch the RuneLite GUI from an agent |
| Worker typecheck | `npx tsc --noEmit` | |
| Worker local | `npx wrangler dev` | Serves on :8787. Unauthenticated `GET /state` → 401 is expected. First run: `npx wrangler d1 execute augur-db --local --file=schema.sql` |

`npm install` warns that npm 11 blocked install scripts (esbuild, workerd, sharp). `wrangler dev` works anyway; run `npm approve-scripts` only if it breaks.

## Environment
- JDK: Temurin 17.0.20.1 at `C:/Users/aubro/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2`. It's pinned in `gradle.properties` via `org.gradle.java.home`, which is machine-specific and must be removed before submission.
- `compileOnly 'net.runelite:client:+'` (always latest).

## Cross-repo contract
Payload: `{ inventory[], bank[], potion_storage[], skills[], ge_offers[], varbits{} }`, with items as `{id, quantity}`. The Worker, the mock server (`PssstAndroid/pssst-mock-server`) and the plugin's local server wrap it as `{ data, updated_at }`. `updated_at` is seconds from the Worker and ms from WS/mock; Android normalizes to ms.
Change one side → update `StateDTO.java` **and** `StateDto.kt` together, plus both servers if the envelope changes.

## Product rules (non-negotiable)
- Plugin Hub compliant: injected `OkHttpClient`/`Gson`/executor only, async `enqueue`, no reflection, no subprocesses, no dynamic code, never block the client thread.
- Free. Read-only and observational. No "do X now" instructions and no AI gear advice.
- Never hotlink or scrape the OSRS Wiki.
- Any third-party network feature is off by default and carries RuneLite's `ConfigItem.warning` IP disclosure.
- Local server binds loopback by default (`bindLoopbackOnly = true`).

## Workflow
Feature branches named `claude/*`, one commit per logical change. Don't push or merge to `main` without asking.
