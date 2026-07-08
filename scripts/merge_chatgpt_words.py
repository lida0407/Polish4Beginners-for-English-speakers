#!/usr/bin/env python3
"""Merge entry_type=='word' rows from the ChatGPT A1-B2 CSV into the deck.
Phrase/idiom rows are intentionally skipped (they overlap heavily with a
copyrighted workbook); bare vocabulary words are factual and safe."""
import csv, json, re, hashlib

REPO = "/Users/dali/Sensaka/Polish4Beginners"
CSV  = "/Users/dali/Downloads/chatgpt_polish_a1_b2_learning_list_1200.csv"
ASSET = f"{REPO}/polish-phrasebook-android/app/src/main/assets/phrases.json"
DOCS  = f"{REPO}/docs/phrases.json"
DB    = f"{REPO}/docs/database.json"

TOPIC2CAT = {
    "city and places":"Places & Directions","city, places, local life":"Places & Directions",
    "family and relationships":"People & Family","relationships and social life":"People & Family",
    "food and drink":"Food & Drink","cooking and restaurant":"Food & Drink",
    "personal basics":"Greetings & Essentials",
    "technology and internet":"Technology & Media","technology and media":"Technology & Media",
    "technology, data and AI":"Technology & Media",
    "home and household":"Home & Daily Life","home and renting":"Home & Daily Life",
    "shopping and money":"Shopping & Money","consumer choices and money":"Shopping & Money",
    "daily life, consumer choices":"Shopping & Money",
    "transport":"Travel & Transport","travel and accommodation":"Travel & Transport",
    "travel problems and planning":"Travel & Transport","travel and transport":"Travel & Transport",
    "clothes and appearance":"Clothing & Appearance",
    "body and health":"Health & Body","health and wellbeing":"Health & Body",
    "daily routine and time":"Time & Calendar","holidays and celebrations":"Time & Calendar",
    "free time and leisure":"Activities & Sports","sport and health":"Activities & Sports",
    "weather and seasons":"Weather & Nature",
    "nature and environment":"Nature & Animals","environment and climate":"Nature & Animals",
    "environment and policy":"Nature & Animals",
    "services and administration":"Administration & Services",
    "society and community":"Administration & Services","society and public affairs":"Administration & Services",
    "social problems and helping":"Administration & Services",
    "work and study":"Work & School","education and learning":"Work & School",
    "work and career":"Work & School","business and marketing":"Work & School",
    "leadership and workplace":"Work & School","education and school":"Work & School",
    "feelings and personality":"Feelings & Qualities","relationships and psychology":"Feelings & Qualities",
    "feelings and emotions":"Feelings & Qualities",
    "culture and media":"Religion & Culture","culture and intercultural situations":"Religion & Culture",
    "law and safety":"Emergencies & Safety","law, safety, society":"Emergencies & Safety",
    "advanced daily life and problem solving":"General Core",
}

def norm(s):
    s = (s or "").strip().lower()
    s = re.sub(r"[.!?,;:]+$", "", s)
    return re.sub(r"\s+", " ", s)

existing = json.load(open(ASSET, encoding="utf-8"))
exkeys = {norm(e["polish"]) for e in existing}

rows = list(csv.DictReader(open(CSV, encoding="utf-8-sig")))
words = [r for r in rows if r["entry_type"].strip() == "word"]

added, seen = [], set()
skip_dupe = skip_internal = skip_unmapped = 0
for r in words:
    k = norm(r["polish"])
    if k in exkeys:  skip_dupe += 1; continue
    if k in seen:    skip_internal += 1; continue
    cat = TOPIC2CAT.get(r["topic"].strip())
    if cat is None:  # default catch-all, but log it
        cat = "General Core"; skip_unmapped += 1
    seen.add(k)
    added.append({
        "category": cat, "scenario": cat, "level": r["level"].strip(),
        "polish": r["polish"].strip(), "english": r["english"].strip(),
        "phonetic": "",
    })

merged = existing + added
for path in (ASSET, DOCS):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(merged, f, ensure_ascii=False, indent=2); f.write("\n")

raw = open(DOCS, "rb").read()
db = json.load(open(DB))
db["dataVersion"] = db.get("dataVersion", 4) + 1
db["phraseCount"] = len(merged)
db["phrasesSha256"] = hashlib.sha256(raw).hexdigest()
db["phrasesSizeBytes"] = len(raw)
db["releaseNotes"] = f"Adds {len(added)} A1-B2 vocabulary words across everyday and advanced topics."
with open(DB, "w") as f:
    json.dump(db, f, ensure_ascii=False, indent=2); f.write("\n")

print(f"word rows in CSV:      {len(words)}")
print(f"  skipped (dupe of DB):  {skip_dupe}")
print(f"  skipped (dupe in CSV): {skip_internal}")
print(f"  net new added:         {len(added)}")
print(f"  (defaulted to General Core, topic unmapped: {skip_unmapped})")
print(f"total deck now:        {len(merged)}")
print(f"dataVersion -> {db['dataVersion']}  sha {db['phrasesSha256'][:12]}  size {db['phrasesSizeBytes']}")
json.dump(added, open(f"{REPO}/scripts/_chatgpt_words_added_preview.json","w"), ensure_ascii=False, indent=2)
