# Polish4Beginners — current interface inventory

Snapshot of **v1.38** (versionCode 39). Written to hand to a designer: it
describes what exists today, the constraints any redesign has to live inside,
and the rough edges worth designing away.

Audience: someone proposing visual/UX improvements. It is not a code guide.

---

## 1. What the app is

An Android app for **English speakers learning Polish**, A1–C1. Free, offline
first, no account, no analytics, no ads. Content today:

| Content | Count |
|---|---|
| Flashcards | 3,170 across 34 topics, levels A1–C1 |
| Conversations (dialogs) | 33 across 12 scenarios |
| Grammar lessons | 23 (A1–A2 only) |
| Alphabet tiles | 39 letters and digraphs |

The user can add their own material: word lists (CSV), conversations (JSON),
and an offline dictionary — so **every list in the app can grow far beyond the
bundled counts**. Design for "hundreds", not for the numbers above.

---

## 2. Hard constraints on any redesign

These are properties of the implementation, not preferences. A design that
ignores them cannot be built without a rewrite.

1. **No XML layouts and no Jetpack Compose.** The entire UI is built
   programmatically in Java, in one `MainActivity.java` (~5,650 lines). Every
   view is constructed in code. There is no layout inspector, no preview, no
   design-time tooling — a mock has to be described precisely enough to be
   re-typed as Java.
2. **Every interaction rebuilds the whole screen.** Tapping anything calls
   `render()`, which does a full `setContentView`. Consequences:
   - There are **no animations or transitions anywhere in the app** (verified:
     zero animator/interpolator calls). Screens cut instantly.
   - **No ripple or press feedback** on any control (zero `RippleDrawable`).
   - Anything requiring continuous motion (swipeable cards, drag-to-reorder,
     shared-element transitions, parallax) is a significant rework, not a
     restyle.
3. **Backgrounds are custom drawables, not images.** No illustrations, no
   raster assets, no icon font. Icons are hand-drawn in `Canvas` code
   (`NavIcon`), so **each new icon is bespoke code** — a large icon set is
   expensive. Vector/SVG assets would be a new capability.
4. **Min SDK 23** (Android 6). Fonts fall back to system sans below API 26.
5. **Everything must work offline.** No remote images, web fonts or CDN.

Things that *are* cheap: colour, type scale, spacing, corner radius, border
weight, copy, the order and grouping of elements, adding or removing cards and
sections, empty states.

---

## 3. The design system as it stands

### Themes

Four user-selectable themes, switched from **swatches in the home masthead** or
from Settings. Every screen follows the active theme — no screen is hardcoded.

| Theme | Character | Radius | Halftone texture |
|---|---|---|---|
| **Komiks** (default) | Violet/lime, comic-book | 12dp | Yes |
| **Borówka** | Blue/coral | 18dp | No |
| **Mięta** | Mint/red | 18dp | No |
| **Zachód** | Sunset orange/purple | 18dp | No |

All four are **light**. There is no dark theme; `isDarkTheme()` derives from
background luminance, so adding a dark theme requires only a new entry — the
status bar already adapts.

Each theme is 18 colour tokens plus 3 shape tokens:

```
bg  panel  ink  body  muted  faint  ghost  softLine  dash  shadow
accent  accentAlt  accentSoft  onAccent
accent2  onAccent2  accent2Text  accent3
radius(dp)  border(dp)  halftone(bool)
```

A redesign should be expressible as **new values for these tokens** wherever
possible — that propagates everywhere for free. Adding tokens is possible but
touches every call site.

### Type

Two families, bundled (SIL OFL):

- **Baloo 2** — display/headings (rounded, cartoon).
- **Nunito** — body and UI, four weights.

Sizes in use run roughly 9.5sp (nav labels) → 34sp (home wordmark "Mój polski").

### Shape and depth

- Cards and buttons: rounded rect, theme `radius`, **2.5dp solid border**, flat
  fill. The border is the defining visual device — this is an outlined,
  comic-panel look, not a Material elevation look.
- Depth comes from one custom `ShadowLayout` (hard offset shadow, no blur).
- `HalftoneDrawable` paints a dotted background texture (Komiks only).
- `DashedLine` separates sections.
- `StripeProgress` is the progress bar.

---

## 4. Navigation

**Five bottom tabs**, always visible except during a study session:

`Home` · `Cards` · `Listen` · `Read` · `More`

Sub-screens reached from tabs, not from the bar: Session, Grammar, Alphabet,
News, Translate, Conversations, Settings. `Read` is a two-way chooser
(News / Conversations); `More` is a menu of four rows.

There is **no back arrow in-app** — the system back button is the only way up
from most sub-screens.

---

## 5. Screen-by-screen

### Home
- Masthead: wordmark **"Mój polski"** + four theme swatches inline.
- Level chips: A1 / A2 / B1 / B2 / C1.
- Three stat cells (counts, coloured by theme accents).
- **Hero card**: kicker "TODAY'S LESSON", headline "10 flashcards, level A1",
  primary CTA *"Zaczynamy — start session"*.
