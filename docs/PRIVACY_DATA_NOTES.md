# Privacy and data behaviour — source-derived notes

**Purpose:** raw material for writing a privacy policy and completing the
Google Play **Data Safety** form. Every statement below was checked against the
source in `MainActivity.java` and `app/build.gradle.kts` at versionCode 33.
Anything not fully determinable from this app's own code is marked
**⚠️ UNCERTAIN** — verify before relying on it legally.

This is a factual engineering description, **not legal advice**.

---

## 1. Quick answers

| Question | Answer | Evidence |
|---|---|---|
| Do users need an account? | **No.** There is no sign-in, registration or user identity anywhere. | No auth code, no account APIs |
| Is personal information collected? | **No.** The app never asks for name, email, phone, age or any identifier. | No such input fields exist |
| Analytics SDK? | **No.** | Only dependency is `com.google.mlkit:translate` |
| Crash reporting SDK? | **No.** No Crashlytics/Sentry/Bugsnag. | Dependency list |
| Advertising? | **No.** No ad SDK, no ad IDs. | Dependency list |
| Location accessed? | **No.** No location permission or API use. | Manifest has only `INTERNET` |
| Contacts accessed? | **No.** | No permission, no API use |
| Camera / microphone? | **No.** | No permission, no API use |
| Photos / media access? | **No.** No storage permissions. | Manifest |
| Does the app have a backend? | **No.** No server is operated for this app. | No custom endpoints |
| Is anything sold or shared with third parties? | **No.** The app transmits no user data anywhere. | See §3 |

**Only one Android permission is declared: `INTERNET`.**

---

## 2. What is stored on the device

All user data stays in app-private storage. Nothing is uploaded.

### SharedPreferences (`phrasebook`)

| Key | Contents |
|---|---|
| `memory:<cardKey>` | Study progress per card, `"box\|dueAtMillis"` |
| `customCards` | Words the user typed or imported (JSON, with list tag) |
| `customDialogs` | Conversations the user uploaded |
| `favourites` | Starred card identifiers |
| `theme`, `interfaceLanguage`, `speechSpeed` | Display settings |
| `ttsEngine`, `ttsVoicePl`, `ttsVoiceEn` | Chosen speech engine and voices |
| `dataVersion`, `lastDataCheckAt` | Vocabulary update bookkeeping |
| `lastTag` | Last used "My Words" list name |

### Files in app-private storage (`getFilesDir()`)

| File | Contents |
|---|---|
| `user_dictionary.json` | The dictionary the user uploaded, normalized |
| `gloss_cache.json` | Pre-resolved translations (regenerable) |
| `phrases_remote.json` | Downloaded vocabulary database (regenerable) |

None of this is readable by other apps. There is no external/shared storage
write except files the user explicitly saves via the system file picker
(templates they asked to download).

---

## 3. Network endpoints contacted

The app makes **HTTP GET requests only**. It never POSTs, uploads, or sends
user content.

### 3.1 GitHub — vocabulary updates
```
https://api.github.com/repos/lida0407/Polish4Beginners-for-English-speakers/contents/docs/database.json?ref=main
https://api.github.com/repos/lida0407/Polish4Beginners-for-English-speakers/contents/docs/phrases.json?ref=main
```
- Triggered on app start at most once per 24 h, and when the user taps
  "Update words".
- Downloads a version manifest and, if newer, the vocabulary file.
- **Sends no user data.** GitHub necessarily observes the device IP address and
  standard HTTP headers, as with any web request.

### 3.2 Polish news RSS feeds
```
https://tvn24.pl/tvnwarszawa/najnowsze.xml
https://warszawa.wyborcza.pl/pub/rss/warszawa.xml
https://www.rmf24.pl/fakty/feed
https://wiadomosci.onet.pl/.feed
https://www.polsatnews.pl/rss/wszystkie.xml
```
- Fetched **only when the user opens the News tab** and picks a source.
- Read-only XML fetch; headlines and summaries are parsed locally.
- **Sends no user data.** These publishers see the device IP address.
- Opening a full article launches the user's browser (leaves the app).

