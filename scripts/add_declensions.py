#!/usr/bin/env python3
"""Attach full case declension tables to common noun cards.
Each entry: lemma -> (gender, [sgN,sgG,sgD,sgA,sgI,sgL,sgV, plN,plG,plD,plA,plI,plL,plV]).
Forms are hand-written and verified; only cards whose Polish lemma matches
are touched. The table is stored preformatted in a `declension` field."""
import json, re

REPO = "/Users/dali/Sensaka/Polish4Beginners"
ASSET = f"{REPO}/polish-phrasebook-android/app/src/main/assets/phrases.json"
DOCS = f"{REPO}/docs/phrases.json"

CASES = ["N", "G", "D", "A", "I", "L", "V"]

# gender, 14 forms
N = {
 # ---- masculine ----
 "dom":("m","dom","domu","domowi","dom","domem","domu","domu","domy","domów","domom","domy","domami","domach","domy"),
 "stół":("m","stół","stołu","stołowi","stół","stołem","stole","stole","stoły","stołów","stołom","stoły","stołami","stołach","stoły"),
 "sklep":("m","sklep","sklepu","sklepowi","sklep","sklepem","sklepie","sklepie","sklepy","sklepów","sklepom","sklepy","sklepami","sklepach","sklepy"),
 "samochód":("m","samochód","samochodu","samochodowi","samochód","samochodem","samochodzie","samochodzie","samochody","samochodów","samochodom","samochody","samochodami","samochodach","samochody"),
 "pociąg":("m","pociąg","pociągu","pociągowi","pociąg","pociągiem","pociągu","pociągu","pociągi","pociągów","pociągom","pociągi","pociągami","pociągach","pociągi"),
 "autobus":("m","autobus","autobusu","autobusowi","autobus","autobusem","autobusie","autobusie","autobusy","autobusów","autobusom","autobusy","autobusami","autobusach","autobusy"),
 "telefon":("m","telefon","telefonu","telefonowi","telefon","telefonem","telefonie","telefonie","telefony","telefonów","telefonom","telefony","telefonami","telefonach","telefony"),
 "komputer":("m","komputer","komputera","komputerowi","komputer","komputerem","komputerze","komputerze","komputery","komputerów","komputerom","komputery","komputerami","komputerach","komputery"),
 "klucz":("m","klucz","klucza","kluczowi","klucz","kluczem","kluczu","kluczu","klucze","kluczy","kluczom","klucze","kluczami","kluczach","klucze"),
 "chleb":("m","chleb","chleba","chlebowi","chleb","chlebem","chlebie","chlebie","chleby","chlebów","chlebom","chleby","chlebami","chlebach","chleby"),
 "ser":("m","ser","sera","serowi","ser","serem","serze","serze","sery","serów","serom","sery","serami","serach","sery"),
 "kraj":("m","kraj","kraju","krajowi","kraj","krajem","kraju","kraju","kraje","krajów","krajom","kraje","krajami","krajach","kraje"),
 "ogród":("m","ogród","ogrodu","ogrodowi","ogród","ogrodem","ogrodzie","ogrodzie","ogrody","ogrodów","ogrodom","ogrody","ogrodami","ogrodach","ogrody"),
 "rok":("m","rok","roku","rokowi","rok","rokiem","roku","roku","lata","lat","latom","lata","latami","latach","lata"),
 "dzień":("m","dzień","dnia","dniowi","dzień","dniem","dniu","dniu","dni","dni","dniom","dni","dniami","dniach","dni"),
 # masculine personal / animate (A sg = G sg; masc-personal A pl = G pl)
 "pan":("m","pan","pana","panu","pana","panem","panu","panie","panowie","panów","panom","panów","panami","panach","panowie"),
 "syn":("m","syn","syna","synowi","syna","synem","synu","synu","synowie","synów","synom","synów","synami","synach","synowie"),
 "brat":("m","brat","brata","bratu","brata","bratem","bracie","bracie","bracia","braci","braciom","braci","braćmi","braciach","bracia"),
 "ojciec":("m","ojciec","ojca","ojcu","ojca","ojcem","ojcu","ojcze","ojcowie","ojców","ojcom","ojców","ojcami","ojcach","ojcowie"),
 "mąż":("m","mąż","męża","mężowi","męża","mężem","mężu","mężu","mężowie","mężów","mężom","mężów","mężami","mężach","mężowie"),
 "student":("m","student","studenta","studentowi","studenta","studentem","studencie","studencie","studenci","studentów","studentom","studentów","studentami","studentach","studenci"),
 "nauczyciel":("m","nauczyciel","nauczyciela","nauczycielowi","nauczyciela","nauczycielem","nauczycielu","nauczycielu","nauczyciele","nauczycieli","nauczycielom","nauczycieli","nauczycielami","nauczycielach","nauczyciele"),
 "lekarz":("m","lekarz","lekarza","lekarzowi","lekarza","lekarzem","lekarzu","lekarzu","lekarze","lekarzy","lekarzom","lekarzy","lekarzami","lekarzach","lekarze"),
 "przyjaciel":("m","przyjaciel","przyjaciela","przyjacielowi","przyjaciela","przyjacielem","przyjacielu","przyjacielu","przyjaciele","przyjaciół","przyjaciołom","przyjaciół","przyjaciółmi","przyjaciołach","przyjaciele"),
 "kot":("m","kot","kota","kotu","kota","kotem","kocie","kocie","koty","kotów","kotom","koty","kotami","kotach","koty"),
 "pies":("m","pies","psa","psu","psa","psem","psie","psie","psy","psów","psom","psy","psami","psach","psy"),
 "ptak":("m","ptak","ptaka","ptakowi","ptaka","ptakiem","ptaku","ptaku","ptaki","ptaków","ptakom","ptaki","ptakami","ptakach","ptaki"),
 "koń":("m","koń","konia","koniowi","konia","koniem","koniu","koniu","konie","koni","koniom","konie","końmi","koniach","konie"),
 # ---- feminine ----
 "kobieta":("f","kobieta","kobiety","kobiecie","kobietę","kobietą","kobiecie","kobieto","kobiety","kobiet","kobietom","kobiety","kobietami","kobietach","kobiety"),
 "dziewczyna":("f","dziewczyna","dziewczyny","dziewczynie","dziewczynę","dziewczyną","dziewczynie","dziewczyno","dziewczyny","dziewczyn","dziewczynom","dziewczyny","dziewczynami","dziewczynach","dziewczyny"),
 "matka":("f","matka","matki","matce","matkę","matką","matce","matko","matki","matek","matkom","matki","matkami","matkach","matki"),
 "siostra":("f","siostra","siostry","siostrze","siostrę","siostrą","siostrze","siostro","siostry","sióstr","siostrom","siostry","siostrami","siostrach","siostry"),
 "żona":("f","żona","żony","żonie","żonę","żoną","żonie","żono","żony","żon","żonom","żony","żonami","żonach","żony"),
 "córka":("f","córka","córki","córce","córkę","córką","córce","córko","córki","córek","córkom","córki","córkami","córkach","córki"),
 "ręka":("f","ręka","ręki","ręce","rękę","ręką","ręce","ręko","ręce","rąk","rękom","ręce","rękami","rękach","ręce"),
 "noga":("f","noga","nogi","nodze","nogę","nogą","nodze","nogo","nogi","nóg","nogom","nogi","nogami","nogach","nogi"),
 "głowa":("f","głowa","głowy","głowie","głowę","głową","głowie","głowo","głowy","głów","głowom","głowy","głowami","głowach","głowy"),
 "woda":("f","woda","wody","wodzie","wodę","wodą","wodzie","wodo","wody","wód","wodom","wody","wodami","wodach","wody"),
 "kawa":("f","kawa","kawy","kawie","kawę","kawą","kawie","kawo","kawy","kaw","kawom","kawy","kawami","kawach","kawy"),
 "herbata":("f","herbata","herbaty","herbacie","herbatę","herbatą","herbacie","herbato","herbaty","herbat","herbatom","herbaty","herbatami","herbatach","herbaty"),
 "książka":("f","książka","książki","książce","książkę","książką","książce","książko","książki","książek","książkom","książki","książkami","książkach","książki"),
 "szkoła":("f","szkoła","szkoły","szkole","szkołę","szkołą","szkole","szkoło","szkoły","szkół","szkołom","szkoły","szkołami","szkołach","szkoły"),
 "praca":("f","praca","pracy","pracy","pracę","pracą","pracy","praco","prace","prac","pracom","prace","pracami","pracach","prace"),
 "rodzina":("f","rodzina","rodziny","rodzinie","rodzinę","rodziną","rodzinie","rodzino","rodziny","rodzin","rodzinom","rodziny","rodzinami","rodzinach","rodziny"),
 "godzina":("f","godzina","godziny","godzinie","godzinę","godziną","godzinie","godzino","godziny","godzin","godzinom","godziny","godzinami","godzinach","godziny"),
 "ulica":("f","ulica","ulicy","ulicy","ulicę","ulicą","ulicy","ulico","ulice","ulic","ulicom","ulice","ulicami","ulicach","ulice"),
 "droga":("f","droga","drogi","drodze","drogę","drogą","drodze","drogo","drogi","dróg","drogom","drogi","drogami","drogach","drogi"),
 "noc":("f","noc","nocy","nocy","noc","nocą","nocy","nocy","noce","nocy","nocom","noce","nocami","nocach","noce"),
 "córka ":None,
 # ---- neuter ----
 "okno":("n","okno","okna","oknu","okno","oknem","oknie","okno","okna","okien","oknom","okna","oknami","oknach","okna"),
 "krzesło":("n","krzesło","krzesła","krzesłu","krzesło","krzesłem","krześle","krzesło","krzesła","krzeseł","krzesłom","krzesła","krzesłami","krzesłach","krzesła"),
 "łóżko":("n","łóżko","łóżka","łóżku","łóżko","łóżkiem","łóżku","łóżko","łóżka","łóżek","łóżkom","łóżka","łóżkami","łóżkach","łóżka"),
 "miasto":("n","miasto","miasta","miastu","miasto","miastem","mieście","miasto","miasta","miast","miastom","miasta","miastami","miastach","miasta"),
 "dziecko":("n","dziecko","dziecka","dziecku","dziecko","dzieckiem","dziecku","dziecko","dzieci","dzieci","dzieciom","dzieci","dziećmi","dzieciach","dzieci"),
 "jezioro":("n","jezioro","jeziora","jezioru","jezioro","jeziorem","jeziorze","jezioro","jeziora","jezior","jeziorom","jeziora","jeziorami","jeziorach","jeziora"),
 "drzewo":("n","drzewo","drzewa","drzewu","drzewo","drzewem","drzewie","drzewo","drzewa","drzew","drzewom","drzewa","drzewami","drzewach","drzewa"),
 "mieszkanie":("n","mieszkanie","mieszkania","mieszkaniu","mieszkanie","mieszkaniem","mieszkaniu","mieszkanie","mieszkania","mieszkań","mieszkaniom","mieszkania","mieszkaniami","mieszkaniach","mieszkania"),
 "śniadanie":("n","śniadanie","śniadania","śniadaniu","śniadanie","śniadaniem","śniadaniu","śniadanie","śniadania","śniadań","śniadaniom","śniadania","śniadaniami","śniadaniach","śniadania"),
 "imię":("n","imię","imienia","imieniu","imię","imieniem","imieniu","imię","imiona","imion","imionom","imiona","imionami","imionach","imiona"),
 "oko":("n","oko","oka","oku","oko","okiem","oku","oko","oczy","oczu","oczom","oczy","oczami","oczach","oczy"),
 "serce":("n","serce","serca","sercu","serce","sercem","sercu","serce","serca","serc","sercom","serca","sercami","sercach","serca"),
 "słowo":("n","słowo","słowa","słowu","słowo","słowem","słowie","słowo","słowa","słów","słowom","słowa","słowami","słowach","słowa"),
 "morze":("n","morze","morza","morzu","morze","morzem","morzu","morze","morza","mórz","morzom","morza","morzami","morzach","morza"),
 "pole":("n","pole","pola","polu","pole","polem","polu","pole","pola","pól","polom","pola","polami","polach","pola"),
 "jabłko":("n","jabłko","jabłka","jabłku","jabłko","jabłkiem","jabłku","jabłko","jabłka","jabłek","jabłkom","jabłka","jabłkami","jabłkach","jabłka"),
 # ---- batch 2: more masculine ----
 "sok":("m","sok","soku","sokowi","sok","sokiem","soku","soku","soki","soków","sokom","soki","sokami","sokach","soki"),
 "obiad":("m","obiad","obiadu","obiadowi","obiad","obiadem","obiedzie","obiedzie","obiady","obiadów","obiadom","obiady","obiadami","obiadach","obiady"),
 "rachunek":("m","rachunek","rachunku","rachunkowi","rachunek","rachunkiem","rachunku","rachunku","rachunki","rachunków","rachunkom","rachunki","rachunkami","rachunkach","rachunki"),
 "bilet":("m","bilet","biletu","biletowi","bilet","biletem","bilecie","bilecie","bilety","biletów","biletom","bilety","biletami","biletach","bilety"),
 "pokój":("m","pokój","pokoju","pokojowi","pokój","pokojem","pokoju","pokoju","pokoje","pokoi","pokojom","pokoje","pokojami","pokojach","pokoje"),
 "hotel":("m","hotel","hotelu","hotelowi","hotel","hotelem","hotelu","hotelu","hotele","hoteli","hotelom","hotele","hotelami","hotelach","hotele"),
 "park":("m","park","parku","parkowi","park","parkiem","parku","parku","parki","parków","parkom","parki","parkami","parkach","parki"),
 "las":("m","las","lasu","lasowi","las","lasem","lesie","lesie","lasy","lasów","lasom","lasy","lasami","lasach","lasy"),
 "most":("m","most","mostu","mostowi","most","mostem","moście","moście","mosty","mostów","mostom","mosty","mostami","mostach","mosty"),
 "świat":("m","świat","świata","światu","świat","światem","świecie","świecie","światy","światów","światom","światy","światami","światach","światy"),
 "kwiat":("m","kwiat","kwiatu","kwiatowi","kwiat","kwiatem","kwiecie","kwiecie","kwiaty","kwiatów","kwiatom","kwiaty","kwiatami","kwiatach","kwiaty"),
 "człowiek":("m","człowiek","człowieka","człowiekowi","człowieka","człowiekiem","człowieku","człowieku","ludzie","ludzi","ludziom","ludzi","ludźmi","ludziach","ludzie"),
 "sąsiad":("m","sąsiad","sąsiada","sąsiadowi","sąsiada","sąsiadem","sąsiedzie","sąsiedzie","sąsiedzi","sąsiadów","sąsiadom","sąsiadów","sąsiadami","sąsiadach","sąsiedzi"),
 "gość":("m","gość","gościa","gościowi","gościa","gościem","gościu","gościu","goście","gości","gościom","gości","gośćmi","gościach","goście"),
 # ---- batch 2: more feminine ----
 "ryba":("f","ryba","ryby","rybie","rybę","rybą","rybie","rybo","ryby","ryb","rybom","ryby","rybami","rybach","ryby"),
 "zupa":("f","zupa","zupy","zupie","zupę","zupą","zupie","zupo","zupy","zup","zupom","zupy","zupami","zupach","zupy"),
 "gazeta":("f","gazeta","gazety","gazecie","gazetę","gazetą","gazecie","gazeto","gazety","gazet","gazetom","gazety","gazetami","gazetach","gazety"),
 "lampa":("f","lampa","lampy","lampie","lampę","lampą","lampie","lampo","lampy","lamp","lampom","lampy","lampami","lampach","lampy"),
 "mapa":("f","mapa","mapy","mapie","mapę","mapą","mapie","mapo","mapy","map","mapom","mapy","mapami","mapach","mapy"),
 "apteka":("f","apteka","apteki","aptece","aptekę","apteką","aptece","apteko","apteki","aptek","aptekom","apteki","aptekami","aptekach","apteki"),
 "poczta":("f","poczta","poczty","poczcie","pocztę","pocztą","poczcie","poczto","poczty","poczt","pocztom","poczty","pocztami","pocztach","poczty"),
 "restauracja":("f","restauracja","restauracji","restauracji","restaurację","restauracją","restauracji","restauracjo","restauracje","restauracji","restauracjom","restauracje","restauracjami","restauracjach","restauracje"),
 "stacja":("f","stacja","stacji","stacji","stację","stacją","stacji","stacjo","stacje","stacji","stacjom","stacje","stacjami","stacjach","stacje"),
 "lekcja":("f","lekcja","lekcji","lekcji","lekcję","lekcją","lekcji","lekcjo","lekcje","lekcji","lekcjom","lekcje","lekcjami","lekcjach","lekcje"),
 "rzeka":("f","rzeka","rzeki","rzece","rzekę","rzeką","rzece","rzeko","rzeki","rzek","rzekom","rzeki","rzekami","rzekach","rzeki"),
 "chwila":("f","chwila","chwili","chwili","chwilę","chwilą","chwili","chwilo","chwile","chwil","chwilom","chwile","chwilami","chwilach","chwile"),
 "minuta":("f","minuta","minuty","minucie","minutę","minutą","minucie","minuto","minuty","minut","minutom","minuty","minutami","minutach","minuty"),
 "twarz":("f","twarz","twarzy","twarzy","twarz","twarzą","twarzy","twarzy","twarze","twarzy","twarzom","twarze","twarzami","twarzach","twarze"),
 "rzecz":("f","rzecz","rzeczy","rzeczy","rzecz","rzeczą","rzeczy","rzeczy","rzeczy","rzeczy","rzeczom","rzeczy","rzeczami","rzeczach","rzeczy"),
 # ---- batch 2: more neuter ----
 "jajko":("n","jajko","jajka","jajku","jajko","jajkiem","jajku","jajko","jajka","jajek","jajkom","jajka","jajkami","jajkach","jajka"),
 "lato":("n","lato","lata","latu","lato","latem","lecie","lato","lata","lat","latom","lata","latami","latach","lata"),
 "zwierzę":("n","zwierzę","zwierzęcia","zwierzęciu","zwierzę","zwierzęciem","zwierzęciu","zwierzę","zwierzęta","zwierząt","zwierzętom","zwierzęta","zwierzętami","zwierzętach","zwierzęta"),
 "miejsce":("n","miejsce","miejsca","miejscu","miejsce","miejscem","miejscu","miejsce","miejsca","miejsc","miejscom","miejsca","miejscami","miejscach","miejsca"),
 "zdanie":("n","zdanie","zdania","zdaniu","zdanie","zdaniem","zdaniu","zdanie","zdania","zdań","zdaniom","zdania","zdaniami","zdaniach","zdania"),
 "pytanie":("n","pytanie","pytania","pytaniu","pytanie","pytaniem","pytaniu","pytanie","pytania","pytań","pytaniom","pytania","pytaniami","pytaniach","pytania"),
 "spotkanie":("n","spotkanie","spotkania","spotkaniu","spotkanie","spotkaniem","spotkaniu","spotkanie","spotkania","spotkań","spotkaniom","spotkania","spotkaniami","spotkaniach","spotkania"),
}
N = {k.strip(): v for k, v in N.items() if v is not None}

