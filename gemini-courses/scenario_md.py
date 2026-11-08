#!/usr/bin/env python3
"""Custom scenario cards (Markdown) -> lessons. The reference implementation:
the translator app's Kotlin importer must parse and build prompts exactly like this.

    python3 gemini-courses/scenario_md.py card.md ...            # check cards, print the lesson + prompt
    python3 gemini-courses/scenario_md.py --publish [DIR]        # ~/agent-share/lessons/scenarios/*.md -> packs/my-*.json
    python3 gemini-courses/scenario_md.py --selftest

Card format: ~/agent-share/lessons/templates/ (README there). Rule for every "A | B" line:
A is what the model reads (English, or the language being learnt in Lines and Phrases),
B is the learner's own language. B may be left out (the app fills it with ML Kit).
"""
import hashlib
import json
import re
import sys
from pathlib import Path

CJK = re.compile(r"[一-鿿]")
NAMES = {"en": "English", "pl": "Polish", "zh": "Chinese"}
LANG_ALIASES = {"en": "en", "english": "en", "英语": "en", "pl": "pl", "polish": "pl", "polski": "pl", "波兰语": "pl",
                "zh": "zh", "chinese": "zh", "中文": "zh", "汉语": "zh"}
LEVEL_RULE = {
    "en": {"A1": "Use the present tense and very common words, sentences of about 8 words, and speak a little slowly.",
           "A2": "Use present, past and future, everyday topics, sentences of about 12 words."},
    "pl": {"A1": "Use the present tense and everyday words, sentences of about 8 words, and speak slowly.",
           "A2": "Use present, past and future, everyday topics, sentences of about 12 words."},
}
RECAST = {"en": '(learner: "How much it cost?" → you: "How much does it cost? It\'s twelve złoty.")',
          "pl": '(learner: "Poproszę kawa" → you: "Kawę? Proszę bardzo!")'}
DEFAULT_CLOSER = {"en": "Okay, bye for now!", "pl": "Dobrze, do widzenia!"}
WRAP_AFTER = {"A1": 8, "A2": 10}
COACH = {"complication": "(Coach: if you have not yet, bring in the complication now.)",
         "wrap_up": "(Coach: it is time to go. Wrap up in character and say goodbye.)"}


class CardError(ValueError):
    pass


def pair(text, left_key, right_key):
    """'A | B' -> {left_key: A, right_key: B}. A lone Chinese text goes to the zh side."""
    parts = [p.strip() for p in text.split("|", 1)]
    a = parts[0]
    b = parts[1] if len(parts) > 1 and parts[1] else None
    out = {}
    if b is None and right_key == "zh" and CJK.search(a) and left_key != "zh":
        out[right_key] = a
    else:
        out[left_key] = a
        if b:
            out[right_key] = b
    return out


def slug(s):
    s = re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")
    return s or hashlib.sha1(s.encode()).hexdigest()[:8]


