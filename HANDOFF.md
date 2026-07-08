# Handoff: Polish4Beginners Android app

Context for picking up work on this repo. Read this before touching data or code.

## What this app is

Android phrasebook/flashcard app for English speakers learning Polish (A1–C1).
Single-activity, programmatic UI, no XML layouts. Everything lives in one file:
`polish-phrasebook-android/app/src/main/java/com/example/polishphrasebook/MainActivity.java`
(~2,900 lines). Content lives in three JSON assets:

```
polish-phrasebook-android/app/src/main/assets/
  phrases.json          # flashcard deck, 2817 entries
  grammar_lessons.json  # 23 lessons, A1/A2 only
  alphabet.json         # 39 letter/digraph tiles
```

`docs/` mirrors `phrases.json` and serves as the GitHub Pages update channel
(`docs/latest.json` = APK version manifest, `docs/database.json` = phrase-data
version manifest, `docs/P4B.apk` = the distributed APK, both fetched by the
app at runtime via `?ref=main` GitHub API calls — **only `main` is ever read**,
feature branches are invisible to the running app).

## Current repo state (check before assuming anything below is still true)

- `main` is at commit `f3d075e` (v1.13 / dataVersion 3, 2264 phrases).
- Branch `srs-and-example-fields` (merged into main already) added Leitner
  spaced repetition and split the old overloaded `phonetic` field into
  `phonetic` / `examplePolish` / `exampleEnglish` / `notes`.
- Branch `add-book-vocabulary` (commit `97d4740`, **not yet merged to main,
  not pushed to origin**) adds 553 new phrase/vocab entries, bumps to
  v1.14 / dataVersion 4 / 2817 phrases. **`docs/latest.json` on this branch
  still says 1.13** — whoever merges this needs to bump `latest.json` to
  point at the 1.14 APK commit before/as part of pushing to main, or the
  in-app updater won't see the new version. See `README.md` "Publish APK
  Update" section for the exact steps (bump version → rebuild → replace
  `docs/P4B.apk` → commit → point `latest.json` `apkUrl` at that commit sha).
- Run `git status`, `git log --oneline -5`, `git branch -a` first — don't
  trust this file's snapshot over live git state.

## IMPORTANT: copyright policy for content additions

This project's `CONTRIBUTING.md` explicitly says: "Avoid copyrighted source
text copied from books, apps, paid courses, or websites." This has come up
concretely twice this session and will come up again — the user has several
pirated Polish textbook PDFs (from Z-Library/1lib.sk) they've asked to have
"OCR'd and added to the database."

**The line that's been held so far:**
- OK to extract: bare vocabulary (word + gender + translation), and short,
  generic, formulaic phrasebook expressions that appear in essentially every
  Polish phrasebook ("Nice to meet you," "How are you," "My throat hurts").
  These are treated as factual/functional data, not creative expression.
- Not OK: dialogue/narrative text, the author's composed example sentences,
  grammar explanation prose, and — this came up on the second book — a
  workbook's *exercise content* (task prompts, function-phrase sequences
  built for a specific pedagogical sequence), even when it superficially
  looks like a word/phrase list. A CSV the user later provided, extracted
  from a still-in-print 2021 workbook (*Mówię po polsku*, Prolog Publishing),
  was declined for this reason — its own `source_note` column literally said
  "book exercise/text" on every row.
- When content is extracted from a legitimately-usable source (the first
  book, an Internet-Archive-digitized older textbook), every entry was
  **rewritten by hand** — OCR'd Polish diacritics were corrected using actual
  Polish knowledge (the OCR systematically mangled ł/ą/ę/ć/ś/ż/ź), not
  regex-substituted blindly, and no dialogue/prose was copied in.
- If asked again to bulk-import from a book/CSV/PDF: check what the source
  actually is (in-print commercial book? personal-copy watermark? exercise
  workbook vs. plain phrasebook?) before agreeing. When in doubt, ask, or
  offer to write original content inspired by the topic list instead of
  extracting the source's actual text.

Update after this pass: the workbook CSV was **not** copied into the app.
Instead, 116 original B1/B2 speaking-practice cards were written around the
same broad topic areas and marked in `notes` as original cards.

## What's implemented (this session, chronological)

1. **Schema fix** — `phonetic` field used to be overloaded (1968/2264 cards
   had a full example sentence crammed into what should've been a
   pronunciation hint). Split into `phonetic` / `examplePolish` /
   `exampleEnglish` / `notes`. Parser in `MainActivity.java` still
   backward-compatibly detects the old " / "-joined format from any
   already-downloaded remote data.
