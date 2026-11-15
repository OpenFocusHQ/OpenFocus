# Translating OpenFocus

OpenFocus has two kinds of text, and each has its own files. English is the source for both. Anything you leave untranslated falls back to English, so a partial translation is fine to ship.

## 1. App text (buttons, screens, notifications)

Android string resources in `app/src/main/res/values/`:

| File | What's in it |
|---|---|
| `strings.xml` | app name, accessibility service description |
| `strings_ui.xml` | screens, buttons, dialogs |
| `strings_quests.xml` | the quest screen, quest messages, objective wording |

To add a language (e.g. Spanish):

1. Create `app/src/main/res/values-es/` (`values-pt-rBR/` for Brazilian Portuguese).
2. Copy the files there and translate the text, keeping the `name` attributes.
3. Keep placeholders such as `%1$s` and `%2$d` (you may reorder them), and keep `\'` for apostrophes.
4. Translate `<plurals>` with your language's quantities (`zero`, `one`, `few`, `many`, `other`).

Strings you leave out use English. You don't need to register the language anywhere: the build generates the locale list (`generateLocaleConfig`), so Android 13+ shows it under *Settings → Apps → OpenFocus → Language*.

## 2. Game content (items, achievements, quests, NPC dialogue, messages)

Content lives in `app/src/main/resources/content/content.json`. Translations are flat overlays in `app/src/main/resources/content/i18n/<lang>.json`:

```json
{
  "$language": "es",
  "items.hat_party.name": "Gorro de fiesta",
  "questLines.first_ascent.stages.pack.intro.0.text": "¿Así que quieres ver la cumbre, {hero}?",
  "npcs.mira.dialogue.greeting.0": "¡Buenos días, {hero}!"
}
```

The easiest way to write these is the **Translations** tab of the [content editor](../tools/content-editor/README.md) (`python3 tools/content-editor/server.py`). It lists every string with its English source, shows what's missing, and saves only the keys you filled in.

How keys work:
- Keys are the JSON path to the text. Entries in a list are named by their `id` (`items.hat_party.name`). Entries without an id use their position (`messages.resist.3`, `titles.0.title`).
- Only text can be translated (names, titles, descriptions, stories, dialogue, messages). Ids, colours and numbers can't, so a translation can't break the game.
- Keep `{hero}` wherever the English has it. It becomes the player's name.
- The app picks `pt-BR.json` first, then `pt.json`, then English.

`ContentValidationTest` fails if an overlay has a key that no longer exists, such as after an id was renamed, or if a line dropped `{hero}`.

## Checking your work

Debug builds include the **pseudolocales**: *English (XA)* makes every string longer and accented, and *Arabic (XB)* flips the layout right-to-left. Turn on Developer options, then pick one under *Settings → System → Languages*. Text that stays plain English in *English (XA)* is still hard-coded and needs moving to `strings_ui.xml`. A few computed labels, mostly on the pause screen and in some game messages, are still in Kotlin; pull requests to move them are welcome.
