# Polish4Beginners — Android engineering handoff

Written for an Android developer taking this project over. It describes what
exists, how to build it, how the data pipeline works, and — candidly — what
would have to change before this could ship on Google Play.

**Repo:** https://github.com/lida0407/Polish4Beginners-for-English-speakers
**Current release:** v1.31 (versionCode 32)

---

## 1. What the app is

An offline-first Polish learning app for English speakers. Feature set as built:

| Area | Detail |
|---|---|
| Vocabulary | 3,170 phrase/word cards, A1–C1, 34 topic categories |
| Declensions | Full 7-case singular/plural tables on 98 common nouns |
| Grammar | 23 lessons (A1–A2) |
| Alphabet | 39 letter/digraph tiles with audio |
| Conversations | 33 scenario dialogs, 521 lines, with line-by-line and full playback |
| Study engine | Leitner spaced repetition, 5 boxes (1/3/7/16/35 days) |
| Listening | Hands-free loop: Polish ×2 → English ×1 → 1 s pause → next |
| Translation | On-device ML Kit PL↔EN, plus user-uploaded dictionary with a prebuilt gloss cache |
| User content | CSV word lists, named "My Words" tags, favourites, uploadable JSON dialogs |
| TTS | Any installed engine (MultiTTS, Google…), per-language voice selection |
| News | Polish RSS feed with in-app translation |

Everything except the news feed and first-time ML Kit model download works
offline.

---

## 2. Build

