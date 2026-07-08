from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DOCS_PHRASES = ROOT / "docs/phrases.json"
ASSET_PHRASES = ROOT / "polish-phrasebook-android/app/src/main/assets/phrases.json"
DATABASE = ROOT / "docs/database.json"


REPAIRS_BY_CORE_INDEX = {
    32: {"english": "Monday", "exampleEnglish": "The workweek starts on Monday."},
    33: {"english": "Tuesday", "exampleEnglish": "This year January 1st falls on a Tuesday."},
    34: {"english": "Wednesday", "exampleEnglish": "On Wednesday nights we play poker at my house."},
    35: {"english": "Thursday", "exampleEnglish": "Thursday, January 3rd"},
    36: {"english": "Friday", "exampleEnglish": "On Friday people often eat fish."},
    38: {"english": "Sunday", "exampleEnglish": "Father's Day is on Sunday."},
    66: {
        "polish": "cztery",
        "english": "four",
        "examplePolish": "czteropasmowa droga szybkiego ruchu",
        "exampleEnglish": "four-lane motorway",
        "notes": "",
    },
    67: {"exampleEnglish": "The starfish has five arms."},
    71: {
        "english": "nine",
        "examplePolish": "Mam dziewięć biletów.",
        "exampleEnglish": "I have nine tickets.",
        "notes": "",
    },
    89: {"english": "January", "exampleEnglish": "It's very cold here in January."},
    90: {"english": "February", "exampleEnglish": "February is the shortest month and has twenty-eight days."},
    91: {"english": "March", "exampleEnglish": "It is now April, so last month was March."},
    92: {"english": "April", "exampleEnglish": "April showers bring May flowers."},
    93: {
        "category": "Time & Calendar",
        "scenario": "Time & Calendar",
        "english": "May",
        "exampleEnglish": "May 31st is World No Smoking Day.",
    },
    94: {"english": "June", "exampleEnglish": "We are getting married in June."},
    95: {"english": "July", "exampleEnglish": "The month of July is named for Julius Caesar, who was born in July."},
    96: {"english": "August", "exampleEnglish": "The school is closed in August."},
    97: {"english": "September", "exampleEnglish": "Today is Saturday, September 10th."},
    98: {
        "polish": "trzynasty października",
        "english": "October thirteenth",
        "examplePolish": "trzynasty października",
        "exampleEnglish": "October 13th",
    },
    99: {"english": "November", "exampleEnglish": "November is one of four months that have thirty days."},
    306: {"english": "Internet", "exampleEnglish": "Use the internet. It's full of listening resources."},
    512: {"english": "Fahrenheit", "exampleEnglish": "His body temperature was far above the normal 98.6 degrees Fahrenheit."},
    576: {"examplePolish": "Odległość między Moskwą a Petersburgiem wynosi siedemset kilometrów."},
    584: {
        "polish": "Celsjusz",
        "english": "Celsius",
        "examplePolish": "Dzisiejsza temperatura wynosi 30 stopni Celsjusza.",
        "exampleEnglish": "Today's temperature is 30 degrees Celsius.",
    },
    594: {
        "english": "nine hundred ninety-nine",
        "exampleEnglish": "We bought nine hundred ninety-nine chairs for the event.",
    },
    611: {"english": "Taoism", "exampleEnglish": "Taoism is also known as Daoism."},
    615: {"english": "Judaism", "exampleEnglish": "Judaism has been practiced for over three thousand years."},
    686: {"english": "Islam", "exampleEnglish": "Islam was founded by the prophet Muhammad."},
    687: {"english": "Protestantism", "exampleEnglish": "Protestantism came as a break from the Roman Catholic Church."},
    688: {"english": "Catholicism", "exampleEnglish": "Catholicism is the religion of those who accept the leadership of the Pope."},
    689: {"english": "Hinduism", "exampleEnglish": "Hinduism, from India, involves belief in reincarnation and many gods."},
    690: {"english": "Buddhism", "exampleEnglish": "Buddhism is based on the teachings of the Buddha."},
    748: {
        "polish": "Australia",
        "english": "Australia",
        "examplePolish": "Australia to państwo i kontynent.",
        "exampleEnglish": "Australia is a country and continent.",
    },
    749: {
        "polish": "Antarktyda",
        "english": "Antarctica",
        "examplePolish": "Kontynent Antarktydy otacza biegun południowy.",
        "exampleEnglish": "The continent of Antarctica surrounds the South Pole.",
    },
    750: {
        "polish": "Azja",
        "english": "Asia",
        "examplePolish": "Azja jest największym z siedmiu kontynentów Ziemi.",
        "exampleEnglish": "Asia is the largest of Earth's seven continents.",
    },
    751: {"english": "Africa", "exampleEnglish": "Africa, Earth's second-biggest continent, has vast natural resources."},
    880: {
        "category": "Places & Directions",
        "scenario": "Places & Directions",
        "polish": "Madryt",
        "english": "Madrid",
        "examplePolish": "Madryt jest stolicą i największym miastem Hiszpanii.",
        "exampleEnglish": "Madrid is the capital and largest city of Spain.",
    },
    900: {
        "category": "Places & Directions",
        "scenario": "Places & Directions",
        "polish": "Tokio",
        "english": "Tokyo",
        "examplePolish": "Tokio jest stolicą Japonii.",
        "exampleEnglish": "Tokyo is the capital of Japan.",
    },
    901: {
        "category": "Places & Directions",
        "scenario": "Places & Directions",
        "polish": "Pekin",
        "english": "Beijing",
        "examplePolish": "Pekin jest stolicą Chin.",
        "exampleEnglish": "Beijing is the capital of China.",
    },
    902: {
        "category": "Places & Directions",
        "scenario": "Places & Directions",
        "polish": "Londyn",
        "english": "London",
        "examplePolish": "Londyn jest stolicą Anglii i Wielkiej Brytanii.",
        "exampleEnglish": "London is the capital of England and the United Kingdom.",
    },
    1024: {
        "category": "Clothing & Appearance",
        "scenario": "Clothing & Appearance",
        "polish": "bawełna",
        "english": "cotton",
        "examplePolish": "bawełniany T-shirt",
        "exampleEnglish": "cotton T-shirt",
    },
    1044: {"english": "month", "examplePolish": "miesiąc lipiec", "exampleEnglish": "the month of July"},
    1052: {"english": "half past", "exampleEnglish": "Nine thirty is the same as half past nine."},
    1088: {
        "polish": "sto tysięcy",
        "english": "one hundred thousand",
        "examplePolish": "pożyczka w wysokości stu tysięcy dolarów",
        "exampleEnglish": "a loan for one hundred thousand dollars",
    },
    1090: {
        "examplePolish": "Bank stracił sto miliardów dolarów.",
        "exampleEnglish": "The bank lost one hundred billion dollars.",
    },
    1091: {"polish": "dziesięć miliardów"},
    1432: {
        "category": "Places & Directions",
        "scenario": "Places & Directions",
        "polish": "Bangkok",
        "english": "Bangkok",
        "examplePolish": "Bangkok jest największym miastem i stolicą Tajlandii.",
        "exampleEnglish": "Bangkok is the largest city and capital of Thailand.",
    },
    1445: {
        "category": "Places & Directions",
        "scenario": "Places & Directions",
        "polish": "Brazylia",
        "english": "Brazil",
        "examplePolish": "Brazylia jest największym krajem w Ameryce Południowej.",
        "exampleEnglish": "Brazil is the largest country in South America.",
    },
    1449: {
        "category": "Places & Directions",
        "scenario": "Places & Directions",
        "polish": "Bruksela",
        "english": "Brussels",
        "examplePolish": "Bruksela jest stolicą Belgii.",
        "exampleEnglish": "Brussels is the capital of Belgium.",
    },
    1461: {
        "category": "Places & Directions",
        "scenario": "Places & Directions",
        "polish": "Kanada",
        "english": "Canada",
        "examplePolish": "Kanada jest drugim co do wielkości państwem na świecie.",
        "exampleEnglish": "Canada is the second-largest country in the world by area.",
    },
    1478: {
        "polish": "Chiny",
        "english": "China",
        "examplePolish": "Chiny to kraj z największą populacją na Ziemi.",
        "exampleEnglish": "China is the country with the largest population on Earth.",
    },
    1528: {"english": "Dubai", "exampleEnglish": "Dubai is the largest city of the United Arab Emirates."},
    1603: {
        "polish": "Hotmail",
        "english": "Hotmail",
        "examplePolish": "Hotmail to usługa internetowa.",
        "exampleEnglish": "Hotmail is an internet service.",
    },
    1624: {"english": "Japan", "exampleEnglish": "Japan has four main islands and many smaller ones."},
    1676: {"english": "Mexico", "exampleEnglish": "The heart of the Aztec empire was in modern-day Mexico."},
    1737: {
        "polish": "Portugalia",
        "english": "Portugal",
        "examplePolish": "Portugalia jest znana z produkcji wina porto.",
        "exampleEnglish": "Portugal is famous for its production of port wine.",
    },
    1775: {
        "category": "Places & Directions",
        "scenario": "Places & Directions",
        "polish": "Rosja",
        "english": "Russia",
        "examplePolish": "Rosja jest największym państwem na świecie.",
        "exampleEnglish": "Russia is the world's largest country.",
    },
    1792: {
        "category": "Places & Directions",
        "scenario": "Places & Directions",
        "polish": "Seul",
        "english": "Seoul",
        "examplePolish": "Seul jest stolicą Korei Południowej.",
        "exampleEnglish": "Seoul is the capital city of South Korea.",
    },
    1799: {
        "polish": "Singapur",
        "english": "Singapore",
        "examplePolish": "Singapur jest wyspiarskim państwem-miastem.",
        "exampleEnglish": "Singapore is an island city-state.",
    },
    1838: {
        "category": "Places & Directions",
        "scenario": "Places & Directions",
        "polish": "Sydney",
        "english": "Sydney",
        "examplePolish": "Sydney to największe miasto w Australii.",
        "exampleEnglish": "Sydney is the largest city in Australia.",
    },
    1859: {
        "category": "Places & Directions",
        "scenario": "Places & Directions",
        "polish": "Toronto",
        "english": "Toronto",
        "examplePolish": "Toronto jest stolicą Ontario w Kanadzie.",
        "exampleEnglish": "Toronto is the capital of Ontario in Canada.",
    },
    1869: {
        "english": "to translate",
        "examplePolish": "tłumaczyć z angielskiego na francuski",
        "exampleEnglish": "to translate from English to French",
    },
}


