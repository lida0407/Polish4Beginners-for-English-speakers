"""Turn the guide's chapters into a course: short lessons, each with a quiz.

Called by build_grammar_guide.py. Lessons point into the chapter blocks
(no text is duplicated); quizzes are generated from the lesson's own tables
and example sentences, so every question has a source in the guide.

Question kinds
  mc    multiple choice: prompt, options, answer, explain
  type  typed answer (chapter 15 practice): prompt, accepted, model

Generation is seeded per lesson, so a rebuild gives the same course.
"""
import random
import re

from guide_vocabulary import expand

MAX_QUESTIONS = 5
MAX_TABLE_ROWS = 15
CASE_NAMES = {"N": "nominative", "G": "genitive", "D": "dative", "A": "accusative",
              "I": "instrumental", "L": "locative", "V": "vocative"}
CASE_OPTIONS = ["genitive (G)", "dative (D)", "accusative (A)", "instrumental (I)", "locative (L)"]


def text(cell):
    return "".join(s["t"] for s in cell).strip()


def polish(cell):
    return bool(cell) and all(s["pl"] for s in cell if s["t"].strip())


def clean(value):
    return re.sub(r"\s+", " ", value).strip().rstrip(".;")


def options_from(answer, pool, rng, n=4):
    """answer plus up to n-1 distinct distractors from pool."""
    others = sorted({p for p in pool if p and p != answer and p.lower() != answer.lower()})
    rng.shuffle(others)
    opts = [answer] + others[:n - 1]
    rng.shuffle(opts)
    return opts if len(opts) >= 2 else None


def mc(prompt, answer, pool, rng, explain="", n=4, kind="mc", say=""):
    opts = options_from(answer, pool, rng, n)
    if not opts:
        return None
    return {"kind": "mc", "gen": kind, "prompt": prompt, "options": opts,
            "answer": answer, "explain": explain, "say": say}


# ---- generators: one per table shape -----------------------------------------

def gen_vocab(header, rows, rng):
    pairs = []
    for r in rows:
        for col in (0, 2):
            if col + 1 < len(r) and text(r[col]):
                pairs.extend((w, e) for w, e, _ in expand(text(r[col]), text(r[col + 1])))
    out = []
    english = [e for _, e in pairs]
    pl = [w for w, _ in pairs]
    for w, e in pairs:
        out.append(mc(f"What does „{w}” mean?", e, english, rng, f"{w} = {e}", kind="vocab", say=w))
        out.append(mc(f"How do you say “{e}” in Polish?", w, pl, rng, f"{e} = {w}", kind="vocab-rev", say=w))
    return out


GENDER_HEADERS = {"m": "masculine", "f": "feminine", "n": "neuter"}


def gen_paradigm(header, rows, rng, lesson_title=""):
    """Case / person tables: ask for one form, offer the column's other forms."""
    out = []
    by_meaning = header[0].lower() == "meaning"
    for col in range(1, len(header)):
        column = [clean(text(r[col])) for r in rows if col < len(r)]
        head = header[col]
        nominative = next((clean(text(r[col])) for r in rows
                           if r and clean(text(r[0])) == "N" and col < len(r)), "")
        # Name the word: a column headed "Masculine" asks about dobry.
        if nominative and nominative.lower() not in head.lower():
            subject = f"{nominative.split(' / ')[0]} ({head.lower()})"
        elif header[0].lower() == "person" and head.lower().startswith(("masculine", "feminine")):
            # Past tense columns are genders, not verbs: name the verb.
            verb = next((clean(text(r[col])) for r in rows if col < len(r)), "")
            subject = f"{lesson_title} of czytać ({head.split(' / ')[0].lower()})" \
                if verb.startswith("czyta") else f"{lesson_title} ({head.lower()})"
        else:
            subject = head
        for r in rows:
            if col >= len(r):
                continue
            value = clean(text(r[col]))
            label = clean(text(r[0]))
            if not value or "/" in value or ";" in value or column.count(value) != 1:
                continue
            if not polish(r[col]) or label == "N":
                continue   # the nominative is the word itself: no question
            if by_meaning:
                prompt = f"“{label}”, {GENDER_HEADERS.get(head, head.lower())}: which form?"
            else:
                case = CASE_NAMES.get(label)
                what = f"{case} ({label})" if case else label
                prompt = f"{subject}: which is the {what} form?"
            out.append(mc(prompt, value, column, rng, f"{prompt.split(':')[0]} → {value}",
                          kind="paradigm", say=value))
    return out


