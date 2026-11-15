# Content editor (developer tool)

A local editor for `app/src/main/resources/content/content.json`. That file holds all of OpenFocus's game content:
- **Items:** hats, cloaks/clothes, armour, backdrops and any new slot
- **Slots:** the equipment layers
- **Achievements**
- **Quests:** onboarding starter habits
- **Titles:** by level
- **Messages:** encouragement and pause-screen lines

This is **not** part of the app and isn't deployed anywhere. It only runs on your machine.

```bash
python3 tools/content-editor/server.py     # Python 3.9+, standard library only
# open http://127.0.0.1:8765
```

## Workflow
1. **Edit** in the browser. Problems (duplicate ids, unknown slots/stats/speakers/enemies, stages without exactly one win condition, prerequisite cycles, missing sprites…) are listed on the right, with the same checks CI runs (`ContentValidationTest`).
2. **Save** (Ctrl/Cmd+S). This writes `content.json`.
3. **Regenerate art** (button) if you added or reordered achievements, added a cloak/armour piece, or dropped a new `px_*.png` into `res/drawable-nodpi/`. The button runs `art/tools/build_placeholders.py`, which:
   - rebuilds the badge atlas from the achievements (new ones get a star glyph until someone draws one),
   - generates cloaks from each item's colour,
   - regenerates `SpriteRegistry.kt` so the app can find every sprite by name.
4. Run `./gradlew :app:testDebugUnitTest` and commit the JSON, the PNGs and `SpriteRegistry.kt` together.

## Adding things
- **A cloak:** new item, slot `cloak`, pick a colour, and set the sprite to `px_cloak_<name>`. Regenerate art.
- **A hat or armour piece:**
  1. Add the item.
  2. Draw a 32×32 PNG aligned to the hero's frame 0 (see `art/README.md`). Or add a text grid to `HATS`/`ARMOUR` in the generator.
  3. Save the PNG as `res/drawable-nodpi/px_<id>.png` and regenerate.
- **A new equipment slot** (e.g. "shield"):
  1. Add it under *Slots* with an unused layer.
  2. Use `OVERLAY` + an anchor (`head` follows the head bob; `body` follows the torso).
  3. Add items to it.

  The shop and the hero renderer pick it up automatically, with no code changes.
- **An achievement:** pick a stat from the list, a target and a medal tier. To give it its own badge glyph, add a 12×12 grid to `GLYPHS` in the generator (keyed by the achievement id).

- **An NPC:** id, name, colour, skin tone and an optional hat. Regenerate art to draw `px_npc_<id>` (the hero body with a cloak in their colour). Dialogue is a map of keys to lines, with one line picked at random: `greeting` shows on the Today card, `idle` on the *Talk* button. `{hero}` becomes the player's name.
- **An enemy:** HP, strength (HP it takes per missed daily or slip), colour and a shape (`slime`, `ghost`, `golem`). Regenerate art to draw `px_enemy_<id>`. Its `appear`, `hit` and `defeat` lines play when the fight starts, when it strikes and when it falls.
- **A quest line:**
  1. Set the quest giver (an NPC), the level it unlocks at, an optional prerequisite line, a scroll cost and whether it's repeatable.
  2. Add stages in order. Each stage has a title, an optional story and **exactly one** win condition:
     - **Objective:** a stat goal. `GAIN` means *N more from when the stage starts*; `REACH` means *a total of N*.
     - **Boss:** an enemy from the Enemies tab.
     - **Collect:** an item name and amount. Items drop from habits (40%), focus sessions, milestones and goals (100%), and resisting (25%).
  3. Give each stage an intro and an outro conversation (speaker + line; speakers are `hero`, NPC ids or enemy ids) and a reward (XP, coins, an item).

  The preview on the right shows the whole flow.
- **Translations:** the *Translations* tab lists every player-facing string in the content with its key. Pick or add a language, fill in what you can, and save. The editor writes `content/i18n/<lang>.json` with only the translated keys, and anything missing falls back to English. See [docs/TRANSLATING.md](../../docs/TRANSLATING.md).

Don't rename ids after a release: players' inventories, outfits and unlocked achievements store them.

`content.schema.json` describes the format. The editor uses it for dropdowns, IDEs can use it for validation, and a test keeps its enums in sync with the Kotlin code.
