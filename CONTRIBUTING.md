# Contributing

Contributions are welcome. Areas where help is especially useful:

## Short-video detection rules
Apps rename their internal view IDs fairly often. To update a rule:
1. Open the app's short-video feed with *Layout Inspector*, or use `adb shell uiautomator dump` and look for a stable `resource-id`.
2. Add it to the matching `ShortsRule` in `app/src/main/java/org/openfocus/app/blocking/ShortsDetector.kt`.
3. Say which app version you tested in the PR.

## Art
All current art is placeholder vector or Canvas drawing:
- the hero (`ui/components/Avatar.kt`)
- the mountain (`ui/components/Mountain.kt`)
- the launcher icon (`res/drawable/ic_launcher_foreground.xml`)

Replacement art must be **CC0, CC-BY or CC-BY-SA**, or your own work released under one of those licences. Credit it in the README. Cosmetic items are keyed by the `ShopItem.id` values in `core/ShopCatalog.kt`.

## Game balance
All the numbers live in `core/GameRules.kt` and are covered by `GameRulesTest`. If you change them, please explain why in the PR.

## Code
- Keep game rules and blocking policy pure: no Android imports in `core/`, and none in `BlockPolicy`.
- Run `./gradlew :app:testDebugUnitTest` before opening a PR. This also regenerates `docs/screenshots/`.
- Please don't add network access or analytics.