def gen_mapping(header, rows, rng):
    """Two Polish columns (perfective of…, plural of…, in March…) and
    Polish → meaning columns."""
    out = []
    keys = [r for r in rows if r and polish(r[0])]
    if len(keys) < 3:
        return out
    for col in range(1, len(header)):
        column = [clean(text(r[col])) for r in rows if col < len(r)]
        col_polish = sum(1 for r in rows if col < len(r) and polish(r[col])) >= len(rows) * 0.7
        for r in keys:
            if col >= len(r):
                continue
            key = clean(text(r[0]))
            value = clean(text(r[col]))
            if not value or column.count(value) != 1:
                continue
            if col_polish:
                out.append(mc(f"{key} → {header[col].lower()}?", value, column, rng,
                              f"{key} → {value}", kind="mapping", say=value))
            elif header[col].lower() in ("meaning", "english", "core meaning"):
                out.append(mc(f"What does „{key}” mean?", value, column, rng,
                              f"{key} = {value}", kind="meaning", say=key))
    return out


def gen_meaning_first(header, rows, rng):
    """Meaning | Polish | Example (modal verbs): which Polish says this?"""
    out = []
    pcol = [h.lower() for h in header].index("polish")
    column = [clean(text(r[pcol])) for r in rows]
    for r in rows:
        value = clean(text(r[pcol]))
        if value and column.count(value) == 1:
            out.append(mc(f"Which Polish means “{clean(text(r[0]))}”?", value, column, rng,
                          f"{text(r[0])}: {value} — {text(r[-1])}", kind="meaning-rev"))
    return out


def gen_errors(header, rows, rng):
    out = []
    for r in rows:
        avoid, use, reason = (clean(text(c)) for c in r[:3])
        avoid = re.sub(r"\s*\(intended:.*?\)", "", avoid).rstrip(".")
        use = use.rstrip(".")
        opts = [use, avoid]
        rng.shuffle(opts)
        out.append({"kind": "mc", "gen": "errors", "prompt": "Which is correct?",
                    "options": opts, "answer": use, "explain": reason, "say": use})
    return out


def gen_verb_cases(header, rows, rng):
    out = []
    meanings = [clean(text(r[2])) for r in rows]
    for r in rows:
        verb = clean(text(r[0])).replace("(pf)", "").strip()
        structure = clean(text(r[3]))
        cases = re.findall(r"\(([GDAIL])\)", structure)
        if len(cases) == 1:
            answer = f"{CASE_NAMES[cases[0]]} ({cases[0]})"
            pool = [o for o in CASE_OPTIONS if o != answer]
            rng.shuffle(pool)
            opts = [answer] + pool[:3]
            rng.shuffle(opts)
            out.append({"kind": "mc", "gen": "verb-case-a" if cases[0] == "A" else "verb-case",
                        "prompt": f"„{verb}” takes which case?",
                        "options": opts, "answer": answer, "explain": structure,
                        "say": re.sub(r"\s*\([A-Z]\)", "", structure)})
        out.append(mc(f"What does „{verb}” mean?", clean(text(r[2])), meanings, rng,
                      f"{verb} = {text(r[2])}", kind="verb-meaning", say=verb))
    return out


def gen_case_questions(header, rows, rng):
    """Case | Questions | uses | Example: which case answers komu? czemu?"""
    out = []
    labels = [clean(text(r[0])) for r in rows]
    for r in rows:
        q = clean(text(r[1]))
        if "?" in q:
            out.append(mc(f"Which case answers „{q}”?", clean(text(r[0])), labels, rng,
                          f"{text(r[0])}: {text(r[2])}. {text(r[3])}", kind="case-q"))
    return out


