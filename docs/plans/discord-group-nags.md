# Plan: Discord group nags

Goal: a friend group's Discord channel where Pssst posts when someone's farming or birdhouse timers are ready, then escalates the longer they're ignored ("Bruh, do your farming run"), so the group can give them grief. Status: not started. Written 2026-10-17.

## Architecture (decided)
One person in the group, the **admin**, deploys a Pssst Worker in group mode. Each friend's plugin sends **timers only** to it. A Worker Cron Trigger posts to the channel through a Discord webhook.
- Friends only change plugin settings: no Cloudflare or Discord developer account.
- It works while everyone's PCs are off.
- It is still self-hosted. There is no Pssst-run server.

Rejected:
- **Discord Bot Maker** (Steam, $9.99): Windows-only editor, bots stored in its own format instead of code, still needs a host.
- **One public Pssst bot:** breaks "no central server" and "no sign-ups".
- **A Discord app per friend:** too much setup for a group feature.
- **A bot on the PC (discord.js against the local server):** dies when the PC is off, which is exactly when nags matter.

## Guardrail check (the five questions in `GUIDELINES.md`)
1. **Server we don't run?** The plugin sends to the admin's Worker: opt-in, off by default, with `ConfigItem.warning`. The Worker calls Discord's documented webhook API, honors 429 `Retry-After`, and disables the webhook on 401/404 instead of retrying. The plugin and app never call Discord, so `GuardrailsTest` is unaffected.
2. **Tells the player what to do?** Pssst's own default messages state facts only ("Aub's herbs have been ready for 6h"). Roast lines are written by the group, stored on the Worker, and opt-in per member. Pssst ships no roast lines and no advice.
3. **User data off the machine?** A nickname plus `{kind, patch, readyAt}` per timer. Never the RSN, bank, items, skills or location.
4. **Guessed values?** No. `readyAt` comes from the ported RuneLite logic (G0).
5. **Paid or account?** No. The admin needs free Cloudflare and Discord accounts; friends need nothing.

GUIDELINES addition, approved by Aub 2026-10-17; add it to `GUIDELINES.md` when G starts: *"Group nags are opt-in social notifications. Pssst's own text states timer facts; any instruction-style or joke text is written by the group, never shipped by Pssst."*

## Items

| ID | Repo | Item | Depends on |
|---|---|---|---|
| G0 | Plugin + Android | Add `readyAt` (epoch ms) to `farming[]` entries, computed in the plugin with the formula Android uses today. Birdhouses already have it. Android may keep its own ETA or switch over; pin both with one test value. The Worker then needs no growth math. | — |
| G1 | Plugin | New config section "Group" (off by default, with a warning): Enable Group Sharing, Group Server URL, Group Nickname, Group Token. The Group Token is generated separately from the Sync Token, so the admin never holds a key to a friend's full data. Push `{nickname, timers:[{kind, patch, readyAt}]}` only when a timer changes; reuse `NetworkDispatcher`'s 403/429 back-off. Compliance test: the group payload has no item, skill or RSN fields. | G0 |
| G2 | Worker | Group mode. D1 tables `members(tokenHash, nickname, discordUserId, roastOptIn, quietHours, muteUntil)` and `timers(memberId, kind, patch, readyAt, notifiedTier)`. `POST /group/sync` accepts only listed hashes (the same fail-closed rule as `ALLOWED_TOKEN_HASHES`). The admin adds a friend with `npm run hash-token`. Webhook URL is a Worker secret. Same size cap and per-token throttle. | G1 |
| G3 | Worker | Cron about every 15 min. Tiers: ready → +2h → +6h → +24h → +72h. Post only when a member crosses a tier, one batched message per member per run, a per-channel cap on posts per hour, respect quiet hours and mute. | G2 |
| G4 | Worker | Messages from templates (no LLM). Default set is factual. Group-written roast lines live in D1 and are used only for members with `roastOptIn`. @mention only if the admin mapped a `discordUserId`. | G3 |
| G5 | Worker | Response stats: when a `readyAt` moves forward (replanted), record how long the member took. | G3 |
| G6 | Worker | Weekly "rot report" post: longest-ignored patch, fastest farmer, worst streak. | G5 |
| G7 | Worker | Optional slash commands through the Interactions Endpoint URL (one Discord app owned by the admin): `/overdue`, `/shame` (weekly board), `/mute 8h`, `/roast on\|off`. Personal replies are ephemeral. | G4, G5 |
| G8 | Docs | "Group Discord (advanced)" setup page for the admin and a short one for friends. The privacy page says the group server gets a nickname and timer times only. Update `CLAUDE.md` contracts in all three repos. | G1–G4 |

## Open questions
- Should the Android app show the group board? Default: no. Discord is the group surface; the app stays personal.
- Can one plugin send to both a personal Worker and a group Worker at once? Default: yes. They are separate settings with separate tokens.
