# Pssst Companion

A RuneLite plugin that mirrors a read-only snapshot of your character to the **Pssst** Android companion app, so you can check your bank, timers and progress away from the game.

Free, read-only and observational: it never sends input to the game, never tells you what to do, and gives no gear or strategy advice. It does not touch the OSRS Wiki.

## Setup

Install Pssst Companion, turn on **Enable Sync**, copy the **Sync Token**, and pair the Pssst app with your PC over your home Wi-Fi (or Tailscale, which works the same way with a different address). The **Pssst** panel in the RuneLite side bar shows the address and the Sync Token to type into the app. Step-by-step instructions: [docs/setup.md](docs/setup.md).

## Off by default

Nothing leaves your PC until you turn on **Enable Sync**. The main way to get the data to your phone is the Local Server; sending to your own server is optional and tucked away under *Advanced: Your Own Server*:

| Feature | What it does | Default |
|---|---|---|
| Local Server | Serves your state at `GET /state` on your PC for the app to poll. Needs your Sync Token. Listens on `127.0.0.1` unless you turn off **This PC Only** (then devices on your home network or Tailscale can reach it, unencrypted, still needing the token; connections from any other address are closed). | on, once sync is enabled; this PC only |
| Send to My Server (Advanced) | `POST`s the same state to the **Server URL** you enter (a server you run yourself, such as a Cloudflare Worker you deploy). Most players do not need it. | **off**; shows RuneLite's third-party IP warning |

## What is sent

Only data the game already shows you, as plain numbers and names:

- inventory, bank, and potion storage (item ids and quantities)
- skill levels and XP
- Grand Exchange offers
- a fixed list of progress flags: achievement diary tiers, daily reset claims, Tears of Guthix, Miscellania approval and coffer, Nightmare Zone reward points
- the current Slayer assignment (task, area, kills left, points, streak)
- the last-seen state of each farming patch and each Fossil Island birdhouse, with the time it was seen

Not sent: your username, chat, location, friends, or anything about other players.

## The last session is kept

While sync is on, the plugin saves the latest snapshot to one small file in RuneLite's own folder for this plugin (`last-snapshot.json`; it never contains the Sync Token). When RuneLite starts, the Local Server serves it straight away, even before you log in, so the phone gets the final state of your last session. Turning **Enable Sync** off deletes the file. Every snapshot carries `observedAt`, the time the game last fed it, which is separate from the time it was served, so the app can say how old the game state is.

If your PC shuts down before the phone has fetched the latest state, the phone shows the previous state until RuneLite is next open on the same Wi-Fi.

## Sync token

On first run the plugin generates a random 128-bit **sync token** (shown in the plugin settings). It is the only credential: the app presents it to read your data, and a server you push to uses it to key your record. Treat it like a password. It is never written to the log. If you change it, enter the new one in the app.

## Network behaviour

- Pushes use RuneLite's own HTTP client, asynchronously, only while the plugin is running and logged in.
- The local server is a single-purpose plain-HTTP `GET /state` endpoint: header-only authentication, no CORS, no WebSocket, bounded request size and time, and only answers loopback, home-network and Tailscale addresses.
- Farming and birdhouse timers are estimates: they follow RuneLite's Time Tracking logic and ignore the per-player farm tick offset, so they can be up to one growth tick early or late.

## Building

`./gradlew build` (Java 11 source level, built on JDK 17). `./gradlew generateFarmingData` regenerates the farming tables from RuneLite's own Time Tracking classes; `FarmingParityTest` and `BirdhouseParityTest` fail if they drift from RuneLite.
