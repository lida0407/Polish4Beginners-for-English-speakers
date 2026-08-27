# Polish4Beginners — design handoff

For a designer redesigning the interface. Describes what the app is, every
screen that exists, the current visual system with real values, and the
technical constraints any redesign has to survive.

**Platform:** Android phone, portrait-first. minSdk 23, targetSdk 35.
**Status:** shipping (sideloaded), being prepared for Google Play.

---

## 1. What this is, and who uses it

An **offline-first Polish learning app for English speakers**. One person's
daily study tool, not a social product: no accounts, no feed, no gamification
streaks, no ads.

The learner is an adult who lives in or visits Poland and needs the language
for real errands — the bundled conversations are things like buying a train
ticket, checking into a hotel, returning a faulty product, describing symptoms
at a pharmacy, reporting a lost passport.

Typical session: a few minutes of flashcards, or hands-free listening while
doing something else, or reading a conversation before an appointment.

**Content scale** (this is a content-dense app — designs must survive real
volume):

| Thing | Count |
|---|---|
| Vocabulary cards | 3,170 (A1–C1, 34 topic categories) |
| Conversations | 33 dialogs / 521 lines |
| Grammar lessons | 23 |
| Alphabet tiles | 39 |
| Built-in dictionary | 50,234 entries |

---

## 2. Current look — "study journal"

The existing design is a deliberate **warm editorial / paper** aesthetic:
cream backgrounds, a serif for Polish text, a sans for UI, hairline borders,
dashed rules, hard offset shadows rather than soft elevation. It reads like a
notebook, not like Material Design.

The masthead says **"TWÓJ DZIENNIK NAUKI"** (your study journal) with the title
**"Mój polski"** and today's date in Polish.

**This aesthetic is not sacred** — you may redesign freely. But two things
about it are worth preserving in spirit:

1. **Polish text is the hero.** It is set larger, in a serif, and is the first
   thing the eye lands on. English is supporting text.
2. **Calm, not gamified.** No confetti, streak counters, or nagging.

---

## 3. Design tokens as they exist today

### Colour — 5 user-selectable themes

Themes are chosen by the user from swatches in the masthead. **A redesign must
keep a multi-theme system**, or explicitly propose replacing it.

Each theme defines 17 named roles. Roles matter more than the specific hexes:

| Role | Meaning |
|---|---|
| `bg` | screen background |
| `panel` | card/surface background |
| `ink` | primary text + strong borders |
| `body` | body text |
| `muted` / `faint` / `ghost` | descending text emphasis (3 steps) |
| `softLine` / `dash` | hairline and dashed rules |
| `shadow` | hard offset shadow colour |
| `accent` / `accentAlt` / `accentSoft` / `onAccent` | primary action colour set |
| `accent2` / `onAccent2` / `accent2Text` | secondary accent set |

Actual values:

```
Klasyczny (default, warm paper)
  bg #f7f1e6  panel #fffdf7  ink #23251f  body #4b463b
  muted #6d6759  faint #8c8677  ghost #c9c0ad
  softLine #e3d9c6  dash #d9cfba
  accent #c2402f (rust)  accentSoft #fbe9e4  onAccent #fdf8ee
  accent2 #29489c (ink blue)  onAccent2 #fdf8ee

Las (forest)
  bg #edf1e4  panel #fafcf3  ink #1e2a1f  accent #2d6a4f  accent2 #a5651f

Bałtyk (baltic)
  bg #e9eff1  panel #f9fcfd  ink #182630  accent #1f6f8b  accent2 #ad4a2f

Wrzos (heather)
  bg #f2ecf2  panel #fcf9fc  ink #2a2130  accent #6d3f7d  accent2 #ad4a2f

Atrament (ink — the only dark theme)
  bg #201d18  panel #2a2620  ink #f1e9d8  body #d8cfba
  accent #d9a441 (gold)  accent2 #c96f4a
```

Note there is exactly **one dark theme**. There is no system dark-mode
following — worth fixing.

### Type

- **Serif — Source Serif 4 Bold.** Polish words, headlines, card fronts.
- **Sans — IBM Plex Sans** in Regular / Medium / SemiBold / Bold. Everything
  else.
- Sizes currently in use: 10, 10.5, 11, 11.5, 12, 12.5, 13, 13.5, 14, 15, 16.5,
  17, 18, 19, 20, 22, 24, 26, 30, 33, 34 sp. **This is not a scale — it is
  drift.** Defining a real type scale is one of the most valuable things a
  redesign can do.
