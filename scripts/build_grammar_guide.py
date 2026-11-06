#!/usr/bin/env python3
"""Turn content/grammar_guide.md into the app's grammar_guide.json.

Re-runnable: edit the Markdown, run this, rebuild the app.

    python3 scripts/build_grammar_guide.py            # writes the asset
    python3 scripts/build_grammar_guide.py --report   # also prints what was
                                                       # classified as Polish

The app renders a small set of block types, so all Markdown parsing happens
here, where it can be checked, rather than on the phone:

  h        section heading inside a chapter
  p        paragraph: a list of inline spans
  list     bulleted or numbered items, each a list of spans
  table    header + rows, each cell a list of spans
  dialogue a short exchange, one line per speaker turn
  exercise fill-in or translate items with their answers (chapter 15)
  study    the Polish headwords of a vocabulary table, for "Study these words"

An inline span is {"t": text, "b": bold?, "pl": Polish?}. Polish spans become
tappable in the app (translation, audio, Say it, Ask AI, notes), so the
Polish/English split matters; it is decided from a word list built from the
app's own deck and the guide's Polish table columns.
"""
import json
import re
import sys
import unicodedata
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from guide_vocabulary import expand  # noqa: E402  (same splitting as the deck)

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "content" / "grammar_guide.md"
ASSET = ROOT / "polish-phrasebook-android" / "app" / "src" / "main" / "assets"
OUT = ASSET / "grammar_guide.json"
DECK = ASSET / "phrases.json"

POLISH_LETTERS = set("ąćęłńóśźżĄĆĘŁŃÓŚŹŻ")
WORD = re.compile(r"[A-Za-zÀ-ÿĄĆĘŁŃÓŚŹŻąćęłńóśźż]+")
# Tokens that are words in both languages; on their own they say nothing.
AMBIGUOUS = {"a", "i", "to", "na", "on", "no", "do", "so", "or", "by", "we", "my",
             "ten", "pan", "ma", "z", "w", "u", "o"}
# Short Polish words the English list happens to contain (mu, je, ci, …).
POLISH_FUNCTION = {"nie", "tak", "co", "mi", "mu", "go", "ci", "je", "jej", "ich", "im",
                   "pani", "czy", "jak", "ja", "ty", "wy", "oni", "one", "ona", "ono"}
# English words that also occur somewhere in the Polish deck (names, loans).
ENGLISH_HINTS = {"the", "and", "is", "are", "of", "with", "for", "in", "it", "that", "this",
                 "you", "use", "uses", "not", "be", "can", "as", "an", "at", "from",
                 "takes", "means", "form", "forms", "case", "noun", "verb", "plural",
                 "singular", "feminine", "masculine", "neuter", "personal", "other",
                 "example", "examples", "meaning", "pattern", "learn", "word", "words"}


def load_english():
    """System word list, used only as a tie-breaker for tokens the Polish
    lexicon does not know (mostly inflected forms such as czytacie, domach)."""
    try:
        return {w.strip().lower() for w in open("/usr/share/dict/words", encoding="utf-8")}
    except OSError:
        return set()


ENGLISH = load_english()


def norm(text):
    return unicodedata.normalize("NFC", text).strip()


def build_polish_lexicon(md_lines):
    words = set()
    deck = json.loads(DECK.read_text(encoding="utf-8"))
    for card in deck:
        for field in ("polish", "examplePolish"):
            for w in WORD.findall(card.get(field, "")):
                words.add(w.lower())
    # Polish columns of the guide's own tables, located by header name.
    header = None
    for line in md_lines:
        if line.startswith("|"):
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if set("".join(cells)) <= set("-: "):
                continue
            if header is None:
                header = cells
                continue
            for name, cell in zip(header, cells):
                if name.lower() in ("polish", "imperfective", "perfective", "verb", "form",
                                    "singular", "plural", "infinitive", "ty command",
                                    "wy command", "let's…", "month", "in that month",
                                    "date form", "location", "destination", "origin",
                                    "masculine", "feminine", "neuter", "base adjective",
                                    "comparative", "superlative", "adverb / comparative"):
                    for w in WORD.findall(cell):
                        words.add(w.lower())
        else:
            header = None
    return words - ENGLISH_HINTS


def is_polish(text, lexicon):
    tokens = [t.lower() for t in WORD.findall(text)]
    tokens = [t for t in tokens if len(t) >= 2]
    meaningful = [t for t in tokens if t not in AMBIGUOUS]
    # English cue words first: an English sentence that mentions one Polish
    # word ("... uses znać.") is still English.
    if any(t in ENGLISH_HINTS for t in meaningful):
        return False
    if any(ch in POLISH_LETTERS for ch in text):
        return True
    if not meaningful:
        return False

    def polish_token(t):
        if t in lexicon or t in POLISH_FUNCTION:
            return True
        # Unknown to the deck: an inflected Polish form, unless it is plainly
        # an English word.
        return bool(ENGLISH) and t not in ENGLISH

    hits = sum(1 for t in meaningful if polish_token(t))
    return hits / len(meaningful) >= 0.6


SENTENCE = re.compile(r"(?<=[.?!])\s+(?=[A-ZĄĆĘŁŃÓŚŹŻ])")


