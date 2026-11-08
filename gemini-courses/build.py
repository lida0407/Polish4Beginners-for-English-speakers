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
            if len(drills) < 3:
                # Transformation pairs in the text ("Mam bilet → Nie mam biletu")
                # make good spoken drills when the lesson has few quiz items.
                for b in blocks:
                    groups = [b["spans"]] if b["type"] == "p" else (b["items"] if b["type"] == "list" else [])
                    for spans in groups:
                        for sp in spans:
                            if not (sp["pl"] and "→" in sp["t"]):
                                continue
                            parts = [x.strip().rstrip(".") for x in sp["t"].split("→")]
                            if len(parts) == 2 and parts[1].startswith("Nie ") and len(drills) < 6:
                                drills.append({"ask": f"Make it negative: {parts[0]}", "answer": parts[1]})
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


# ---- scenario calls (the main course) ------------------------------------------

LEVEL_RULE = {
    "A1": "Use the present tense and everyday words, sentences of about 8 words, and speak slowly.",
    "A2": "Use present, past and future, everyday topics, sentences of about 12 words.",
}
WRAP_AFTER = {"A1": 8, "A2": 10}   # learner turns before the app cues the goodbye
MAX_SCENARIO_PROMPT = 3200

FEEDBACK_PROMPT = (
    "You are a friendly Polish coach. Below is a practice call between a learner (level {{level}}) "
    "and a character. The learner's mission was: {{mission}}.\n"
    "Write feedback in English, under 150 words, in exactly this format:\n"
    "Mission: one line per task, ✓ or ✗ and a few words.\n"
    "Fix these: up to three lines \"what they said → better Polish (why)\". Only real mistakes; write \"nothing to fix\" if none.\n"
    "Try next time: two Polish phrases that would have helped, with English.\n"
    "Scores: accuracy N/5, range N/5.\n"
    "One sentence of encouragement.\n"
    "Transcript:\n{{transcript}}"
)
MEMORY_PROMPT = (
    "Read this call transcript. List up to five short facts the learner said about themselves that a "
    "friend would remember next time (name, family, job, home, likes, plans). One per line, in English. "
    "Only what the learner actually said. If there is nothing, write \"none\".\n"
    "Transcript:\n{{transcript}}"
)


def scenario_prompt(sc, who, level):
    loves = ", ".join(who["loves"])
    mission = " ".join(f"{i + 1}) {m}." for i, m in enumerate(sc["mission"]))
    phrases = "; ".join(f"{pl} = {en}" for pl, en in sc["phrases"])
    return "\n".join([
        f"You are {who['name']}, {who['bio']}. You are {', '.join(who['traits'])}. You love {loves}.",
        "Stay in character for the whole call. Never say you are an AI, a tutor or a teacher.",
        f"You are on a live voice call in Polish with {{{{learner_name}}}}, an English speaker learning Polish at level {level}. "
        f"The learner is {{{{learner_gender}}}}: use the matching Polish forms for them. Register: {who['register']}.",
        f"Level: {LEVEL_RULE[level]}",
        f"Scene, as the learner sees it: {sc['scene']}. The learner is {sc['learner_role']}.",
        f"The learner's mission (they can see it): {mission} Steer the talk towards each task, but let them try first.",
        f"Complication: {sc['twist']} Bring it in after the first or second task.",
        f"Phrases the learner prepared: {phrases}. Use them naturally.",
        "How to talk:",
        "- Speak only Polish. One or two short sentences per turn, then stop and let the learner speak.",
        "- React to what they say first, then move on. Ask at most one question per turn.",
        "- If they seem confused, say it again more simply or offer two choices. If they are lost twice in a row, say the key word in English once, then carry on in Polish.",
        "- If they answer in English, reply in simple Polish and give them the Polish to say.",
        "- Do not correct mistakes directly. Use the correct form naturally in your reply instead "
        "(learner: \"Poproszę kawa\" → you: \"Kawę? Proszę bardzo!\").",
        "- If they mention something you love, sound pleased. If they want to talk about something else, go with it briefly, then come back to the scene.",
        f"Call shape: open with exactly \"{sc['opener']['pl']}\". When they answer, ask \"{sc['first']['pl']}\". "
        f"Then work through the mission. When it is done, or when told it is time, finish in character with \"{sc['closer']['pl']}\"",
        "{{memory}}",
    ])


def grammar_index(guide, grammar_units):
    """Lesson title -> id, plus verb -> the verb-bank part that teaches it."""
    by_title, by_verb = {}, {}
    for u in grammar_units:
        for l in u["lessons"]:
            by_title.setdefault(l["title"], l["id"])
    chapters = guide["chapters"]
    for u in guide["course"]["units"]:
        ch = chapters[u["chapter"]]
        if ch["id"] != "12":
            continue
        for i, lesson in enumerate(u["lessons"]):
            lid = f"grammar-12-{i + 1:02d}"
            for r in lesson["refs"]:
                b = ch["blocks"][r["b"]]
                if b["type"] == "table" and "rows" in r:
                    for row in b["rows"][r["rows"][0]:r["rows"][1]]:
                        by_verb[plain(row[0]).replace("(pf)", "").strip()] = lid
    return by_title, by_verb


