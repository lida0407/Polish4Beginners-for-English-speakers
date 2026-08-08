# Offline dictionary format

Upload path: **Translate tab → Offline dictionary → Upload dictionary**.

The app accepts four shapes. It picks one by looking at the **first non-space
character of the file**:

| First character | Treated as |
|---|---|
| `{` | JSON object map |
| `[` | JSON array of entry objects |
| anything else | Delimited text — TSV or CSV, decided per line |

A UTF-8 BOM is stripped automatically, so "UTF-8 with BOM" exports are fine.

---

## 1. JSON object map (most compact)

Key = Polish, value = translation.

```json
{
  "dom": "house, home",
  "chleb": "bread",
  "dziękuję": "thank you",
  "wolałbym": "I would prefer (male speaker)"
}
```

## 2. JSON array of objects

Field names: `polish` (or `word`) and `english` (or `translation`). Rows
missing either side are skipped. Extra fields are ignored.

```json
[
  { "polish": "dom",     "english": "house, home" },
  { "polish": "chleb",   "english": "bread" },
  { "word":   "gardło",  "translation": "throat" }
]
```

## 3. CSV

Column 1 = Polish, column 2 = translation. Further columns are ignored, so an
existing multi-column export usually works unchanged.

```csv
polish,english
dom,"house, home"
chleb,bread
dziękuję,thank you
```

- Quoted fields are handled properly, so a translation may contain commas.
- A header row is skipped **only** if the first cell is `polish` or the second
  is `english` (case-insensitive). Any other first row is treated as data.

## 4. TSV

Same as CSV but tab-separated. Detection is **per line**: a line containing a
tab is split on tabs, otherwise it is parsed as CSV. Tabs are the safer choice
for dictionary text full of commas.

```tsv
dom	house, home
chleb	bread
```

---

## How entries are stored and matched

Every Polish key is normalized before storage and before lookup:

```
trim  →  lowercase  →  strip trailing . ! ? , ; :
```

So `Dom`, `dom` and `dom.` all become the key `dom`. **Diacritics are kept** —
`gardło` and `gardlo` are different keys, so your file must use proper Polish
characters.

Other behaviour worth knowing:

- **Duplicate keys:** the last occurrence in the file wins.
- **Empty translations** are ignored at lookup time and fall back to the card's
  built-in English.
- **Matching is exact-form, not lemmatized.** A dictionary keyed on base forms
  will match a card that says `dom` but **not** one that says `domu` or
  `domach`. Polish inflection means a base-form dictionary hits fewer cards
  than you might expect — this is the main limitation today.
- The parsed dictionary is written to app-private storage
  (`user_dictionary.json`) and reloaded on a background thread at each launch,
  so size does not affect startup.

## After uploading: build translations

Uploading only loads the dictionary. Press **Build translations** to apply it.
That pass walks all cards once and resolves each:

1. dictionary hit → used directly (instant)
2. no hit but the card already has English → keep that
3. neither → translated once by the on-device engine

The result is cached to `gloss_cache.json`. From then on, study and immersive
listening read translations from an in-memory map — no lookups, no translation
calls, nothing that could stall audio mid-playback. Rebuild after uploading a
new dictionary. **Clear dictionary** removes both the dictionary and the cache.

## Converting MDX / MOBI / StarDict

Binary dictionary formats are not read natively. Convert once on a computer
with [PyGlossary](https://github.com/ilius/pyglossary), then upload the result:

```bash
pip install pyglossary
pyglossary input.mdx output.csv          # MDX  → CSV
pyglossary input.mobi output.tsv         # MOBI → TSV
pyglossary input.ifo  output.csv         # StarDict → CSV
```

If the export carries HTML markup in the definitions (common with MDX), strip
it before uploading — the app shows the value as plain text:

```bash
python3 -c "
import csv,re,sys
rows=[(a,re.sub(r'<[^>]+>',' ',b)) for a,b in csv.reader(open('output.csv',encoding='utf-8')) ]
w=csv.writer(open('clean.csv','w',newline='',encoding='utf-8'))
w.writerows((a,' '.join(b.split())) for a,b in rows)
"
```
