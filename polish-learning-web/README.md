# Polish Learning Desk

Local web app for the shared Polish phrasebook data.

## Run

```bash
./polish-learning-web/start.sh
```

Open:

```text
http://127.0.0.1:48733/polish-learning-web/
```

## TTS

This app uses Supertonic as the local model TTS backend:

- `POST /api/tts` generates cached WAV audio with Supertonic.
- `GET /api/tts/status` reports whether the local SDK is available.
- The first model-backed read can take longer because Supertonic downloads model assets from Hugging Face.
- If the local model is unavailable, the browser falls back to `speechSynthesis`.

The UI has separate `Read Polish` and `Read English` buttons for each card.

## Learning Flow

Every phrase, verb, and grammar card has a memory state:

- `New`
- `Forget`
- `Learnt`

The states are stored locally in the browser. Use the memory filters in the sidebar to review only new, forgotten, or learnt material. `Shuffle Test` shuffles the current filtered deck and hides the English answer until you reveal it.

## Source Library

The source library in `resource-library.json` includes the resources shared for this project:

- Forvo Polish travel guide
- Quizlet Kattarynka Polish sets
- Quizlet Polish Swadesh verbs deck
- University of Illinois Elementary Polish
- Let's Learn Polish Learner News

It stores local summaries, topic lists, current headline metadata, and source-linked preview images where available. It does not mirror entire third-party pages, full Quizlet decks, full news articles, source audio, or copyrighted lesson text. These resources are presented as in-app learning modules rather than a visible link directory.

## Why Supertonic

Supertonic was chosen over NeuTTS for this app because the current Supertonic README explicitly lists both Polish (`pl`) and English (`en`) among its supported languages and documents a local HTTP/server workflow. The NeuTTS README describes strong on-device TTS support, but the model list shown there does not include Polish.
