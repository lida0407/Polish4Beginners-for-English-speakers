#!/usr/bin/env python3
"""Vocabulary from content/grammar_guide.md as flashcard candidates.

Shared by add_guide_vocabulary.py and its dry run. Each guide cell such as
"matka / mama (f)" with English "mother / mum" becomes one card per Polish
word, with gender kept as a note.
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "content" / "grammar_guide.md"

GENDER = {"m": "Gender: masculine", "f": "Gender: feminine", "n": "Gender: neuter",
          "pl": "Plural-only noun"}
FEMININE_SUFFIXES = ("ka", "ica", "owa", "yni", "ini")
# Guide topic -> the deck's existing category, so new cards join topic sessions.
TOPIC_CATEGORY = {
    "11.1": "Greetings & Essentials",
    "11.2": "People & Family",
    "11.3": "Home & Daily Life",
    "11.4": "Food & Drink",
    "11.5": "Travel & Transport",
    "11.6": "Shopping & Money",
    "11.7": "Work & School",
    "11.8": "Health & Body",
    "11.9": "Time & Calendar",
    "11.10": "Feelings & Qualities",
}
TOPIC_LEVEL = {"11.1": "A1", "11.2": "A1", "11.3": "A1", "11.4": "A1", "11.5": "A1",
               "11.6": "A2", "11.7": "A2", "11.8": "A2", "11.9": "A1", "11.10": "A1"}
FIXES = {"tall-high": "tall / high", "short-low": "short / low"}


def plain(text):
    return re.sub(r"\*\*(.+?)\*\*", r"\1", text).strip()


def split_gender(cell):
    """'człowiek (m), ludzie (pl)' -> [('człowiek', 'm'), ('ludzie', 'pl')]"""
    out = []
    for part in [p.strip() for p in cell.split(",")]:
        m = re.match(r"^(.*?)\s*\((m|f|n|pl)\)$", part)
        out.append((m.group(1).strip(), m.group(2)) if m else (part, None))
    return out


def expand(polish, english):
    """One guide cell pair -> list of (polish, english, note)."""
    english = FIXES.get(english, english)
    parts = split_gender(polish)
    cards = []
    if len(parts) == 2 and parts[1][1] == "pl" and "," in english:
        # "dziecko (n), dzieci (pl)" / "child, children": a singular card that
        # carries its irregular plural in the note.
        (sg, g), (pl, _) = parts
        en_sg = english.split(",")[0].strip()
        note = (GENDER.get(g, "") + "; " if g else "") + "plural: " + pl
        return [(sg, en_sg, note)]
    word, g = parts[0]
    alts = [a.strip() for a in word.split(" / ")]
    eng = [e.strip() for e in english.split(" / ")]
    m = re.match(r"^male / female (.+)$", english)
    if len(alts) == 2 and m:
        eng = ["male " + m.group(1), "female " + m.group(1)]
    note_g = GENDER.get(g, "") if g else ""
    if len(alts) == len(eng):
        for a, e in zip(alts, eng):
            cards.append((a, e, note_g))
    elif len(alts) == 2 and is_female_form(alts[0], alts[1]):
        # "lekarz / lekarka" = doctor: the second is the female form.
        cards.append((alts[0], english, note_g))
        cards.append((alts[1], "female " + english, note_g))
    else:
        # Two Polish words, one English meaning (dzisiaj / dziś = today).
        for a in alts:
            cards.append((a, english, note_g))
    return cards


def is_female_form(male, female):
    """lekarz/lekarka, uczeń/uczennica, szef/szefowa — not dzisiaj/dziś."""
    if not female.endswith(FEMININE_SUFFIXES) or len(female) <= len(male):
        return False
    stem = 0
    while stem < min(len(male), len(female)) and male[stem] == female[stem]:
        stem += 1
    return stem >= 3


ADJECTIVE = re.compile(r"(y|i|ny|ki|gi)$")


def category_for(section, polish):
    if section == "11.10" and not (" " not in polish and ADJECTIVE.search(polish)
                                   and polish not in ("tylko", "prawie")):
        return "Grammar & Function Words"
    return TOPIC_CATEGORY[section]


def guide_vocabulary():
    lines = SOURCE.read_text(encoding="utf-8").splitlines()
    section = None
    in_vocab = False
    entries = []
    for line in lines:
        m = re.match(r"^### (11\.\d+) ", line)
        if m:
            section, in_vocab = m.group(1), True
            continue
        if line.startswith("## 12."):
            section, in_vocab = "12", False
            continue
        if line.startswith("## ") and not line.startswith("## 11."):
            if section == "12" and not line.startswith("## 12."):
                section = None
            in_vocab = False
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if set("".join(cells)) <= set("-: ") or cells[0] in ("Polish", "Verb"):
            continue
        if in_vocab and len(cells) == 4:
            for pl, en in ((cells[0], cells[1]), (cells[2], cells[3])):
                if pl:
                    for word, eng, note in expand(plain(pl), plain(en)):
                        entries.append({"polish": word, "english": eng, "notes": note,
                                        "category": category_for(section, word),
                                        "level": TOPIC_LEVEL[section], "section": section})
        elif section == "12" and len(cells) == 4:
            verb, form, meaning, structure = (plain(c) for c in cells)
            perfective = "(pf)" in verb
            verb = verb.replace("(pf)", "").strip()
            english = " / ".join("to " + m.strip() for m in meaning.split(" / "))
            bits = [("perfective" if perfective else "imperfective"),
                    ("future: " if perfective else "ja: ") + form]
            note = "; ".join(bits) + ". Pattern: " + structure
            entries.append({"polish": verb, "english": english, "notes": note,
                            "category": "Verbs", "level": "A1", "section": "12"})
    return merge_duplicates(entries)


def merge_duplicates(entries):
    """A word listed twice keeps both meanings: wolny = slow / free, available."""
    merged = {}
    order = []
    for e in entries:
        k = e["polish"].strip().lower()
        if k not in merged:
            merged[k] = dict(e)
            order.append(k)
            continue
        have = merged[k]
        if e["english"].lower() not in have["english"].lower():
            have["english"] = have["english"] + "; " + e["english"]
        if e["notes"] and e["notes"] not in have["notes"]:
            have["notes"] = (have["notes"] + "; " if have["notes"] else "") + e["notes"]
    return [merged[k] for k in order]
