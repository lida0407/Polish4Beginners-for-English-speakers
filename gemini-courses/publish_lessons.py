#!/usr/bin/env python3
"""Publish the live-call lesson packs to the shared lesson library.

    python3 gemini-courses/publish_lessons.py [--share ~/agent-share/lessons]

Reads out/polish_live_courses.json, out/english_live_courses.json and the
Chinese layer for the Polish calls (pl-zh/batches/out_*.json, if present) and
writes one self-contained pack per (target, support) language pair:
  packs/en-for-zh.json   English calls, Chinese support
  packs/pl-for-zh.json   Polish calls, Chinese support
  packs/pl-for-en.json   Polish calls, English support
plus catalog.json. A pack's version goes up only when its content changes.
Every shown field is a dict keyed by language code; the app shows
field[support] and speaks field[target]. Schema: lessons/README.md.
"""
import argparse
import datetime
import hashlib
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
CJK = re.compile(r"[一-鿿]")
LOCALES = {"en": "en-US", "pl": "pl-PL", "zh": "cmn-Hans-CN"}
UNIT_ZH = {
    "u01": "初到克拉科夫", "u02": "数字与购物", "u03": "饮食与外出就餐", "u04": "出行",
    "u05": "家", "u06": "时间与计划", "u07": "健康", "u08": "发生了什么？",
    "u09": "商店与服务", "u10": "工作与学习", "u11": "旅行", "u12": "观点与文化",
}
FEEDBACK_ZH_PL = (
    "You are a friendly Polish coach for Chinese speakers. Below is a practice call between a learner "
    "(level {{level}}) and a character. The learner's mission was: {{mission}}.\n"
    "Write the feedback in Simplified Chinese, quoting Polish where needed, in exactly this format:\n"
    "任务：每项一行，✓ 或 ✗，加几个字说明。\n"
    "需要改正：最多三行，“学员说的 → 更好的波兰语（原因）”。没有错误就写“没有需要改正的”。\n"
    "下次可以用：两个波兰语短语，附中文意思。\n"
    "评分：准确度 N/5，表达丰富度 N/5。\n"
    "一句鼓励的话。\n"
    "Transcript:\n{{transcript}}"
)
COACH_TEXT = {
    "complication": "(Coach: if you have not yet, bring in the complication now.)",
    "wrap_up": "(Coach: it is time to go. Wrap up in character and say goodbye.)",
}


def L(**kw):
    return {k: v for k, v in kw.items() if v is not None}


TOKEN = re.compile(r"\{m:[^|{}]+\|f:[^|{}]+\}")


def norm(t):
    return re.sub(r"[^\w]+", " ", TOKEN.sub("", t or "").lower()).strip()


def director_layer(lang):
    """id -> director dict from director/batches/out_<lang>_*.json (Gemini via Antigravity)."""
    merged = {}
    for f in sorted((HERE / "director" / "batches").glob(f"out_{lang}_*.json")):
        t = f.read_text(encoding="utf-8").strip()
        if "[" not in t:
            continue
        for it in json.loads(t[t.index("["):t.rindex("]") + 1]):
            merged[it.get("id")] = it
    return merged


def check_director(d, lesson, target, support_keys):
    """Validate and normalise one lesson's director. Returns (director, problem)."""
    def line_ok(x, keys):
        if not isinstance(x, dict) or not str(x.get(target, "")).strip():
            return False
        if target != "zh" and CJK.search(x[target]):
            return False
        if "zh" in keys and not CJK.search(str(x.get("zh", ""))):
            return False
        if x[target].count("{") != len(TOKEN.findall(x[target])):
            return False
        return all(str(x.get(k, "")).strip() for k in keys)
    beats = d.get("beats") or []
    if not 4 <= len(beats) <= 8:
        return None, f"{len(beats)} beats"
    for b in beats:
        if not str(b.get("goal", "")).strip() or not 1 <= len(b.get("lines", [])) <= 4 or not b.get("expect"):
            return None, f"bad beat {str(b)[:80]}"
        bad = [x for x in b["lines"] if not line_ok(x, support_keys)]
        if bad:
            return None, f"bad line {str(bad[0])[:100]}"
        b["expect"] = [str(e).lower().strip() for e in b["expect"] if str(e).strip()]
    first, closer = lesson["first_question"], lesson["closer"]
    if norm(beats[0]["lines"][0][target]) != norm(first[target]):
        beats[0]["lines"].insert(0, {k: v for k, v in first.items()})
    if norm(beats[-1]["lines"][0][target]) != norm(closer[target]):
        beats[-1]["lines"].insert(0, {k: v for k, v in closer.items()})
    ex = d.get("example") or []
    if len(ex) < 6 or any(e.get("s") not in ("C", "L") or not line_ok(e, support_keys) for e in ex):
        return None, "bad example"
    for k, lo in (("reactions", 4), ("repair", 1)):
        if len(d.get(k) or []) < lo or not all(line_ok(x, support_keys) for x in d[k]):
            return None, f"bad {k}"
    return {"beats": beats, "example": ex, "reactions": d["reactions"], "repair": d["repair"]}, None


