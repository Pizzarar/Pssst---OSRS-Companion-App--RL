# Pssst guardrails: how not to become Mystix

Applies to all three repos: `PssstRunelite` (plugin), `PssstAndroid` (app + mock server), `PssstWorker` (optional relay). This file is the canonical copy, and the other repos' `CLAUDE.md` point here. Read it before touching networking, prices, assets, or anything "smart".

Mystix was a companion plugin removed from the RuneLite Plugin Hub. The details below come from a secondhand report built on Reddit threads, so treat them as likely, not verified. The rules stand on their own either way.

## The five questions (ask before merging anything)

1. **Does it make a request to a server we don't run?** Then it uses a documented API, sends an honest User-Agent, caches, and stops completely when the feature is off.
2. **Does it tell the player what to do?** Remove it. Pssst shows state, timers and notifications. It does not advise.
3. **Does it send user data off the machine?** Then it is opt-in, off by default, and carries an IP disclosure.
4. **Is any game value guessed rather than sourced?** Source it from RuneLite's code or the game, or show the raw value.
5. **Is anything behind payment or an account?** Remove it. Pssst is free with no sign-ups.

## Rules, and what Mystix did

| # | Mystix (reported) | Rule for Pssst |
|---|---|---|
| 1 | Hotlinked OSRS Wiki images, so the Wiki paid the bandwidth (and banned them). | Never load images from the Wiki or any third party at runtime. Icons ship inside the APK. |
| 2 | Evaded the ban with proxies and spoofed User-Agents. | Always send an honest, descriptive User-Agent that names the app and a real contact. Never proxy, rotate, or retry around a 403 or 429: a block means stop and fix. |
| 3 | Scraped Wiki pages and kept polling after "shutdown". | Use documented APIs only. Every network client stops when disabled: plugin `shutDown()`, config toggles, cancelled workers, and disconnect in the app. No zombie polling. |
| 4 | LLM gear loadouts, some of them impossible. | No recommendations, solvers, "best item", flipping, or combat prediction. No LLM in any game-logic path. |
| 5 | Paywalled features. | Everything free. No accounts, subscriptions or premium tier. |
| 6 | Sent data off-machine without clear consent. | Third-party sends are off by default, with `ConfigItem.warning` stating the IP goes to a server not controlled or verified by RuneLite. |

## Self-hosted / bring-your-own-server is the model

- The data path is plugin → **your** server (its local server, your mock server, or a Worker *you* deploy) → your phone. There is no central Pssst service, and the app must not default to one.
- The Worker is meant to be deployed per user and is owner-only: it accepts just the tokens whose hashes are in `ALLOWED_TOKEN_HASHES`.

## Prices

- **Preferred:** the plugin attaches prices from RuneLite's injected `ItemManager` (`getItemPrice`) to the items it already sends. That adds zero extra external traffic and no Wiki dependency.
- **The app does not fetch prices.** (If that ever changes: only the Wiki real-time prices **bulk** endpoints, `/latest`, never per-item loops; honest User-Agent; cached in Room; at most about hourly; never on screen open; opt-in with an IP disclosure.)
- No price history, no flipping, no "worth buying" or profit suggestions.
- The bundled manifest's prices are a fallback snapshot, regenerated only by `pssst-mock-server/scripts/build-item-manifest.js` (3 requests, real contact required).

## Plugin Hub constraints

