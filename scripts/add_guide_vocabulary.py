#!/usr/bin/env python3
"""Add the grammar guide's vocabulary to the flashcard deck.

Idempotent: words already in the deck (by normalized headword) are skipped, so
re-running after editing content/grammar_guide.md only adds what is new.
Existing cards are never changed or renumbered; new cards carry no coreIndex,
so their progress key is category + Polish, like every other added card.

    python3 scripts/add_guide_vocabulary.py --dry-run
    python3 scripts/add_guide_vocabulary.py "Release note for the data update"

Writes the bundled asset, mirrors it to docs/phrases.json (what installed apps
download) and bumps docs/database.json so they fetch it.
"""
import hashlib
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from guide_vocabulary import guide_vocabulary  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
ASSET = ROOT / "polish-phrasebook-android" / "app" / "src" / "main" / "assets" / "phrases.json"
PUBLISHED = ROOT / "docs" / "phrases.json"
MANIFEST = ROOT / "docs" / "database.json"
TAG = "grammar-guide"


def key(text):
    # Mirrors LearningLogic.normalizeHeadword.
    return re.sub(r"[.!?,;:]+$", "", text.strip().lower())


def main():
    dry = "--dry-run" in sys.argv
    notes = [a for a in sys.argv[1:] if not a.startswith("--")]
    deck = json.loads(ASSET.read_text(encoding="utf-8"))
    have = {key(c["polish"]) for c in deck}
    added = []
    for e in guide_vocabulary():
        k = key(e["polish"])
        if k in have:
            continue
        have.add(k)
        added.append({
            "category": e["category"],
            "scenario": e["category"],
            "level": e["level"],
            "polish": e["polish"],
            "english": e["english"],
            "phonetic": "",
            "examplePolish": "",
            "exampleEnglish": "",
            "notes": e["notes"],
            "tags": [TAG],
        })
    print(f"{len(added)} new cards (deck {len(deck)} -> {len(deck) + len(added)})")
    if dry or not added:
        for c in added:
            print(f"  {c['level']} {c['category']:<26} {c['polish']:<24} {c['english']}")
        return

    deck.extend(added)
    data = json.dumps(deck, ensure_ascii=False, indent=2) + "\n"
    ASSET.write_text(data, encoding="utf-8")
    PUBLISHED.write_text(data, encoding="utf-8")

    raw = data.encode("utf-8")
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    manifest["dataVersion"] = int(manifest.get("dataVersion", 0)) + 1
    manifest["phraseCount"] = len(deck)
    manifest["phrasesSha256"] = hashlib.sha256(raw).hexdigest()
    manifest["phrasesSizeBytes"] = len(raw)
    manifest["releaseNotes"] = notes[0] if notes else (
        f"Adds {len(added)} words from the grammar & vocabulary guide.")
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"dataVersion -> {manifest['dataVersion']}, {len(raw)} bytes")


if __name__ == "__main__":
    main()
