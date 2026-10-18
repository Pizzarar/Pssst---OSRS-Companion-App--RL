# Checklist: farming payments (N1)

**For Aub.** Open each Wiki page in your browser once, as a player would, and fill in the Wiki columns. Don't copy text; just record the facts. Bring the filled file back and the code follows it. Nothing in Pssst fetches the Wiki.

Source of the rows: `PssstAndroid/app/src/main/java/com/pssst/util/FarmingPaymentCatalog.kt`. "Manifest name" is what the app's bundled item list calls that item id, so a mismatch with the payment means the **id** is wrong.

How to fill a row:
- **Wiki payment / level:** what the page says, for example `1 × Basket of tomatoes(5)`, level 15.
- **Verdict:** `OK` if code and Wiki match, `FIX` if you wrote the right values, `DROP` if you can't confirm it (the row is deleted, not guessed).
- **URL:** the exact page you read. Date checked goes at the top.

Date checked: ____________

## Where to look
| Rows | Suggested page (any page that states the payment is fine) |
|---|---|
| Trees | https://oldschool.runescape.wiki/w/Tree_patch, or each sapling's page (Payment in the infobox) |
| Fruit trees | https://oldschool.runescape.wiki/w/Fruit_tree_patch, or each sapling's page |
| Teak, Mahogany | https://oldschool.runescape.wiki/w/Hardwood_tree_patch |
| Calquat | https://oldschool.runescape.wiki/w/Calquat_patch |
| Celastrus | https://oldschool.runescape.wiki/w/Celastrus_patch |
| Redwood | https://oldschool.runescape.wiki/w/Redwood_tree_patch |
| Allotments | https://oldschool.runescape.wiki/w/Allotment_patch, or each seed's page |

## Trees
| # | Crop | Level (code) | Payment (code) | Manifest name for id | Wiki payment | Wiki level | Verdict | URL |
|---|---|---|---|---|---|---|---|---|
| 1 | Oak Tree | 15 | 1 × id 5968 "Basket of tomatoes" | Tomatoes(5) | | | | |
| 2 | Willow Tree | 30 | 1 × id 5386 "Basket of apples" | Apples(5) | | | | |
| 3 | Maple Tree | 45 | 1 × id 5396 "Basket of oranges" | Oranges(5) | | | | |
| 4 | Yew Tree | 60 | 10 × id 6016 "Cactus spines" | Cactus spine | | | | |
| 5 | Magic Tree | 75 | 25 × id 5974 "Coconuts" | Coconut | | | | |

## Fruit trees
| # | Crop | Level (code) | Payment (code) | Manifest name for id | Wiki payment | Wiki level | Verdict | URL |
|---|---|---|---|---|---|---|---|---|
| 6 | Apple Tree | 27 | 9 × id 5986 "Sweetcorn" | Sweetcorn | | | | |
| 7 | Banana Tree | 33 | 4 × id 5386 "Basket of apples" | Apples(5) | | | | |
| 8 | Orange Tree | 39 | 3 × id 5406 "Basket of strawberries" | Strawberries(5) | | | | |
| 9 | Curry Tree | 42 | 5 × id 5416 "Basket of bananas" | Bananas(5) | | | | |
| 10 | Pineapple Plant | 51 | 10 × id 5982 "Watermelons" | Watermelon | | | | |
| 11 | Papaya Tree | 57 | 10 × id 2114 "Pineapples" | Pineapple | | | | |
| 12 | Palm Tree | 68 | 15 × id 5972 "Papaya fruit" | Papaya fruit | | | | |
| 13 | Dragonfruit Tree | 81 | 15 × id 5974 "Coconuts" | Coconut | | | | |

## Special and hardwood
| # | Crop | Level (code) | Payment (code) | Manifest name for id | Wiki payment | Wiki level | Verdict | URL |
|---|---|---|---|---|---|---|---|---|
| 14 | Teak Tree | 35 | 15 × id 5464 "Limpwurt root" | **Cabbages(3)**, id is wrong | | | | |
| 15 | Mahogany Tree | 55 | 25 × id 5978 "Yanillian hops" | **Coconut shell**, id is wrong | | | | |
| 16 | Calquat Tree | 72 | 8 × id 5984 "Poison ivy berries" | **Watermelon slice**, id is wrong | | | | |
| 17 | Celastrus Tree | 85 | 8 × id 3138 "Potato cactus" | Potato cactus | | | | |
| 18 | Redwood Tree | 90 | 6 × id 22929 "Dragonfruit" | Dragonfruit | | | | |

## Allotments
| # | Crop | Level (code) | Payment (code) | Manifest name for id | Wiki payment | Wiki level | Verdict | URL |
|---|---|---|---|---|---|---|---|---|
| 19 | Potato | 1 | 2 × id 5438 "Sack of potatoes" | Potatoes(10) | | | | |
| 20 | Onion | 5 | 1 × id 5458 "Sack of onions" | Onions(10) | | | | |
| 21 | Cabbage | 7 | 1 × id 5478 "Sack of cabbages" | Cabbages(10) | | | | |
| 22 | Tomato | 12 | 2 × id 6032 "Sacks of garlic" | **Compost**, name and id disagree | | | | |
| 23 | Sweetcorn | 20 | 10 × id 5968 "Basket of tomatoes" | Tomatoes(5) | | | | |
| 24 | Strawberry | 31 | 1 × id 5386 "Basket of apples" | Apples(5) | | | | |
| 25 | Watermelon | 47 | 10 × id 5970 "Curry leaves" | Curry leaf | | | | |
| 26 | Snape Grass | 61 | 5 × id 247 "Jangerberries" | Jangerberries | | | | |

The allotment rows look suspicious as a group (several pay with the crop's own sack). Check each one.

## Crops the page lists that the code does not have (optional)
Only fill this in if you want them in v1. Otherwise v1 ships the rows above.

| Crop | Category | Wiki payment | Wiki level | URL |
|---|---|---|---|---|
| | | | | |

## Notes
Anything odd (multiple accepted payments, noted vs unnoted, protection that changed in an update):