- "Label" style = uppercase, small (10–12sp), letter-spaced 0.06–0.18.

### Shape and depth

- Corner radius: **3–4dp only**. Nearly square.
- Borders: 1.5dp hairlines; cards use a full `ink` border.
- Shadow: a **hard offset rectangle** (no blur), drawn by a custom
  `ShadowLayout`. Not Material elevation.
- Spacing: multiples of 4dp via a `dp()` helper; screen padding 20dp
  horizontal, 16dp top, 24dp bottom.

---

## 4. Screen inventory

13 render methods. Bottom nav has **7 tabs** — already crowded, and two more
destinations (Listen, Conversations) had to be pushed onto the Home screen as
buttons because they didn't fit. **Navigation is the single biggest structural
problem to solve.**

### Bottom nav (7 tabs)
`Home · Cards · Grammar · ABC · News · Translate · Settings`
Icons are hand-drawn vector paths in a custom `NavIcon` view (home, book,
open-book, "Aa", newspaper, two arrows, gear).

### 1. Home
Masthead (kicker, title, date, 5 theme swatches) → hero card ("Today's lesson",
headline, dashed rule, due/new counts, big start button) → level selector
(A1/A2/B1/B2/C1) → **Listen** and **Conversations** buttons → stats strip
(New / To review / Scheduled — each tappable) → Favourites button (if any) →
"My word lists" (per-list buttons) → Topics (6 topic buttons with counts).

*Problem: this screen has become a dumping ground. Everything that didn't fit
elsewhere lives here.*

### 2. Study session (full screen, no nav)
Close ×, striped progress bar, "card n/N" → large card: level·category chip,
star toggle, direction hint (`EN → PL` / `PL → EN`), the word, and on reveal:
dashed divider, translation, pronunciation, example block, notes, full
**declension table** (monospace, 7 cases × sg/pl), Read PL / Read EN, Share,
Google Translate → two answer buttons ("Jeszcze nie" / "Umiem!").

*Problem: the revealed state is extremely dense — up to 8 stacked elements plus
a 9-row table. This is the highest-value screen to redesign.*

### 3. Cards (browse)
Search field → horizontally scrolling topic chips → count label → rows (Polish,
English, a coloured status stripe, a "PL ▸" speak button) → "Show more" (pages
of 25).

### 4. Grammar
23 accordion cards: unit badge, scenario, topic, focus, rule, pattern, examples,
and a self-check prompt with reveal.

### 5. ABC (alphabet)
39 tiles: letter, "sound like" hint, Polish example + English. Tap = speak.

### 6. News
Source picker → article cards with Polish headline/summary and an in-app
English translation, swipe or ‹ › to page, link out to the browser.

### 7. Translate
Direction header with a ⇄ swap → input → Translate button → result card (Read /
Copy / Add to a list) → **Your word lists** section (template download, CSV
upload, per-list study buttons) → **Offline dictionary** section (entry counts,
upload, "Build translations", clear).

*Problem: three unrelated jobs stacked on one screen.*

### 8. Listen (immersive)
Topic chips → big card (level·category, Polish word, translation, example,
position) → ‹ Play/Pause › → Hide/Show English. Reads Polish ×2 then English ×1
with a 1s gap, hands-free, looping. Screen stays awake.

*Tap any Polish word → word sheet (see below). Playback pauses.*

### 9. Conversations
List of dialog cards (level·scenario, line count, EN + PL title, description) →
detail view: chat bubbles alternating left/right by speaker, role labels,
Polish + English + learner note, active line highlighted during playback →
Play conversation / Hide EN. Below the list: dialog template download + JSON
upload.

*Tap any Polish word in a line → word sheet.*

### 10. Word sheet (dialog, appears from Listen and Conversations)
Word, source label ("built-in dictionary" / "on-device translation"),
translation, then 🔊 Play · ★ Favourite · **Add to a list**.

### 11. Settings
Interface language (EN/PL) · Theme (5 swatches) · Reading speed (5 steps) ·
Reading voice (engine picker + per-language voice picker + install links) ·
App version · Word database update.

### 12. Loading / error
Shown while ~4 MB of JSON parses on a background thread. Currently just the
title and "Loading your cards…", plus an error state with Retry.

### 13. Session complete
"BRAWO!", "Lesson finished", score line, Again / Back home.

---

