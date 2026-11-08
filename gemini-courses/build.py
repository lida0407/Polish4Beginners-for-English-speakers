#!/usr/bin/env python3
"""Build live-call Polish courses for a Gemini voice tutor.

    python3 gemini-courses/build.py

Reads the app's own reviewed content and writes gemini-courses/out/:
  polish_live_courses.json   every course, unit and lesson, machine-readable
  COURSES.md                 the catalogue, for people

Three courses:
  grammar   the grammar guide's course lessons that can be practised aloud
  vocab-a1  A1 flashcards grouped by topic, ~10 items a lesson
  vocab-a2  A2 flashcards grouped by topic

Each lesson carries a ready-to-send `system_prompt` written for a small
on-device model: short, explicit, one plan, hard limits on turn length. The
role-plays and goals are hand-written in scenarios.json; everything else
comes from data that was already reviewed for the Polish4Beginners app.
"""
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
ASSETS = ROOT / "polish-phrasebook-android" / "app" / "src" / "main" / "assets"
OUT = HERE / "out"
sys.path.insert(0, str(ROOT / "scripts"))
from course_builder import sentence_pairs  # noqa: E402

TUTOR = "Ola"
WORDS_PER_LESSON = 10
MAX_PROMPT_CHARS = 2200          # comfortably inside a small on-device context
MAX_ITEM_WORDS = 6               # longer cards are sentences, not vocabulary

RULES = (
    "Rules: explain in English, practise in Polish. Keep every turn under 25 words. "
    "Speak Polish slowly and clearly. Ask one thing at a time and wait for the answer. "
    "If the learner makes a mistake, say the correct Polish once, ask them to repeat it, then move on. "
    "Praise briefly and specifically. Stay inside this lesson's material unless the learner asks."
)


def key(text):
    return re.sub(r"[.!?,;:]+$", "", text.strip().lower())


def plain(spans):
    return "".join(s["t"] for s in spans).strip()


def short(text, limit):
    text = re.sub(r"\s+", " ", text).strip()
    if len(text) <= limit:
        return text
    cut = text[:limit].rsplit(" ", 1)[0]
    return cut.rstrip(",;:") + "…"


def first_sentences(text, limit):
    """Whole sentences up to a length, so a summary never stops mid-thought."""
    out = ""
    for s in re.split(r"(?<=[.!?])\s+", text.strip()):
        # Guide signposts ("Useful tendencies:", "These are complete example
        # paradigms…") mean nothing on a call.
        s = re.sub(r"^Useful tendencies:\s*", "", s)      # signpost glued to real content
        if not s or s.rstrip().endswith(":") or re.match(r"^(These are|This is a selective)", s):
            continue
        if len(out) + len(s) + 1 > limit:
            break
        out = (out + " " + s).strip()
    return out or short(text, limit)


def gloss(english):
    """A spoken-length meaning that never ends mid-phrase.

    "This is a house. Basic sentence: To…" -> "This is a house"
    "My name is Alex. / My first name is…"  -> "My name is Alex"
    "to establish, to found, to start…"     -> "to establish, to found"
    """
    text = re.sub(r"\s*\([^)]*\)", "", english).strip()
    text = re.split(r"(?<=[.!?])\s+", text)[0]            # first sentence only
    text = text.split(" / ")[0] if len(text) > 60 else text
    text = text.rstrip(".…").strip()
    if len(text) > 60:                                    # still long: whole comma parts
        parts, out = text.split(", "), ""
        for part in parts:
            if len(out) + len(part) + 2 > 60:
                break
            out = (out + ", " + part) if out else part
        text = out or text[:60].rsplit(" ", 1)[0]
    return text.rstrip(",;: …")


GENDER_NOTES = {"(m.)": "masculine", "(f.)": "feminine", "(n.)": "neuter", "(pl.)": "plural noun"}


def clean_note(note):
    """Keep what helps a speaker (gender, key verb forms); drop topic tags."""
    note = (note or "").strip()
    if note in GENDER_NOTES:
        return GENDER_NOTES[note]
    if note.startswith("Gender: "):
        return note[len("Gender: "):]
    if re.match(r"^\([^)]*,[^)]*\)$", note):                # (założę, założysz)
        return "forms " + note.strip("()")
    return ""