### 3.3 Google ML Kit — translation model download
- On first translation, ML Kit downloads a Polish↔English model from Google's
  servers (`downloadModelIfNeeded`).
- **After the model is downloaded, translation runs entirely on the device.**
  Text being translated is **not** sent to a server. This is the documented
  behaviour of ML Kit's on-device Translation API and matches how the app calls
  it (`Translation.getClient` + local `translate`).
- ⚠️ **UNCERTAIN:** ML Kit is delivered through Google Play Services. Google may
  collect its own telemetry about model downloads / Play Services usage,
  independently of this app's code. That behaviour is Google's, is not
  controllable from this source, and should be described using Google's own
  ML Kit terms rather than assumed. Review:
  https://developers.google.com/ml-kit/terms

### 3.4 Text-to-speech
- Uses whichever TTS engine the user has installed (device default, Google,
  MultiTTS, etc.) through the standard Android `TextToSpeech` API.
- ⚠️ **UNCERTAIN:** whether audio synthesis happens on-device or in the cloud
  **depends on the third-party engine the user selected**, not on this app. Some
  voices are explicitly network voices (the app labels these "online" in the
  voice picker). Text sent to synthesis is app vocabulary or the user's own
  words. If you need a definitive statement, scope it to "the app passes text to
  the user's chosen system TTS engine".

**There are no other network endpoints.**

---

## 4. User-uploaded content

Users may import, via the Android system file picker (Storage Access
Framework — no storage permission, user picks each file explicitly):

- CSV/TSV/JSON word lists
- CSV/TSV/JSON dictionaries
- JSON conversation files

**This content never leaves the device.** It is parsed locally and written to
app-private storage. The only processing that touches a Google component is
on-device ML Kit translation of blank fields (see §3.3), which does not
transmit text after the model is present.

Users may also *export* templates (word list / dialog templates) to a location
they choose in the file picker.

---

## 5. Android backup

Configured in `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`.

- **Included:** the `phrasebook` SharedPreferences file — study progress,
  favourites, custom words, settings. Chosen so a user restoring a device keeps
  their learning history.
- **Excluded:** `gloss_cache.json` and `phrases_remote.json` — both regenerable,
  and no reason to consume backup quota.
- **Device-to-device transfer** additionally includes `user_dictionary.json`.

If Android Auto Backup is enabled by the user, the included data is stored in
their own Google account backup. This is standard Android platform behaviour;
the app operates no backup service of its own.

---

## 6. Data Safety form — suggested answers

Based on the above. Confirm each against Google's current definitions.

| Play question | Suggested answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** — the app transmits no user or device data to the developer or any third party |
| Is all user data encrypted in transit? | All app-initiated requests use HTTPS |
| Do you provide a way to delete data? | Yes — in-app: Clear dictionary, delete conversations; plus OS-level uninstall/clear data |
| Data types collected | None |
| Data shared with third parties | None |

⚠️ Consider whether Google's ML Kit / Play Services telemetry (§3.3) needs to be
disclosed for your account's circumstances. This app's own code collects
nothing, but Play's definitions concern what happens through the app overall.

---

## 7. Privacy policy content checklist

A policy can truthfully state:

- No account required; no personal information requested or collected
- No analytics, no advertising, no crash reporting, no tracking identifiers
- No location, contacts, camera, microphone or photo access
- All learning progress and imported content is stored only on the device
- The app connects to the internet only to: check for vocabulary updates on
  GitHub, fetch Polish news headlines when the user opens the News tab, and let
  Google ML Kit download an offline translation model
- Translation runs on the device once the model is downloaded
- Speech uses the device's own text-to-speech engine
- Uninstalling removes all locally stored data

A policy should **not** claim: that no data whatsoever reaches Google (Play
Services is involved in model delivery), or make guarantees about third-party
TTS engines the user installs.
