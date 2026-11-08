#!/usr/bin/env python3
"""Briefs for the per-lesson 'director' content: a beat plan with ready, correct
character lines, an example dialogue and a reaction bank, so the small on-device
model (Gemini Nano) gets one clear move per turn and good lines to copy.
    python3 gemini-courses/director/make_briefs.py
Writes director/batches/brief_{pl,en}_{1..4}.txt; run each through ask-agy.sh."""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
SHARE = Path.home() / "agent-share" / "lessons" / "packs"

COMMON = """Each lesson is a short voice role-play: a character (played by a small on-device AI model) talks with a beginner learner. The small model is weak at planning and at grammar, so for every lesson write a step-by-step plan with ready-made, perfectly correct lines it can use or copy.

For each scenario write:
1. "beats": the call in 5-7 steps, in order. Step 1 is the character's first question (use the given first_question as the first line). Then one step per mission task, the complication step where it fits, and a last step "wrap up". Each beat has:
   - "goal": what the character does in this step, in English, imperative, max 8 words (e.g. "Ask where she is from").
   - "lines": 2 or 3 alternative things the character can say for this step, each 1-2 short sentences, at the scenario's level. The lines must invite the learner to do the matching mission task (ask, offer a choice, prompt).
   - "expect": 3-6 short words or stems the learner's answer would likely contain if they did the task (in the language being learnt, lower case, e.g. "chin", "jestem z", "from"). Used to tick the step.
   The last beat's first line must be exactly the given closer.
2. "example": a model call of 8-10 lines alternating character / learner, as it should go at this level, using the learner phrases. Speaker is "C" or "L".
3. "reactions": 6 short neutral or positive reactions the character can say to almost anything (e.g. "Ah, I see.", "Great!", "Really?"), in the character's style.
4. "repair": 2 lines the character says when it did not understand (ask to repeat, offer two choices).

Level rules: A1 = present tense, very common words, max 8 words per sentence. A2 = past and future allowed, max 12 words per sentence.
"""

PL = COMMON + """
Language being learnt: POLISH. Every line must be correct, natural spoken Polish with all diacritics, in the character's register (given per scenario: formal pan/pani, or informal ty). When a line addresses the learner and the Polish form depends on the learner's gender, write both forms as {m:male form|f:female form} around the smallest part that changes, e.g. "Skąd {m:pan jest|f:pani jest}?" or "Jesteś {m:głodny|f:głodna}?". Use this token only for the learner's gender, never for the character's own.
Every line is an object {"pl": "...", "en": "...", "zh": "..."}: the Polish, a natural English translation, and natural Simplified Chinese (for the zh side of a gender token line, translate neutrally). "expect" stays Polish only.

Output ONLY a JSON array, no prose, no code fences. One object per input scenario, same order:
{"id": same as input,
 "beats": [{"goal": "...", "lines": [{"pl":"...","en":"...","zh":"..."}], "expect": ["..."]}],
 "example": [{"s": "C", "pl": "...", "en": "...", "zh": "..."}, {"s": "L", ...}],
 "reactions": [{"pl":"...","en":"...","zh":"..."}],
 "repair": [{"pl":"...","en":"...","zh":"..."}]}
Keep the JSON valid with straight double quotes. Do not write or edit any files.

Scenarios:
"""

EN = COMMON + """
Language being learnt: ENGLISH (the learner is a Chinese speaker). Every line must be natural, friendly spoken English at the level. Every line is an object {"en": "...", "zh": "..."}: the English and natural Simplified Chinese. "expect" stays English only.

Output ONLY a JSON array, no prose, no code fences. One object per input scenario, same order:
{"id": same as input,
 "beats": [{"goal": "...", "lines": [{"en":"...","zh":"..."}], "expect": ["..."]}],
 "example": [{"s": "C", "en": "...", "zh": "..."}, {"s": "L", ...}],
 "reactions": [{"en":"...","zh":"..."}],
 "repair": [{"en":"...","zh":"..."}]}
Keep the JSON valid with straight double quotes. Do not write or edit any files.

Scenarios:
"""


def items(pack_file, target):
    pack = json.loads((SHARE / pack_file).read_text(encoding="utf-8"))
    out = []
    for u in pack["units"]:
        for l in u["lessons"]:
            who = pack["cast"].get(l["character"]["id"], {})
            out.append({
                "id": l["id"], "level": l["level"],
                "character": l["character"]["name"], "bio": l["character"]["bio"],
                "register": who.get("register", "neutral"),
                "scene": l["scene"].get("en"), "learner_role": l["learner_role"].get("en"),
                "mission": [m.get("en") for m in l["mission"]],
                "complication": (l.get("complication") or {}).get("en"),
                "opener": l["opener"][target], "first_question": l["first_question"][target],
                "closer": l["closer"][target], "phrases": [p[target] for p in l["phrases"]],
            })
    return out


for lang, pack_file, head in (("pl", "pl-for-en.json", PL), ("en", "en-for-zh.json", EN)):
    its = items(pack_file, lang)
    for i in range(4):
        (HERE / "batches" / f"brief_{lang}_{i + 1}.txt").write_text(
            head + json.dumps(its[i * 12:(i + 1) * 12], ensure_ascii=False, indent=1), encoding="utf-8")
print("8 briefs written")
