# Architecture

OpenFocus is a single-module Kotlin app built with Jetpack Compose, Material 3, Room and WorkManager. It has no DI framework. Instead, `AppContainer` wires the dependencies together by hand.

```
com.openfocushq.openfocus
├── core/        Pure Kotlin logic with no Android dependencies. Unit-tested.
│   ├── content/         GameContent: loads content.json (slots, items, achievements, starter habits, quest lines, NPCs, enemies, titles, messages)
│   ├── GameRules        XP, coin, HP, level curve and unlock prices (all the tuning lives here)
│   ├── HabitSchedule    whether a habit is due or satisfied, its streak, and misses
│   ├── Achievements     declarative achievement list evaluated against GameStats
│   ├── ShopCatalog      cosmetic items
│   └── Encouragement    copy
├── data/
│   ├── db/      Room entities and DAOs
│   ├── prefs/   SettingsStore (SharedPreferences exposed as a StateFlow, so the blocker can read it synchronously)
│   ├── repo/    Habit, Goal, Block and Shop repositories (they orchestrate the DAOs and GameEngine)
│   └── sync/    SyncGateway seam (offline by default)
├── game/GameEngine      the ONLY place XP/coins/HP change; every change is logged as an ActivityEvent
├── blocking/    BlockPolicy (pure), ShortsDetector (rules), AccessibilityService, BlockActivity, UsageTracker
├── focus/       FocusManager (session lifecycle) + FocusService (foreground countdown)
├── work/        DailyWorker: day rollover, evening reminder, outbox flush
└── ui/          Compose screens, one package per tab
    └── pixel/   SpriteCatalog (sheet registry) + PixelSprite (crisp, whole-number-scaled, layered renderer)
```

## Game content is data
`app/src/main/resources/content/content.json` holds every cosmetic, slot, achievement, starter habit, quest line, NPC, enemy, title and message. It's a Java resource, so the same loader (`GameContent`) works in the APK and in plain JVM tests. Kotlin keeps only *rules*:
- achievement conditions are `stat ≥ target`, with stats from `GameStats.STATS`;
- slots are stacked by `layer` around the hero's body (layer 1);
- `OVERLAY` slots follow a body anchor (`head` or `body`).

A profile's outfit is a `{slot: item}` JSON map (`ProfileEntity.equipped`), so a new slot needs no schema change. Sprites are referenced by name and resolved through the generated `SpriteRegistry`. `ContentValidationTest` checks the file against the code and against `tools/content-editor/content.schema.json`. The [content editor](tools/content-editor/README.md) is a local developer tool and isn't shipped.

Content is translated with flat overlays in `content/i18n/<lang>.json` (`"items.hat_party.name": "…"`). `ContentI18n` builds each key from the JSON path, naming list entries by their `id`, and only text fields can be overridden. `GameContent.current` loads the overlay for the current locale (`pt-BR`, then `pt`) and falls back to English for missing keys. UI text is in `res/values/strings*.xml`; repositories that need text outside Compose get it through `core/Strings`. See [docs/TRANSLATING.md](docs/TRANSLATING.md).

## How blocking works
1. `BlockerAccessibilityService` receives `TYPE_WINDOW_STATE_CHANGED` events and re-checks the foreground app every 15 seconds, which catches time limits and schedules that start while an app is already open.
2. It reads the current `BlockConfig` (rules + schedules + settings). This is a `StateFlow` that is always kept up to date, so the read is synchronous.
3. It passes that config to `BlockPolicy.decide()`. This is a pure function, so every rule (focus, deep focus, strict mode, unlock passes, overnight schedules, limits) is unit-tested.
4. If the app should be blocked, the service shows `BlockActivity` **on top of the app** (Ascent-style); it doesn't send the user home. The decision carries the rule's **gate**:
   - `HARD`: only *Close app*, plus coin-priced unlock passes.
   - `WAIT`: *Continue in app* unlocks after `waitSeconds`, then a free pass lasts `allowMinutes`; coins can skip the wait.
   Each rule also has `allowCoins`. Paid unlocks cost `minutes × price per minute`, plus the skip fee on wait gates. A `RewardEntity` with `unlockPackage` (a package or `*`) and `unlockMinutes` is offered in the pause screen's coin dialog at its fixed price, through `ShopRepository.redeem(id, forPackage)`. Strict mode during focus forces `HARD` with no paid way through. Prices, defaults for new rules and the pause-screen message live in `Settings` and are edited under Settings → Pause screen. Passes live in `Settings.unlockPasses` and `BlockPolicy` honours them.