- Two secondary buttons side by side: 🎧 **Listen**, 💬 **Conversations**.
- ★ **Favourites** row (only when non-empty), then **MY WORD LISTS** rows.
- **TOPICS** — one full-width button per topic with a card count.

### Study session (full screen, nav hidden)
- Progress strip + "card 3 / 10".
- Big flashcard. Unrevealed it reads `ODWRÓĆ KARTĘ · TAP TO FLIP`; tapping
  reveals translation, phonetic, example sentence, notes.
- Card tools: ☆/★ favourite, **Read PL**, **Share**, **Google Translate**.
- Grading: **"Jeszcze nie"** / **"Umiem!"**; before reveal, **"Pokaż odpowiedź"**.
- Finish screen: "WELL DONE! / Lesson finished", score, **Again** / **Back home**.

### Cards (catalog)
- Search field ("Search: dworzec, to eat…"), level filter, topic chips.
- Result list with **Show more** paging.

### Listen (immersive listening)
- **Source chips**: ★ Favourites, each My Words list, then All + level topics.
- Player: **Play/Pause**, **Show/Hide English**, current word large.
- Loop is Polish ×2 → English ×1 → pause; screen stays awake.
- Tap any word → lookup sheet. Empty sources show an explanatory card.

### Read → Conversations
- **Expandable scenario filter** (new in 1.38): collapsed summary row
  (`▸ All scenarios (12)`), expands to every scenario as a wrapping grid of
  toggles with counts; multi-select; **Show all** / **Done**.
- List **pages 10 at a time** with Back / Next and a page readout.
- Conversation detail: role chips, line-by-line Polish + English, per-line tap
  to hear, **Play conversation**, **Show/Hide EN**, delete (custom uploads).
- Tap any word → lookup sheet.

### Read → News
- Polish RSS headlines, refreshed on demand, with in-app translation.

### More
Four rows: **Grammar** (23 lessons with self-checks) · **Alphabet** (39 letters
and sounds) · **Translate** (dictionary, word lists, imports) · **Settings**.

### Grammar / Alphabet
- Grammar: expandable lessons — Rule, Pattern, "Check yourself", answer,
  **Read examples**.
- Alphabet: tap-a-tile grid, plays letter + example word.

### Translate
- Two-way EN↔PL translator (on-device ML Kit), **Translate / Read / Copy /
  Add to a list**.
- **YOUR WORD LISTS** — counts, **Download template**, **Upload list**.
- **OFFLINE DICTIONARY** — upload, **Build translations**, **Clear**.

### Settings
Interface language · Colour theme · Reading speed (5 steps) · Reading voice
(TTS engine + per-language voice, supports MultiTTS) · App version · Word
database updater.

### Word lookup sheet
A themed panel inside a **system `AlertDialog`**: word, translation, optional
"Dictionary form:" hint, then 🔊 Play · ★ Favourite · **Add to a list**.

---

## 6. Rough edges worth designing away

Honest list, from reading the current code and screens:

1. **Right-aligned counts are faked with whitespace.** Rows like Favourites,
   word lists and topics are single buttons whose label is
   `"Topic" + 34 spaces + "12 cards →"`. At other text sizes or in Polish this
   will misalign. These want to be real two-column rows.
2. **No press feedback anywhere.** Nothing reacts to touch before the screen
   redraws. A cheap, high-value fix.
3. **No transitions.** Every navigation is a hard cut, including entering and
   leaving a study session.
4. **The study session is the least designed screen** despite being the core
   loop — and its buttons are the only UI text hardcoded in Polish
   (`Umiem!`, `Jeszcze nie`, `Pokaż odpowiedź`) rather than localised. Decide
   whether that is deliberate immersion or a bug.
5. **Settings copy says "the same five themes"; there are four.** Stale string.
6. **The word lookup uses a system dialog frame** around themed content, so its
   corners, backdrop and insets do not match the app's look.
7. **No dark theme**, though the plumbing supports one.
8. **Long lists rely on `Show more` or paging** rather than real recycling —
   worth knowing before designing anything list-heavy.
9. **Level is global**, but Favourites and word lists deliberately ignore it.
   That inconsistency is invisible in the UI and can confuse.
10. **Five tabs, twelve screens.** Grammar, Alphabet, Translate and Settings all
    hide behind *More*; News and Conversations both hide behind *Read*. Whether
    that hierarchy is right is an open question.

---

## 7. Ground rules for a proposal

- Express changes as **theme tokens, type scale, spacing and copy** first;
  those land cheaply and everywhere.
- Flag explicitly anything that needs motion, gestures, new icons or raster
  assets — each is real engineering work here, not a restyle.
- **Do not propose changes that would drop content or reset user progress.**
  Card identity, favourites and word lists are persisted; renaming or
  renumbering them loses real study history.
- Keep it **offline and dependency-free**: no web fonts, no remote images, no
  new SDKs without asking.
- Polish text is long. Any label that fits in English must be checked in
  Polish — the app ships a full Polish UI translation.
