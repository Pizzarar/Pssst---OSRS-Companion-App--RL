# Pssst

**Your OSRS character, on your phone.** Pssst is a RuneLite plugin and an Android app. The plugin sends a read-only snapshot of your character straight to the app over your own network, and the app shows it and reminds you about it. Think [Dink](https://github.com/pajlads/DinkPlugin), but the alerts go to an app on your phone instead of a Discord webhook.

Check your bank, GE offers and skills from the couch. Get a ping when your herbs are grown or your birdhouses are ready. No server, no account, nothing to host.

<table>
  <tr>
    <td><img src="https://raw.githubusercontent.com/Pizzarar/Pssst---OSRS-Companion-App/main/docs/images/overview.png" width="260" alt="Overview screen"></td>
    <td><img src="https://raw.githubusercontent.com/Pizzarar/Pssst---OSRS-Companion-App/main/docs/images/timers-dailies.png" width="260" alt="Timers screen, Dailies tab"></td>
    <td><img src="https://raw.githubusercontent.com/Pizzarar/Pssst---OSRS-Companion-App/main/docs/images/progress.png" width="260" alt="Progress screen, Skills tab"></td>
  </tr>
</table>

---

## What it does

| Tab | What you see |
|---|---|
| **Overview** | Total account wealth (bank, inventory, potions, GE offers), a goal tracker (bank value toward a GP goal, at GE or High Alch prices), your Slayer task, Miscellania kingdom and coffer, and counts of what's ready |
| **Storage** | Your bank (search, sort by slot, total value, price or quantity), inventory and potion storage |
| **Exchange** | Your own Grand Exchange offers, all 8 slots |
| **Progress** | Skills (level, XP to next level, combat level, total) and quests |
| **Timers** | Dailies, Fossil Island birdhouses, farming patches (crop, stage, time left, diseased) and farmer payments |

<table>
  <tr>
    <td><img src="https://raw.githubusercontent.com/Pizzarar/Pssst---OSRS-Companion-App/main/docs/images/storage.png" width="260" alt="Storage screen, Bank tab"></td>
    <td><img src="https://raw.githubusercontent.com/Pizzarar/Pssst---OSRS-Companion-App/main/docs/images/exchange.png" width="260" alt="Exchange screen"></td>
  </tr>
</table>

### Notifications

Reminders are scheduled on the phone from the last snapshot, so they still fire when your PC is off.

| Alert | When |
|---|---|
| Farming patch grown | At each growing patch's estimated finish time |
| Birdhouses ready | When your last seeded birdhouse should be ready |
| Slayer task complete | When your task count hits 0 |
| Daily resets | Zaff's battlestaves and NMZ herb boxes available again |
| Kingdom warnings | Miscellania approval drops below 90%, or the coffer below 750,000 GP |

Farming and birdhouse times follow RuneLite's own Time Tracking logic, so they can be up to one growth tick early or late. The daily and kingdom alerts are newer and still being checked against the game.

---

## How it works

<img src="https://raw.githubusercontent.com/Pizzarar/Pssst---OSRS-Companion-App/main/docs/images/how-it-works.png" width="800" alt="The plugin on your PC sends a snapshot to the Pssst app on your phone over your home Wi-Fi or Tailscale. Nothing goes to a Pssst server.">

- The plugin **only sends**. It never opens a port on your PC.
- It sends when something changes, at most every 2 minutes while you play, plus once when you log out. If your phone can't be reached, it tries again every 30 seconds until the snapshot lands.
- The app listens while it's open, and for about a minute each time Android runs its background check (roughly every 15 minutes).
- The app only accepts snapshots from devices on your own network that know your sync token. It makes no outbound requests of its own.
- Prices come from RuneLite's own price data inside the snapshot. Item icons are bundled with the app; any that are missing are drawn by your RuneLite and sent once.
- The plugin keeps the last snapshot in RuneLite's folder (`last-snapshot.json`, never containing your token), so anything unsent goes out next time. Turning off **Enable Sync** deletes it.

---

## Setup

You need RuneLite on your PC and the Pssst app on an Android phone (Android 7.0 or newer), both on the same home Wi-Fi or the same Tailscale network.

1. **Install both.** Pssst Companion from RuneLite's Plugin Hub, and the app from the [latest release](https://github.com/Pizzarar/Pssst---OSRS-Companion-App--Android/releases/latest).
2. **Turn on Enable Sync** in the plugin's settings. Copy the **Sync Token** (also shown in the Pssst side panel), like `goblin-whip`.
3. **Open the app** and enter the token. The app shows your phone's address.
4. **Back in RuneLite**, type that address into **Phone Address** and turn on **Send to My Phone**.
5. Log in. The side panel shows when the last snapshot was sent, and the app fills in. Allow notifications when the app asks.

### Keeping the address stable

Phones can get a new Wi-Fi address now and then. If that happens, the app shows a banner with the new one. To stop it changing:

- **Tailscale (easiest):** install it on your PC and phone. The phone gets a `100.x` address that never changes, and it works away from home too. You can also type the phone's Tailscale name into Phone Address.
- **Router reservation:** reserve your phone's address in your router's settings.

### Troubleshooting

- **App says it's waiting for data:** check both devices are on the same Wi-Fi (or both on Tailscale), and that Phone Address matches what the app shows.
- **Still nothing:** Windows Firewall or your router's "client isolation" / guest network can block devices from talking to each other.
- **Bank is empty:** turn on **Include Bank**, then open your bank in-game once so RuneLite can see it.
- **Side panel says the phone is unreachable:** the address probably changed; see above.

---

## Settings

| Setting | Default | What it does |
|---|---|---|
| Enable Sync | Off | Turns Pssst on. Nothing leaves RuneLite until this is on. |
| Sync Token | Generated | The pairing code between this PC and your phone. Type it into the app. |
| Send to My Phone | Off | Sends your data to the app when it changes. |
| Phone Address | Empty | The address the app shows (like `192.168.1.42`), or a name for your phone. Must be on your own network; internet addresses are refused. |
| Include Bank | On | Also sends your bank and potion storage. Turn it off to keep them on your PC. |

Enable Sync, Send to My Phone and Include Bank all show RuneLite's warning before they turn on, because they send data off your PC.

**About the sync token:** it's a two-word pairing code, not a password. The app only listens to devices on your own network, so the token just makes sure the RuneLite it accepts is yours. Change it any time, then enter the new one in the app.

---

## Rules and compliance

Pssst is built to follow RuneLite's [Plugin Hub rules](https://github.com/runelite/plugin-hub) and Jagex's [third-party client guidelines](https://oldschool.runescape.wiki/w/Update:Third_Party_Client_Guidelines). Each point below has an automated test in the repo that fails if it's ever broken.

- **No server in the plugin.** RuneLite [doesn't accept](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features) plugins that expose player information over HTTP. Pssst's plugin opens no port; it only sends to a phone you name.
- **Opt-in, with warnings.** Nothing leaves your PC until you turn it on, and every sending option carries RuneLite's IP warning.
- **Your devices only.** The phone address must be on your home network, Tailscale, or the PC itself.
- **Not spammy.** At most one send every 2 minutes, and it backs off instead of hammering when it's told to slow down.
- **Hands off the game.** No input, no clicks, no menu or interface changes. It only reads what the game already shows you.
- **No boss helpers.** Alerts are for farming, birdhouses, dailies and Slayer completion, never combat mechanics.
- **No third-party traffic.** No OSRS Wiki scraping or image hotlinking, no price APIs, no analytics. The app calls no one.
- **Shows, doesn't tell.** Pssst shows your state and timers. It doesn't give gear, profit or "go do this" advice.
- **Sourced, not guessed.** Farming, birdhouse, Slayer and daily logic is ported from RuneLite's own plugins and checked against them. Anything that can't be verified is shown raw or marked as unverified in the app.

---

## Why this exists

I made Pssst for me. I watched a companion plugin get pulled from the Plugin Hub after people found out what it was doing, and my reaction was: people are using these tools to churn out something for a quick buck, when they could be used to actually make something. So I decided to make something.

It's free and it's staying free. No ads, no accounts, no premium tier, no paywalled features. I have no intention of ever monetizing it.

I plan to keep it working as RuneLite and the game update, but above all I built this for myself. I'm just letting others enjoy it too.

## Issues and requests

Found a bug or have an idea? [Open an issue](https://github.com/Pizzarar/Pssst---OSRS-Companion-App/issues). Screenshots and the steps to reproduce it help a lot.

I'll get to things when I can and when I feel like it. No timelines, no promises. At the end of the day I'm doing this because I thought I could, and I wanted to give it a shot.

---

Pssst is a fan project and isn't affiliated with or endorsed by Jagex or RuneLite. Old School RuneScape item images are Jagex's.