def normalize(value: str) -> str:
    value = value.casefold()
    value = re.sub(r"[^\wąćęłńóśźż]+", " ", value, flags=re.IGNORECASE)
    return re.sub(r"\s+", " ", value).strip()


def english_key(value: str) -> str:
    value = normalize(value)
    value = re.sub(r"^to ", "", value)
    return value


def split_meanings(value: str) -> list[str]:
    return [part.strip() for part in re.split(r"\s*/\s*", value or "") if part.strip()]


def should_merge_english(value: str) -> bool:
    if not value or "?" in value or "!" in value:
        return False
    return len(value.split()) <= 5


def add_english(kept: dict, candidate: str) -> None:
    if not should_merge_english(candidate):
        return
    existing = split_meanings(kept.get("english", ""))
    existing_keys = [english_key(item) for item in existing]
    candidate_key = english_key(candidate)
    if not candidate_key:
        return
    for item_key in existing_keys:
        if candidate_key == item_key or candidate_key in item_key or item_key in candidate_key:
            return
    existing.append(candidate.strip())
    kept["english"] = " / ".join(existing)


def merge_duplicate(kept: dict, duplicate: dict) -> None:
    add_english(kept, duplicate.get("english", ""))
    if not kept.get("examplePolish") and duplicate.get("examplePolish"):
        kept["examplePolish"] = duplicate["examplePolish"]
        if duplicate.get("exampleEnglish"):
            kept["exampleEnglish"] = duplicate["exampleEnglish"]
    if not kept.get("exampleEnglish") and duplicate.get("exampleEnglish"):
        kept["exampleEnglish"] = duplicate["exampleEnglish"]
    if not kept.get("notes") and duplicate.get("notes"):
        kept["notes"] = duplicate["notes"]
    tags = []
    for source in (kept.get("tags", []), duplicate.get("tags", [])):
        if isinstance(source, list):
            for tag in source:
                if tag not in tags:
                    tags.append(tag)
    if tags:
        kept["tags"] = tags