def parse(md):
    head, sections, cur = {}, {}, None
    title = None
    for raw in md.splitlines():
        line = raw.strip()
        if not line or line.startswith("<!--") or line.startswith("```"):
            continue
        if line.startswith("## "):
            cur = line[3:].strip().lower()
            sections[cur] = []
        elif line.startswith("# ") and title is None:
            title = line[2:].strip()
        elif cur is None:
            m = re.match(r"([A-Za-z_ ]+)\s*[:：]\s*(.*)", line)
            if m:
                head[m.group(1).strip().lower()] = m.group(2).strip()
        else:
            sections[cur].append(re.sub(r"^([-*•]|\d+[.)])\s*", "", line))
    if not title:
        raise CardError("no title line (# Title | 标题)")
    target = LANG_ALIASES.get(head.get("learn", "").lower())
    support = LANG_ALIASES.get(head.get("speak", "en").lower(), "en")
    level = head.get("level", "A1").upper()
    if target not in ("en", "pl"):
        raise CardError("learn: must be en or pl")
    if support == target:
        raise CardError("speak: must differ from learn")
    if level not in ("A1", "A2"):
        raise CardError("level: must be A1 or A2")
    ch = [p.strip() for p in head.get("character", "").split("|")]
    if not ch[0]:
        raise CardError("character: name | about | male/female")
    lines = {}
    for l in sections.get("lines", []):
        m = re.match(r"(opener|first|closer)\s*[:：]\s*(.*)", l, re.I)
        if m:
            lines[m.group(1).lower()] = pair(m.group(2), target, support)
    mission = [pair(m, "en", support) for m in sections.get("mission", []) if m][:4]
    phrases = [pair(p, target, support) for p in sections.get("phrases", []) if p][:8]
    if not head.get("scene"):
        raise CardError("scene: missing")
    if not mission:
        raise CardError("## Mission: add 1-4 tasks")
    if len(phrases) < 2:
        raise CardError("## Phrases: add at least 2")
    if "opener" not in lines or target not in lines["opener"]:
        raise CardError(f"## Lines: opener in {NAMES[target]} is required")
    twist = " ".join(sections.get("twist", [])).strip()
    grammar = " ".join(sections.get("grammar", [])).strip()
    t = pair(title, "en", support)
    lesson = {
        "id": f"my-{target}-{slug(t.get('en') or title)}", "level": level, "minutes": 6,
        "character": {"id": "custom", "name": ch[0], "bio": ch[1] if len(ch) > 1 else "",
                      "voice": (ch[2].lower() if len(ch) > 2 and ch[2].lower() in ("male", "female") else "female")},
        "title": t,
        "scene": pair(head["scene"], "en", support),
        "learner_role": pair(head.get("me", "yourself"), "en", support),
        "mission": mission,
        "complication": pair(twist, "en", support) if twist else None,
        "opener": lines["opener"],
        "first_question": lines.get("first"),
        "closer": lines.get("closer") or {target: DEFAULT_CLOSER[target]},
        "phrases": phrases,
        "grammar_focus": pair(grammar, "en", support) if grammar else None,
        "coach_notes": ([{"after_learner_turns": 3, "text": COACH["complication"]}] if twist else [])
                       + [{"after_learner_turns": WRAP_AFTER[level], "text": COACH["wrap_up"]}],
    }
    return lesson, target, support


def en_or_any(d):
    """The model reads English; until the app translates, fall back to whatever is there."""
    return (d or {}).get("en") or next(iter((d or {}).values()), "")


def build_prompt(lesson, target, support):
    who = lesson["character"]
    level = lesson["level"]
    tn, sn = NAMES[target], NAMES[support]
    mission = " ".join(f"{i + 1}) {en_or_any(m).rstrip('.')}." for i, m in enumerate(lesson["mission"]))
    phrases = "; ".join(f"{p[target]} = {p.get('en') or p.get(support, '')}".rstrip(" =") for p in lesson["phrases"] if target in p)
    bio = f", {who['bio']}" if who["bio"] else ""
    out = [
        f"You are {who['name']}{bio}.",
        "Stay in character for the whole call. Never say you are an AI, a tutor or a teacher.",
        f"You are on a live voice call in {tn} with {{{{learner_name}}}}, {'an' if sn[0] in 'AEIOU' else 'a'} {sn} speaker learning {tn} at level {level}."
        + (" The learner is {{learner_gender}}: use the matching Polish forms for them." if target == "pl" else ""),
        f"Level: {LEVEL_RULE[target][level]}",
        f"Scene, as the learner sees it: {en_or_any(lesson['scene']).rstrip('.')}. The learner is {en_or_any(lesson['learner_role']).rstrip('.')}.",
        f"The learner's mission (they can see it): {mission} Steer the talk towards each task, but let them try first.",
    ]
    if lesson["complication"]:
        out.append(f"Complication: {en_or_any(lesson['complication']).rstrip('.')}. Bring it in after the first or second task.")
    out += [
        f"Phrases the learner prepared: {phrases}. Use them naturally.",
        "How to talk:",
        f"- Speak only {tn}. One or two short sentences per turn, then stop and let the learner speak.",
        "- React to what they say first, then move on. Ask at most one question per turn.",
        f"- If they seem confused, say it again more simply and slowly, or offer two choices. Never switch to {sn}: the app shows the learner a translation of what you say.",
        f"- If they answer in {sn}, reply in simple {tn} and give them the {tn} to say.",
        f"- Do not correct mistakes directly. Use the correct form naturally in your reply instead {RECAST[target]}.",
        "- If they want to talk about something else, go with it briefly, then come back to the scene.",
    ]
    shape = f"Call shape: open with exactly \"{lesson['opener'][target]}\". "
    if lesson["first_question"] and target in lesson["first_question"]:
        shape += f"When they answer, ask \"{lesson['first_question'][target]}\". "
    shape += f"Then work through the mission. When it is done, or when told it is time, finish in character with \"{lesson['closer'][target]}\""
    out += [shape, "{{memory}}"]
    return "\n".join(out)


