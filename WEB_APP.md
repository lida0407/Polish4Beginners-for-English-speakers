# Polish4Beginners web app

Live at <https://lida0407.github.io/Polish4Beginners-for-English-speakers/app/> (GitHub Pages serves `docs/`, so the app is `docs/app/`). It works on phones and desktops, installs from the browser, and runs offline after the first visit.

## How it is built

No build step: plain ES modules, one stylesheet, static files.

| Path | What |
| :-- | :-- |
| `docs/app/index.html`, `css/app.css` | page and the three themes (same colour tokens as Android) |
| `js/core.js` | data loading, storage, speech, UI helpers: the API every screen uses |
| `js/logic.js` | port of Android `LearningLogic.java`; `js/logic.test.mjs` is the same 46 tests (`node --test docs/app/js/logic.test.mjs`) |
| `js/word.js` | word lookup sheet, tappable Polish text, favourites |
| `js/views/*.js` | one file per screen |
| `js/sync.js`, `js/firebase-config.js` | optional sign-in and progress sync |
| `sw.js`, `manifest.webmanifest`, `icons/` | offline cache and install |
| `data/`, `fonts/` | copied and subset by `scripts/sync_web_app.py` |

The phrase deck and the conversations are **not copied**: the web app reads `docs/phrases.json` and `docs/dialogs.json`, the same files the Android app updates from.

## Keeping web and Android aligned

One rule: **every Android release ends with `python3 scripts/sync_web_app.py`, then commit `docs/app`.**

- **Version:** the script stamps the Android `versionName` into the web app (Settings → About shows "web 1.50, matches Android 1.50"). The web app has no version number of its own.
- **Lessons:** the script copies the grammar guide, grammar lessons and alphabet from the Android assets. The deck and conversations are shared files.
- **Guard:** `python3 scripts/sync_web_app.py --check` exits with an error when the web copy lags (different data or version). Run it before any release.
- **Progress format:** card ids and the review schedule use the same code (`LearningLogic` ↔ `logic.js`), so a learner's progress means the same thing in both apps. The web app's Settings → Backup exports and imports that progress as a JSON file.

- **Moving a learner from Android to the web:** `scripts/android_to_web_backup.py` converts the Android app's saved data (review memory, favourites, own words, own conversations, notes, course progress) into a backup file for the web app's Settings → Backup → Import. `--list "Name=file.json"` adds any other word list, e.g. words collected from another course. The output is personal data: keep it outside this repository.

A feature added to one app is not automatically in the other: screens are separate code (Java on Android, `js/views` on the web). Add it to both, or note the gap here.

Not in the web app today: news with word translation, on-device translator, offline dictionary upload, custom conversation upload, Polish interface language.

## Turning on sign-in (Google and GitHub)

The app works without accounts; progress then stays in the browser. Sign-in adds sync across devices. It uses Firebase's free plan (no card needed). Only Da can do steps 1–5 because they create an account-level project.

1. <https://console.firebase.google.com> → **Add project** → name it `polish4beginners` → Google Analytics **off**.
2. **Build → Authentication → Get started → Sign-in method:**
   - Enable **Google** (choose a support email).
   - Enable **GitHub**. It asks for a Client ID and secret: on GitHub go to Settings → Developer settings → OAuth Apps → **New OAuth App**, homepage `https://lida0407.github.io/Polish4Beginners-for-English-speakers/app/`, callback = the URL Firebase shows (`https://<project>.firebaseapp.com/__/auth/handler`). Paste the ID and secret into Firebase. The secret stays in Firebase and never goes in this repository.
3. **Authentication → Settings → Authorized domains → Add** `lida0407.github.io`.
4. **Build → Firestore Database → Create database** (production mode, a Europe region) → **Rules**, paste and publish:
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /users/{uid} {
         allow read, write: if request.auth != null && request.auth.uid == uid;
       }
     }
   }
   ```
5. **Project settings → Your apps → Web (`</>`)** → register `Polish4Beginners web` (no hosting) → copy the `firebaseConfig = { … }` object.
6. Put that object in `docs/app/js/firebase-config.js` (`export const firebaseConfig = { … };`), run `python3 scripts/sync_web_app.py`, commit and push. These values are public identifiers; the rules above are what protect the data.
7. Update `docs/privacy.html` before step 6 goes live: the web app then stores, per signed-in learner, their study progress, word lists and notes, plus the account's name, email and photo held by Firebase Authentication. Learners can delete both in Settings → Account.

What is stored: one document per user, `users/<uid>`, holding the same keys as the browser storage (`mem`, `fav`, `lists`, `notes`, `course`, `settings`, …), each with a last-changed time. Merging is per key, newest wins.

## Local preview

```bash
python3 -m http.server 4780 --directory docs
```

Then open <http://localhost:4780/app/>. The service worker is off on localhost so edits show at once.