- Java 11 target. Use only the injected `OkHttpClient`, `Gson` and `ScheduledExecutorService`. Never `new OkHttpClient()`, `new Gson()`, `HttpClient`, `HttpURLConnection` or your own pools. Network calls use `enqueue`, never the client thread.
- No reflection (test code excepted), dynamic code, subprocesses, or input injection.
- **No listening socket in the plugin.** The Hub rejects "Plugins which expose player information over HTTP" (osrs-mcp was rejected in 2026). The plugin only sends, opt-in with the IP warning, to targets the player owns: the Pssst app on their phone (Phone Address limited to home-network, Tailscale or loopback addresses) or their own server. Sends are paced (at most every 2 minutes while playing) and the bank has its own toggle, following approved plugins (osrs-bank-sync, ha-exporter, ironman-path-sync). `HubRulesTest` fails on `ServerSocket`/`HttpServer`. Details: `docs/plans/phone-push.md`.
- File I/O only through `net.runelite.client.util.Filepath` (the plugin's snapshot uses `getPluginDirectory()` with `internalName = "pssst-companion"`). No `java.io.File`, `Files` or `Paths`; `HubRulesTest` scans for them.
- No `Thread.interrupt` or `Thread.sleep` (forbidden by the Hub); close the socket to stop the accept thread.
- `gameval` ids only, with no deprecated `Varbits`/`VarPlayer`/`InventoryID`/`WidgetID`. No Worker code and no machine paths in the plugin repo.

## Data correctness

- Don't invent varbit meanings, patch names, thresholds, or item tables. Port them from RuneLite (`timetracking`, `SlayerPlugin`) and pin them with parity tests against the real classes.
- If a value can't be verified, show it raw or as "unknown" instead of a guessed label.
- Tests assert against known real values, not against the code's own assumptions.

## Android / backend

- No permanent foreground service. Use WorkManager (15-minute floor), refresh on open, and poll the local server while playing.
- Never log payloads or tokens, not even a prefix.
- Worker: size cap, per-token throttle, a per-IP WAF rule, and hashed tokens, all of which exist. Accept only the owner's token(s) (see Status).
- Item icons are Jagex assets: fine for a free app, never in anything monetized.
- Never ask the user for anything beyond pairing. No nags. Notification sound is a core feature; keep the default-importance channel.

## Status (2026-10-17)

Compliant, each with a test that fails if it regresses:
- **No third-party requests at runtime.** Prices come from RuneLite's `ItemManager` inside the plugin payload (`PriceBookTest`); the app calls only the server the user configured (`GuardrailsTest` fails on any Wiki, RuneLite-static or built-in host in shipped code). There is no default server and no central service (`SyncWorkerTest`: no URL, no request).
- Icons are bundled; nothing loads images at runtime.
- Injected HTTP client with `enqueue`; gameval-only ids with deprecation lint on (`TrackedVarIdsTest` pins the ids the app reads).
- Sync and push are off by default with warnings; the token is never logged, not even a prefix (`SyncWorkerTest`).
- Sending: header-only token, injected OkHttp with `enqueue`, paced by `DeliveryPolicy` (no retry loop around 401/403/429; a refused connection is retried every 30 s only while data is unsent), Phone Address restricted to the player's own network (`DeliveryPolicyTest`, `PhoneAddressTest`).
- **Last snapshot on disk:** `SnapshotStore` writes `last-snapshot.json` atomically through `Filepath`, never holds the token, is deleted when Enable Sync is turned off, and a snapshot from another RS profile never donates its bank (`SnapshotStoreTest`, `BankCarryTest`). Nothing blocks the client thread: the heartbeat sync and the writes run on the injected executor.
- Worker: separate repo, owner-only (`ALLOWED_TOKEN_HASHES`, fails closed), hashed tokens, size cap, per-token throttle.
- **Backing off:** a 403/429 ends the attempt in the app and pauses pushes in the plugin for `Retry-After`; there is no retry loop around a block. Network drops are ridden out (retry with backoff, the schedule continues, "unreachable" only after 3 failures in a row). Disconnect cancels all background work.
- **Observational only:** dailies show eligibility and claimed state, with no profit, no what-to-buy and no where-to-go (`DailyTasksTest.noRewardAmountsOrAdviceAppearAnywhere`). The GE screen shows the player's own offers.
- **Sourced, not guessed:** farming, birdhouses and slayer are ported from RuneLite with parity tests; dailies from `DailyTasksPlugin` (`DailyTasksParityTest` pins its constants and ids); Miscellania from `KingdomPlugin` (approval out of 127, raw coffer); quest states come from the client; potion storage uses RuneLite's named constants (`PotionStorageConstantsTest`).
- No foreground service, default notification sound, nothing paid.

Shown raw or marked unverified (not guessed); the plan to close these is in `docs/plans/unverified-logic.md`:
- **Tears of Guthix:** RuneLite has no rule for the two varbits, so the app shows "Countdown: N, Quest points check: N" and never calls it available.
- **Farming payments** (`FarmingPaymentCatalog`): hand-written, unchecked; the Payments tab says so. Before removing the notice, verify each row and record its source in the file.

Known risks (decide before submission):
- The plugin no longer runs a server (resolved 2026-10-17 by switching to phone push). The remaining review question is pacing: sends are on change, at most every 2 minutes, which matches approved "send to your own device" plugins.
- The Worker deploys per user with their own token hash; there is deliberately no public instance. A public one would need real accounts, which breaks the "no sign-ups" rule.
- The build script fetches RuneLite's `static.runelite.net` names and the Wiki mapping and latest prices at build time only (3 requests, honest User-Agent via the required `PSSST_CONTACT`).
- The app's bundled price snapshot is a fallback only; it is as old as the last `npm run manifest`.
