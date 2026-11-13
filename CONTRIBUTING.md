# Contributing

Contributions are welcome. Areas where help is especially useful:

## Short-video detection rules
Apps rename their internal view IDs fairly often. To update a rule:
1. Open the app's short-video feed with *Layout Inspector*, or use `adb shell uiautomator dump` and look for a stable `resource-id`.
2. Add it to the matching `ShortsRule` in `app/src/main/java/org/openfocus/app/blocking/ShortsDetector.kt`.
3. Say which app version you tested in the PR.

## Art
OpenFocus is moving to pixel art. All current art is placeholder. **[`art/README.md`](art/README.md)** covers the palette, sprite sizes, sheet layout, how hero layers and anchors work, the workflow, and which licences are accepted. Credit every file in [`art/CREDITS.md`](art/CREDITS.md).

## Game balance
All the numbers live in `core/GameRules.kt` and are covered by `GameRulesTest`. If you change them, please explain why in the PR.

## Code
- Keep game rules and blocking policy pure: no Android imports in `core/`, and none in `BlockPolicy`.
- Run `./gradlew :app:testDebugUnitTest` before opening a PR. This also regenerates `docs/screenshots/`.
- Please don't add network access or analytics.