2. **Leitner spaced repetition** — replaced the old permanent
   new/forgot/learnt status with per-card `CardMemory(box, dueAt)` in
   SharedPreferences. Boxes 0–5, intervals `{0, 1d, 3d, 7d, 16d, 35d}`.
   Session builder (`startSession()`) now pulls due reviews first, then new
   cards, falling back to soonest-scheduled if nothing's due. Old
   learnt/forgot statuses auto-migrate into boxes on first load.
3. **553 new phrase/vocab entries** — see copyright section above for how
   these were sourced. Reuses the existing 33-category taxonomy, all tagged
   A1/A2 (the source material didn't go higher), spread across Feelings &
   Qualities, Grammar & Function Words, Work & School, Travel & Transport,
   Banking, etc. Deduped against the existing DB by normalized Polish
   headword (158 candidates were already present and dropped).

## Known gaps / good next tasks

- **`docs/latest.json` needs bumping** before `add-book-vocabulary` can go
  live (see "Current repo state" above) — do this as part of the merge.
- **B1/B2/C1 grammar lessons don't exist** — `grammar_lessons.json` stops at
  A2-04 even though 1,092 of the 2,817 phrase cards are B1–C1. This is the
  most requested-but-not-yet-done item; the plan discussed with the user was
  to write original B1/B2 grammar lessons (dative case, conditional `by`,
  prefixed motion verbs, opinion/persuasion/summarizing function-phrase
  sets) inspired by — but not copied from — the topic list of the declined
  *Mówię po polsku* workbook (10 B1 units: Znani Polacy, W wolnym czasie nie
  robię nic, Sport to zdrowie, Moje miasto, Wakacje nad Bałtykiem czy w
  Tatrach?, Na piątkę z plusem, Kiedy obchodzisz imieniny?, Książka czy
  film?, Ach! Och! Ech!, Mailem czy esemesem? — plus 10 more B2 units). User
  has not yet said "go ahead" on this — confirm before writing a large batch.
- **~9 garbled Core-2000 cards** — a background task was spawned
  (`task_41c29de8`, title "Repair 9 garbled Core-2000 phrase cards") to fix
  entries where word/translation/example got scrambled across fields during
  the original app's data generation (e.g. `polish: "dziewięć"`,
  `english: "nine ninety-nine percent"`). Check whether that task ran; if
  not, it's still a valid, well-scoped fix.
- **146 duplicate Polish headwords** remain in `phrases.json` (pre-existing,
  not introduced this session) — same phrase appears 2–3× with independent
  memory/scheduling state, so a learner can see "learnt" material resurface
  as "new." Not fixed yet.
- **Reverse-direction (EN→PL) recall mode** and **typed/cloze answers** were
  suggested early in this session as ways to move the app from pure
  recognition drilling toward production practice. Not started.
- **One `MainActivity.java` file (~2,900 lines)** — works, but any further
  feature growth (SRS, new screens) would benefit from splitting out a data
  layer / repository class. Not done, noted as a maintainability risk only.

## Build & verify

```bash
cd polish-phrasebook-android
./build-local.sh :app:assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

Toolchain is pre-configured locally: JDK 17 (temurin) + Android SDK under
`~/.local` and `~/Library/Android/sdk` — see `build-local.sh` for exact env
vars. No emulator was used this session; changes were verified by build
success + manual JSON/data inspection, not on-device testing. Worth doing
a real device/emulator pass before publishing anything new.

## Publishing checklist (from README, repeated here since it's easy to miss a step)

1. Bump `versionCode`/`versionName` in
   `polish-phrasebook-android/app/build.gradle.kts`.
2. If phrase data changed: bump `dataVersion` in `docs/database.json`,
   recompute `phrasesSha256`/`phrasesSizeBytes` against `docs/phrases.json`,
   and mirror any assets/phrases.json changes into docs/phrases.json (they
   must stay byte-identical).
3. Rebuild the APK, copy it to `docs/P4B.apk`.
4. Commit.
5. Compute the new APK's sha256 + size, update `docs/latest.json`'s
   `apkUrl` (must point at the **commit sha that contains the matching
   APK**, via `raw.githubusercontent.com/.../<sha>/docs/P4B.apk`),
   `apkSha256`, `apkSizeBytes`, `versionCode`, `versionName`.
6. Commit again.
7. Merge/push to `main` — the app only ever reads `main` via
   `?ref=main` GitHub API calls, so nothing is live until it's there.
