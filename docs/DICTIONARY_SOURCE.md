# Built-in dictionary — provenance and licensing

**⚠️ Action required before publishing to Google Play.** Read §3.

## 1. What ships

`app/src/main/assets/dictionary_pl_en.tsv`

- 50,234 Polish → English entries, tab separated (`headword\tgloss`)
- Headwords normalized (trimmed, lowercased, trailing punctuation stripped)
- Glosses carry a part-of-speech prefix and up to two senses, e.g.
  `dom → (noun) home; house (building)`
- 2.95 MB raw, ~0.89 MB compressed inside the AAB

## 2. How it was produced

Converted from a user-supplied Kindle dictionary:

```
Polish-English-Chinese Super Dictionary WORKING.mobi   (32.8 MB)
```

- Mobipocket, format version 7, UTF-8, **PalmDOC (LZ77) compression**
- Decompressed with `scripts/mobi_extract.py` (written for this project); the
  decompressed stream matched the length declared in the MOBI header exactly
  (19,998,807 bytes), which is a strong correctness signal
- Entries are `<hr/>`-separated: `<b>` headword, `<div><i>` part of speech,
  `<ol><li>` English senses, and a separate `<div><b>中文</b>` block
- **The Chinese block was dropped** — the app needs Polish → English only
- Entries typed `character` / `punct` were skipped as non-vocabulary

To regenerate:

```bash
python3 scripts/mobi_extract.py "path/to/dictionary.mobi"   # -> dict_raw.html
# then the parsing step documented in this file's git history
```

## 3. ⚠️ Licensing — unresolved

The source `.mobi` was provided from a local `output/` folder, so it was
probably generated rather than purchased. The entry style is a strong match for
**Wiktionary**-derived data:

- sense markers such as `(intransitive)`, `(figuratively)`, `(obsolete)`
- glosses like `ticket [+ do (genitive)] or [+ na (accusative)]`
- Chinese glosses in CC-CEDICT style (`[地名] [荷兰] …`)

**If it is Wiktionary-derived**, the data is **CC BY-SA 3.0**, which permits
redistribution — including in a Play Store app — but requires:

1. **Attribution** to Wiktionary/Wikimedia contributors
2. **Share-alike**: the dictionary file stays under CC BY-SA
3. A link to the licence

That is easy to satisfy: add an attribution line to the app's Settings screen
and to the Play listing, e.g.

> Dictionary data derived from Wiktionary, © Wiktionary contributors,
> licensed under CC BY-SA 3.0 — https://creativecommons.org/licenses/by-sa/3.0/

**If it came from a commercial dictionary instead**, bundling it would be
copyright infringement and it must be removed before release.

### What you need to do

- [ ] Confirm where the `.mobi` originally came from
- [ ] If Wiktionary/open data → add the attribution above to Settings and the
      Play listing, and note the licence in the repo
- [ ] If commercial/unknown → delete `dictionary_pl_en.tsv` from assets and
      revert the bundled-dictionary wiring (the app degrades gracefully: word
      lookup falls back to on-device ML Kit translation)

Until this is resolved, treat the bundled dictionary as **not cleared for
publication**. Everything else in the release is unaffected.

## 4. How the app uses it

| Step | Behaviour |
|---|---|
| Startup | Loaded on the background data thread into a `HashMap` (~50k entries) |
| Exact hit | Shown as the answer, labelled "built-in dictionary" |
| No exact hit | The word is translated by on-device ML Kit; if suffix trimming finds a plausible base form it is shown as a separate "Dictionary form:" hint |
| Precedence | A user-uploaded dictionary always wins over the built-in one |
| Gloss build | "Build translations" consults the built-in dictionary before falling back to translation |

### Why exact-match only

Polish is heavily inflected and the dictionary is keyed on lemmas. Suffix
trimming reaches roughly **75%** of the words in the app's 33 conversations,
but it is not reliable enough to present as truth: `dzwoni` (from *dzwonić*,
"to call") trims to the noun `dzwon`, "bell". A confidently wrong translation
is worse for a learner than no dictionary entry, so trimmed matches are only
ever offered as a labelled hint next to a real translation.

Measured against all 771 unique words in the bundled conversations:

| Method | Coverage |
|---|---|
| Exact dictionary hit | 324 words (42%) |
| + suffix/stem hint | 582 words (75%) |
| Remainder | on-device ML Kit translation |