def clean_empty_fields(row: dict) -> dict:
    for field in ("notes", "examplePolish", "exampleEnglish"):
        if field in row and row[field] == "":
            row.pop(field)
    if "tags" in row and not row["tags"]:
        row.pop("tags")
    return row


def main() -> None:
    rows = json.loads(DOCS_PHRASES.read_text(encoding="utf-8"))

    repair_count = 0
    for row in rows:
        repair = REPAIRS_BY_CORE_INDEX.get(row.get("coreIndex"))
        if repair:
            row.update(repair)
            repair_count += 1

    deduped: list[dict] = []
    by_polish: dict[str, dict] = {}
    duplicate_count = 0
    for row in rows:
        key = normalize(row.get("polish", ""))
        if key and key in by_polish:
            merge_duplicate(by_polish[key], row)
            duplicate_count += 1
            continue
        by_polish[key] = row
        deduped.append(row)

    deduped = [clean_empty_fields(row) for row in deduped]
    phrase_json = json.dumps(deduped, ensure_ascii=False, indent=2) + "\n"
    DOCS_PHRASES.write_text(phrase_json, encoding="utf-8")
    ASSET_PHRASES.write_text(phrase_json, encoding="utf-8")

    checksum = hashlib.sha256(phrase_json.encode("utf-8")).hexdigest()
    database = json.loads(DATABASE.read_text(encoding="utf-8"))
    database.update(
        {
            "dataVersion": 6,
            "phraseCount": len(deduped),
            "phrasesSha256": checksum,
            "phrasesSizeBytes": len(phrase_json.encode("utf-8")),
            "releaseNotes": (
                "Fixes garbled OCR/import artifacts in core vocabulary and removes duplicate "
                "Polish headword cards while preserving alternate meanings on the kept cards."
            ),
        }
    )
    DATABASE.write_text(json.dumps(database, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(f"applied repairs: {repair_count}")
    print(f"removed duplicate rows: {duplicate_count}")
    print(f"phrase count: {len(rows)} -> {len(deduped)}")


if __name__ == "__main__":
    main()