def classify(part, bold, lexicon):
    """One run of text -> spans. Mixed runs such as "Jestem w sklepie. I am at
    the shop." are split by sentence so each half is tagged on its own."""
    pieces = SENTENCE.split(part) if not bold else [part]
    out = []
    for k, piece in enumerate(pieces):
        text = piece if k == len(pieces) - 1 else piece + " "
        out.append({"t": text, "b": 1 if bold else 0,
                    "pl": 1 if is_polish(piece, lexicon) else 0})
    return out


def spans(text, lexicon):
    """Inline Markdown: **bold** only. Links are flattened to their text."""
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)
    out = []
    for i, part in enumerate(re.split(r"\*\*(.+?)\*\*", text)):
        if not part:
            continue
        bold = i % 2 == 1
        part = part.replace("  ", " ")
        out.extend(classify(part, bold, lexicon))
    return out


def table_cells(line):
    return [c.strip() for c in line.strip().strip("|").split("|")]


def plain(text):
    return re.sub(r"\*\*(.+?)\*\*", r"\1", text).strip()


# ---- chapter 15: exercises and answers -----------------------------------

def parse_exercises(lines):
    groups = {}
    order = []
    key = {}
    current = None
    in_key = False
    for line in lines:
        m = re.match(r"^### ([A-D])\. (.+)", line)
        if m:
            current = m.group(1)
            groups[current] = {"group": current, "prompt": m.group(2).strip(), "items": []}
            order.append(current)
            continue
        if line.startswith("### Answer key"):
            in_key = True
            current = None
            continue
        if in_key:
            m = re.match(r"^\*\*([A-D])[^*]*\*\*:?\s*(.*)", line)
            if m:
                current = m.group(1)
                rest = m.group(2).strip()
                if rest:
                    key[current] = rest
                else:
                    key[current] = []
                continue
            m = re.match(r"^(\d+)\. (.+)", line)
            if m and isinstance(key.get(current), list):
                key[current].append(m.group(2).strip())
            continue
        m = re.match(r"^(\d+)\. (.+)", line)
        if m and current:
            groups[current]["items"].append({"q": m.group(2).strip()})

    for g in order:
        answers = key.get(g)
        items = groups[g]["items"]
        if isinstance(answers, str):
            # "1 biletu; 2 siostrze; ... The full final sentence is **...**."
            body = re.split(r"\.\s+The full", answers)[0]
            parts = [p.strip() for p in body.rstrip(".").split(";")]
            for p in parts:
                m = re.match(r"(\d+)\s+(.+)", p)
                if not m:
                    continue
                n = int(m.group(1)) - 1
                if 0 <= n < len(items):
                    # "książki, książek" answers two blanks in one item.
                    items[n]["a"] = [plain(x).strip() for x in m.group(2).split(",")]
        elif isinstance(answers, list):
            for n, a in enumerate(answers):
                if n < len(items):
                    # Translation: alternatives separated by " / ".
                    items[n]["alts"] = [plain(x).strip() for x in a.split(" / ")]
        for it in items:
            if "a" in it:
                filled = it["q"]
                for ans in it["a"]:
                    filled = filled.replace("___", ans, 1)
                filled = re.sub(r"\s*\([^)]*\)", "", filled).strip()
                filled = re.sub(r"\s+([.,?!])", r"\1", filled)
                it["full"] = filled[:1].upper() + filled[1:]
    return [groups[g] for g in order]


# ---- main -----------------------------------------------------------------

