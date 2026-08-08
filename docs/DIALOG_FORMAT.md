# Conversation (dialog) format

Conversations you upload into Polish4Beginners are **JSON**. A file may contain
either one dialog object or an array of dialog objects.

Upload path in the app: **Home → 💬 Conversations → Upload dialog**.

## Fields

### Dialog

| Field | Required | What it is |
|---|---|---|
| `id` | no | Short unique slug, e.g. `"post-office"`. Auto-generated if omitted. |
| `title` | no | English title shown in the list, e.g. `"At the post office"`. |
| `titlePolish` | no | Polish title, e.g. `"Na poczcie"`. |
| `level` | no | `A1`, `A2`, `B1`, `B2` or `C1`. Defaults to `A1`. |
| `scenario` | no | Topic label. Reuse an app category (see list below) so it fits in. |
| `description` | no | One line describing what happens in the conversation. |
| `roles` | no | Map of speaker key → label, e.g. `{"A": "Klient · Customer", "B": "Urzędnik · Clerk"}`. |
| `lines` | **yes** | The conversation itself, in order. |

### Line

| Field | Required | What it is |
|---|---|---|
| `polish` | **yes** | The Polish sentence. This is what gets read aloud. |
| `speaker` | no | `"A"` or `"B"` (must match a key in `roles`). Defaults to `"A"`. `A` renders on the left, everything else on the right. |
| `english` | no | Translation, shown under the Polish and hideable in the app. |
| `note` | no | Short learner tip — an idiom, a case, a formality warning. Keep it to one line. |

Anything else in the file is ignored, so extra fields won't break the import.

## Minimal valid example

```json
{
  "title": "Asking for directions",
  "lines": [
    { "speaker": "A", "polish": "Przepraszam, gdzie jest dworzec?", "english": "Excuse me, where is the station?" },
    { "speaker": "B", "polish": "Prosto i w lewo.", "english": "Straight ahead and to the left." }
  ]
}
```

## Scenario labels used by the app

`Greetings & Essentials`, `Food & Drink`, `Shopping & Money`, `Travel & Transport`,
`Health & Body`, `Work & School`, `Home & Daily Life`, `People & Family`,
`Places & Directions`, `Administration & Services`, `Banking`,
`Clothing & Appearance`, `Technology & Media`, `Activities & Sports`,
`Time & Calendar`, `Weather & Nature`, `Emergencies & Safety`,
`Feelings & Qualities`, `Religion & Culture`, `Nature & Animals`.

## Prompt for generating a conversation

Paste this into any capable LLM, fill in the two bracketed slots, and save the
reply as a `.json` file.

---

You are a Polish language teacher creating practice material for an English
speaker learning Polish.

Write a realistic Polish conversation about: **[DESCRIBE THE SITUATION, e.g.
"registering a car at the local office" or "returning a broken item to a shop"]**
at CEFR level **[A1 / A2 / B1 / B2]**.

Return **only** a JSON array containing one dialog object — no commentary, no
markdown fences. Use exactly this shape:

```json
[
  {
    "id": "short-slug",
    "title": "English title",
    "titlePolish": "Polski tytuł",
    "level": "A2",
    "scenario": "Pick one: Greetings & Essentials, Food & Drink, Shopping & Money, Travel & Transport, Health & Body, Work & School, Home & Daily Life, People & Family, Places & Directions, Administration & Services, Banking, Clothing & Appearance, Technology & Media, Activities & Sports, Time & Calendar, Weather & Nature, Emergencies & Safety",
    "description": "One sentence on what happens.",
    "roles": { "A": "Rola po polsku · Role in English", "B": "Rola po polsku · Role in English" },
    "lines": [
      { "speaker": "B", "polish": "…", "english": "…" },
      { "speaker": "A", "polish": "…", "english": "…", "note": "optional short tip" }
    ]
  }
]
```

Rules:

1. **12–18 lines**, alternating naturally between speakers. `A` is the learner
   (the customer, patient, applicant); `B` is the local (staff member, official).
2. **Natural spoken Polish**, not translated-from-English Polish. Use the
   phrasing a Pole would actually use, including `proszę`, `poproszę`,
   `dzień dobry`, and polite `pan`/`pani` forms where a stranger is addressed.
3. **Correct diacritics** everywhere: ą ć ę ł ń ó ś ź ż. Never substitute
   plain letters.
4. **Match the level.** A1/A2: present tense, short sentences, high-frequency
   vocabulary. B1/B2: subordinate clauses, conditionals, aspect pairs, more
   idiom.
5. **English translations should be natural English**, not word-for-word glosses.
6. Add a `note` only where it genuinely helps — an idiom, a case that surprises
   learners (`szukam + genitive`), a male/female speaker form
   (`chciałbym` vs `chciałabym`), or a false friend. Around 3–5 notes total.
7. Start with a greeting and end with a natural closing.
8. Keep every string on one line and valid JSON (escape any internal quotes).

---

### Tips

- Ask for several conversations at once by requesting multiple objects in the
  array — the app imports them all in one upload.
- If your generated file fails to import, it is almost always a stray markdown
  fence (` ```json `) left at the top, or a trailing comma. Strip those and
  retry.
- Give each dialog a distinct `id`; re-uploading the same `id` adds a second
  copy rather than replacing the first.
