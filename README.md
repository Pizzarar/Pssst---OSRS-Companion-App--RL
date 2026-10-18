# Pssst Companion

A RuneLite plugin that mirrors a read-only snapshot of your character to the **Pssst** Android companion app, so you can check your bank, timers and progress away from the game.

Free, read-only and observational: it never sends input to the game, never tells you what to do, and gives no gear or strategy advice. It does not touch the OSRS Wiki.

## Setup

Install Pssst Companion, turn on **Enable Sync**, and copy the **Sync Token** (also shown in the **Pssst** side panel) into the Pssst app on your phone. The app shows your phone's address: type it into **Phone Address** and turn on **Send to My Phone**. Step-by-step instructions: [docs/setup.md](docs/setup.md).

## Off by default

Nothing leaves your PC until you turn on **Enable Sync**. The plugin never opens a port on your PC; it only sends, to places you choose:

| Feature | What it does | Default |
|---|---|---|
| Send to My Phone | `POST`s your state to the Pssst app at the **Phone Address** you enter (your phone on your home Wi-Fi, or its Tailscale address). Sends when something changed, at most every 2 minutes while you play, plus once when you log out. If the app isn't listening it tries again every 30 seconds until it lands. | **off**; RuneLite's third-party IP warning |
| Include Bank | Also sends your bank and potion storage. | on, once sending is on; its own warning |

## What is sent

Only data the game already shows you, as plain numbers and names:

- inventory, bank, and potion storage (item ids and quantities)
- skill levels and XP
- Grand Exchange offers
- a fixed list of progress flags: achievement diary tiers, daily reset claims, Tears of Guthix, Miscellania approval and coffer, Nightmare Zone reward points
- the current Slayer assignment (task, area, kills left, points, streak)
- the last-seen state of each farming patch and each Fossil Island birdhouse, with the time it was seen
- small item pictures (icons) for items your phone is missing, drawn by your own RuneLite client and sent once each

Not sent: your username, chat, location, friends, or anything about other players.

## The last session is kept

While sync is on, the plugin saves the latest snapshot, and what your phone has already received, to one small file in RuneLite's own folder for this plugin (`last-snapshot.json`; it never contains the Sync Token). Anything your phone hadn't received when RuneLite closed is sent the next time RuneLite runs. Turning **Enable Sync** off deletes the file. Every snapshot carries `observedAt`, the time the game last fed it, so the app can say how old the game state is.

Your phone gets new data while the Pssst app is open, or every so often in the background, whenever it can reach your PC. If your PC shuts down before that, the phone shows your previous state and timers until RuneLite and the app next meet.

## Sync token

On first run the plugin generates a random 128-bit **sync token** (shown in the plugin settings). It is the only credential: the app presents it to read your data, and a server you push to uses it to key your record. Treat it like a password. It is never written to the log. If you change it, enter the new one in the app.

## Network behaviour

- Sends use RuneLite's own HTTP client, asynchronously, only while the plugin is running. The plugin opens no port.
- The Phone Address must be on your own network (home Wi-Fi ranges, Tailscale, or this PC); internet addresses are refused.
- Farming and birdhouse timers are estimates: they follow RuneLite's Time Tracking logic and ignore the per-player farm tick offset, so they can be up to one growth tick early or late.

## Building

`./gradlew build` (Java 11 source level, built on JDK 17). `./gradlew generateFarmingData` regenerates the farming tables from RuneLite's own Time Tracking classes; `FarmingParityTest` and `BirdhouseParityTest` fail if they drift from RuneLite.