def zh_layer():
    merged = {}
    for f in sorted((HERE / "pl-zh" / "batches").glob("out_*.json")):
        t = f.read_text(encoding="utf-8").strip()
        if "[" not in t:
            continue
        for it in json.loads(t[t.index("["):t.rindex("]") + 1]):
            merged[it["id"]] = it
    return merged


def check_zh(lesson, z):
    keys = ["title", "can_do", "scene", "learner_role", "mission", "complication", "opener",
            "first_question", "closer", "phrases", "grammar_focus"]
    errs = [k for k in keys if k not in z]
    if errs:
        return f"missing {errs}"
    if len(z["mission"]) != len(lesson["mission"]) or len(z["phrases"]) != len(lesson["phrases"]):
        return "mission/phrase count differs"
    for k in keys:
        for v in (z[k] if isinstance(z[k], list) else [z[k]]):
            if not isinstance(v, str) or not CJK.search(v):
                return f"{k}: no Chinese in {str(v)[:60]}"
    return None


def polish_prompt_for_zh(p):
    swaps = [
        ("an English speaker learning Polish", "a Chinese speaker learning Polish (her English is weak)"),
        ("If they are lost twice in a row, say the key word in English once, then carry on in Polish.",
         "Never switch to English or Chinese: the app shows the learner a Chinese translation of what you say."),
        ("- If they answer in English, reply in simple Polish and give them the Polish to say.",
         "- If they answer in Chinese or English, reply in simple Polish and give them the Polish to say."),
    ]
    for a, b in swaps:
        if p.count(a) != 1:
            raise SystemExit(f"Polish prompt changed shape, cannot find: {a}")
        p = p.replace(a, b)
    return p


def polish_lessons(pack, support, zh):
    units, problems = [], []
    for u in pack["courses"][0]["units"]:
        lessons = []
        for l in u["lessons"]:
            z = zh.get(l["id"]) if support == "zh" else None
            if support == "zh":
                err = "missing" if z is None else check_zh(l, z)
                if err:
                    problems.append(f"{l['id']}: {err}")
                    continue
            g = lambda k: z[k] if z else None
            lessons.append({
                "id": l["id"].replace("call-", "pl-call-", 1), "level": l["level"], "minutes": l["minutes"],
                "character": l["character"],
                "title": L(en=l["title"], zh=g("title")),
                "can_do": L(en=l["can_do"], zh=g("can_do")),
                "scene": L(en=l["scene"], zh=g("scene")),
                "learner_role": L(en=l["learner_role"], zh=g("learner_role")),
                "mission": [L(en=m, zh=z["mission"][i] if z else None) for i, m in enumerate(l["mission"])],
                "complication": L(en=l["complication"], zh=g("complication")),
                "opener": L(pl=l["opener"]["pl"], en=l["opener"]["en"], zh=g("opener")),
                "first_question": L(pl=l["first_question"]["pl"], en=l["first_question"]["en"], zh=g("first_question")),
                "closer": L(pl=l["closer"]["pl"], en=l["closer"]["en"], zh=g("closer")),
                "phrases": [L(pl=p["pl"], en=p["en"], zh=z["phrases"][i] if z else None) for i, p in enumerate(l["phrases"])],
                "grammar_focus": L(en=l["prep"]["grammar"]["focus"], zh=g("grammar_focus")),
                "coach_notes": l["coach_notes"],
                "system_prompt": polish_prompt_for_zh(l["system_prompt"]) if support == "zh" else l["system_prompt"],
            })
        units.append({"key": u["key"], "level": u["level"],
                      "title": L(en=u["title"], zh=UNIT_ZH[u["key"]] if support == "zh" else None),
                      "lessons": lessons})
    return units, problems


def english_lessons(pack):
    units = []
    for u in pack["courses"][0]["units"]:
        lessons = [{k: v for k, v in l.items()} for l in u["lessons"]]
        units.append({"key": u["key"], "level": u["level"], "title": L(en=u["title"], zh=UNIT_ZH[u["key"]]),
                      "lessons": lessons})
    return units