# ---- prompts ---------------------------------------------------------------

def vocab_prompt(level, topic, lesson_title, words, opener):
    word_lines = "; ".join(f"{w['pl']} = {w['en']}" for w in words)
    return "\n".join([
        f"You are {TUTOR}, a warm Polish tutor on a live voice call with an English speaker at level {level}.",
        f"Lesson: {lesson_title}. Goal: the learner can understand and say these words and use them in a short conversation about {topic['scene']}.",
        RULES,
        f"Lesson words: {word_lines}.",
        "Plan:",
        "1. Greet in Polish (\"Cześć!\") and give the goal in one English sentence.",
        "2. Teach the words three at a time: say the Polish, the learner repeats, then give the English.",
        "3. Check: in a new order, ask \"How do you say …?\" for each word. Recycle any word they miss at the end.",
        f"4. Role-play ({topic['scene']}): you are {topic['tutor_role']}, the learner is {topic['learner_role']}. "
        f"Open with \"{opener['pl']}\" ({opener['en']}). Keep to 6–8 short exchanges and use the lesson words.",
        "5. Finish: name three things they did well and one word to review. Say \"Do zobaczenia!\"",
    ])


def grammar_prompt(level, unit, lesson_title, summary, examples, drills):
    ex = "; ".join(f"{p} = {e}" for p, e in examples)
    drill_lines = "\n".join(f"   - {d['ask']} → {d['answer']}" for d in drills)
    return "\n".join([
        f"You are {TUTOR}, a warm Polish tutor on a live voice call with an English speaker at level {level}.",
        f"Lesson: {lesson_title}. Goal: the learner can {unit['can_do']}.",
        RULES,
        f"The point in brief: {summary}",
        *([f"Examples: {ex}."] if ex else []),
        "Plan:",
        "1. Greet in Polish (\"Cześć!\") and give the goal in one English sentence.",
        "2. Explain the point in two or three short English sentences. "
        + ("Say each example in Polish; the learner repeats." if ex
           else "Give one Polish example from the drills; the learner repeats."),
        "3. Drills: ask these one at a time and check the answer. If they are stuck, offer two choices.",
        drill_lines,
        f"4. Role-play ({unit['scene']}): you are {unit['tutor_role']}, the learner is {unit['learner_role']}. "
        f"Open with \"{unit['opener']['pl']}\" ({unit['opener']['en']}). Keep to 6–8 short exchanges and steer towards this lesson's point.",
        "5. Finish: name three things they did well and one form to review. Say \"Do zobaczenia!\"",
    ])


# ---- vocabulary courses -------------------------------------------------------