def card_to_lesson(md):
    lesson, target, support = parse(md)
    lesson["system_prompt"] = build_prompt(lesson, target, support)
    return {k: v for k, v in lesson.items() if v is not None}, target, support


def publish(src_dir, share):
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from publish_lessons import COACH_TEXT, LOCALES  # noqa: same constants as the main packs
    groups, bad = {}, []
    for f in sorted(Path(src_dir).rglob("*.md")):
        try:
            lesson, target, support = card_to_lesson(f.read_text(encoding="utf-8"))
            groups.setdefault((target, support), []).append(lesson)
        except CardError as e:
            bad.append(f"{f.name}: {e}")
    for (target, support), lessons in groups.items():
        pid = f"my-{target}-for-{support}"
        pack = {"schema": "p4b-lesson-pack/1", "id": pid, "version": 1, "target": target, "support": support,
                "title": {"en": f"My {NAMES[target]} scenarios", "zh": f"我的{ {'en': '英语', 'pl': '波兰语'}[target]}场景"},
                "speech": {"target_locale": LOCALES[target], "support_locale": LOCALES[support]},
                "uses_gender": target == "pl", "custom": True, "cast": {},
                "templates": json.loads((share / "packs" / ("pl-for-zh.json" if support == "zh" and target == "pl" else
                                                             "en-for-zh.json" if support == "zh" else "pl-for-en.json"))
                                        .read_text(encoding="utf-8"))["templates"],
                "units": [{"key": "my", "level": "A1", "title": {"en": "My scenarios", "zh": "我的场景"}, "lessons": lessons}]}
        (share / "packs" / f"{pid}.json").write_text(json.dumps(pack, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"{pid}: {len(lessons)} scenarios")
    for b in bad:
        print("SKIPPED", b)


def selftest():
    tpl = Path.home() / "agent-share" / "lessons" / "templates"
    for name in ("example-barber-pl-zh.md", "example-pharmacy-en-zh.md"):
        lesson, t, s = card_to_lesson((tpl / name).read_text(encoding="utf-8"))
        assert lesson["opener"][t] and len(lesson["mission"]) == 3 and len(lesson["phrases"]) == 5, name
        assert "{{learner_name}}" in lesson["system_prompt"] and "Complication:" in lesson["system_prompt"]
        assert all(s in m for m in lesson["mission"]), "Chinese side of mission kept"
    # tolerant input: Chinese-only mission, numbered list, missing first/closer/twist
    loose = "# 买咖啡\nlearn: English\nspeak: 中文\nlevel: a1\ncharacter: Ben\nscene: 咖啡店\n## Mission\n1. 点一杯咖啡\n## Phrases\n- A latte, please. | 一杯拿铁\n- How much? \n## Lines\nopener: Hi! What can I get you?\n"
    lesson, t, s = card_to_lesson(loose)
    assert lesson["mission"][0] == {"zh": "点一杯咖啡"} and lesson["closer"]["en"] and "complication" not in lesson
    for broken, why in (("learn: pl\n", "title"), ("# X\nlearn: de\n", "learn")):
        try:
            card_to_lesson(broken)
            raise AssertionError("should fail: " + why)
        except CardError as e:
            assert why in str(e), e
    print("selftest ok")


if __name__ == "__main__":
    a = sys.argv[1:]
    if not a or a[0] == "--selftest":
        selftest()
    elif a[0] == "--publish":
        share = Path.home() / "agent-share" / "lessons"
        publish(a[1] if len(a) > 1 else share / "scenarios", share)
    else:
        for f in a:
            lesson, t, s = card_to_lesson(Path(f).read_text(encoding="utf-8"))
            print(json.dumps({k: v for k, v in lesson.items() if k != "system_prompt"}, ensure_ascii=False, indent=1))
            print(lesson["system_prompt"])
