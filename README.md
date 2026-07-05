# Polish4Beginners for English Speakers

An Android Polish learning app for English speakers.

## Features

- `2264` Polish-English phrase and vocabulary cards
- `23` grammar lessons
- `39` alphabet and pronunciation tiles
- Warm editorial Android UI
- 10-card study sessions
- New, learning, and learnt memory states
- Browse, grammar, and alphabet tabs
- Five color themes
- Android TextToSpeech for Polish and English

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
```
