#!/usr/bin/env python3
"""Turn a learner's Android app data into a backup file the web app imports
(Settings -> Backup -> Import a backup).

    python3 scripts/android_to_web_backup.py PREFS.xml [--notes word_notes.json]
        [--list "Name=words.json" ...] -o backup.json

PREFS.xml is the app's shared_prefs/phrasebook.xml (debug builds:
`adb exec-out run-as <package> cat shared_prefs/phrasebook.xml`).
--list adds an extra word list from a JSON array of {polish, english}.
Nothing here is specific to one person; the output is personal, keep it out of the repository.
"""
import argparse
import json
import time
import unicodedata
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def headword(text):
    """Same idea as LearningLogic.normalizeHeadword: lower case, trimmed, no edge punctuation."""
    t = unicodedata.normalize("NFC", (text or "").strip().lower())
    return t.strip(" .,!?;:\"'„”()[]…")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("prefs")
    ap.add_argument("--notes")
    ap.add_argument("--list", action="append", default=[])
    ap.add_argument("-o", "--out", required=True)
    a = ap.parse_args()

    prefs = {e.get("name"): (e.get("value") if e.get("value") is not None else (e.text or ""))
             for e in ET.parse(a.prefs).getroot()}
    deck = json.loads((ROOT / "docs/phrases.json").read_text(encoding="utf-8"))
    deck_ids = {(f"core:{p['coreIndex']}" if p.get("coreIndex", 0) > 0 else f"{p['category']}:{p['polish']}") for p in deck}
    custom = json.loads(prefs.get("customCards") or "[]")
    custom_by_polish = {c["polish"]: c for c in custom}
    guide = json.loads((ROOT / "docs/app/data/grammar_guide.json").read_text(encoding="utf-8"))
    quiz_len = {l["id"]: len(l["quiz"]) for u in guide["course"]["units"] for l in u["lessons"]}
    keys, report = {}, {}

    # Review memory: "memory:<cardKey>" = "box|dueAt". The web app uses the same card keys.
    mem = {}
    for name, value in prefs.items():
        if name.startswith("memory:") and "|" in value:
            box, due = value.split("|")[:2]
            mem[name[len("memory:"):]] = [int(box), int(due)]
    keys["mem"] = mem
    report["studied cards"] = f"{len(mem)} ({sum(1 for k in mem if k in deck_ids)} in the shared deck)"

    # Favourites: deck cards keep their id; the learner's own words become free phrases.
    fav = []
    for key in json.loads(prefs.get("favourites") or "[]"):
        if key in deck_ids:
            fav.append(key)
        else:
            polish = key.split(":", 1)[1] if ":" in key else key
            fav.append({"polish": polish, "english": custom_by_polish.get(polish, {}).get("english", "")})
    keys["fav"] = fav
    report["favourites"] = len(fav)

    # The learner's own cards, grouped by tag into word lists.
    lists = {}
    for c in custom:
        lists.setdefault(c.get("tag") or "My Words", []).append({"polish": c["polish"], "english": c.get("english", "")})
    for spec in a.list:
        name, path = spec.split("=", 1)
        items = json.loads(Path(path).read_text(encoding="utf-8"))
        seen = set()
        lists[name] = [{"polish": i["polish"], "english": i.get("english", "")} for i in items
                       if i.get("polish") and not (i["polish"] in seen or seen.add(i["polish"]))]
    keys["lists"] = [{"name": n, "items": items} for n, items in lists.items()]
    report["word lists"] = ", ".join(f"{n} ({len(i)})" for n, i in lists.items())

    dialogs = json.loads(prefs.get("customDialogs") or "[]")
    if dialogs:
        keys["dialogs"] = dialogs
        report["own conversations"] = len(dialogs)

    if a.notes and Path(a.notes).exists():
        raw = json.loads(Path(a.notes).read_text(encoding="utf-8"))
        keys["notes"] = {headword(v.get("word") or k): v.get("text", "") for k, v in raw.items() if v.get("text")}
        report["notes"] = len(keys["notes"])

    done = {}
    best = json.loads(prefs.get("courseBest") or "{}")
    for lesson in filter(None, (prefs.get("courseDone") or "").split(",")):
        n = quiz_len.get(lesson, 0)
        done[lesson] = round(100 * best[lesson] / n) if n and lesson in best else 100
    if done:
        keys["course"] = {"done": done, "last": prefs.get("guideLast") or list(done)[-1]}
        report["course lessons done"] = len(done)

    settings = {}
    if prefs.get("theme") in ("Komiks", "Książka", "Atrament"):
        settings["theme"] = prefs["theme"]
    if prefs.get("speechSpeed"):
        settings["speed"] = prefs["speechSpeed"]
    if settings:
        keys["settings"] = settings

    now = int(time.time() * 1000)
    backup = {"app": "polish4beginners", "format": 1, "exportedAt": now, "source": "android",
              "keys": {k: {"v": json.dumps(v, ensure_ascii=False), "t": now} for k, v in keys.items()}}
    Path(a.out).write_text(json.dumps(backup, ensure_ascii=False), encoding="utf-8")
    for k, v in report.items():
        print(f"{k}: {v}")
    print("written", a.out, f"({Path(a.out).stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
