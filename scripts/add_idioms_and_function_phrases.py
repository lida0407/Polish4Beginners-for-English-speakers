#!/usr/bin/env python3
"""Two additions:
 (1) 21 standard, dictionary-level Polish idioms (fixed expressions that exist
     in the language independently of any source) — vetted by hand.
 (2) ~55 ORIGINAL B1/B2 communicative-function phrases, composed here (not
     copied from any list): opinion, agreement, requests, suggestions,
     certainty/doubt, structuring, preference, recommending, surprise,
     contrast, clarifying. Grouped under a new 'Conversation & Opinions'
     category (categories are data-driven, so it appears as a browse filter
     automatically)."""
import json, re, hashlib

ASSET="polish-phrasebook-android/app/src/main/assets/phrases.json"
DOCS="docs/phrases.json"; DB="docs/database.json"

IDIOMS = [
 ("być niespokojnym duchem","to be a restless spirit","B1","Feelings & Qualities"),
 ("flaki z olejem","dull as dishwater; deadly boring","B1","Feelings & Qualities"),
 ("rozwijać skrzydła","to spread one's wings; grow into one's potential","B1","Feelings & Qualities"),
 ("bić rekordy popularności","to break popularity records; be a huge hit","B1","General Core"),
 ("mól książkowy","a bookworm","B1","Feelings & Qualities"),
 ("bułka z masłem","a piece of cake; dead easy","B1","Feelings & Qualities"),
 ("trzymać rękę na pulsie","to keep a finger on the pulse; stay on top of things","B1","General Core"),
 ("bez dwóch zdań","without a doubt; no question about it","B1","Grammar & Function Words"),
 ("tracić głowę dla kogoś","to lose one's head over someone","B1","Feelings & Qualities"),
 ("mieć złamane serce","to be heartbroken","B1","Feelings & Qualities"),
 ("wyprowadzić kogoś z równowagi","to throw someone off balance; unsettle someone","B1","Feelings & Qualities"),
 ("strach ma wielkie oczy","fear makes things look worse than they are (proverb)","B1","Feelings & Qualities"),
 ("czerwony jak burak","red as a beet (from embarrassment)","B1","Feelings & Qualities"),
 ("skakać z radości","to jump for joy","B1","Feelings & Qualities"),
 ("mieć motyle w brzuchu","to have butterflies in one's stomach","B1","Feelings & Qualities"),
 ("stawać na rzęsach","to bend over backwards; go all out","B1","Feelings & Qualities"),
 ("być oczkiem w głowie","to be the apple of someone's eye","B1","Feelings & Qualities"),
 ("zapierać dech w piersiach","to take one's breath away","B2","Feelings & Qualities"),
 ("być jak z bajki","to be like something out of a fairy tale","B2","Feelings & Qualities"),
 ("odłożyć coś do lamusa","to consign something to the past; shelve for good","B2","General Core"),
 ("mieć węża w kieszeni","to be stingy; be tight-fisted","B1","Feelings & Qualities"),
]

