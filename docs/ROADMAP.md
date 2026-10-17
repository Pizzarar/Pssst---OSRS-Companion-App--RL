# Pssst roadmap

Index of open work across `PssstRunelite`, `PssstAndroid` and `PssstWorker`. Detailed plans live in `docs/plans/`. Rules: `GUIDELINES.md`. Last updated 2026-10-17.

## Done
Every item from the 2026-10-17 code review is closed: farming and birdhouses ported from RuneLite (birdhouses carry `readyAt`), varps split from varbits, Slayer restored, gameval ids, prices from `ItemManager`, a full item manifest, the Worker split into its own repo and hardened. The evidence is in the Status section of `GUIDELINES.md`.

## Now: before Plugin Hub submission

| ID | Item | Plan / owner |
|---|---|---|
| N1 | Verify the farming payment rows, then remove the "unverified" notice | `plans/unverified-logic.md` §1 |
| N2 | Tears of Guthix: find a source or observe it in game | `plans/unverified-logic.md` §2 |
| N3 | Decide on the listening-socket risk (keep `LocalStateServer`, or rely on push only) | `GUIDELINES.md`, Known risks |
| N4 | Fill in `docs/setup.md` (the design handoff is in progress) | Project doc `claude/setup-docs-handoff.md` |
| N5 | Real-phone same-Wi-Fi test, Windows Firewall prompt, the 8 setup-guide screenshots | Aub only |

## Later

| ID | Item | Plan |
|---|---|---|
| G | Discord group nags: a friend group's channel gets told when someone's timers are ready, and escalates the longer they ignore them | `plans/discord-group-nags.md` |

## Ideas (not planned)
- Hand solo notifications to the Dink plugin when the PC is on (Dink accepts webhook requests from other Hub plugins). Revisit after G4.
- Solo Discord slash commands on a user's own Worker.
