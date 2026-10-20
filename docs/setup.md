# Setting up Pssst Companion

A guide to connecting the **Pssst Companion** RuneLite plugin with the **Pssst** Android app.

## Prerequisites

- RuneLite installed on your PC.
- Pssst Android app installed on your Android device.
- Both devices connected to the same local network (home Wi-Fi).

---

## Step 1: Enable Sync in RuneLite

1. Open RuneLite settings (wrench icon) and find **Pssst Companion**.
2. Turn on **Enable Sync**.
3. Note or copy the generated **Sync Token** (also visible in the Pssst side panel).

> The Sync Token is a short two-word pairing code (like `goblin-whip`) generated on your PC. It isn't meant to be a strong password: the app only accepts snapshots from devices on your own network, and the token just keeps it matched to your RuneLite. It is never logged and never sent anywhere but your phone.

---

## Step 2: Configure the Pssst Android App

1. Open the Pssst app on your phone.
2. Enter your **Sync Token** in the setup screen.
3. Check the **Phone Address** displayed on the app's setup/status screen (for example, `192.168.1.50`).

---

## Step 3: Connect RuneLite to Your Phone

1. Return to RuneLite's **Pssst Companion** settings or side panel.
2. Enter your phone's address into the **Phone Address** field.
3. Turn on **Send to My Phone**.
4. (Optional) Turn on **Include Bank** if you want your bank contents and potion storage mirrored to the app.

---

## Step 4: Verify the Connection

1. Log into your character in RuneLite.
2. Check the Pssst side panel in RuneLite: it will show when the latest snapshot was sent.
3. Open the Pssst app on your phone: your skills, inventory, and timers will appear.

---

## How It Works

- **Direct Push:** The plugin sends snapshots directly to the app over your local network using port 8789.
- **Pacing:** Sync triggers on state changes, at most once every 2 minutes while playing, and once immediately upon logout.
- **Privacy:** Your username, chat, coordinates, friends list, and IP are never broadcast or shared externally.
- **Offline Cache:** The plugin saves the last snapshot locally (`last-snapshot.json`) so unsent changes are dispatched when RuneLite restarts. Turning off **Enable Sync** permanently deletes this cache.

---

## Troubleshooting

- **Phone shows "Disconnected" or "Waiting for data":**
  - Verify that both devices are on the same Wi-Fi subnet.
  - Verify the Phone Address in RuneLite matches the IP and port shown in the Pssst app.
  - Ensure local network access is not blocked by Windows Defender Firewall or router client isolation.
- **Bank data not showing:**
  - Verify **Include Bank** is enabled in RuneLite settings.
  - Open your bank in-game at least once so RuneLite can observe its contents.
