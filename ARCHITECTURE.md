# Architecture

OpenFocus is a single-module Kotlin app built with Jetpack Compose, Material 3, Room and WorkManager. It has no DI framework. Instead, `AppContainer` wires the dependencies together by hand.

```
org.openfocus.app
├── core/        Pure Kotlin logic with no Android dependencies. Unit-tested.
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

## How blocking works
1. `BlockerAccessibilityService` receives `TYPE_WINDOW_STATE_CHANGED` events and re-checks the foreground app every 15 seconds, which catches time limits and schedules that start while an app is already open.
2. It reads the current `BlockConfig` (rules + schedules + settings). This is a `StateFlow` that is always kept up to date, so the read is synchronous.
3. It passes that config to `BlockPolicy.decide()`. This is a pure function, so every rule (focus, deep focus, strict mode, unlock passes, overnight schedules, limits) is unit-tested.
4. If the app should be blocked, the service sends the user to the home screen and shows `BlockActivity`.
5. For Shorts and Reels, `ShortsDetector` looks for known view IDs. It presses **Back** for in-app feeds, or **Home** for apps that are entirely short video.

## Game loop
Every user action goes through a repository, which calls `GameEngine.apply()`. That call:
- updates the profile inside a mutex (handling level-ups, HP clamping and fainting),
- appends an `ActivityEventEntity`,
- checks achievements,
- and emits `GameFeedback`. The UI shows this as a snackbar, or as a celebration dialog for level-ups and achievements.

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
