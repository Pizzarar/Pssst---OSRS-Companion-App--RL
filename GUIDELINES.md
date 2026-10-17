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
- The Worker is meant to be deployed per user. Publicly reachable, it must accept only its owner's token, not create a row for any token that shows up.

## Prices

- **Preferred:** the plugin attaches prices from RuneLite's injected `ItemManager` (`getItemPrice`) to the items it already sends. That adds zero extra external traffic and no Wiki dependency.
- **If the app fetches prices itself:** only the Wiki real-time prices **bulk** endpoints (`/latest`; `/mapping` at build time). Never per-item loops. Honest User-Agent, cached in Room, at most about hourly, never on screen open, opt-in.
- No price history, no flipping, no "worth buying" or profit suggestions.
- The bundled manifest's prices are a fallback snapshot, regenerated only by `pssst-mock-server/scripts/build-item-manifest.js` (3 requests, real contact required).

## Plugin Hub constraints

- Java 11 target. Use only the injected `OkHttpClient`, `Gson` and `ScheduledExecutorService`. Never `new OkHttpClient()`, `new Gson()`, `HttpClient`, `HttpURLConnection` or your own pools. Network calls use `enqueue`, never the client thread.
- No reflection (test code excepted), dynamic code, subprocesses, or input injection.
- Local server: loopback by default; LAN is opt-in with a warning. Reviewers may still object to any listening socket (see Known risks).
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

Compliant:
- Icons bundled, no runtime image loads.
- Injected HTTP client with `enqueue`.
- Gameval-only ids; deprecation lint is on.
- Sync and push off by default with warnings; token not logged.
- Local server: header-only token, no CORS, loopback by default.
- Worker split out, with hashed tokens, a size cap and a throttle.
- Farming, birdhouses and slayer ported from RuneLite with parity tests.
- No foreground service, default notification sound, nothing paid.
- GE offers screen: shows your own offers only, with no suggestions.

Deviations to fix:

| Where | Deviation | Fix |
|---|---|---|
| App prices | Fetches the Wiki itself (opt-in, 12 h, bulk, honest User-Agent). This is allowed, but it's the fallback path, not the preferred one. | Plugin sends `ItemManager` prices with the items it already sends; the app stops calling the Wiki, and the Setup price question goes away. |
| App, `PriceRefreshWorker` | Retries on 429. | Treat 429 like 403: stop until the next scheduled run. |
| App, Disconnect | Doesn't cancel the periodic sync or the price refresh, so the price refresh keeps calling the Wiki with nobody paired. | Cancel all Pssst work on disconnect. |
| App, `StateApiService.DEFAULT_BASE_URL` | Defaults to `pssst.rosserlogic.tech`, a central server that doesn't exist and contradicts self-hosting. | No default; the server URL comes from pairing only. |
| App, `SyncWorker` | Logs the first 4 characters of the token. | Don't log any part of it. |
| App, dailies | "Estimated profit" for Zaff staves and "Can buy N" for NMZ herb boxes read as buy/sell advice. | Show availability only. |
| Worker | Any well-formed token creates a row. | Owner-only: `ALLOWED_TOKEN_HASHES` secret; reject other tokens, and fail closed when it's unset. |
| App, unverified logic | Dailies, Miscellania (`/127`), NMZ costs, `FarmingPaymentCatalog` are inherited and unsourced. | Source each one, or show raw values. |

Known risks (decide before submission):
- `LocalStateServer` runs one daemon thread to accept connections, because a listening socket can't share the client's executor. Reviewers may reject any listening socket. The fallback is to drop the local server and rely on push to a self-hosted server.
- The build script fetches RuneLite's `static.runelite.net` names and the Wiki mapping at build time only (3 requests, honest User-Agent).
