# Contributing Phrase Cards

Thank you for helping improve Polish4Beginners for English Speakers.

## Phrase Format

Use `docs/phrase-template.json` as the starting point for new cards.

Required fields:

- `level`: CEFR-style level, for example `A1`, `A2`, `B1`, `B2`, or `C1`.
- `scenario`: practical learning situation, for example `Shopping`, `Health`, `Transport`, `Work & School`, or `Greetings & Essentials`.
- `category`: usually the same value as `scenario`; use this for app grouping.
- `polish`: the Polish word or phrase.
- `english`: the English meaning.

Recommended fields:

- `phonetic`: pronunciation respelling only, for example `chi MOH-gheh zah-PWAH-cheech KAR-tom`. Do not put usage notes or example sentences here.
- `examplePolish`: one natural Polish sentence using the word or phrase.
- `exampleEnglish`: the English translation of `examplePolish`.
- `tags`: short searchable labels.
- `notes`: anything learners or reviewers should know, such as formal/informal tone, gendered speaker form, or regional usage.

## Example

```json
{
  "level": "A1",
  "scenario": "Shopping",
  "category": "Shopping",
  "polish": "Czy mogę zapłacić kartą?",
  "english": "Can I pay by card?",
  "phonetic": "chi MOH-gheh zah-PWAH-cheech KAR-tom",
  "examplePolish": "Czy mogę zapłacić kartą, czy tylko gotówką?",
  "exampleEnglish": "Can I pay by card, or only in cash?",
  "tags": ["payment", "shop", "question"],
  "notes": "Useful in shops, restaurants, and ticket offices."
}
```

## Quality Notes

- Keep phrases practical for beginners.
- Prefer natural Polish over word-for-word translation.
- Mark formal or informal usage in `notes` when relevant.
- Avoid copyrighted source text copied from books, apps, paid courses, or websites.
- Keep one phrase per JSON object.
