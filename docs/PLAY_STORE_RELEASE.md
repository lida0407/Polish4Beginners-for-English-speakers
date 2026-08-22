# Google Play release guide

Everything needed to build, sign and ship Polish4Beginners.

- **Application ID:** `com.mustardseed.polish4beginners`
- **Module:** `polish-phrasebook-android/`
- **Artifact for Play:** Android App Bundle (`.aab`)

---

## 1. Prerequisites

| Tool | Version |
|---|---|
| JDK | 17 |
| Android SDK | API 35 installed |
| Gradle | supplied by the wrapper (8.10.2) — do not use system Gradle |
| AGP | 8.7.3 |

Point Gradle at your SDK, either by exporting `ANDROID_HOME` or by creating
`polish-phrasebook-android/local.properties` (git-ignored):

```properties
sdk.dir=/Users/you/Library/Android/sdk
```

All commands below run from `polish-phrasebook-android/`.

---

## 2. Create your upload keystore

Do this **once**. Keep the file and passwords safe — with Play App Signing you
can recover a lost *upload* key via Google support, but treat it as precious.

```bash
keytool -genkeypair -v \
  -keystore ~/keystores/p4b-upload.jks \
  -alias p4b-upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

You will be asked for a keystore password, a key password, and your name /
organization details.

**Store it outside the repository.** `~/keystores/` is a good choice.
`.gitignore` already blocks `*.jks`, `*.keystore` and `keystore.properties`, but
do not rely on that alone — never place a keystore inside the project folder.

---

## 3. Configure signing

The build reads credentials from **either** source; use whichever you prefer.

### Option A — `keystore.properties` (recommended for local builds)

Create `polish-phrasebook-android/app/keystore.properties` (git-ignored):

```properties
storeFile=/Users/you/keystores/p4b-upload.jks
storePassword=«your keystore password»
keyAlias=p4b-upload
keyPassword=«your key password»
```

### Option B — environment variables (recommended for CI)

```bash
export P4B_KEYSTORE=/Users/you/keystores/p4b-upload.jks
export P4B_KEYSTORE_PASSWORD='…'
export P4B_KEY_ALIAS=p4b-upload
export P4B_KEY_PASSWORD='…'
```

Verify it is picked up:

```bash
./gradlew checkReleaseSigning
```

- Configured → prints the keystore file name.
- Not configured → **fails** with instructions. This guard exists so a release
  can never be uploaded unsigned by accident.

If signing is not configured, `bundleRelease` still runs and produces an
**unsigned** bundle. That is fine for build verification, but Play will reject it.

---

## 4. Build the release bundle

```bash
./gradlew clean bundleRelease
```

Output:

```
app/build/outputs/bundle/release/app-release.aab
```

That single file is what you upload to Play. Google generates per-device APKs
from it, so users download considerably less than the bundle size.

To test the release build on a real device first:

```bash
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

---

## 5. Release optimization

Already configured in `app/build.gradle.kts`:

- `isMinifyEnabled = true` — R8 code shrinking/obfuscation
- `isShrinkResources = true` — removes unreferenced resources
- Rules in `app/proguard-rules.pro`, notably **keep rules for ML Kit**, which
  loads classes dynamically and would otherwise break at runtime
- Line numbers are preserved so Play Console crash reports stay readable

**If you add a library that uses reflection, add keep rules and re-test the
release build.** A release-only crash is almost always a missing keep rule.

---

## 6. Version numbers

Edit `app/build.gradle.kts`:

```kotlin
versionCode = 33      // integer; MUST increase for every Play upload
versionName = "1.32"  // human-readable
```

Rules:
- `versionCode` must strictly increase; Play rejects re-uploads of the same value.
- `versionName` is cosmetic.
- The in-app Settings screen shows both.

---

## 7. Permissions

The release manifest declares exactly one permission:

| Permission | Why it is needed |
|---|---|
| `android.permission.INTERNET` | Vocabulary updates from GitHub; Polish news RSS feeds; ML Kit's one-time translation model download |