def make_pack(pid, target, support, title, cast, templates, units, gender):
    return {
        "schema": "p4b-lesson-pack/1", "id": pid, "version": 0,
        "target": target, "support": support, "title": title,
        "speech": {"target_locale": LOCALES[target], "support_locale": LOCALES[support]},
        "uses_gender": gender, "cast": cast, "templates": templates, "units": units,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--share", default=str(Path.home() / "agent-share" / "lessons"))
    share = Path(ap.parse_args().share).expanduser()
    (share / "packs").mkdir(parents=True, exist_ok=True)

    pl = json.loads((HERE / "out" / "polish_live_courses.json").read_text(encoding="utf-8"))
    en = json.loads((HERE / "out" / "english_live_courses.json").read_text(encoding="utf-8"))
    pl_t, en_t = pl["call_templates"], en["call_templates"]
    packs = []

    packs.append(make_pack("en-for-zh", "en", "zh", {"zh": "英语情景通话", "en": "English live calls"},
                           en["cast"], {"feedback_prompt": en_t["feedback_prompt"],
                                        "memory_prompt": en_t["memory_prompt"], "coach": COACH_TEXT},
                           english_lessons(en), False))
    units, _ = polish_lessons(pl, "en", {})
    packs.append(make_pack("pl-for-en", "pl", "en", {"en": "Polish live calls"}, pl["cast"],
                           {"feedback_prompt": pl_t["feedback_prompt"], "memory_prompt": pl_t["memory_prompt"],
                            "coach": COACH_TEXT}, units, True))
    zh = zh_layer()
    if zh:
        units, problems = polish_lessons(pl, "zh", zh)
        if problems:
            print("pl-for-zh NOT published, problems:\n  " + "\n  ".join(problems[:30]))
        else:
            packs.append(make_pack("pl-for-zh", "pl", "zh", {"zh": "波兰语情景通话", "en": "Polish live calls (Chinese support)"},
                                   pl["cast"], {"feedback_prompt": FEEDBACK_ZH_PL, "feedback_prompt_en": pl_t["feedback_prompt"],
                                                "memory_prompt": pl_t["memory_prompt"], "coach": COACH_TEXT}, units, True))
    for p in packs:
        if p["id"] == "en-for-zh":
            p["templates"]["feedback_prompt_en"] = en_t["feedback_prompt"].split("Write the feedback")[0] + (
                "Write the feedback in English, under 150 words: Mission (✓/✗ per task), Fix these (up to 3, "
                "\"what they said → better English (why)\"), Try next time (2 phrases), Scores: accuracy N/5, range N/5, "
                "one sentence of encouragement.\nTranscript:\n{{transcript}}")

    director_problems = []
    layers = {"pl": director_layer("pl"), "en": director_layer("en")}
    for p in packs:
        layer = layers[p["target"]]
        if not layer:
            continue
        keys = [k for k in ("en", "zh") if k != p["target"]]
        if p["target"] == "pl":
            keys = ["en", "zh"]
        for u in p["units"]:
            for l in u["lessons"]:
                d = layer.get(l["id"].replace("pl-call-", "call-"))  or layer.get(l["id"])
                if d is None:
                    director_problems.append(f"{p['id']}/{l['id']}: no director")
                    continue
                import copy
                fixed, err = check_director(copy.deepcopy(d), l, p["target"], keys)
                if err:
                    director_problems.append(f"{p['id']}/{l['id']}: {err}")
                else:
                    l["director"] = fixed
    if director_problems:
        print(f"director: {len(director_problems)} problems (those lessons ship without it):\n  " + "\n  ".join(director_problems[:40]))
    old = {}
    cat_path = share / "catalog.json"
    if cat_path.exists():
        old = {p["id"]: p for p in json.loads(cat_path.read_text(encoding="utf-8"))["packs"]}
    entries = []
    for p in packs:
        body = json.dumps({k: v for k, v in p.items() if k != "version"}, ensure_ascii=False, sort_keys=True)
        sha = hashlib.sha256(body.encode()).hexdigest()
        prev = old.get(p["id"], {})
        p["version"] = prev.get("version", 0) + (0 if prev.get("sha256") == sha else 1)
        f = f"packs/{p['id']}.json"
        (share / f).write_text(json.dumps(p, ensure_ascii=False, indent=1), encoding="utf-8")
        n = sum(len(u["lessons"]) for u in p["units"])
        entries.append({"id": p["id"], "file": f, "version": p["version"], "sha256": sha,
                        "target": p["target"], "support": p["support"], "title": p["title"],
                        "levels": sorted({u["level"] for u in p["units"]}), "units": len(p["units"]), "lessons": n})
        print(f"{p['id']}: v{p['version']}, {n} lessons")
    cat_path.write_text(json.dumps({"schema": "p4b-lesson-catalog/1",
                                    "updated": datetime.datetime.now().isoformat(timespec="minutes"),
                                    "packs": entries}, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
