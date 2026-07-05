# Polish Phrasebook Android

A small native Android learning database for useful Polish travel phrases, beginner verbs, starter grammar, and integrated resource modules.

## What is included

- Offline Polish-to-English phrase data in `app/src/main/assets/phrases.json`.
- A Forvo Guide phrase layer covering everyday, transport, dining, shopping, directions, banking, and care situations.
- A curated high-frequency Polish verbs category inspired by the requested study direction.
- A small original grammar category aligned with early beginner topics.
- Resource modules in `app/src/main/assets/resource_library.json`, shaped from the requested Quizlet pages, Elementary Polish course, Forvo guide, and Polish Learner News.
- Polish CORE 2000 vocabulary imported from the provided local PDF into the offline card database.
- CEFR-style `A1`, `A2`, `B1`, `B2`, and `C1` level labels inferred from the Core 2000 frequency order.
- Scenario labels inferred from vocabulary keywords for food, travel, work, school, health, home, nature, activities, and general core vocabulary.
- Category filters, text search, and memory filters for `New`, `Forget`, and `Learnt`.
- Learnt cards are hidden from scenario practice, random practice, and testing.
- Large decks render progressively as you scroll, so the Core 2000 database does not create every card view at once.
- Polish alphabet and digraph pronunciation cards with example words.
- A persistent native tab strip with Alphabet as its own top-level tab.
- Text-only source/resource notes in the More tab.
- Multiple-choice flashcard tests that show a Polish prompt with randomized English choices.
- Flashcard tests can be filtered by CEFR level and scenario.
- Green, blue, and orange study themes.
- Android back button and gesture navigation between app sections.
- A custom `P` launcher icon.
- Audio playback using Android's built-in TextToSpeech voices for Polish and English.
- A dependency-light native Java UI so the project is easy to open in Android Studio.

## Forvo note

The phrase categories and the `Forvo ...` phrase cards are based on Forvo's public Polish travel guide text. This app does not scrape, download, bundle, or stream Forvo recordings. Forvo audio is available through their official API and licensing plans. If you get a Forvo API key, the app can be extended to fetch licensed pronunciation audio; otherwise playback uses device/browser TextToSpeech only.

## Quizlet note

The requested Quizlet pages shaped local study modules and verb coverage. The app does not copy user-authored Quizlet card content wholesale; the offline verbs category is an original curated list of common Polish verbs with English meanings and learning notes.

## Elementary Polish note

The University of Illinois Elementary Polish course is represented as a local beginner grammar module. The offline grammar cards are short original examples aligned with beginner topics surfaced by the course page, not a copy of the course text.

## Learner News note

The Let's Learn Polish Learner News page is represented as a local news-study module with headline metadata for a regular reading habit. The source page offers learner-friendly Polish news, vocabulary support, English translations, exercises, and listening practice.

Sources checked:

- https://forvo.com/guides/useful_phrases_in_polish/
- https://api.forvo.com/
- https://quizlet.com/user/kattarynka/folders/polish-sets
- https://quizlet.com/320432/polish-swadesh-verbs-55-high-frequency-polish-verbs-flash-cards/
- https://faculty.las.illinois.edu/gladney/Elementary_Polish/Start.html
- https://www.lets-learn.eu/polish/news
- https://en.wikibooks.org/wiki/Polish/Polish_pronunciation

Local user-provided PDF:

- `/Users/dali/Downloads/Polish_CORE2000.pdf`

The import script is:

```bash
python3 tools/import_core2000.py /Users/dali/Downloads/Polish_CORE2000.pdf app/src/main/assets/phrases.json
```

## Build

### Android Studio

1. Open this folder in Android Studio:

   ```text
   /Users/dali/Documents/bbbrowser/test/polish-phrasebook-android
   ```

2. Let Android Studio install/sync Gradle and Android SDK dependencies.
3. Run the `app` configuration on an emulator or Android phone.

If audio does not play, install Polish and English voices from Android settings:

```text
Settings -> System -> Languages & input -> Text-to-speech output
```

The exact path varies by Android device.

### Local CLI Build

The local Android SDK, JDK, and Gradle install can build the app from this folder:

```bash
./build-local.sh :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Local web app

The companion web app lives in:

```text
/Users/dali/Documents/bbbrowser/test/polish-learning-web
```

Run it with:

```bash
./polish-learning-web/start.sh
```

Then open:

```text
http://127.0.0.1:48733/polish-learning-web/
```

The web app uses Supertonic for local Polish and English TTS through a FastAPI backend, with browser speech synthesis as a fallback. The native Android app uses Android TextToSpeech because the Supertonic Python/ONNX backend is not bundled into this lightweight Java Android prototype.

## Next upgrades

- Add speech speed controls.
- Add an import path for your own exported or licensed Quizlet/CSV cards.
- Add a deeper native Supertonic/ONNX Android integration.
- Import licensed Forvo API audio when an API key is available.
- Add custom recorded MP3 files under `app/src/main/res/raw` for fully offline human audio.