def gen_prepositions(header, rows, rng):
    """Case | Common prepositions | Examples: which case follows 'bez'?"""
    owner = {}
    for r in rows:
        case = clean(text(r[0]))
        for part in re.split(r"[,;]", text(r[1])):
            word = part.strip().split(" ")[0] if part.strip() else ""
            for w in word.split("/"):
                w = w.strip()
                if w:
                    owner.setdefault(w, set()).add(case)
    out = []
    for w, cases in owner.items():
        if len(cases) != 1:
            continue          # z, na, w, za belong to two cases: not a fair question
        case = next(iter(cases))
        answer = f"{CASE_NAMES.get(case, case)} ({case})"
        pool = [f"{CASE_NAMES[c]} ({c})" for c in "GDAIL" if c != case]
        rng.shuffle(pool)
        opts = [answer] + pool[:3]
        rng.shuffle(opts)
        out.append({"kind": "mc", "gen": "preposition", "prompt": f"Which case follows „{w}”?",
                    "options": opts, "answer": answer, "explain": f"{w} + {case}", "say": w})
    return out


def gen_patterns(header, rows, rng):
    """Function/Purpose | Pattern | Example: pick the pattern for a purpose."""
    out = []
    patterns = [clean(text(r[1])) for r in rows]
    for r in rows:
        purpose, pattern, example = (clean(text(c)) for c in r[:3])
        if patterns.count(pattern) == 1:
            out.append(mc(f"{purpose}: which pattern?", pattern, patterns, rng, example,
                          kind="pattern"))
    return out


def gen_gender(header, rows, rng):
    out = []
    names = [clean(text(r[0])) for r in rows]
    for r in rows:
        for w in [x.strip() for x in text(r[2]).split(",") if x.strip()]:
            out.append(mc(f"Which gender is „{w}”?", clean(text(r[0])), names, rng,
                          f"{w}: {text(r[0]).lower()}. {text(r[1])}", n=3, kind="gender", say=w))
    return out


def gen_numbers(header, rows, rng):
    pairs = []
    for r in rows:
        for col in (0, 2):
            if col + 1 < len(r):
                pairs.append((clean(text(r[col])), clean(text(r[col + 1]))))
    words = [p for _, p in pairs]
    return [mc(f"{n} in Polish?", p, words, rng, f"{n} = {p}", kind="number", say=p)
            for n, p in pairs if "/" not in p]


def gen_spelling(header, rows, rng):
    spellings = [clean(text(r[0])) for r in rows]
    out = []
    for r in rows:
        spelling, reminder, example = (clean(text(c)) for c in r[:3])
        if reminder.lower().startswith(("like ", "hard ", "soft", "same sound", "a distinct")):
            out.append(mc(f"“{reminder}”: which spelling?", spelling, spellings, rng,
                          f"{spelling}: {reminder}, as in {example}", kind="spelling"))
    return out


def table_questions(block, rng, lesson_title=""):
    header = block["header"]
    rows = block["rows"]
    h = [x.lower() for x in header]
    if h == ["polish", "english", "polish", "english"]:
        return gen_vocab(header, rows, rng)
    if h[:2] == ["spelling", "practical reminder"]:
        return gen_spelling(header, rows, rng)
    if h == ["number", "polish", "number", "polish"]:
        return gen_numbers(header, rows, rng)
    if h[:3] == ["avoid", "use", "reason"]:
        return gen_errors(header, rows, rng)
    if h == ["verb", "form", "meaning", "useful structure"]:
        return gen_verb_cases(header, rows, rng)
    if h[:2] == ["case", "questions"]:
        return gen_case_questions(header, rows, rng)
    if h[:2] == ["case", "common prepositions"]:
        return gen_prepositions(header, rows, rng)
    if h[:2] in (["function", "pattern"], ["purpose", "pattern"]):
        return gen_patterns(header, rows, rng)
    if h[:2] == ["category", "common clue"]:
        return gen_gender(header, rows, rng)
    if h and h[0] == "meaning" and "polish" in h:
        return gen_meaning_first(header, rows, rng)
    if h and h[0] in ("case", "person", "meaning"):
        return gen_paradigm(header, rows, rng, lesson_title)
    return gen_mapping(header, rows, rng)


