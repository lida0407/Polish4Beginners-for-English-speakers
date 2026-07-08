#!/usr/bin/env python3
"""Enrich existing single-word NOUN cards with Genitive singular + Nominative
plural, written by hand for nouns I'm confident about. Only cards whose Polish
lemma exactly matches this curated table are touched; everything else is left
alone. Format written into notes: "(gender) · G. <gen.sg> · pl. <nom.pl>".
Irregular plurals are intentional (ręka->ręce, dziecko->dzieci, ...)."""
import json, re

ASSET = "polish-phrasebook-android/app/src/main/assets/phrases.json"
DOCS  = "docs/phrases.json"

# lemma: (gender, genitive_sg, nominative_pl or None for singular-only,
#         None nom_pl means uncountable/no common plural)
NOUNS = {
 # --- feminine -a ---
 "kobieta":("f","kobiety","kobiety"), "dziewczyna":("f","dziewczyny","dziewczyny"),
 "matka":("f","matki","matki"), "córka":("f","córki","córki"), "siostra":("f","siostry","siostry"),
 "żona":("f","żony","żony"), "ciotka":("f","ciotki","ciotki"), "ciocia":("f","cioci","ciocie"),
 "kuzynka":("f","kuzynki","kuzynki"), "koleżanka":("f","koleżanki","koleżanki"),
 "przyjaciółka":("f","przyjaciółki","przyjaciółki"), "nauczycielka":("f","nauczycielki","nauczycielki"),
 "pielęgniarka":("f","pielęgniarki","pielęgniarki"), "kasjerka":("f","kasjerki","kasjerki"),
 "sekretarka":("f","sekretarki","sekretarki"), "stewardesa":("f","stewardesy","stewardesy"),
 "ręka":("f","ręki","ręce"), "noga":("f","nogi","nogi"), "głowa":("f","głowy","głowy"),
 "woda":("f","wody","wody"), "kawa":("f","kawy","kawy"), "herbata":("f","herbaty","herbaty"),
 "zupa":("f","zupy","zupy"), "ryba":("f","ryby","ryby"), "kapusta":("f","kapusty","kapusty"),
 "kasza":("f","kaszy","kasze"), "sałata":("f","sałaty","sałaty"), "malina":("f","maliny","maliny"),
 "wiśnia":("f","wiśni","wiśnie"), "róża":("f","róży","róże"), "łza":("f","łzy","łzy"),
 "książka":("f","książki","książki"), "gazeta":("f","gazety","gazety"), "kartka":("f","kartki","kartki"),
 "piosenka":("f","piosenki","piosenki"), "bajka":("f","bajki","bajki"), "szkoła":("f","szkoły","szkoły"),
 "praca":("f","pracy","prace"), "lekcja":("f","lekcji","lekcje"), "nauka":("f","nauki",None),
 "rodzina":("f","rodziny","rodziny"), "godzina":("f","godziny","godziny"), "minuta":("f","minuty","minuty"),
 "chwila":("f","chwili","chwile"), "sekunda":("f","sekundy","sekundy"), "niedziela":("f","niedzieli","niedziele"),
 "droga":("f","drogi","drogi"), "ulica":("f","ulicy","ulice"), "apteka":("f","apteki","apteki"),
 "poczta":("f","poczty","poczty"), "restauracja":("f","restauracji","restauracje"),
 "stacja":("f","stacji","stacje"), "informacja":("f","informacji","informacje"),
 "dzielnica":("f","dzielnicy","dzielnice"), "wioska":("f","wioski","wioski"), "willa":("f","willi","wille"),
 "walizka":("f","walizki","walizki"), "lodówka":("f","lodówki","lodówki"), "kanapa":("f","kanapy","kanapy"),
 "miska":("f","miski","miski"), "lalka":("f","lalki","lalki"), "gwiazda":("f","gwiazdy","gwiazdy"),
 "choroba":("f","choroby","choroby"), "grypa":("f","grypy","grypy"), "gąbka":("f","gąbki","gąbki"),
 "karta":("f","karty","karty"), "umowa":("f","umowy","umowy"), "pensja":("f","pensji","pensje"),
 "opłata":("f","opłaty","opłaty"), "wypłata":("f","wypłaty","wypłaty"), "propozycja":("f","propozycji","propozycje"),
 "sprawa":("f","sprawy","sprawy"), "rada":("f","rady","rady"), "prawda":("f","prawdy",None),
 "kobieta_":("f","kobiety","kobiety"), "chusteczka":("f","chusteczki","chusteczki"),
 "pamiątka":("f","pamiątki","pamiątki"), "góra":("f","góry","góry"), "trasa":("f","trasy","trasy"),
 "mapa":("f","mapy","mapy"), "kobyła":None,  # guard placeholder removed below
 # --- feminine soft/consonant ---
 "miłość":("f","miłości",None), "przyjemność":("f","przyjemności","przyjemności"),
 "cierpliwość":("f","cierpliwości",None), "gościnność":("f","gościnności",None),
 "trudność":("f","trudności","trudności"), "możliwość":("f","możliwości","możliwości"),
 "przyszłość":("f","przyszłości",None), "przeszłość":("f","przeszłości",None),
 "noc":("f","nocy","noce"), "rzecz":("f","rzeczy","rzeczy"), "podróż":("f","podróży","podróże"),
 "sprzedaż":("f","sprzedaży","sprzedaże"), "twarz":("f","twarzy","twarze"), "śmierć":("f","śmierci",None),
 "sól":("f","soli",None), "krew":("f","krwi",None),
 # --- masculine inanimate ---
 "dom":("m","domu","domy"), "stół":("m","stołu","stoły"), "sklep":("m","sklepu","sklepy"),
 "hotel":("m","hotelu","hotele"), "pokój":("m","pokoju","pokoje"), "kościół":("m","kościoła","kościoły"),
 "ogród":("m","ogrodu","ogrody"), "park":("m","parku","parki"), "las":("m","lasu","lasy"),
 "most":("m","mostu","mosty"), "rynek":("m","rynku","rynki"), "bank":("m","banku","banki"),
 "dworzec":("m","dworca","dworce"), "przystanek":("m","przystanku","przystanki"),
 "samochód":("m","samochodu","samochody"), "pociąg":("m","pociągu","pociągi"),
 "autobus":("m","autobusu","autobusy"), "tramwaj":("m","tramwaju","tramwaje"),
 "samolot":("m","samolotu","samoloty"), "rower":("m","roweru","rowery"), "korek":("m","korka","korki"),
 "bilet":("m","biletu","bilety"), "paszport":("m","paszportu","paszporty"), "adres":("m","adresu","adresy"),
 "list":("m","listu","listy"), "obraz":("m","obrazu","obrazy"), "film":("m","filmu","filmy"),
 "teatr":("m","teatru","teatry"), "komputer":("m","komputera","komputery"), "telefon":("m","telefonu","telefony"),
 "zegarek":("m","zegarka","zegarki"), "klucz":("m","klucza","klucze"), "nóż":("m","noża","noże"),
 "ołówek":("m","ołówka","ołówki"), "zeszyt":("m","zeszytu","zeszyty"), "słownik":("m","słownika","słowniki"),
 "podręcznik":("m","podręcznika","podręczniki"), "papieros":("m","papierosa","papierosy"),
 "chleb":("m","chleba","chleby"), "ser":("m","sera","sery"), "cukier":("m","cukru",None),
 "miód":("m","miodu",None), "sok":("m","soku","soki"), "obiad":("m","obiadu","obiady"),
 "posiłek":("m","posiłku","posiłki"), "rachunek":("m","rachunku","rachunki"), "sklep_":None,
 "kompot":("m","kompotu","kompoty"), "targ":("m","targu","targi"), "supermarket":("m","supermarketu","supermarkety"),
 "bankomat":("m","bankomatu","bankomaty"), "dokument":("m","dokumentu","dokumenty"),
 "formularz":("m","formularza","formularze"), "prezent":("m","prezentu","prezenty"),
 "pomysł":("m","pomysłu","pomysły"), "błąd":("m","błędu","błędy"), "problem":("m","problemu","problemy"),
 "temat":("m","tematu","tematy"), "sen":("m","snu","sny"), "głos":("m","głosu","głosy"),
 "ból":("m","bólu","bóle"), "katar":("m","kataru",None), "palec":("m","palca","palce"),
 "żołądek":("m","żołądka","żołądki"), "ząb":("m","zęba","zęby"), "koc":("m","koca","koce"),
 "ogień":("m","ognia",None), "wiatr":("m","wiatru","wiatry"), "śnieg":("m","śniegu",None),
 "deszcz":("m","deszczu","deszcze"), "znak":("m","znaku","znaki"), "kwiat":("m","kwiatu","kwiaty"),
 "owoc":("m","owocu","owoce"), "wyjazd":("m","wyjazdu","wyjazdy"), "pobyt":("m","pobytu","pobyty"),
 "grant":("m","grantu","granty"), "dochód":("m","dochodu","dochody"), "przelew":("m","przelewu","przelewy"),
 "mandat":("m","mandatu","mandaty"), "kilometr":("m","kilometra","kilometry"),
 # --- masculine personal/animate ---
 "pan":("m","pana","panowie"), "syn":("m","syna","synowie"), "brat":("m","brata","bracia"),
 "ojciec":("m","ojca","ojcowie"), "mąż":("m","męża","mężowie"), "dziadek":("m","dziadka","dziadkowie"),
 "wujek":("m","wujka","wujkowie"), "kuzyn":("m","kuzyna","kuzyni"), "chłopiec":("m","chłopca","chłopcy"),
 "człowiek":("m","człowieka","ludzie"), "przyjaciel":("m","przyjaciela","przyjaciele"),
 "kolega":("m","kolegi","koledzy"), "sąsiad":("m","sąsiada","sąsiedzi"), "gość":("m","gościa","goście"),
 "lekarz":("m","lekarza","lekarze"), "nauczyciel":("m","nauczyciela","nauczyciele"),
 "student":("m","studenta","studenci"), "uczeń":("m","ucznia","uczniowie"), "kelner":("m","kelnera","kelnerzy"),
 "kierowca":("m","kierowcy","kierowcy"), "policjant":("m","policjanta","policjanci"),
 "turysta":("m","turysty","turyści"), "sprzedawca":("m","sprzedawcy","sprzedawcy"),
 "autor":("m","autora","autorzy"), "lektor":("m","lektora","lektorzy"), "dyrektor":("m","dyrektora","dyrektorzy"),
 "profesor":("m","profesora","profesorowie"), "klient":("m","klienta","klienci"),
 "kot":("m","kota","koty"), "pies":("m","psa","psy"), "ptak":("m","ptaka","ptaki"),
 "koń":("m","konia","konie"), "niedźwiedź":("m","niedźwiedzia","niedźwiedzie"), "kangur":("m","kangura","kangury"),
 # --- neuter ---
 "okno":("n","okna","okna"), "krzesło":("n","krzesła","krzesła"), "łóżko":("n","łóżka","łóżka"),
 "biurko":("n","biurka","biurka"), "miasto":("n","miasta","miasta"), "jezioro":("n","jeziora","jeziora"),
 "drzewo":("n","drzewa","drzewa"), "morze":("n","morza","morza"), "pole":("n","pola","pola"),
 "jabłko":("n","jabłka","jabłka"), "jajko":("n","jajka","jajka"), "mięso":("n","mięsa",None),
 "masło":("n","masła",None), "mleko":("n","mleka",None), "dziecko":("n","dziecka","dzieci"),
 "mieszkanie":("n","mieszkania","mieszkania"), "śniadanie":("n","śniadania","śniadania"),
 "zdjęcie":("n","zdjęcia","zdjęcia"), "imię":("n","imienia","imiona"), "oko":("n","oka","oczy"),
 "ucho":("n","ucha","uszy"), "serce":("n","serca","serca"), "słowo":("n","słowa","słowa"),
 "zwierzę":("n","zwierzęcia","zwierzęta"), "miejsce":("n","miejsca","miejsca"),
 "święto":("n","święta","święta"), "wydarzenie":("n","wydarzenia","wydarzenia"),
 "spotkanie":("n","spotkania","spotkania"), "zdanie":("n","zdania","zdania"),
 "pytanie":("n","pytania","pytania"), "życzenie":("n","życzenia","życzenia"),
 "ćwiczenie":("n","ćwiczenia","ćwiczenia"), "zebranie":("n","zebrania","zebrania"),
 # --- plurale tantum / suppletive number ---
 "drzwi":("pl","drzwi",None), "pieniądze":("pl","pieniędzy",None), "urodziny":("pl","urodzin",None),
 "wakacje":("pl","wakacji",None), "imieniny":("pl","imienin",None), "rodzice":("pl","rodziców",None),
 "ludzie":("pl","ludzi",None), "drzwi_":None,
 "rok":("m","roku","lata"), "dzień":("m","dnia","dni"), "tydzień":("m","tygodnia","tygodnie"),
 "miesiąc":("m","miesiąca","miesiące"),
}
# drop guard placeholders
NOUNS = {k:v for k,v in NOUNS.items() if v is not None and not k.endswith("_")}

def fmt(g, gen, pl):
    gl = {"f":"f.","m":"m.","n":"n.","pl":"pl."}[g]
    parts = [f"({gl})", f"G. {gen}"]
    if pl: parts.append(f"pl. {pl}")
    elif g != "pl": parts.append("no pl.")
    return " · ".join(parts)

data = json.load(open(ASSET, encoding="utf-8"))
matched, missing = 0, []
by_lemma = {}
for e in data:
    by_lemma.setdefault(e["polish"].strip().lower(), []).append(e)

for lemma,(g,gen,pl) in NOUNS.items():
    hits = by_lemma.get(lemma.lower())
    if not hits:
        missing.append(lemma); continue
    for e in hits:
        e["notes"] = fmt(g, gen, pl)
        matched += 1

for path in (ASSET, DOCS):
    with open(path,"w",encoding="utf-8") as f:
        json.dump(data,f,ensure_ascii=False,indent=2); f.write("\n")

print(f"curated nouns: {len(NOUNS)}")
print(f"cards updated: {matched}")
print(f"lemmas not found in deck ({len(missing)}): {', '.join(sorted(missing))}")
