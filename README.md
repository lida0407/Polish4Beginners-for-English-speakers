# Polish for Beginners: Android App for English Speakers

Polish4Beginners is an Android app for English speakers who want to learn Polish for beginner travel, daily life, grammar, alphabet pronunciation, and practical phrase practice.

This repository is focused on **Polish for beginners**, especially learners starting from English.

Download the Android APK: [P4B.apk](https://lida0407.github.io/Polish4Beginners-for-English-speakers/P4B.apk)

## Features

- `3329` Polish-English phrase and vocabulary cards
- Example sentences with Polish audio on most cards
- `23` grammar lessons
- `39` alphabet and pronunciation tiles
- A1, A2, B1, B2, and C1 level filters
- Scenario-based Polish phrase learning for beginner conversations
- Warm editorial Android UI
- 10-card study sessions
- Leitner-style spaced repetition: cards you know come back at growing intervals (1, 3, 7, 16, 35 days), cards you miss return the same day
- Browse, grammar, and alphabet tabs
- Daily Polish news tab with in-app Polish-English translation for headlines and snippets
- Settings for interface language, color theme, and reading speed
- GitHub-connected APK update checking from Settings
- Separate GitHub-connected word database updates from Settings
- Five color themes
- Android TextToSpeech for Polish and English

## Who This Is For

- English speakers looking for a Polish for beginner Android app
- Learners who want Polish flashcards, grammar, and pronunciation in one place
- Travelers who need practical Polish phrases for everyday situations
- Contributors who want to add beginner-friendly Polish-English phrase cards

## Build Android APK

```bash
cd polish-phrasebook-android
./build-local.sh :app:assembleDebug
```

The debug APK is created at:

```text
polish-phrasebook-android/app/build/outputs/apk/debug/app-debug.apk
```

## Publish APK Update

GitHub Pages serves:

```text
docs/P4B.apk
docs/latest.json
docs/phrases.json
docs/database.json
```

When publishing a new APK, bump `versionCode`/`versionName`, rebuild, replace `docs/P4B.apk`, commit it, then update `docs/latest.json` so `apkUrl` points to that APK commit.

When publishing phrase-only contributions, update `docs/phrases.json`, bump `dataVersion` in `docs/database.json`, and keep the phrase count/checksum in sync.

## Project Layout

```text
polish-phrasebook-android/
  app/src/main/assets/
    alphabet.json
    grammar_lessons.json
    phrases.json
  app/src/main/java/com/example/polishphrasebook/MainActivity.java
docs/
  phrase-template.json
```

## Contribute Phrases

New phrase contributions are welcome. Please follow the format in:

```text
docs/phrase-template.json
```

See `CONTRIBUTING.md` for field definitions and review notes.
