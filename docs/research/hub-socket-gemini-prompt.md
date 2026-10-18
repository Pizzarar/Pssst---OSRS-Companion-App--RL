# Prompt for Gemini: will the RuneLite Plugin Hub accept a LAN HTTP server?

Paste everything below the line into Gemini (with web search on). Bring the answer back; the decision it feeds is N3 in `docs/ROADMAP.md` and §2 of `docs/plans/lan-first.md`.

---

You are researching a policy question about the RuneLite Plugin Hub (the plugin store for RuneLite, the third-party Old School RuneScape client). I need a definite, sourced answer, not an opinion. Search the web, GitHub (code, issues and pull requests) and any public archive of the RuneLite Discord you can find.

## The plugin in question

"Pssst Companion", not yet submitted. It is a read-only companion for an Android app. When the player turns it on (off by default, with RuneLite's standard IP-address warning), it runs a tiny plain-HTTP server on port 8788 with one route, `GET /state`, which returns one JSON document of the player's own data: bank, inventory, skills, Grand Exchange offers, Slayer task, and farming/birdhouse timers. It has no location, no interface state, no other players, and no actions; it updates on change (2-second debounce), not every tick. Every request must carry a 128-bit token in the `Authorization: Bearer` header. It binds `127.0.0.1` by default; a second opt-in setting with its own warning binds `0.0.0.0` so the player's own phone can read it over home Wi-Fi or Tailscale, and it then closes connections from non-private addresses. The fallback design is the reverse direction: the plugin POSTs the same JSON to a server the player runs themselves (opt-in, IP warning).

## The rule

RuneLite's wiki page "Rejected or Rolled-Back Features" (https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features) lists, under "Not currently being considered": "Plugins which expose player information over HTTP." The example plugin's `AGENTS.md` (https://github.com/runelite/example-plugin/blob/master/AGENTS.md) repeats it as "No exposing player information over HTTP".

## What is already known (verify, don't just repeat)

- runelite/plugin-hub PR #11453 (osrs-mcp, local and LAN HTTP MCP server exposing stats, gear, inventory and location): rejected 2026-05-15 by maintainer riktenx, quoting the rule.
- PR #10181 (streamdeck, changed from hosting an HTTP server to POSTing game state to a local Stream Deck server): rejected; riktenx wrote that "the direction (served vs. sent) is irrelevant".
- PR #10300 (ESP32 XP tracker): challenged under the rule; Adam- (RuneLite's author) replied that the rule is meant for people who want "generic inv/stats/action endpoints for their bots". Then PR #10539 (esp32-tracker update: an unauthenticated `HttpServer` on `0.0.0.0` serving XP to a LAN device) was merged by riktenx on 2026-02-15.
- PR #14607 (bank-bridge: a loopback-only WebSocket serving bank, inventory, equipment and levels to a gear-planning website) was merged 2026-08-25; the only change requested was removing `Thread.interrupt`.
- PR #10595 (expose-bank-state: loopback HTTP, bank open/closed only) was merged. PR #15610 (expose-interface-state, loopback HTTP) was closed as having "no legitimate use outside of botting".
- WikiSync (weirdgloop/WikiSync) runs a loopback WebSocket with an origin allowlist.

## Questions to answer

1. Is there any statement by a RuneLite maintainer (riktenx, Adam-, Alexsuperfly, raiyni, iProdigy, Felanbird, ldavid432 or others) after 2026-02-15 that clarifies whether an **authenticated, opt-in, LAN-bound** HTTP server serving the player's own data to the player's own device is allowed? Quote it and link it.
2. Find every plugin-hub PR or issue from 2025-2026 that involves a plugin **listening on a socket** (HTTP, WebSocket, TCP) or the rule above. For each: PR number and link, what it served, loopback or LAN, authenticated or not, merged/closed/open, and any maintainer comment about the rule. Search terms to try: "expose player information", "HttpServer", "ServerSocket", "WebSocketServer", "0.0.0.0", "localhost server", "LAN", "companion app", "Stream Deck", "home assistant", "ESP32", "OBS", "overlay server", "bank state".
3. Has any plugin been **removed or disabled** from the Hub for running a local server or exposing data over HTTP? Check plugin-hub commits that disable plugins.
4. Is the reverse direction (plugin POSTs the player's own data to a server the player runs, opt-in with the IP warning) treated as allowed? Find merged plugins that do exactly this with full inventory/bank/stats (for example Home Assistant integrations, Dink, Status Socket, WikiSync's upload) and any maintainer comment on the #10181 "direction is irrelevant" remark.
5. Are there written criteria anywhere (wiki, README, AGENTS.md, Discord pins, review templates) for what makes a local server acceptable: loopback only, auth, origin checks, payload scope, purpose?

## Output format

- **Verdict** for each of: (a) loopback-only server, (b) LAN-bound server with token, (c) outbound POST to the player's own server. Each one: allowed / disallowed / unclear, with a confidence level and the 1-3 strongest links.
- **Evidence table**: PR or source | date | what it did | outcome | maintainer quote (short) | link.
- **New since 2026-08-25**: anything newer than the facts listed above.
- **Gaps**: what you could not find or verify (e.g. Discord-only discussions).

Do not speculate past the evidence. If something is only in the RuneLite Discord and you cannot see it, say so.