CONV = "Conversation & Opinions"
# (polish, english, level, note)  -- original complete phrases
FUNC = [
 # opinion
 ("Uważam, że to bardzo dobry pomysł.","I think it's a very good idea.","B1",""),
 ("Według mnie warto spróbować.","In my view it's worth a try.","B1",""),
 ("Wydaje mi się, że masz rację.","It seems to me you're right.","B1",""),
 ("Z mojego punktu widzenia to nie jest konieczne.","From my point of view it's not necessary.","B2",""),
 ("Osobiście wolę spokojniejsze rozwiązanie.","Personally, I prefer a calmer solution.","B2",""),
 # agreeing
 ("Całkowicie się z tobą zgadzam.","I completely agree with you.","B1",""),
 ("Masz zupełną rację.","You're absolutely right.","B1",""),
 ("Też tak myślę.","I think so too.","B1",""),
 ("Dokładnie o to mi chodziło.","That's exactly what I meant.","B1",""),
 # disagreeing
 ("Nie do końca się z tym zgadzam.","I don't entirely agree with that.","B1",""),
 ("Obawiam się, że muszę się nie zgodzić.","I'm afraid I have to disagree.","B2",""),
 ("Rozumiem twój punkt widzenia, ale mam inne zdanie.","I see your point, but I have a different opinion.","B2",""),
 ("Z jednej strony tak, z drugiej jednak nie.","On one hand yes, but on the other, no.","B2",""),
 # requests
 ("Czy mógłbyś mi pomóc z tym zadaniem?","Could you help me with this task?","B1","to a male, informal"),
 ("Czy mogłabym prosić o chwilę uwagi?","May I ask for a moment of your attention?","B1","female speaker"),
 ("Byłbym wdzięczny za szybką odpowiedź.","I'd be grateful for a quick reply.","B2","male speaker; f. Byłabym"),
 ("Czy dałoby się to załatwić dzisiaj?","Would it be possible to sort this out today?","B2",""),
 # suggestions
 ("Może pójdziemy dzisiaj do kina?","Maybe we could go to the cinema today?","B1",""),
 ("Proponuję, żebyśmy zaczęli od początku.","I suggest we start from the beginning.","B1",""),
 ("A gdybyśmy spróbowali inaczej?","What if we tried a different way?","B2",""),
 ("Co powiesz na krótką przerwę?","What do you say to a short break?","B1",""),
 # certainty
 ("Jestem pewien, że wszystko się uda.","I'm sure everything will work out.","B1","male speaker; f. pewna"),
 ("Nie mam co do tego żadnych wątpliwości.","I have no doubts about it whatsoever.","B2",""),
 ("Bez wątpienia to najlepsze wyjście.","Without a doubt, it's the best option.","B2",""),
 # doubt
 ("Nie jestem pewna, czy to dobry pomysł.","I'm not sure if it's a good idea.","B1","female speaker; m. pewien"),
 ("Trudno powiedzieć, jak to się skończy.","It's hard to say how it'll turn out.","B1",""),
 ("Mam pewne wątpliwości co do tego planu.","I have some doubts about this plan.","B2",""),
 # structuring a monologue
 ("Zacznę od najważniejszej sprawy.","I'll start with the most important thing.","B1",""),
 ("Przede wszystkim chcę podkreślić jedno.","Above all, I want to emphasize one thing.","B2",""),
 ("Przejdźmy teraz do kolejnego punktu.","Let's move on to the next point now.","B2",""),
 ("Na koniec chcę coś dodać.","Finally, I'd like to add something.","B1",""),
 ("Krótko mówiąc, warto to przemyśleć.","In short, it's worth thinking over.","B2",""),
 # preference
 ("Wolę herbatę niż kawę.","I prefer tea to coffee.","B1",""),
 ("Najbardziej odpowiada mi poranny termin.","A morning slot suits me best.","B2",""),
 ("Gdybym miał wybór, zostałbym w domu.","If I had a choice, I'd stay home.","B2","male speaker"),
 # recommending
 ("Gorąco polecam tę książkę.","I highly recommend this book.","B1",""),
 ("Naprawdę warto tam pojechać.","It's really worth going there.","B1",""),
 ("Szczerze odradzam ten pomysł.","I honestly advise against this idea.","B2",""),
 # surprise
 ("Nie mogę w to uwierzyć!","I can't believe it!","B1",""),
 ("To naprawdę niesamowite!","That's really amazing!","B1",""),
 ("Zupełnie mnie to zaskoczyło.","It took me completely by surprise.","B2",""),
 # contrast / it depends
 ("W porównaniu z zeszłym rokiem jest lepiej.","Compared with last year, it's better.","B2",""),
 ("To zależy od sytuacji.","It depends on the situation.","B1",""),
 ("Wręcz przeciwnie, bardzo mi się to podoba.","On the contrary, I like it a lot.","B2",""),
 # clarifying
 ("Przepraszam, nie dosłyszałem. Czy może pan powtórzyć?","Sorry, I didn't catch that. Could you repeat it?","B1","male speaker, formal address"),
 ("Co dokładnie ma pan na myśli?","What exactly do you mean?","B2","formal address"),
 ("Czy dobrze rozumiem, że się zgadzasz?","Do I understand correctly that you agree?","B1",""),
 # giving examples
 ("Na przykład możemy zacząć od jutra.","For example, we could start tomorrow.","B1",""),
 ("Weźmy chociażby ostatni tydzień.","Take last week, for instance.","B2",""),
 # softening / hedging
 ("Szczerze mówiąc, nie mam nic przeciwko.","Honestly speaking, I have nothing against it.","B1",""),
 ("O ile dobrze pamiętam, było inaczej.","As far as I remember, it was different.","B2",""),
 ("Muszę się nad tym zastanowić.","I need to think it over.","B1",""),
]

def norm(s):
    s=(s or '').strip().lower(); s=re.sub(r'[.!?,;:]+$','',s); return re.sub(r'\s+',' ',s)

deck=json.load(open(ASSET,encoding='utf-8'))
keys={norm(x['polish']) for x in deck}
added=[]; skipped=0
def push(pol,eng,lvl,cat,note):
    global skipped
    if norm(pol) in keys: skipped+=1; return
    keys.add(norm(pol))
    e={"category":cat,"scenario":cat,"level":lvl,"polish":pol,"english":eng,"phonetic":""}
    if note: e["notes"]=note
    added.append(e)

for pol,eng,lvl,cat in IDIOMS: push(pol,eng,lvl,cat,"(idiom)")
for pol,eng,lvl,note in FUNC:  push(pol,eng,lvl,CONV,note)

deck2=deck+added
for path in (ASSET,DOCS):
    with open(path,"w",encoding="utf-8") as f:
        json.dump(deck2,f,ensure_ascii=False,indent=2); f.write("\n")

raw=open(DOCS,'rb').read()
db=json.load(open(DB)); db['dataVersion']+=1
db['phrasesSha256']=hashlib.sha256(raw).hexdigest(); db['phrasesSizeBytes']=len(raw)
db['phraseCount']=len(deck2)
db['releaseNotes']=f"Adds {sum(1 for _ in IDIOMS)} common Polish idioms and {len(FUNC)} original B1/B2 conversation phrases (opinions, requests, agreeing)."
json.dump(db,open(DB,'w'),ensure_ascii=False,indent=2); open(DB,'a').write('\n')

print(f"idioms defined: {len(IDIOMS)} | function phrases defined: {len(FUNC)}")
print(f"added: {len(added)} | skipped (already in deck): {skipped}")
print(f"deck now: {len(deck2)} | dataVersion {db['dataVersion']} sha {db['phrasesSha256'][:12]}")
