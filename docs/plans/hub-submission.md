# Plan: Hub submission prep (and the Play release it depends on)

Status: planned 2026-10-17 from an interview with Aub. Nothing built yet. Follows `plans/lan-first.md` (built) and closes ROADMAP N1-N5.

## Decisions from the interview
| Topic | Decision |
|---|---|
| Focus | Hub submission prep: N1 (farming payments), N2 (Tears of Guthix), setup docs, packaging, ready to submit once the Hub question is answered |
| Hub socket question | **Answered by research (2026-10-17):** a plugin serving data is very likely rejected; sending to your own device is accepted. Switched to `plans/phone-push.md` (the plugin sends to the app). HS-1 is done; HS-15 and HS-19 follow phone-push |
| Android merge | Commit Aub's uncommitted design work first, then merge `claude/lan-first` on top |
| Real device | Tested by Aub: pairing, sync and timers work on a real phone over Wi-Fi |
| Farming payments | Aub checks the Wiki pages by hand with `docs/checklists/farming-payments.md`; the code follows the filled checklist; unconfirmed rows are deleted |
| Tears of Guthix | Claude searches RuneLite and Hub plugin code first; if nothing, Aub observes with `docs/checklists/tears-of-guthix.md` |
| Setup guide | **Scrap** the drafts in the `pssst-setup-wiki-99bed1` worktree. Write a fresh LAN-first guide for the **GitHub Wiki** |
| App distribution | **Google Play**, with a new personal developer account |
| Privacy policy | **GitHub Pages** |
| Contact | **GitHub Issues** only (plus the developer email Play requires privately) |
| Public repos | Yes, all of them, but **only when it is actually needed** (reminder task HS-30) |
| License | **BSD-2-Clause** |
| Demo mode | **Yes**: bundled sample data, so Play reviewers and new users can see the app without a PC |
| Play testers (12 for 14 days) | **Not sure yet**: recruiting is a task (HS-26) |

## Constraint that orders everything
GitHub Pages, the Wiki and the Plugin Hub all need **public** repos on a free account. Play needs a **public privacy-policy URL** before any track, closed testing included. So the first of these forces the go-public step (HS-30):
1. uploading the first Play build to closed testing,
2. publishing the guide to the Wiki,
3. opening the Hub PR.

Everything else is drafted inside the repos while they are still private. If you want to stay private longer, a public GitHub **gist** can host the privacy policy for closed testing (HS-31, option).

## Work items
Owner: **C** = Claude, **A** = Aub. Size S/M/L.

### A. Inputs from Aub (start now; they have lead time)
| ID | Item | Owner | Size | Depends |
|---|---|---|---|---|
| HS-1 | Run the Gemini prompt; bring back the verdicts and links. Then post the maintainer question (`lan-first.md` §2) if Gemini finds nothing definite | A | S | — |
| HS-2 | Fill `docs/checklists/farming-payments.md` (26 rows; three ids are already known to be wrong) | A | M | — |
| HS-25 | Register the Play developer account (personal, $25, identity verification can take days) | A | S | — |
| HS-26 | Find 12+ closed-test testers who will stay opted in for 14 days in a row (friends, clan, Discord). Claude drafts the invite text | A (+C) | M | — |

### B. Merges and cleanup
| ID | Item | Owner | Size | Depends |
|---|---|---|---|---|
| HS-4 | Android: commit your 20-file design work as-is on `design/wip` (from `main`), **leaving out `pssst-mock-server/state.json`** (your real bank snapshot; it gets untracked in HS-30). Merge `claude/lan-first` into it, resolve the `DashboardScreen`/`MainViewModel`/`MainUiState` overlaps, run the 84+ tests, then ask before it goes to `main` | C | M | — |
| HS-5 | Plugin: merge `claude/pssst-lan-first-plan-0982b8` into `main` (ask first). Worker: merge `claude/lan-first` into `main` | C | S | — |
| HS-6 | Scrap the old wiki drafts: commit them once to their branch as an archive (free and reversible), then remove the `pssst-setup-wiki-99bed1` worktree. Ask before removing | C | S | — |