## 5. Technical constraints — read before designing

These are real limits of the codebase, not preferences.

1. **The UI is built in Java code, not XML or Compose.** Every view is
   constructed programmatically in one 5,000-line Activity. There is no layout
   editor and no design-system library.
   → Practical effect: **custom shapes, gradients, blur, complex overlays and
   fancy transitions are expensive to build.** Anything expressible as
   rectangles, hairlines, text and simple drawn paths is cheap.

2. **Every interaction rebuilds the whole screen** (`setContentView` on each
   state change). There are no view animations or shared-element transitions
   today, and adding them is non-trivial.
   → Design for **static states**, not motion. If motion matters, call it out
   separately as a stretch goal.

3. **No icon library.** Icons are hand-drawn `Canvas` paths. Every new icon is
   hand-coded.
   → Prefer few, simple, geometric icons. Or specify an icon set and expect it
   to be added as vector drawables (a real but bounded task).

4. **Themes are code objects**, not resource files. Adding a colour role means
   editing all 5 themes.

5. **Text length varies wildly.** Polish words can be 3 characters or a full
   sentence; declension tables need a monospace grid; conversation lines can run
   3 lines. **No fixed-height cards.**

6. **Long lists are plain `LinearLayout`s inside a `ScrollView`** (paged 25 at a
   time). A design that assumes `RecyclerView` behaviour (sticky headers, swipe
   actions, fast-scroll) needs that noted as engineering work.

7. **Fonts are bundled** (IBM Plex Sans, Source Serif 4). New typefaces must be
   licensed for app embedding and add to APK size.

8. **Accessibility today is weak** — no content descriptions, tap targets vary,
   contrast unverified. A redesign should specify minimum 48dp targets and
   verify contrast against all 5 themes.

---

## 6. Known problems worth solving

Ranked by impact:

1. **Navigation overflow.** 7 bottom tabs, plus 2 destinations exiled to Home.
   Needs a real information architecture — probably 4–5 primary destinations
   with the rest grouped.
2. **Home is a junk drawer.** Hero + level + 2 mode buttons + 3 stats +
   favourites + N word lists + 6 topics, stacked vertically.
3. **Study card density.** The revealed answer can stack 8 blocks plus a 14-cell
   declension table. Needs progressive disclosure.
4. **Translate screen does three jobs** (translate / word lists / dictionary
   management).
5. **No type scale.** ~21 distinct font sizes in use.
6. **One dark theme, no system dark-mode following.**
7. **Loading state is bare** — first launch shows a near-empty screen for a
   moment.
8. **Empty states are mostly toasts** rather than designed screens.

---

## 7. What must not change

- **Feature set.** Nothing may be dropped; this is a redesign, not a rescope.
- **Offline-first.** No design that assumes network (no remote images/fonts).
- **Polish-first hierarchy.** The Polish text is the content; English supports.
- **5 themes with user choice** (or an explicit, better proposal).
- **Both interface languages** — every label exists in English *and* Polish.
  Polish strings are typically **20–40% longer**; layouts must not break.
  Example: "Conversations" → "Rozmowy", but "Settings" → "Ustawienia",
  "Reading speed" → "Szybkość czytania".
- **Diacritics must render**: ą ć ę ł ń ó ś ź ż. Any specified typeface must
  support full Polish.

---

## 8. Useful deliverables

Most valuable first:

1. **Information architecture** — how ~10 destinations resolve into a nav that
   fits.
2. **Redesigned study card**, both states (front / revealed), handling the
   dense case with a declension table.
3. **Home screen** rethought around "what do I do right now".
4. **Design tokens**: a real type scale, spacing scale, and the colour roles
   mapped for all 5 themes (light + dark).
5. **Component sheet**: card, list row, chip, button variants, stat, bubble,
   progress, nav.
6. Then: Listen, Conversation detail, Translate, Browse, Settings.

Screen dimensions to design at: **360 × 800 dp** (typical Android phone), with
notes for 320dp-wide and large-font accessibility settings.

---

## 9. Reference

Source of truth for current behaviour:
`polish-phrasebook-android/app/src/main/java/.../MainActivity.java`
(render methods listed in §4; theme values in `buildThemes()`).

Related docs in `docs/`: `ANDROID_HANDOFF.md` (engineering state),
`PLAY_STORE_RELEASE.md`, `DIALOG_FORMAT.md`, `DICTIONARY_FORMAT.md`.