META = re.compile(r"^(uses|retains|normally|as phrases|same|negative|important|compare|"
                  r"the form|this|both|learn|use|do not|in a and|mean |emphasizes|asks|describes|"
                  r"reports|has|takes|but|is|are)\b", re.I)


def sentence_pairs(blocks):
    """Bold Polish followed by its English translation: the guide's examples.

    Only real translations: introduced by "=" or "means", or a capitalised
    English sentence after a full Polish sentence. Commentary such as
    "uses the vocative" or "retains the case" is not a meaning.
    """
    pairs = []
    for b in blocks:
        groups = [b["spans"]] if b["type"] == "p" else (b["items"] if b["type"] == "list" else [])
        for spans in groups:
            for i, s in enumerate(spans[:-1]):
                nxt = spans[i + 1]
                if not (s["b"] and s["pl"]) or nxt["pl"]:
                    continue
                pl = clean(s["t"])
                if any(mark in pl for mark in ("→", "—", "/", "+", "…")):
                    continue
                raw = nxt["t"].strip()
                introduced = bool(re.match(r"^(=|means)\s", raw))
                en = re.sub(r"^(=|means)\s*", "", raw)
                en = re.split(r";|\.\s|\.$", en)[0].strip().rstrip(".")
                sentence_like = s["t"].strip()[-1:] in ".?!" and en[:1].isupper()
                if not (introduced or sentence_like) or META.match(en):
                    continue
                if len(pl.split()) >= 2 and 2 <= len(en.split()) <= 12:
                    pairs.append((pl, en))
    return pairs


# ---- lessons -------------------------------------------------------------

def slice_words(table, start, end):
    """Headwords of rows start..end, split exactly as the deck splits them."""
    h = [x.lower() for x in table["header"]]
    words = []
    for r in table["rows"][start:end]:
        if h == ["polish", "english", "polish", "english"]:
            for col in (0, 2):
                if col + 1 < len(r) and text(r[col]):
                    words.extend(w for w, _, _ in expand(text(r[col]), text(r[col + 1])))
        elif r:
            words.append(clean(text(r[0])).replace("(pf)", "").strip())
    out = []
    for w in words:
        if w not in out:
            out.append(w)
    return out


def substantial(blocks):
    return any(b["type"] in ("table", "exercise", "dialogue", "study") for b in blocks) \
        or sum(1 for b in blocks if b["type"] in ("p", "list")) >= 3