def main():
    report = "--report" in sys.argv
    lines = SOURCE.read_text(encoding="utf-8").splitlines()
    lexicon = build_polish_lexicon(lines)

    chapters = []
    chapter = None
    i = 0
    # The guide's own contents list and authorship line are replaced by the
    # app's chapter list; "Notation" becomes a short first chapter.
    while i < len(lines):
        line = lines[i]
        if line.startswith("# ") or (chapter is None and not line.startswith("### Notation")
                                     and not line.startswith("## 1.")):
            i += 1
            continue
        if line.startswith("### Notation"):
            chapter = {"id": "0", "title": "How to read this guide", "blocks": []}
            chapters.append(chapter)
            i += 1
            continue
        m = re.match(r"^## (\d+)\. (.+)", line)
        if m:
            chapter = {"id": m.group(1), "title": m.group(2).strip(), "blocks": []}
            chapters.append(chapter)
            if m.group(1) == "15":
                # Exercises are structured separately; keep only the intro.
                j = i + 1
                body = []
                while j < len(lines) and not lines[j].startswith("## 16."):
                    body.append(lines[j])
                    j += 1
                intro = [l for l in body[:4] if l.strip() and not l.startswith("#")]
                for l in intro:
                    chapter["blocks"].append({"type": "p", "spans": spans(l, lexicon)})
                for ex in parse_exercises(body):
                    chapter["blocks"].append(dict(type="exercise", **ex))
                i = j
                continue
            i += 1
            continue
        if line.startswith("### "):
            title = line[4:].strip()
            if title.startswith("Short original practice dialogues"):
                title = "Practice dialogues"
            chapter["blocks"].append({"type": "h", "text": title})
            i += 1
            continue
        if line.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                cells = table_cells(lines[i])
                if not set("".join(cells)) <= set("-: "):
                    rows.append(cells)
                i += 1
            header, body = rows[0], rows[1:]
            cells = [[spans(c, lexicon) for c in r] for r in body]
            # Paradigm columns are all Polish, but a few forms (kaw, nasi, nim)
            # are also English dictionary words. If a column is mostly Polish,
            # treat its plain single-span cells as Polish too.
            for col in range(len(header)):
                column = [r[col] for r in cells if col < len(r) and r[col]]
                if not column:
                    continue
                share = sum(1 for c in column if all(x["pl"] for x in c)) / len(column)
                # A column headed by a Polish word (a pronoun, a verb, a noun
                # being declined) holds Polish forms.
                head_words = {w.lower() for w in WORD.findall(header[col])}
                polish_head = bool(head_words) and head_words <= (POLISH_FUNCTION | {"my", "on"}) \
                    or (is_polish(plain(header[col]), lexicon) and col > 0)
                if share >= 0.7 or polish_head:
                    for c in column:
                        if len(c) == 1 and not any(w.lower() in ENGLISH_HINTS
                                                   for w in WORD.findall(c[0]["t"])):
                            c[0]["pl"] = 1
            block = {"type": "table",
                     "header": [plain(c) for c in header],
                     "rows": cells}
            chapter["blocks"].append(block)
            # Vocabulary tables (Polish | English | Polish | English) get a
            # "Study these words" block listing their Polish headwords.
            # Headwords are split exactly as add_guide_vocabulary.py splits
            # them, so every entry has a card to match.
            lower = [h.lower() for h in header]
            words = []
            if lower == ["polish", "english", "polish", "english"]:
                for r in body:
                    for col in (0, 2):
                        if col + 1 < len(r) and r[col]:
                            words.extend(w for w, _, _ in expand(plain(r[col]), plain(r[col + 1])))
            elif lower == ["verb", "form", "meaning", "useful structure"]:
                words = [plain(r[0]).replace("(pf)", "").strip() for r in body if r and r[0]]
            if words:
                seen = []
                for w in words:
                    if w not in seen:
                        seen.append(w)
                chapter["blocks"].append({"type": "study", "entries": seen})
            continue
        if line.startswith("**") and line.endswith("**") and i + 2 < len(lines) \
                and lines[i + 2].startswith("— "):
            title = plain(line)
            i += 2
            turns = []
            while i < len(lines) and lines[i].startswith("— "):
                turns.append(lines[i][2:].rstrip())
                i += 1
            meaning = ""
            while i < len(lines) and not lines[i].strip():
                i += 1
            if i < len(lines) and lines[i].startswith("Meaning:"):
                meaning = lines[i][len("Meaning:"):].strip()
                i += 1
            chapter["blocks"].append({"type": "dialogue", "title": title,
                                      "lines": [t.strip() for t in turns], "meaning": meaning})
            continue
        m = re.match(r"^(\s*)([-*]|\d+\.) (.+)", line)
        if m:
            ordered = m.group(2)[0].isdigit()
            items = []
            while i < len(lines):
                m2 = re.match(r"^(\s*)([-*]|\d+\.) (.+)", lines[i])
                if not m2:
                    break
                items.append(spans(m2.group(3).rstrip(";"), lexicon))
                i += 1
            chapter["blocks"].append({"type": "list", "ordered": ordered, "items": items})
            continue
        if line.strip():
            chapter["blocks"].append({"type": "p", "spans": spans(line.strip(), lexicon)})
        i += 1

    guide = {"version": 1, "title": "Grammar & Vocabulary Guide", "chapters": chapters}
    OUT.write_text(json.dumps(guide, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    blocks = sum(len(c["blocks"]) for c in chapters)
    tables = sum(1 for c in chapters for b in c["blocks"] if b["type"] == "table")
    study = sum(len(b["entries"]) for c in chapters for b in c["blocks"] if b["type"] == "study")
    exercises = sum(len(b["items"]) for c in chapters for b in c["blocks"] if b["type"] == "exercise")
    print(f"{len(chapters)} chapters, {blocks} blocks, {tables} tables, "
          f"{study} vocabulary entries, {exercises} exercise items -> {OUT.relative_to(ROOT)} "
          f"({OUT.stat().st_size} bytes)")
    if report:
        pl, en = [], []
        for c in chapters:
            for b in c["blocks"]:
                groups = []
                if b["type"] in ("p",):
                    groups = [b["spans"]]
                elif b["type"] == "list":
                    groups = b["items"]
                elif b["type"] == "table":
                    groups = [cell for row in b["rows"] for cell in row]
                for g in groups:
                    for s in g:
                        if s["b"] or b["type"] == "table":
                            (pl if s["pl"] else en).append(s["t"].strip())
        print("\n--- classified Polish (sample of bold/table text):")
        print(" | ".join(sorted(set(pl))[:400]))
        print("\n--- classified NOT Polish:")
        print(" | ".join(sorted(set(en))))


if __name__ == "__main__":
    main()
