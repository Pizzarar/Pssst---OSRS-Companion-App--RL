# Plan: unverified game logic

Rule (GUIDELINES.md, "Data correctness"): every game value is sourced from RuneLite's code or game data, or it is shown raw and labelled unverified. This file tracks what is left. Status as of 2026-10-17, on `main` in both repos.

## Table

| Value | Where it is used | Source | Approach | Status |
|---|---|---|---|---|
| Daily task eligibility (diary gates, claim flags, ironman rules) | App `util/DailyTasks.kt`, Timers → Dailies | RuneLite `DailyTasksPlugin.check*` methods | Ported | **Done.** Pinned by `DailyTasksParityTest` (plugin) and `DailyTasksTest` (app) |
| NMZ herb boxes: 15 per day, 9,500 points each | App `DailyTasks.kt` | `DailyTasksPlugin.HERB_BOX_MAX` / `HERB_BOX_COST` | Ported; the 9,500 cost is only pinned, not shown (showing what you can afford reads as buy advice) | **Done** |
| Bonemeal allowance: 13 per Morytania tier | App `DailyTasks.kt` | `DailyTasksPlugin.BONEMEAL_PER_DIARY` | Ported | **Done** |
| Bert's sand: Hand in the Sand ≥ 160, not UIM | App `DailyTasks.kt` | `DailyTasksPlugin.SAND_QUEST_COMPLETE`, `IRONMAN != 2` | Ported | **Done** |
| Reward amounts per diary tier (staves 15/30/60/120, essence, flax counts) | Was in `MainViewModel` | None in RuneLite | Removed | **Done.** Not shown at all |
| Miscellania approval % (`/127`) and coffer | App `MainViewModel` | `KingdomPlugin.getApprovalPercent` (`MAX_APPROVAL = 127`), `MISC_COFFERS` raw | Ported; legacy fallback ids 3948/3949/4000/4001 deleted | **Done** |
| Quest states | App Progression → Quests | `Quest.getState(client)` in the plugin | Sourced from the client | **Done** (needs the updated plugin to populate) |
| Potion storage ids (enums 4826/4829, scripts 6080/6555, widget 786490) | Plugin `updatePotionStorage` | RuneLite `EnumID` / `ScriptID` / `InterfaceID` constants, as used by RuneLite's `PotionStorage` | Named constants | **Done.** Pinned by `PotionStorageConstantsTest` |
| XP per level | App `OsrsXpTable` | RuneLite `Experience.getXpForLevel` | Verified | **Done.** Pinned by `OsrsXpTableTest` |
| Tears of Guthix: `TOG_COUNTDOWN` (5099), `TOG_QP_BEFORE_RETURN` (456) | App `DailyTasks.kt` | None found in RuneLite | Raw, labelled "meaning unverified", never shown as available | **Raw.** See below |
| Farming payments (crop → item, quantity, level) | App `util/FarmingPaymentCatalog.kt`, Timers → Payments | None in RuneLite; the OSRS Wiki farmer pages are the human reference | Marked unverified in code and on screen | **Planned.** See below |

## Remaining work

### 1. Farming payments (estimate: 2–3 hours)
- For each of the ~30 rows, check crop, payment item, quantity and level against the OSRS Wiki pages for the farmers (Tree, Fruit tree, Hops, Bushes, special trees). Read them as a person at dev time; never fetch them from code.
- Check each item id against the bundled manifest name (`items-complete.json`), which a unit test can assert for every row.
- Record the source URL and check date per row in a comment, then remove the "unverified" notice in `TimersScreen`. Any row that can't be confirmed gets deleted, not guessed.
- Tradeoff: a test that only pins id ↔ name catches typos but not wrong quantities. Quantities stay a human check; that's acceptable because they change rarely and the notice states the date.

### 2. Tears of Guthix (estimate: 1–2 hours, plus one in-game observation)
- Find a source: search RuneLite (core and Plugin Hub plugins) for uses of `TOG_COUNTDOWN` / `TOG_QP_BEFORE_RETURN`. If one interprets them, port it with a reference.
- Otherwise verify in game: note both values the day you play Tears of Guthix, the next day, and a week later. Write down what changes, and only then turn the raw line into a status.
- Until then it stays raw. That's honest and costs nothing.

## Out of scope here
- Farming, birdhouses and slayer: already ported from RuneLite with parity tests.
- Item names and prices: the manifest script and plugin prices (`prices{}`) cover them.
