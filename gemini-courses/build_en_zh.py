#!/usr/bin/env python3
"""English live calls for Chinese speakers, from the Polish scenario calls.

    python3 gemini-courses/build_en_zh.py

Input: en-zh/batches/out_*.json, the English + Simplified Chinese adaptation
of live_scenarios.json made by Gemini (via Antigravity, see README), plus the
cast and structure from live_scenarios.json.
Output:
  en-zh/scenarios_en_zh.json        the merged, validated adaptation
  out/english_live_courses.json     the course an app loads
  out/COURSES_en_zh.md              the catalogue
The build stops on anything missing, malformed or untranslated.
"""
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
CJK = re.compile(r"[一-鿿]")
TITLES = {"Pan ": "", "Pani ": "", "Doktor ": "Dr ", "Sierżant ": "Officer "}
LEVEL_RULE = {
    "A1": "Use the present tense and very common words, sentences of about 8 words, and speak a little slowly.",
    "A2": "Use present, past and future, everyday topics, sentences of about 12 words.",
}
WRAP_AFTER = {"A1": 8, "A2": 10}

FEEDBACK_PROMPT = (
    "You are a friendly English coach for Chinese speakers. Below is a practice call between a learner "
    "(level {{level}}) and a character. The learner's mission was: {{mission}}.\n"
    "Write the feedback in Simplified Chinese, quoting English where needed, in exactly this format:\n"
    "任务：每项一行，✓ 或 ✗，加几个字说明。\n"
    "需要改正：最多三行，“学员说的 → 更好的英文（原因）”。没有错误就写“没有需要改正的”。\n"
    "下次可以用：两个英文短语，附中文意思。\n"
    "评分：准确度 N/5，表达丰富度 N/5。\n"
    "一句鼓励的话。\n"
    "Transcript:\n{{transcript}}"
)
MEMORY_PROMPT = (
    "Read this call transcript. List up to five short facts the learner said about themselves that a "
    "friend would remember next time (name, family, job, home, likes, plans). One per line, in English. "
    "Only what the learner actually said. If there is nothing, write \"none\".\n"
    "Transcript:\n{{transcript}}"
)

KEYS = ["id", "title", "can_do", "scene", "learner_role", "mission", "complication",
        "opener", "first_question", "closer", "phrases", "grammar_focus"]


def english_name(name):
    for polish, english in TITLES.items():
        if name.startswith(polish):
            return english + name[len(polish):]
    return name


def pair_ok(p):
    return isinstance(p, dict) and p.get("en", "").strip() and p.get("zh", "").strip() \
        and CJK.search(p["zh"]) and not CJK.search(p["en"])


def load_batches(expected_ids):
    merged, problems = {}, []
    for f in sorted((HERE / "en-zh" / "batches").glob("out_*.json")):
        text = f.read_text(encoding="utf-8").strip()
        text = re.sub(r"^```(?:json)?\s*|\s*```$", "", text)      # tolerate a stray fence
        try:
            items = json.loads(text[text.index("["):text.rindex("]") + 1])
        except Exception as e:
            problems.append(f"{f.name}: not valid JSON ({e})")
            continue
        for it in items:
            merged[it.get("id")] = it
    for sid in expected_ids:
        it = merged.get(sid)
        if it is None:
            problems.append(f"{sid}: missing")
            continue
        missing = [k for k in KEYS if k not in it]
        if missing:
            problems.append(f"{sid}: missing keys {missing}")
            continue
        for k in KEYS[1:]:
            vals = it[k] if isinstance(it[k], list) else [it[k]]
            for v in vals:
                if not pair_ok(v):
                    problems.append(f"{sid}.{k}: bad pair {str(v)[:80]}")
        if len(it["mission"]) != 3:
            problems.append(f"{sid}: mission has {len(it['mission'])} tasks")
        if not 3 <= len(it["phrases"]) <= 7:
            problems.append(f"{sid}: {len(it['phrases'])} phrases")
        if any(re.search(r"\(m ?/ ?f\)", p["en"]) for p in it["phrases"]):
            problems.append(f"{sid}: gender marker left in a phrase")
    return merged, problems


def lower_first(t):
    return t[:1].lower() + t[1:] if t[:2] not in ('I ', "I'") and not t[1:2].isupper() else t


