#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path

from pypdf import PdfReader


CORE_SOURCE = "Polish CORE 2000 PDF"
HEADER_SKIP = {"PolishPod101.com", "Vocabulary Sample Sentence", "Vocabular y"}
UPPER = "A-ZĄĆĘŁŃÓŚŹŻÀ-ÖØ-Þ"
LOWER = "a-ząćęłńóśźżà-öø-ÿ"
WORD_CHARS = "\\wąćęłńóśźżĄĆĘŁŃÓŚŹŻÀ-ÖØ-Þà-öø-ÿ’'-"
CAP_WORD = f"[{UPPER}][{WORD_CHARS}]*"


def clean_text(value):
    value = re.sub(r"\s+", " ", value).strip()
    value = re.sub(
        r"(?<=[A-Za-ząćęłńóśźżĄĆĘŁŃÓŚŹŻÀ-ÖØ-Þà-öø-ÿ])-\s+(?=[A-Za-ząćęłńóśźżĄĆĘŁŃÓŚŹŻÀ-ÖØ-Þà-öø-ÿ])",
        "",
        value,
    )
    return value.strip()


def is_cap_token(token):
    return bool(re.match(rf"^[{UPPER}]", token)) and token.strip(".,;:!?()") != "I"


def is_lower_token(token):
    return bool(re.match(rf"^[{LOWER}]", token))


