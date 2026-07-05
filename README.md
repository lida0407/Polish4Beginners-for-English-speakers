# Polish for Beginners: Android App for English Speakers

Polish4Beginners is an Android app for English speakers who want to learn Polish for beginner travel, daily life, grammar, alphabet pronunciation, and practical phrase practice.

This repository is focused on **Polish for beginners**, especially learners starting from English.

## Features

- `2264` Polish-English phrase and vocabulary cards
- `23` grammar lessons
- `39` alphabet and pronunciation tiles
- A1, A2, B1, B2, and C1 level filters
- Scenario-based Polish phrase learning for beginner conversations
- Warm editorial Android UI
- 10-card study sessions
- New, learning, and learnt memory states
- Browse, grammar, and alphabet tabs
- Settings for interface language, color theme, and reading speed
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
