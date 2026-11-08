#!/usr/bin/env python3
"""Write the four Antigravity briefs that add Simplified Chinese to the Polish calls.
    python3 gemini-courses/pl-zh/make_briefs.py
Run each with: ask-agy.sh -m gemini-3.8-flash-medium - < brief_N.txt > out_N.json"""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
pack = json.loads((HERE.parent / "out" / "polish_live_courses.json").read_text(encoding="utf-8"))
items = []
for u in pack["courses"][0]["units"]:
    for l in u["lessons"]:
        items.append({
            "id": l["id"], "level": l["level"], "unit": u["title"],
            "character": l["character"]["name"], "character_bio": l["character"]["bio"],
            "title": l["title"], "can_do": l["can_do"], "scene": l["scene"],
            "learner_role": l["learner_role"], "mission": l["mission"],
            "complication": l["complication"],
            "opener": l["opener"], "first_question": l["first_question"], "closer": l["closer"],
            "phrases": l["phrases"], "grammar_focus": l["prep"]["grammar"]["focus"],
        })

HEAD = """You are adding Simplified Chinese (mainland usage) to a Polish speaking course. The learner is an adult Chinese speaker learning Polish at CEFR level A1 or A2; her English is weak, so the Chinese must stand on its own. The Polish stays exactly as it is; you only write Chinese.

For each scenario below, write natural Chinese (not word-for-word, no pinyin) for:
- title, can_do ("我能……"), scene, learner_role, each of the 3 mission tasks, complication;
- opener, first_question, closer: the Chinese meaning of the Polish line (use the English gloss only as help);
- phrases: one Chinese entry per Polish phrase, same number and same order. Give the meaning and, in brackets, when to use it. If the Polish contains "/" alternatives or a {m:…|f:…} pair, explain the choice briefly (e.g. "（男性说 …，女性说 …）", "（对男士的尊称）");
- grammar_focus: explain the Polish grammar point in Chinese in one or two sentences, keeping the Polish examples as they are.
Names: write Polish names as a common Chinese transliteration with the Polish in brackets the first time in each scenario, e.g. "马雷克先生（Pan Marek）", "诺瓦克医生（Doktor Nowak）". Places and dishes (Kraków, Kazimierz, pierogi, żurek, bar mleczny, Wigilia) get a short Chinese explanation.

Output ONLY a JSON array, no prose, no code fences. One object per input scenario, same order, exactly these keys, every value a Chinese string except the two arrays:
{"id": same as input, "title": "...", "can_do": "...", "scene": "...", "learner_role": "...",
 "mission": ["...", "...", "..."], "complication": "...", "opener": "...", "first_question": "...",
 "closer": "...", "phrases": ["...", ...], "grammar_focus": "..."}
Use straight double quotes and keep the JSON valid. Do not write or edit any files.

Scenarios:
"""
for i in range(4):
    batch = items[i * 12:(i + 1) * 12]
    (HERE / "batches" / f"brief_{i + 1}.txt").write_text(
        HEAD + json.dumps(batch, ensure_ascii=False, indent=1), encoding="utf-8")
print(len(items), "scenarios in 4 briefs")