def build_scenarios(data, guide, grammar_units, vocab_courses):
    by_title, by_verb = grammar_index(guide, grammar_units)
    vocab_ids = {}
    for course in vocab_courses:
        lvl = course["id"][-2:].upper()
        for u in course["units"]:
            vocab_ids[(lvl, u["key"])] = [l["id"] for l in u["lessons"]][:2]
    units = []
    for u in data["units"]:
        lessons = []
        for sc in u["scenarios"]:
            level = u["level"]
            who = data["cast"][sc["char"]] if "char" in sc else sc["guest"]
            g = sc["grammar"]
            gid = by_verb.get(g["verb"]) if "verb" in g else by_title.get(g["lesson"])
            if not gid:
                raise SystemExit(f"{sc['id']}: no grammar lesson for {g}")
            other = "A2" if level == "A1" else "A1"
            vids = vocab_ids.get((level, sc["vocab"])) or vocab_ids.get((other, sc["vocab"]), [])
            lessons.append({
                "id": f"call-{u['key']}-{sc['id']}",
                "title": sc["title"],
                "level": level,
                "minutes": 6,
                "can_do": f"I can {sc['can_do']}.",
                "character": {"id": sc.get("char", "guest"), "name": who["name"], "bio": who["bio"],
                              "voice": who["voice"]},
                "scene": sc["scene"],
                "learner_role": sc["learner_role"],
                "mission": sc["mission"],
                "complication": sc["twist"],
                "opener": sc["opener"], "first_question": sc["first"], "closer": sc["closer"],
                "phrases": [{"pl": pl, "en": en} for pl, en in sc["phrases"]],
                "prep": {"grammar": {"lesson_id": gid, "focus": g["text"]}, "vocab_lesson_ids": vids},
                "coach_notes": [
                    {"after_learner_turns": 3,
                     "text": "(Coach: if you have not yet, bring in the complication now.)"},
                    {"after_learner_turns": WRAP_AFTER[level],
                     "text": "(Coach: it is time to go. Wrap up in character and say goodbye.)"},
                ],
                "system_prompt": scenario_prompt(sc, who, level),
            })
        units.append({"key": u["key"], "title": u["title"], "level": u["level"], "lessons": lessons})
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
                limit = MAX_SCENARIO_PROMPT if c["id"] == "calls" else MAX_PROMPT_CHARS
                if len(l["system_prompt"]) > limit:
                    problems.append(f"{l['id']}: prompt {len(l['system_prompt'])} chars")
                if c["id"] != "calls" and not l["checks"]:
                    problems.append(f"{l['id']}: no checks")
                if "{" in l["system_prompt"].replace("{{learner_name}}", "").replace("{{learner_gender}}", "").replace("{{memory}}", "") \
                        and not re.search(r"\{m:[^|{}]+\|f:[^|{}]+\}", l["system_prompt"]):
                    problems.append(f"{l['id']}: stray brace in prompt")
                if c["id"].startswith("vocab") and len(l["words"]) < 5:
                    problems.append(f"{l['id']}: only {len(l['words'])} words")
                opener = l["opener"] if c["id"] == "calls" else l["roleplay"]["opener"]
                if not opener["pl"]:
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
            if c["id"] == "calls":
                lines.append(f"- **{u['title']}** ({u['level']}): " + "; ".join(
                    f"{l['title']} with {l['character']['name']}" for l in u["lessons"]))
                continue
            if c["id"].startswith("vocab"):
                count = sum(len(l["words"]) for l in u["lessons"])
                lines.append(f"- **{u['title']}** · {len(u['lessons'])} lessons, {count} words")
            else:
                lines.append(f"- **{u['title']}** · {titles}")
        lines.append("")
    first = courses[0]["units"][0]["lessons"][1]
    lines += ["## Example: a call's system prompt", "", "```text", first["system_prompt"], "```", ""]
    return "\n".join(lines)


def main():
    guide = json.loads((ASSETS / "grammar_guide.json").read_text(encoding="utf-8"))
    deck = json.loads((ASSETS / "phrases.json").read_text(encoding="utf-8"))
    scenarios = json.loads((HERE / "scenarios.json").read_text(encoding="utf-8"))
    taken = set()   # a word taught at A1 is not taught again at A2
    live = json.loads((HERE / "live_scenarios.json").read_text(encoding="utf-8"))
    practice = [
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
    for c in practice:
        c["role"] = "practice"
    calls = {"id": "calls", "role": "main", "title": "Live calls: Polish in real situations", "levels": ["A1", "A2"],
             "description": "The main path. Each call is a scene with a character: a mission to complete, a "
                            "complication, natural conversation, then feedback. Grammar and vocabulary lessons "
                            "are linked as optional preparation.",
             "units": build_scenarios(live, guide, practice[0]["units"], practice[1:])}
    courses = [calls] + practice
    problems = validate(courses)
    if problems:
        print("PROBLEMS:\n  " + "\n  ".join(problems[:30]))
        sys.exit(1)
    OUT.mkdir(exist_ok=True)
    pack = {"version": 2, "language": "pl", "learner_language": "en",
            "cast": live["cast"],
            "call_templates": {"feedback_prompt": FEEDBACK_PROMPT, "memory_prompt": MEMORY_PROMPT,
                               "placeholders": ["{{learner_name}}", "{{learner_gender}}", "{{memory}}",
                                                "{m:…|f:…}", "{{level}}", "{{mission}}", "{{transcript}}"]},
            "courses": courses}
    (OUT / "polish_live_courses.json").write_text(json.dumps(pack, ensure_ascii=False, indent=1), encoding="utf-8")
    (OUT / "COURSES.md").write_text(catalogue(courses), encoding="utf-8")
    for c in courses:
        lessons = [l for u in c["units"] for l in u["lessons"]]
        sizes = [len(l["system_prompt"]) for l in lessons]
        print(f"{c.get('role', ''):<8} {c['id']:<9} {len(c['units']):>2} units {len(lessons):>3} lessons · prompt {min(sizes)}–{max(sizes)} chars")
    print("written:", ", ".join(p.name for p in OUT.iterdir()))


if __name__ == "__main__":
    main()