GENDER = {"m": "masculine (m.)", "f": "feminine (f.)", "n": "neuter (n.)"}

def build_table(g, forms):
    sg = list(forms[0:7])
    pl = list(forms[7:14])
    w = max(len(x) for x in sg + ["sg"])
    lines = ["Declension · " + GENDER[g], "     " + "sg".ljust(w + 2) + "pl"]
    for i, c in enumerate(CASES):
        lines.append(f"{c}    " + sg[i].ljust(w + 2) + pl[i])
    return "\n".join(lines)

data = json.load(open(ASSET, encoding="utf-8"))
by_lemma = {}
for e in data:
    by_lemma.setdefault(e["polish"].strip().lower(), []).append(e)

applied, missing = 0, []
for lemma, forms in N.items():
    g = forms[0]
    table = build_table(g, forms[1:])
    hits = by_lemma.get(lemma.lower())
    if not hits:
        missing.append(lemma)
        continue
    for e in hits:
        e["declension"] = table
        applied += 1

body = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
for path in (ASSET, DOCS):
    open(path, "w", encoding="utf-8").write(body)

print(f"nouns in table: {len(N)}")
print(f"cards given a declension: {applied}")
print(f"lemmas not in deck ({len(missing)}): {', '.join(sorted(missing))}")
print("\n--- sample render ---")
print(build_table(*(N['dom'][0], N['dom'][1:])))
print()
print(build_table(*(N['książka'][0], N['książka'][1:])))
