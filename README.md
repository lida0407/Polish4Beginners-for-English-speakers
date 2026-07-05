# Polish4Beginners for English Speakers

A local Polish learning app for English speakers, built around an Android native app plus a local web companion.

## Included

- Android app in `polish-phrasebook-android`
- Local web companion in `polish-learning-web`
- `2264` Polish-English phrase and vocabulary cards
- `23` grammar lessons
- `39` alphabet/pronunciation tiles
- New/forget/learnt memory states
- Scenario sessions, flashcards, grammar, alphabet, themes, and TTS

## Build Android APK

```bash
cd polish-phrasebook-android
./build-local.sh :app:assembleDebug
```

The debug APK is created at:

```text
polish-phrasebook-android/app/build/outputs/apk/debug/app-debug.apk
```

## Run Web Companion

```bash
./polish-learning-web/start.sh
```

Then open:

```text
http://127.0.0.1:48733/polish-learning-web/
```

## Notes

The app keeps learning data local in JSON assets and uses Android TextToSpeech on the phone. The web companion can use local Supertonic TTS when available, with browser speech as fallback.