def split_inline(text, hint_words=None, fallback=True):
    text = clean_text(text)
    if not text:
        return "", ""

    words = text.split()
    candidates = []

    punctuation = re.search(r"([.!?])\s+", text)
    if punctuation and punctuation.end() <= 70:
        candidates.append(punctuation.end())

    if words and is_cap_token(words[0]):
        run = 0
        for word in words:
            if is_cap_token(word):
                run += 1
            else:
                break
        if 2 <= run <= 4 and run < len(words) and is_lower_token(words[run]):
            candidates.append(len(" ".join(words[:run])))

    first = words[0] if words else ""
    first_is_cap = bool(re.match(rf"^[{UPPER}]", first))
    first_is_article = first.lower() in {"the", "a", "an"}
    for match in re.finditer(rf"\s+({CAP_WORD})", text):
        prefix_words = len(text[: match.start()].split())
        if first_is_cap and prefix_words < 2:
            continue
        if first_is_article:
            continue
        if 2 <= match.start() <= 90:
            candidates.append(match.start())

    if candidates:
        split_at = min(candidates)
        return text[:split_at].strip(), text[split_at:].strip()

    normalized_words = [
        re.sub(rf"[^{WORD_CHARS}]+", "", word).lower()
        for word in words
    ]
    max_prefix = min(6, max(1, len(words) // 2))
    for size in range(max_prefix, 0, -1):
        prefix = normalized_words[:size]
        if not all(prefix):
            continue
        for position in range(1, min(len(normalized_words) - size + 1, size + 4)):
            if normalized_words[position : position + size] == prefix:
                return " ".join(words[:size]).strip(), " ".join(words[size:]).strip()

    if not fallback:
        return text, ""
    if hint_words:
        count = max(1, min(hint_words, len(words)))
        return " ".join(words[:count]).strip(), " ".join(words[count:]).strip()
    if len(words) > 4:
        return " ".join(words[:3]).strip(), " ".join(words[3:]).strip()
    return text, ""


def extract_part(parts, hint_words=None):
    useful = [
        part
        for part in parts
        if part[2].strip()
        and not part[2].startswith("PAGE")
        and part[2].strip() not in HEADER_SKIP
        and part[2].strip() != "PolishPod101.com"
    ]
    if not useful:
        return "", ""

    left = []
    right = []
    for y, x, text in sorted(useful, key=lambda item: (-item[0], item[1])):
        if x >= 180:
            right.append(text)
        else:
            left.append(text)

    if right and left:
        head, sample_left = split_inline(clean_text(" ".join(left)), hint_words, fallback=False)
        sample = clean_text(" ".join([sample_left, clean_text(" ".join(right))]))
        return head, sample

    joined = clean_text(" ".join(text for _, _, text in sorted(useful, key=lambda item: (-item[0], item[1]))))
    return split_inline(joined, hint_words, fallback=True)


def parse_pdf(pdf_path):
    reader = PdfReader(str(pdf_path))
    entries = []

    for page_number, page in enumerate(reader.pages, start=1):
        items = []

        def visitor(text, cm, tm, font, font_size):
            for part in text.split("\n"):
                part = part.strip()
                if part:
                    items.append((tm[5], tm[4], part))

        page.extract_text(visitor_text=visitor)
        markers = sorted(
            [
                (int(text), y, x)
                for y, x, text in items
                if re.fullmatch(r"\d{1,4}", text)
                and 40 <= x <= 70
                and 50 <= y <= 730
                and 1 <= int(text) <= 2000
            ],
            key=lambda item: -item[1],
        )

        for index, (core_index, marker_y, marker_x) in enumerate(markers):
            top = (markers[index - 1][1] + marker_y) / 2 if index > 0 else 730
            bottom = (markers[index + 1][1] + marker_y) / 2 if index + 1 < len(markers) else 60
            row = [
                (y, x, text)
                for y, x, text in items
                if bottom <= y <= top
                and not (text == str(core_index) and abs(y - marker_y) < 0.5 and abs(x - marker_x) < 1.0)
            ]
            polish_parts = [item for item in row if item[0] >= marker_y - 1]
            english_parts = [item for item in row if item[0] < marker_y - 1]

            english, english_sample = extract_part(english_parts)
            polish, polish_sample = extract_part(polish_parts, len(english.split()) if english else None)
            if polish and english:
                entries.append(
                    {
                        "index": core_index,
                        "page": page_number,
                        "polish": polish,
                        "english": english,
                        "polish_sample": polish_sample,
                        "english_sample": english_sample,
                    }
                )

    return sorted(entries, key=lambda item: item["index"])


def level_for(index):
    if index <= 400:
        return "A1"
    if index <= 900:
        return "A2"
    if index <= 1400:
        return "B1"
    if index <= 1750:
        return "B2"
    return "C1"


SCENARIO_KEYWORDS = [
    ("Greetings & Essentials", "hello hi goodbye thank thanks sorry excuse yes no please name welcome congratulations address phone telephone bathroom who what where why how may long time see"),
    ("Time & Calendar", "today tomorrow yesterday week weekly month monthly year yearly second minute hour clock watch o'clock calendar monday tuesday wednesday thursday friday saturday sunday january february march april may june july august september october november december birthday anniversary graduation wedding funeral"),
    ("Numbers & Measures", "zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty thirty forty fifty sixty seventy eighty ninety hundred thousand first second third fourth fifth sixth seventh eighth ninth tenth meter kilometer centimetre centimeter inch foot gram kilogram liter litre percent half degree low high"),
    ("Food & Drink", "food drink water tea coffee beer wine juice soda cocoa beef chicken pork fish lamb fruit apple banana lemon peach melon pineapple prune mango grape watermelon strawberry cherry vegetable broccoli cabbage celery cucumber pepper potato sweet rice bread soup milk cheese egg noodle restaurant waiter waitress kitchen cuisine garlic carrot onion lettuce soybean tomato spinach lobster salmon shrimp crab cook bakery spoon fork knife plate bowl"),
    ("Travel & Transport", "train bus taxi car airport airplane plane flight ticket baggage luggage seat gate departure arrival passport customs hotel key highway traffic road subway metro monorail ferry ship boat helicopter bicycle motorcycle scooter commute travel tourist tour sightseeing stop station driver license guidebook beach island fare"),
    ("Health & Body", "doctor nurse hospital dentist medicine pain fever cold cough sneeze allergy rash burn body head face eye ear nose mouth tooth teeth lip cheek neck back chest arm hand finger elbow wrist leg foot shoulder muscle bone blood heart stomach patient injection x-ray toothbrush soap shampoo shower bathe wash brush"),
    ("Work & School", "work job office coworker boss employee superior secretary bank banker meeting negotiation salary wage bonus school student teacher teach learn classroom homework exam test lecture university math science history geography biology chemistry physics economics literature computer department accounting lawyer president superintendent police officer salesman manager engineer programmer farmer writer designer artist soldier entrepreneur construction worker business contract company deal industry promotion retire whiteboard blackboard textbook notebook pencil pen desk library dictionary ruler marker document answer ask explain information"),
    ("People & Family", "person people man woman boy girl adult child children birth family mother father parent brother sister son daughter husband wife friend girlfriend boyfriend colleague homemaker mr mrs baby youth senior citizen"),
    ("Home & Daily Life", "home house room bedroom bathroom kitchen bed table chair desk door window wall curtain ceiling floor hallway apartment entrance exit appliance fan refrigerator microwave oven kettle pot pan frying sink bookshelf dresser mirror remote control key garden backyard laundry towel tissue washcloth toothbrush conditioner deodorant pajamas sleep wake clean dirty sweep mop put away"),
    ("Shopping & Money", "buy shopping sale store shop supermarket market pharmacy bakery coupon aisle bag plastic money bank credit card price expensive cheap fare fee pay cash receipt bill check coin wallet purse delivery parcel package consignee order checkout reservation"),
    ("Technology & Media", "computer internet website email e-mail phone telephone camera webcam wi-fi tv television movie film dvd skype yahoo software keyboard screen radio printer fax machine copy newspaper news channel message sms photography photograph remote control"),
    ("Clothing & Appearance", "shirt pants jeans dress skirt jacket coat shoes sneaker hat vest socks underwear boxer brassiere pajamas clothing wear belt necktie suit tuxedo color black brown gray grey white red green blue yellow orange pretty ugly beautiful beard hair tall slim fat small large glasses"),
    ("Nature & Animals", "animal dog cat bird duck crow horse cow pig sheep rabbit seal mouse hamster frog pigeon snake insect bee ant cockroach mosquito ocean sea river lake mountain forest tree flower weather rain rainy snow cloudy sunny wind storm cloud sun temperature humid windy ice tsunami avalanche alligator anteater jaguar whale turtle crab octopus shark dolphin squid jellyfish rainforest field desert"),
    ("Activities & Sports", "do make use receive search take say call find give talk help move finish start become rest hear listen want watch return leave keep wait close open turn off put remember hold go come walk run play dance sing laugh read write draw plan try measure relax sport football soccer baseball basketball tennis poker badminton exercise swim surf pool playground eat out attend art music opera theater comedy novel short story musical instrument painting"),
    ("Feelings & Qualities", "good bad easy difficult hard strong weak hot cold cool warm delicious interesting boring mean hopeful anxious angry happy sad strict famous casual professional polite funny exciting important busy serious tired disgusting clean dirty low high small big little"),
    ("Places & Directions", "place city country street road direction north south east west outside inside near far here there left right straight front back bridge overpass continent asia africa europe america china japan los angeles dubai washington pharmacy bakery supermarket field desert beach island ocean lake river map suitcase entrance exit"),
    ("Grammar & Function Words", "and or but because if to from in on at for with without this that these those i you he she we they it of can"),
]


def scenario_for(entry):
    text = f"{entry['english']} {entry['polish']}".lower()
    text = re.sub(r"[^a-ząćęłńóśźż0-9' -]+", " ", text)
    best_name = "General Core"
    best_score = 0
    for scenario, keywords in SCENARIO_KEYWORDS:
        score = 0
        for keyword in keywords.split():
            if re.search(rf"\b{re.escape(keyword)}\b", text):
                score += 1
        if score > best_score:
            best_name = scenario
            best_score = score
    return best_name


def build_card(entry):
    level = level_for(entry["index"])
    scenario = scenario_for(entry)
    samples = []
    if entry["polish_sample"]:
        samples.append(entry["polish_sample"])
    if entry["english_sample"]:
        samples.append(entry["english_sample"])
    note = f"Core 2000 #{entry['index']} • {level} • {scenario}"
    if samples:
        note += " • " + " / ".join(samples)
    return {
        "category": scenario,
        "scenario": scenario,
        "level": level,
        "polish": entry["polish"],
        "english": entry["english"],
        "phonetic": note,
        "source": CORE_SOURCE,
        "coreIndex": entry["index"],
        "pdfPage": entry["page"],
    }


def main():
    if len(sys.argv) != 3:
        print("Usage: import_core2000.py /path/to/Polish_CORE2000.pdf /path/to/phrases.json", file=sys.stderr)
        return 2

    pdf_path = Path(sys.argv[1]).expanduser()
    phrases_path = Path(sys.argv[2]).expanduser()
    entries = parse_pdf(pdf_path)
    missing = [index for index in range(1, 2001) if index not in {entry["index"] for entry in entries}]

    with phrases_path.open(encoding="utf-8") as handle:
        existing = json.load(handle)

    base_cards = [card for card in existing if card.get("source") != CORE_SOURCE]
    for card in base_cards:
        card.setdefault("scenario", card.get("category", "General Core"))
        card.setdefault("level", "A1")
        card.setdefault("source", "Local phrasebook")

    merged = base_cards + [build_card(entry) for entry in entries]
    with phrases_path.open("w", encoding="utf-8") as handle:
        json.dump(merged, handle, ensure_ascii=False, indent=2)
        handle.write("\n")

    print(f"Imported {len(entries)} Core 2000 entries into {phrases_path}")
    print(f"Total cards: {len(merged)}")
    if missing:
        print("PDF numbering gaps:", ", ".join(str(index) for index in missing))


if __name__ == "__main__":
    raise SystemExit(main())
