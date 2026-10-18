# Pssst roadmap

Index of open work across `PssstRunelite`, `PssstAndroid` and `PssstWorker`. Detailed plans live in `docs/plans/`. Rules: `GUIDELINES.md`. Last updated 2026-10-17 (LAN-first added).

## Done
Every item from the 2026-10-17 code review is closed: farming and birdhouses ported from RuneLite (birdhouses carry `readyAt`), varps split from varbits, Slayer restored, gameval ids, prices from `ItemManager`, a full item manifest, the Worker split into its own repo and hardened. The evidence is in the Status section of `GUIDELINES.md`.

## Now: before Plugin Hub submission

The full sequence (Hub packaging, the setup guide, the Google Play release, going public) is `plans/hub-submission.md`.

| ID | Item | Plan / owner |
|---|---|---|
| N1 | Verify the farming payment rows, then remove the "unverified" notice. Aub fills `checklists/farming-payments.md` | `plans/hub-submission.md` HS-2, HS-7 |
| N2 | Tears of Guthix: code search first, then observe with `checklists/tears-of-guthix.md` | `plans/hub-submission.md` HS-3, HS-8 |
| N3 | Hub answer on the Local Server: Aub researches with `research/hub-socket-gemini-prompt.md`, asks the maintainers if still unclear, then picks Branch A (LAN) or B (push) | `plans/lan-first.md` §2, `plans/hub-submission.md` HS-1 |
| N4 | Fresh LAN-first guide for the GitHub Wiki (old drafts scrapped), privacy policy on GitHub Pages | `plans/hub-submission.md` HS-19 to HS-24 |
| N5 | Real-phone same-Wi-Fi test (**pairing, sync and timers confirmed working 2026-10-17**), Windows Firewall prompt (which program, which network profile), reminder lateness under Doze (decides AN-4), PC shutdown mid-session, the setup-guide screenshots | Aub only (`plans/lan-first.md` §5) |

| N6 | Google Play release: developer account, 12 testers for 14 days, demo mode, signed AAB, listing | `plans/hub-submission.md` §F |
| **N7** | **Reminder: make the repos public when first needed** (Play closed-test upload, Wiki publish or Hub PR, whichever comes first), after a history secret scan and untracking `pssst-mock-server/state.json` | `plans/hub-submission.md` HS-30 |

### LAN-first (`plans/lan-first.md`)

| ID | Item | Status |
|---|---|---|
| L1 | Shared plugin work: last snapshot on disk, `observedAt`, bank carry, heartbeat off the client thread, pairing panel, peer filter | Built on `claude/pssst-lan-first-plan-0982b8` |
| L2 | Shared app work: quiet unreachable, network-regained trigger, reminders from the saved snapshot, "Game state from X ago", monotonic guard | Built on `claude/lan-first` (PssstAndroid) |
| L3 | Held until the Hub answers: Hub PR (PL-12), docs for Branch A or B (DOC-2 to DOC-5, DOC-7), Branch B items (PL-11, AN-10, WK-3, WK-4) | Waiting on N3 |
| L4 | Not built: QR pairing (PL-9, AN-8), alarm-based reminders (AN-4, measure first), mDNS (rejected) | Deferred |

## Later

| ID | Item | Plan |
|---|---|---|
| G | Discord group nags: a friend group's channel gets told when someone's timers are ready, and escalates the longer they ignore them. Uses the outbound push and the frozen Worker, which LAN-first keeps | `plans/discord-group-nags.md` |

## Ideas (not planned)
- Hand solo notifications to the Dink plugin when the PC is on (Dink accepts webhook requests from other Hub plugins). Revisit after G4.
- Solo Discord slash commands on a user's own Worker.