5. The pause screen splits into a stateless `PauseScreenContent(PauseState)` and the activity, which owns the `WaitTimer`. Unlock choices (free lengths after waiting, priced lengths and the user's rewards behind the gift button) are built by the pure `UnlockSheets` and shown in one bottom sheet. The timer runs on Compose's frame clock and ignores gaps longer than 250 ms, so time only counts while the screen is visible. Leaving the screen discards it, so the wait starts over next time.
6. For Shorts and Reels, `ShortsDetector` looks for known view IDs and presses **Back** out of the feed. Apps that are entirely short video (TikTok) get the pause screen with only *Close app*.

## Game loop
Every user action goes through a repository, which calls `GameEngine.apply()`. That call:
- updates the profile inside a mutex (handling level-ups, HP clamping and fainting),
- appends an `ActivityEventEntity`,
- checks achievements,
- and emits `GameFeedback`. The UI shows this as a snackbar, or as a celebration dialog for level-ups and achievements.

`GameEngine` also notifies `listeners` with a `GameEvent` after each change. `QuestRepository` is one of them, and it runs Habitica-style quests from `content.questLines`:
- **Boss stages:** `HABIT_DONE` / `FOCUS_DONE` / `RESISTED` / `MILESTONE_DONE` / `GOAL_DONE` deal damage (`GameRules.habitDamage`, `focusDamage`, …). `HABIT_SLIP` and `HABIT_MISSED` (whose `refId` carries the number of misses) make the boss strike for `strength × hits` HP, unless gentle mode is on.
- **Collect stages:** each event rolls `GameRules.dropChance`.
- **Objective stages:** store a stat `baseline` when the stage starts. `GAIN` counts from it; `REACH` uses the total.
- Progress is one `quest_progress` row per line (stage index, progress, status, times completed). Stage rewards and the quest's own events (`QUEST_*`, `BOSS_STRIKE`) go back through `GameEngine.apply`, and the repository ignores those events so it never re-enters itself.
- Intros, outros and boss lines are emitted on `conversations` and shown by `DialogueHost`.

Day rollover works like Habitica's "cron" (`HabitRepository.rollover`). It processes each day that has fully passed since the last run, at most the past 7 days, and runs when the app starts, when it resumes, and every 2 hours from `DailyWorker`.

## Designed for future multiplayer (not implemented)
There are deliberately **no multiplayer features**. The data model is set up so they can be added without a rewrite:

| Mechanism | Where | Why it helps |
|---|---|---|
| Client-generated UUID primary keys | all synced entities | rows from several devices or users never collide |
| `ownerId` on user-owned rows | habits, goals, focus sessions, rewards, events | the same tables can hold another user's shared objects |
| `updatedAt` + `deleted` tombstones | habits, goals, milestones, rewards | enough for last-writer-wins replication |
| Append-only `activity_events` with `syncedAt` | `GameEngine` | works as an outbox: party feeds, challenges and leaderboards would consume these events |
| `SyncGateway` interface | `data/sync` | swap `OfflineSyncGateway` for a real backend; `DailyWorker` already flushes the outbox |
| `ProfileEntity.accountKind` | profile | `local` today, a remote account later |

Possible future features include **parties** (shared quests, damage from members' missed dailies), **challenges** (a shared habit template plus a leaderboard built from events), and **accountability partners** (who could see your focus-session events). Each of these can be built on events. None of them needs to change local XP directly, which keeps the single-player game authoritative and harder to cheat.
