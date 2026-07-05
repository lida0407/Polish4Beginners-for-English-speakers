# Polish4Beginners Android

Native Android app for learning Polish from English.

## What Is Included

- Offline Polish-English cards in `app/src/main/assets/phrases.json`
- Grammar lessons in `app/src/main/assets/grammar_lessons.json`
- Alphabet tiles in `app/src/main/assets/alphabet.json`
- Study session, browse, grammar, and alphabet screens
- New, learning, and learnt memory states
- Five local themes
- Android TextToSpeech for Polish and English

## Build

```bash
./build-local.sh :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

If audio does not play, install Polish and English voices from Android Text-to-speech settings.