`REQUEST_INSTALL_PACKAGES` was **removed** — Google Play must manage app
updates, and self-installing APKs violates Play policy. The in-app APK updater
was deleted with it. The vocabulary/data update system is unaffected and still
works.

Verify on any release build:

```bash
./gradlew :app:processReleaseManifestForPackage
grep -c REQUEST_INSTALL_PACKAGES app/build/intermediates/merged_manifest/release/AndroidManifest.xml   # → 0
```

### Restoring the updater for sideloading (optional, not for Play)

The updater lives in git history. To bring it back for a non-Play distribution,
add a product flavour (e.g. `sideload`) with its own
`src/sideload/AndroidManifest.xml` containing `REQUEST_INSTALL_PACKAGES`, and
restore the removed methods into that source set. **Do not add it to the Play
flavour.**

---

## 8. What the app does at runtime

Full detail in [PRIVACY_DATA_NOTES.md](PRIVACY_DATA_NOTES.md). Summary:

- **Network:** GitHub (vocabulary manifest + database), five Polish news RSS
  feeds (only when the News tab is used), Google ML Kit model download.
- **Local storage:** study progress, favourites, custom words/lists, uploaded
  dictionaries and conversations, cached translations — all app-private.
- **Leaves the device:** nothing. No accounts, analytics, ads or crash SDKs.
- **ML Kit:** translation is on-device after the model downloads.
- **News:** read-only RSS; article links open the user's browser.
- **Vocabulary updates:** `docs/database.json` carries `dataVersion` + SHA-256;
  a newer version causes `docs/phrases.json` to be downloaded and cached. This
  ships new words **without** an app update.

---

## 9. Tests

```bash
./gradlew test
```

Covers the logic that user data depends on: Leitner box/interval maths, legacy
progress migration, persistent card identity (`coreIndex`), data-version
comparison, and validation of the shipped JSON assets (including that no two
cards share a progress key, and that `docs/phrases.json` matches the bundled
asset).

---

## 10. Pre-release checklist

Run through this before every Play upload.

- [ ] `./gradlew test` passes
- [ ] `versionCode` incremented
- [ ] `versionName` updated
- [ ] `./gradlew checkReleaseSigning` succeeds
- [ ] `./gradlew clean bundleRelease` succeeds
- [ ] `app-release.aab` exists
- [ ] Release manifest contains **no** `REQUEST_INSTALL_PACKAGES`
- [ ] `applicationId` is `com.mustardseed.polish4beginners`
- [ ] Installed the release APK on a device and smoke-tested: study session,
      listening mode, a conversation, translation, TTS playback
- [ ] `git status` clean of keystores, `keystore.properties`, `local.properties`
- [ ] Privacy policy URL live and reachable
- [ ] Data Safety form matches PRIVACY_DATA_NOTES.md

---

## 11. First-submission notes

Things only needed for the very first release:

1. **Play Console app record** — create it with the exact application ID
   `com.mustardseed.polish4beginners`. This **cannot be changed later.**
2. **Play App Signing** — accept it. You upload with your upload key; Google
   holds the app signing key.
3. **Store listing** — title, short and full description, at least 2 phone
   screenshots, 512×512 icon, 1024×500 feature graphic.
4. **Content rating** questionnaire.
5. **Data safety** form — see PRIVACY_DATA_NOTES.md.
6. **Target audience** — if you declare children as a target audience,
   additional Families policy requirements apply; an educational language app
   aimed at adults avoids that.
7. **Privacy policy URL** — required.

### ⚠️ Existing sideloaded users

The application ID changed from `com.example.polishphrasebook` to
`com.mustardseed.polish4beginners`. Android treats these as **two different
apps**:

- Anyone who installed the old GitHub APK will **not** receive this as an
  update.
- Their existing progress lives under the old package and **will not migrate**.
- Both versions can be installed side by side.

This was unavoidable — Google Play rejects `com.example.*` package names. If you
want to help existing users, tell them to note their progress or simply start
fresh, and consider retiring the GitHub APK distribution once Play is live.