```bash
cd polish-phrasebook-android
./build-local.sh :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

`build-local.sh` just exports a toolchain and calls Gradle:

- JDK 17 (Temurin), `ANDROID_HOME=~/Library/Android/sdk`, Gradle 8.10.2
- AGP 8.7.3, `compileSdk`/`targetSdk` 35, `minSdk` 23
- Single dependency: `com.google.mlkit:translate:17.0.3`

There is **no** `gradlew` wrapper committed — the script uses a system Gradle.
Adding the wrapper should be one of the first things you do.

---

## 3. Architecture — read this before you open the file

The app is **one Java file**:

```
app/src/main/java/com/example/polishphrasebook/MainActivity.java   (4,963 lines)
```

It contains 19 inner classes (data models, custom `View`s, drawables). There
are **no XML layouts** — every screen is built programmatically. `res/` holds
only fonts, colors, styles, launcher icons.

How it works:

- A single `String screen` field acts as the router (`"home"`, `"session"`,
  `"browse"`, `"listen"`, `"dialogs"`, `"translate"`, …).
- All UI state lives in Activity fields.
- `render()` rebuilds the **entire view tree** and calls `setContentView(root)`
  on every state change — every tap, every card flip.

This is unconventional and you will probably want to restructure it. Two
honest observations: it is genuinely simple to follow, and it does work. But it
has hard consequences, listed in §6.

Content is in `app/src/main/assets/`:

```
phrases.json         933 KB   3,170 cards
dialogs.json         102 KB   33 conversations
grammar_lessons.json  20 KB   23 lessons
alphabet.json          3 KB   39 tiles
```

---

## 4. Data model

### Phrase card
```json
{
  "category": "Food & Drink", "scenario": "Food & Drink", "level": "A1",
  "polish": "chleb", "english": "bread",
  "phonetic": "", "examplePolish": "…", "exampleEnglish": "…",
  "notes": "(m.) · G. chleba · pl. chleby",
  "declension": "Declension · masculine (m.)\n     sg …",
  "coreIndex": 123
}
```
Only `polish`, `english`, `level`, `category` matter; the rest are optional.
`coreIndex` (when > 0) is the stable identity used for progress keys — cards
without it key on `category + ":" + polish`. **Do not renumber `coreIndex`**;
it would orphan users' spaced-repetition history.

### Dialog
See `docs/DIALOG_FORMAT.md` for the full spec and a generation prompt.
`docs/dialog-template.json` is a valid example.

### Local persistence
All in `SharedPreferences` ("phrasebook") except two JSON files in
`getFilesDir()`:

| Key / file | Holds |
|---|---|
| `memory:<cardKey>` | `"<box>\|<dueAtMillis>"` — Leitner state per card |
| `customCards` | user-added words (JSON array, each with a `tag`) |
| `customDialogs` | uploaded dialogs |
| `favourites` | starred card keys |
| `ttsEngine`, `ttsVoicePl`, `ttsVoiceEn` | speech selection |
| `user_dictionary.json` | uploaded dictionary, normalized PL → gloss |
| `gloss_cache.json` | prebuilt card → translation, so playback never looks up |

---

## 5. Release pipeline (currently GitHub-based)

Two **independent** update channels, both read from `main` via the GitHub API
(`?ref=main` — nothing is live until it is on `main`):

1. **APK channel** — `docs/latest.json` points `apkUrl` at a specific commit's
   `docs/P4B.apk`. The app downloads it and opens the system installer.
2. **Data channel** — `docs/database.json` carries `dataVersion` + SHA-256; if
   it is newer, the app downloads `docs/phrases.json` and caches it locally.

Consequence worth knowing: **a pure vocabulary change needs no APK.** Bump
`dataVersion`, refresh the checksum, push. Content in `assets/` (dialogs,
grammar, alphabet) *does* require a rebuild — only `phrases.json` is wired to
the data channel.

Helper scripts in `scripts/` (Python) generate and validate the data:
`add_declensions.py`, `add_noun_variations.py`, `clean_phrase_data.py`,
`merge_chatgpt_words.py`, `add_idioms_and_function_phrases.py`.

---

## 6. Known issues and technical debt

Ordered by how much they would bite you.

### Blocking for a Google Play release

1. **`applicationId = "com.example.polishphrasebook"`.** Google Play **rejects**
   any `com.example.*` package. This must change (e.g.
   `com.mustardseed.polish4beginners`) — and note that changing it makes it a
   *different app*: existing sideloaded users cannot upgrade in place.
2. **Debug signing.** There is no release `signingConfig` and no keystore. The
   published APK is debug-signed. You need a release keystore + Play App Signing.
3. **Self-update mechanism violates Play policy.** `REQUEST_INSTALL_PACKAGES`
   plus downloading and installing an APK from GitHub is not permitted for
   Play-distributed apps. For a Play build, strip the updater, remove the
   permission, and let Play handle updates. (Keep it only if you continue
   sideloading.)
4. **No privacy policy.** Required — the app uses network, downloads ML Kit
   models, and reads a news feed.
5. **64 MB APK.** Mostly ML Kit native libs. Ship an **AAB** with ABI/density
   splits; consider ML Kit's downloadable model variant. Also enable R8 —
   there is currently no `minifyEnabled`/ProGuard configuration at all.

### Correctness / UX bugs

6. **No configuration-change handling.** There is no `onSaveInstanceState`, no
   `android:configChanges`, and no ViewModel. Rotating the device destroys and
   recreates the Activity, resetting every state field — the user is thrown
   back to Home and an in-progress flashcard session is lost. This is the most
   user-visible defect in the app.
7. **933 KB of JSON parsed on the main thread in `onCreate`.** `loadPhrases()`
   → 3,170 objects, plus dialogs/grammar/alphabet, all synchronous before the
   first frame. Startup jank on low-end devices and an ANR risk. Move to a
   background load with a splash/empty state. (The dictionary load was already
   moved off-thread — follow that pattern.)
8. **Full view-tree rebuild per interaction.** `render()` calls
   `setContentView()` every time. On the Browse screen with many rows this is
   measurable. A `RecyclerView` for the card/dialog lists is the obvious win.
9. **TTS completion callbacks are engine-dependent.** Listening mode and dialog
   playback chain on `UtteranceProgressListener.onDone`. Some third-party
   engines fire it unreliably; a watchdog timeout fallback would make this
   robust. Not yet implemented.
10. **146 → 0 duplicate headwords were cleaned, but matching is exact-form.**
    The user dictionary lookup does not handle Polish inflection (`dom` matches,
    `domu` does not). Stemming or a morphological index would improve hit rate.

### Engineering hygiene

11. **Zero tests.** No unit tests, no instrumentation tests, no test source set.
12. **All strings hardcoded** in a `t(english, polish)` helper rather than
    `res/values/strings.xml` + `values-pl/`. Blocks proper localization and
    per-locale resource behaviour.
13. **`.git` is 675 MB** because a ~64 MB APK has been committed on nearly every
    release. Move `docs/P4B.apk` to Git LFS or (better) publish binaries as
    GitHub Releases instead of repo files. History rewriting would be needed to
    actually reclaim the space.
14. **`allowBackup="true"`** with no `dataExtractionRules`/`fullBackupContent`.
    Decide deliberately what should be backed up (progress probably yes,
    caches no).
15. **No crash reporting or analytics.**

---

## 7. Suggested first moves

If the goal is a Play Store release:

1. Add the Gradle wrapper; set up CI that builds an AAB.
2. Change `applicationId`, create a release keystore, enable R8/minify.
3. Remove the self-updater and `REQUEST_INSTALL_PACKAGES`; gate it behind a
   `sideload` product flavour if you still want it.
4. Fix rotation state loss (§6.6) — this is the highest user-visible payoff.
5. Move asset loading off the main thread (§6.7).
6. Write a privacy policy; complete the Data Safety form.

If the goal is to keep iterating on features first, do 4 and 5 anyway — they
are cheap and affect every screen — and treat the single-file architecture as
something to decompose incrementally (extract the data layer and the
SharedPreferences repository first; they are the cleanest seams).

---

## 8. Content provenance — please read

Vocabulary and dialogs in this repo are either originally written for the app
or derived from non-copyrightable factual data (word + translation, grammatical
forms). Material from commercial textbooks was deliberately **excluded**:
during development, two pirated textbook PDFs and a CSV derived from a
still-in-print 2021 workbook were declined for import, and the project's
`CONTRIBUTING.md` states the rule explicitly.

If you add content, keep to that line: bare vocabulary and standard idioms are
fine; another author's composed dialogues, example sentences, and exercise
sequences are not.