### C. Game data (N1, N2)
| ID | Item | Owner | Size | Depends |
|---|---|---|---|---|
| HS-3 | Tears of Guthix code search: RuneLite core, plugin-hub plugin sources and GitHub code search for varbits 5099/456 (`TOG_COUNTDOWN`, `TOG_QP_BEFORE_RETURN`, "Tears of Guthix" timers). Port with a reference if found. Otherwise hand Aub the observation checklist | C, then A | S (+ a week of observing) | — |
| HS-7 | Apply the filled payments checklist: fix ids/quantities/levels, delete DROP rows, add a source URL and check date per row, add a test that each row's id matches its item name in the bundled manifest, remove the "unverified" notice from Timers → Payments | C | M | HS-2 |
| HS-8 | Turn ToG into a real status once its meaning is sourced or observed; until then it stays raw (that is acceptable for v1) | C | S | HS-3 |

### D. Plugin Hub packaging (`PssstRunelite`)
| ID | Item | Owner | Size | Depends |
|---|---|---|---|---|
| HS-9 | `LICENSE` (BSD-2-Clause, "Aub") in the plugin repo; the same license in the Android and Worker repos | C | S | — |
| HS-10 | Match the example-plugin template: add `settings.gradle` (`rootProject.name`), use `runeLiteVersion = 'latest.release'` instead of `client:+`, UTF-8 encoding, rename `testPlugin` to `run`, add the `jshell` test dependency, keep `generateFarmingData` (test-only) | C | S | — |
| HS-11 | `runelite-plugin.properties`: add `build=standard` (no third-party dependencies, so the faster automated review applies); check `displayName`, `author`, `description`, `tags` | C | S | — |
| HS-12 | `icon.png` at the repo root, 48×72 max, optimized PNG (the side-panel "P" restyled) | C | S | — |
| HS-13 | Hub-facing README pass: what it does, the opt-ins, the Local Server, the link to the Wiki guide and the Play listing, "not affiliated with Jagex or RuneLite" | C | S | HS-20 |
| HS-14 | Final pre-PR check: `.\gradlew.bat build` green, `HubRulesTest` green, no build artifacts or `node_modules` tracked, `.gitignore` covers them, plugin descriptor text final | C | S | HS-9 to HS-13 |
| HS-15 | Hub PR: plugin-hub `plugins/pssst-companion` file with `repository`, `commit` and the `warning=` from `lan-first.md` Appendix (Branch A), or Branch B items first (PL-11 and the rest) | C (A approves) | S | HS-1, HS-30 |