def build_vocab(level, deck, scenarios, taken):
    topics = scenarios["vocab_topics"]
    units = []
    for t in topics:
        cards = [c for c in deck if c["level"] == level and c["category"] in t["categories"]]
        # Core-frequency cards first, in frequency order, then the rest as authored.
        cards.sort(key=lambda c: (0, c["coreIndex"]) if c.get("coreIndex") else (1, 0))
        picked = []
        for c in cards:
            pl = c["polish"].strip()
            if not pl or not c.get("english", "").strip():
                continue
            if len(pl.split()) > MAX_ITEM_WORDS or key(pl) in taken:
                continue
            taken.add(key(pl))
            item = {"pl": pl.rstrip("."), "en": gloss(c["english"])}
            note = clean_note(c.get("notes"))
            if note:
                item["note"] = note
            if c.get("examplePolish") and len(c["examplePolish"]) <= 80:
                item["example_pl"] = c["examplePolish"]
                item["example_en"] = c.get("exampleEnglish", "")
            picked.append(item)
        if len(picked) < 5:
            # Too few for a lesson at this level: leave them for the next level.
            for item in picked:
                taken.discard(key(item["pl"]))
            continue
        lessons = []
        n = (len(picked) + WORDS_PER_LESSON - 1) // WORDS_PER_LESSON
        for i in range(n):
            # Even split (64 words -> 9,9,9,9,9,9,10), never a 2-word straggler.
            words = picked[i * len(picked) // n:(i + 1) * len(picked) // n]
            if not words:
                continue
            title = f"{t['title']} {i + 1}" if n > 1 else t["title"]
            opener = t[level]
            lessons.append({
                "id": f"vocab-{level.lower()}-{t['key']}-{i + 1:02d}",
                "title": title,
                "level": level,
                "minutes": 10,
                "goal": f"Understand and say {len(words)} {t['title'].lower()} words and use them in a short conversation.",
                "learner_brief": f"{len(words)} words, then a role-play: {t['scene']}.",
                "words": words,
                "roleplay": {"scene": t["scene"], "tutor_role": t["tutor_role"],
                             "learner_role": t["learner_role"], "opener": opener},
                "checks": [{"ask": f"How do you say “{w['en']}”?", "answer": w["pl"]} for w in words],
                "system_prompt": vocab_prompt(level, t, title, words, opener),
            })
        units.append({"key": t["key"], "title": t["title"], "lessons": lessons})
    return units


# ---- grammar course ------------------------------------------------------------

SKIP_UNITS = {"0", "11", "16"}   # notation, vocabulary lists (the vocab courses), study routine


def build_grammar(guide, scenarios):
    chapters = guide["chapters"]
    units_meta = scenarios["grammar_units"]
    units = []
    for u in guide["course"]["units"]:
        ch = chapters[u["chapter"]]
        cid = ch["id"]
        if cid in SKIP_UNITS:
            continue
        meta = units_meta[cid]
        lessons = []
        for lesson in u["lessons"]:
            blocks = [ch["blocks"][r["b"]] for r in lesson["refs"]]
            # The point in brief: the lesson's own English explanation.
            prose = []
            for b in blocks:
                if b["type"] == "p":
                    prose.append(plain(b["spans"]))
                elif b["type"] == "list":
                    for item in b["items"][:2]:
                        line = plain(item).rstrip(" ;")
                        prose.append(line if line.endswith((".", "?", "!")) else line + ".")
            if cid == "14":
                summary = ("Each drill pairs a common learner mistake with the correct sentence. "
                           "The learner picks the correct one, then says why in a few words.")
            elif cid == "15":
                summary = ("Spoken practice: say the missing word or translate the sentence into Polish, "
                           "then say the whole sentence aloud.")
            elif prose:
                summary = first_sentences(" ".join(prose), 320)
            else:
                summary = "Practise this point aloud through the drills."
            examples = sentence_pairs(blocks)[:4]
            drills = []
            for q in lesson["quiz"][:6]:
                if q["kind"] == "mc":
                    ask = q["prompt"]
                    if ask == "Which is correct?":
                        # On a call the choices have to be heard, not seen.
                        ask = "Which is correct: " + " or ".join(f"„{o}”" for o in q["options"]) + "?"
                    drills.append({"ask": ask, "answer": q["answer"], "choices": q["options"]})
                else:
                    drills.append({"ask": f"Say in Polish: {q['prompt']}",
                                   "answer": q.get("model") or q["accepted"][0]})
            if not drills and not examples:
                continue
            level = meta["level"]
            lessons.append({
                "id": f"grammar-{cid}-{len(lessons) + 1:02d}",
                "title": lesson["title"],
                "level": level,
                "minutes": 10,
                "goal": f"Use this to {meta['can_do']}.",
                "learner_brief": f"A short explanation, {len(drills)} spoken drills, then a role-play: {meta['scene']}.",
                "summary": summary,
                "examples": [{"pl": p, "en": e} for p, e in examples],
                "drills": drills,
                "roleplay": {"scene": meta["scene"], "tutor_role": meta["tutor_role"],
                             "learner_role": meta["learner_role"], "opener": meta["opener"]},
                "checks": [{"ask": d["ask"], "answer": d["answer"]} for d in drills],
                "system_prompt": grammar_prompt(level, meta, lesson["title"], summary, examples, drills),
            })
        if lessons:
            units.append({"key": f"unit-{cid}", "title": ch["title"], "lessons": lessons})
    return units


# ---- checks ------------------------------------------------------------------

def validate(courses):
    problems = []
    ids = set()
    for c in courses:
        for u in c["units"]:
            for l in u["lessons"]:
                if l["id"] in ids:
                    problems.append(f"duplicate id {l['id']}")
                ids.add(l["id"])
                if len(l["system_prompt"]) > MAX_PROMPT_CHARS:
                    problems.append(f"{l['id']}: prompt {len(l['system_prompt'])} chars")
                if not l["checks"]:
                    problems.append(f"{l['id']}: no checks")
                if c["id"].startswith("vocab") and len(l["words"]) < 5:
                    problems.append(f"{l['id']}: only {len(l['words'])} words")
                if not l["roleplay"]["opener"]["pl"]:
                    problems.append(f"{l['id']}: no opener")
    return problems


def catalogue(courses):
    lines = ["# Polish live-call courses", "",
             "Lessons for a Gemini voice tutor. Each lesson is about ten minutes: teach, drill, role-play, recap.",
             "Built by `gemini-courses/build.py`; the JSON beside this file is what an app loads.", ""]
    for c in courses:
        n = sum(len(u["lessons"]) for u in c["units"])
        lines += [f"## {c['title']}", "", c["description"], "", f"{len(c['units'])} units, {n} lessons.", ""]
        for u in c["units"]:
            titles = ", ".join(l["title"] for l in u["lessons"])
            if c["id"].startswith("vocab"):
                count = sum(len(l["words"]) for l in u["lessons"])
                lines.append(f"- **{u['title']}** · {len(u['lessons'])} lessons, {count} words")
            else:
                lines.append(f"- **{u['title']}** · {titles}")
        lines.append("")
    first = courses[1]["units"][0]["lessons"][0]
    lines += ["## Example: the first A1 vocabulary lesson's prompt", "", "```text", first["system_prompt"], "```", ""]
    return "\n".join(lines)


def main():
    guide = json.loads((ASSETS / "grammar_guide.json").read_text(encoding="utf-8"))
    deck = json.loads((ASSETS / "phrases.json").read_text(encoding="utf-8"))
    scenarios = json.loads((HERE / "scenarios.json").read_text(encoding="utf-8"))
    taken = set()   # a word taught at A1 is not taught again at A2
    courses = [
        {"id": "grammar", "title": "Polish grammar by conversation", "levels": ["A1", "A2"],
         "description": "The grammar course, rebuilt for speaking: each lesson explains one point, drills it aloud, then uses it in a role-play.",
         "units": build_grammar(guide, scenarios)},
        {"id": "vocab-a1", "title": "Polish vocabulary A1", "levels": ["A1"],
         "description": "Everyday A1 words by topic, about ten a lesson, each lesson ending in a role-play that uses them.",
         "units": build_vocab("A1", deck, scenarios, taken)},
        {"id": "vocab-a2", "title": "Polish vocabulary A2", "levels": ["A2"],
         "description": "A2 words by topic, building on A1, in the same teach, check and role-play format.",
         "units": build_vocab("A2", deck, scenarios, taken)},
    ]
    problems = validate(courses)
    if problems:
        print("PROBLEMS:\n  " + "\n  ".join(problems[:30]))
        sys.exit(1)
    OUT.mkdir(exist_ok=True)
    pack = {"version": 1, "language": "pl", "learner_language": "en", "tutor": TUTOR, "courses": courses}
    (OUT / "polish_live_courses.json").write_text(json.dumps(pack, ensure_ascii=False, indent=1), encoding="utf-8")
    (OUT / "COURSES.md").write_text(catalogue(courses), encoding="utf-8")
    for c in courses:
        lessons = [l for u in c["units"] for l in u["lessons"]]
        sizes = [len(l["system_prompt"]) for l in lessons]
        print(f"{c['id']:<9} {len(c['units']):>2} units {len(lessons):>3} lessons · prompt {min(sizes)}–{max(sizes)} chars")
    print("written:", ", ".join(p.name for p in OUT.iterdir()))


if __name__ == "__main__":
    main()