def call_prompt(it, who, level):
    mission = " ".join(f"{i + 1}) {m['en'].rstrip('.')}." for i, m in enumerate(it["mission"]))
    phrases = "; ".join(p["en"] for p in it["phrases"])
    return "\n".join([
        f"You are {who['name']}, {who['bio']}. Personality: {', '.join(who['traits'])}. You love {', '.join(who['loves'])}.",
        "Stay in character for the whole call. Never say you are an AI, a tutor or a teacher.",
        f"You are on a live voice call in English with {{{{learner_name}}}}, a Chinese speaker learning English at level {level}.",
        f"Level: {LEVEL_RULE[level]}",
        f"Scene, as the learner sees it: {it['scene']['en'].rstrip('.')}. The learner is {lower_first(it['learner_role']['en'].rstrip('.'))}.",
        f"The learner's mission (they can see it): {mission} Steer the talk towards each task, but let them try first.",
        f"Complication: {it['complication']['en'].rstrip('.')}. Bring it in after the first or second task.",
        f"Phrases the learner prepared: {phrases}. Use them naturally.",
        "How to talk:",
        "- Speak only English, clearly and a little slowly. One or two short sentences per turn, then stop and let the learner speak.",
        "- React to what they say first, then move on. Ask at most one question per turn.",
        "- If they seem confused, say it again more simply and slowly, or offer two choices. Never switch to Chinese: the app shows the learner a Chinese translation of what you say.",
        "- If they answer in Chinese, reply in simple English and give them the English to say.",
        "- Do not correct mistakes directly. Use the correct form naturally in your reply instead "
        "(learner: \"How much it cost?\" → you: \"How much does it cost? It's twelve złoty.\").",
        "- If they mention something you love, sound pleased. If they want to talk about something else, go with it briefly, then come back to the scene.",
        f"Call shape: open with exactly \"{it['opener']['en']}\". When they answer, ask \"{it['first_question']['en']}\". "
        f"Then work through the mission. When it is done, or when told it is time, finish in character with \"{it['closer']['en']}\"",
        "{{memory}}",
    ])


def main():
    live = json.loads((HERE / "live_scenarios.json").read_text(encoding="utf-8"))
    ids = [sc["id"] for u in live["units"] for sc in u["scenarios"]]
    merged, problems = load_batches(ids)
    if problems:
        print("PROBLEMS (" + str(len(problems)) + "):\n  " + "\n  ".join(problems[:40]))
        sys.exit(1)
    (HERE / "en-zh" / "scenarios_en_zh.json").write_text(
        json.dumps([merged[i] for i in ids], ensure_ascii=False, indent=1), encoding="utf-8")

    cast = {k: dict(v, name=english_name(v["name"]),
                    register="friendly, natural spoken English") for k, v in live["cast"].items()}
    units = []
    for u in live["units"]:
        lessons = []
        for sc in u["scenarios"]:
            it = merged[sc["id"]]
            who = cast[sc["char"]] if "char" in sc else dict(sc["guest"], name=english_name(sc["guest"]["name"]))
            lessons.append({
                "id": f"en-call-{u['key']}-{sc['id']}",
                "title": it["title"], "level": u["level"], "minutes": 6,
                "can_do": it["can_do"],
                "character": {"id": sc.get("char", "guest"), "name": who["name"], "bio": who["bio"], "voice": who["voice"]},
                "scene": it["scene"], "learner_role": it["learner_role"],
                "mission": it["mission"], "complication": it["complication"],
                "opener": it["opener"], "first_question": it["first_question"], "closer": it["closer"],
                "phrases": it["phrases"], "grammar_focus": it["grammar_focus"],
                "coach_notes": [
                    {"after_learner_turns": 3, "text": "(Coach: if you have not yet, bring in the complication now.)"},
                    {"after_learner_turns": WRAP_AFTER[u["level"]],
                     "text": "(Coach: it is time to go. Wrap up in character and say goodbye.)"},
                ],
                "system_prompt": call_prompt(it, who, u["level"]),
            })
        units.append({"key": u["key"], "title": u["title"], "level": u["level"], "lessons": lessons})
    pack = {"version": 1, "language": "en", "learner_language": "zh-CN", "cast": cast,
            "call_templates": {"feedback_prompt": FEEDBACK_PROMPT, "memory_prompt": MEMORY_PROMPT,
                               "placeholders": ["{{learner_name}}", "{{memory}}", "{{level}}",
                                                "{{mission}}", "{{transcript}}"]},
            "courses": [{"id": "calls", "role": "main", "title": "Live calls: English in real situations",
                         "levels": ["A1", "A2"], "units": units}]}
    OUT.mkdir(exist_ok=True)
    (OUT / "english_live_courses.json").write_text(json.dumps(pack, ensure_ascii=False, indent=1), encoding="utf-8")

    lines = ["# English live calls for Chinese speakers", "",
             "48 scenario calls, A1 and A2. The character speaks English; the learner gets Chinese support and Chinese feedback.", ""]
    for u in units:
        lines.append(f"## {u['title']} ({u['level']})")
        lines.append("")
        for l in u["lessons"]:
            lines.append(f"- **{l['title']['en']}** · {l['title']['zh']} — with {l['character']['name']}: "
                         + "; ".join(m["en"] for m in l["mission"]))
        lines.append("")
    (OUT / "COURSES_en_zh.md").write_text("\n".join(lines), encoding="utf-8")
    sizes = [len(l["system_prompt"]) for u in units for l in u["lessons"]]
    print(f"english calls: {len(units)} units, {sum(len(u['lessons']) for u in units)} lessons, prompts {min(sizes)}–{max(sizes)} chars")


if __name__ == "__main__":
    main()