def build_course(chapters):
    units = []
    for ci, ch in enumerate(chapters):
        blocks = ch["blocks"]
        # Segments start at headings and at exercises.
        segments = []
        cur = {"title": ch["title"], "refs": []}
        for bi, b in enumerate(blocks):
            if b["type"] == "h" or (b["type"] == "exercise"):
                if cur["refs"]:
                    segments.append(cur)
                title = b["text"] if b["type"] == "h" else f"Practice {b['group']}: {b['prompt']}"
                cur = {"title": re.sub(r"^\d+(\.\d+)*\s+", "", title), "refs": []}
                if b["type"] == "h":
                    continue
            cur["refs"].append({"b": bi})
        if cur["refs"]:
            segments.append(cur)

        # Fold thin segments (a sentence or two) into a neighbour.
        merged = []
        for seg in segments:
            seg_blocks = [blocks[r["b"]] for r in seg["refs"]]
            is_exercise = any(b["type"] == "exercise" for b in seg_blocks)
            if merged and not substantial(seg_blocks) and not is_exercise:
                merged[-1]["refs"].extend(seg["refs"])
            elif merged and not substantial([blocks[r["b"]] for r in merged[-1]["refs"]]) \
                    and not any(blocks[r["b"]]["type"] == "exercise" for r in merged[-1]["refs"]):
                seg["refs"] = merged[-1]["refs"] + seg["refs"]
                seg["title"] = seg["title"] if merged[-1]["title"] == ch["title"] else merged[-1]["title"]
                merged[-1] = seg
            else:
                merged.append(seg)

        # Split very long tables (the verb bank) into several lessons.
        lessons = []
        for seg in merged:
            long_tables = [r for r in seg["refs"] if blocks[r["b"]]["type"] == "table"
                           and len(blocks[r["b"]]["rows"]) > MAX_TABLE_ROWS * 1.5]
            if not long_tables:
                lessons.append(seg)
                continue
            t = long_tables[0]
            n = len(blocks[t["b"]]["rows"])
            parts = (n + MAX_TABLE_ROWS - 1) // MAX_TABLE_ROWS
            size = (n + parts - 1) // parts
            for k in range(parts):
                refs = []
                for r in seg["refs"]:
                    if r is t:
                        refs.append({"b": r["b"], "rows": [k * size, min(n, (k + 1) * size)]})
                    elif blocks[r["b"]]["type"] == "study":
                        refs.append({"b": r["b"], "words": slice_words(
                            blocks[t["b"]], k * size, min(n, (k + 1) * size))})
                    elif k == 0 or blocks[r["b"]]["type"] not in ("p", "list"):
                        refs.append(r)
                lessons.append({"title": f"{seg['title']} ({k + 1}/{parts})", "refs": refs})

        unit_lessons = []
        for li, lesson in enumerate(lessons):
            lid = f"{ch['id']}.{li + 1}"
            rng = random.Random(lid)
            lb = []
            for r in lesson["refs"]:
                b = dict(blocks[r["b"]])
                if "rows" in r and b["type"] == "table":
                    b["rows"] = b["rows"][r["rows"][0]:r["rows"][1]]
                lb.append(b)
            quiz = []
            for b in lb:
                if b["type"] == "exercise":
                    for it in b["items"]:
                        accepted = []
                        if it.get("a"):
                            accepted.append(" ".join(it["a"]))
                            if it.get("full"):
                                accepted.append(it["full"])
                        accepted.extend(it.get("alts", []))
                        quiz.append({"kind": "type", "prompt": it["q"], "accepted": accepted,
                                     "model": it.get("full") or " / ".join(it.get("alts", [])),
                                     "say": it.get("full") or (it.get("alts") or [""])[0]})
            if not quiz:
                pool = []
                for b in lb:
                    if b["type"] == "table":
                        pool.extend(q for q in table_questions(b, rng, lesson["title"]) if q)
                pairs = sentence_pairs(lb)
                chapter_pairs = sentence_pairs(blocks)
                english = [e for _, e in chapter_pairs]
                for pl, en in pairs:
                    if len(set(english) - {en}) >= 2:
                        q = mc(f"What does „{pl}” mean?", en, english, rng, f"{pl} = {en}",
                               kind="sentence", say=pl)
                        if q:
                            pool.append(q)
                # Variety first: one question per kind in turn.
                by_kind = {}
                for q in pool:
                    by_kind.setdefault(q["gen"], []).append(q)
                for qs in by_kind.values():
                    rng.shuffle(qs)
                seen = set()
                # Accusative-verb questions only fill gaps: most verbs take A,
                # so the other cases are the ones worth asking about.
                filler = by_kind.pop("verb-case-a", [])
                while len(quiz) < MAX_QUESTIONS and (any(by_kind.values()) or filler):
                    if not any(by_kind.values()):
                        by_kind = {"verb-case-a": filler[:1]}
                        filler = filler[1:]
                    for kind in sorted(by_kind):
                        if by_kind[kind] and len(quiz) < MAX_QUESTIONS:
                            q = by_kind[kind].pop()
                            ident = (q["prompt"], q["answer"])
                            if ident not in seen:
                                seen.add(ident)
                                quiz.append(q)
            for q in quiz:
                q.pop("gen", None)
            unit_lessons.append({"id": lid, "title": lesson["title"], "refs": lesson["refs"],
                                 "quiz": quiz})
        units.append({"chapter": ci, "title": ch["title"], "lessons": unit_lessons})
    return {"units": units}