### E. User guide and privacy (drafted in the repo, published when public)
| ID | Item | Owner | Size | Depends |
|---|---|---|---|---|
| HS-19 | Pick the Branch A or B page set once HS-1 is answered. Draft for Branch A now (it matches the built code) | C | S | HS-1 |
| HS-20 | Fresh LAN-first guide in `docs/guide/` (source for the Wiki): Home, Quick Start, 1 What you need, 2 Install the plugin, 3 Install the app (Google Play; closed-test opt-in link until production), 4 Connect your phone (Same Wi-Fi; one Tailscale line), 5 Check it works, Timers, Troubleshooting (the "Data looks old" causes table), Privacy summary, FAQ (PC off: timers yes, new data no), `_Sidebar`. Includes the residual-gap sentence. No Worker pages | C | M | HS-19 |
| HS-21 | Screenshots for the guide and the Play listing (pairing panel, settings, app setup, dashboard, timers) | A | S | — |
| HS-22 | Privacy policy page for GitHub Pages (`docs/site/privacy.md` or a `gh-pages` source): what the plugin sends and where (your phone or your own server only), the saved snapshot on the PC, what the app stores (Room cache, DataStore token), no analytics, no ads, no accounts, no third-party requests, the contact (GitHub Issues). Also the Play **Data safety** answers to match (expected: no data collected or shared by the developer; the data goes to the user's own devices) | C | S | — |
| HS-23 | `docs/setup.md` becomes a pointer to the Wiki; the README links the Wiki | C | S | HS-20 |
| HS-24 | Publish: enable the Wiki, push `docs/guide/` to `<repo>.wiki.git`, enable Pages for the privacy page | C (A approves) | S | HS-30 |

### F. Android for Google Play (`PssstAndroid`)
| ID | Item | Owner | Size | Depends |
|---|---|---|---|---|
| HS-16 | Demo mode: "Try with sample data" on the setup screen loads a bundled fake snapshot (made-up items, timers relative to now) into a separate demo slot; a permanent "Sample data" banner; no network, no reminders, no price stamping; "Exit demo" returns to setup. Tests: demo never calls the network and never queues reminders | C | M | HS-4 |
| HS-17 | Release build: AAB (`:app:bundleRelease`), R8 with keep rules for the Gson DTOs (`@SerializedName` classes), `versionCode`/`versionName` scheme, upload-key signing read from a gitignored `keystore.properties`, Play App Signing. The keystore never enters git; Aub keeps a backup | C (+A for the key) | M | HS-4 |
| HS-18 | Play listing draft (`docs/play/`): short and full description (states only facts; "not affiliated with Jagex or RuneLite"; no "OSRS" or "RuneScape" in the app name), the category, the content-rating questionnaire answers, the target audience, no ads, App access text that points to demo mode, the Data safety answers (HS-22). Assets: 512 px icon, 1024×500 feature graphic, phone screenshots (HS-21) | C (+A assets) | M | HS-16 |
| HS-27 | Closed-test track: a Google Group for testers, upload the AAB, start the 14-day clock, collect feedback in GitHub Issues | A (+C) | S | HS-17, HS-18, HS-22, HS-25, HS-26, HS-30 |
| HS-28 | Apply for production access after 14 days, then roll out | A | S | HS-27 |

### G. Going public (the reminder)
| ID | Item | Owner | Size | Depends |
|---|---|---|---|---|
| HS-30 | **Reminder: make the repos public when it is first needed** (the first of: Play closed-test upload, Wiki publish, Hub PR). Before flipping: run a secret scan of every repo's history (tokens, `local.properties`, keystores, `.dev.vars`); untrack `pssst-mock-server/state.json` and gitignore it (decide whether its history needs rewriting, since it is your real snapshot); create the Worker's GitHub repo; check the repo names (the plugin remote is `Pssst---OSRS-Companion-App`, which GitHub reports as `...--RL`; a clean name like `pssst-companion` is a **proposal**). Then flip all three to public | C (A approves each flip) | M | — |
| HS-31 | Option to stay private longer: host the privacy policy as a public gist for closed testing only | C | S | — |

## Suggested order
1. **Now, in parallel:** Aub does HS-1, HS-2, HS-25, HS-26. Claude does HS-4, HS-5, HS-6, HS-3, then D (HS-9 to HS-14) and HS-16, HS-17, HS-20, HS-22, HS-18.
2. **When the inputs arrive:** HS-7, HS-8, HS-19.
3. **When closed testing can start:** HS-30 (go public), HS-24, HS-27. The 14-day clock runs.
4. **During the 14 days:** HS-15 (Hub PR). The Hub review queue takes weeks, which overlaps the test.
5. **After:** HS-28, Hub merge, announce.

## Acceptance (done means)
- The payments tab has no "unverified" notice, every row has a source URL and date, and the id↔name test is green.
- ToG either shows a sourced status or the documented raw line, with the observation log attached.
- `.\gradlew.bat build` is green with the template-shaped build. LICENSE, icon and `build=standard` are present. `HubRulesTest` is green.
- The guide exists in `docs/guide/` (LAN-first, no Worker pages) and, once public, on the Wiki. The privacy page is live on Pages, and the Play listing links to it.
- Demo mode works with the network off and queues no reminders. A signed AAB builds. The Play listing draft and Data safety answers are written.
- The Android `design/wip` + LAN-first merge passes all tests and is merged to `main` with your OK.

## Risks
| Risk | Mitigation |
|---|---|
| Not enough testers for 14 days | Start recruiting now (HS-26). The clock only starts once 12 are opted in |
| Play rejects for "can't test" | Demo mode plus App access text (HS-16, HS-18) |
| Jagex IP: item icons ship in the APK, and the game is named in the listing | Free app, no Jagex logos, no "RuneScape" in the app name, disclaimer in the listing. Remove icons only if Play or Jagex objects (open question 2) |
| The Hub says no to the LAN server | Branch B is planned; push and the Worker are kept |
| Publishing leaks something in git history | HS-30 audit before every flip |
| Gson + R8 strips DTO fields in release | Keep rules and a release-build smoke test with the mock server (HS-17) |

## Open questions for Aub (not blocking now)
1. **App name on Play:** "Pssst" is probably taken or too short to search for. Recommended: "Pssst: Companion for RuneLite" (it names the plugin it pairs with, not the game). Alternative: "Pssst Companion".
2. **Item icons in the Play build:** recommended to keep them (free app, common among fan tools). Remove them only on a complaint.
3. **`state.json` history:** untrack it going forward (recommended), or also rewrite history before going public (only needed if you consider that snapshot sensitive).
