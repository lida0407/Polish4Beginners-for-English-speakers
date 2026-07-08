from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSET_PHRASES = ROOT / "polish-phrasebook-android/app/src/main/assets/phrases.json"
DOCS_PHRASES = ROOT / "docs/phrases.json"
DATABASE = ROOT / "docs/database.json"


def normalize(value: str) -> str:
    value = value.casefold()
    value = re.sub(r"[^\wąćęłńóśźż]+", " ", value, flags=re.IGNORECASE)
    return re.sub(r"\s+", " ", value).strip()


def card(level: str, category: str, scenario: str, polish: str, english: str,
         example_pl: str, example_en: str, *tags: str) -> dict:
    return {
        "category": category,
        "scenario": scenario,
        "level": level,
        "polish": polish,
        "english": english,
        "phonetic": "",
        "examplePolish": example_pl,
        "exampleEnglish": example_en,
        "tags": list(tags),
        "notes": "Original B1/B2 speaking-practice card; not copied from a workbook.",
    }


NEW_CARDS = [
    card("B1", "People & Family", "Biography", "życiorys", "life story / CV",
         "Proszę krótko opowiedzieć swój życiorys.", "Please briefly tell your life story.", "biography", "people"),
    card("B1", "People & Family", "Biography", "osiągnięcie", "achievement",
         "Największym osiągnięciem Marii było odkrycie radu.", "Maria's greatest achievement was discovering radium.", "biography", "success"),
    card("B1", "People & Family", "Biography", "wybitna osoba", "an outstanding person",
         "Chciałbym opowiedzieć o wybitnej osobie z mojego kraju.", "I would like to talk about an outstanding person from my country.", "biography", "description"),
    card("B1", "People & Family", "Biography", "zasłynąć z czegoś", "to become famous for something",
         "Ten lekarz zasłynął z trudnych operacji.", "This doctor became famous for difficult operations.", "biography", "verb"),
    card("B1", "People & Family", "Biography", "otrzymać nagrodę", "to receive an award",
         "Pisarka otrzymała nagrodę za najnowszą powieść.", "The writer received an award for her newest novel.", "biography", "success"),
    card("B1", "Feelings & Qualities", "Describing People", "godny podziwu", "admirable",
         "Jej odwaga jest naprawdę godna podziwu.", "Her courage is truly admirable.", "description", "opinion"),

    card("B1", "Home & Daily Life", "Free Time", "spędzać czas na świeżym powietrzu", "to spend time outdoors",
         "W weekend lubię spędzać czas na świeżym powietrzu.", "At the weekend I like spending time outdoors.", "leisure", "routine"),
    card("B1", "Home & Daily Life", "Free Time", "mieć napięty grafik", "to have a tight schedule",
         "Mam napięty grafik, więc rzadko wychodzę w tygodniu.", "I have a tight schedule, so I rarely go out during the week.", "leisure", "work"),
    card("B1", "Activities & Sports", "Free Time", "robić coś dla przyjemności", "to do something for pleasure",
         "Gram na gitarze tylko dla przyjemności.", "I play guitar just for pleasure.", "leisure", "hobby"),
    card("B1", "Activities & Sports", "Free Time", "ładować baterie", "to recharge one's batteries",
         "Krótki wyjazd pomaga mi ładować baterie.", "A short trip helps me recharge my batteries.", "leisure", "idiom"),
    card("B1", "Home & Daily Life", "Free Time", "zrezygnować z planów", "to give up / cancel plans",
         "Musieliśmy zrezygnować z planów przez pogodę.", "We had to cancel our plans because of the weather.", "leisure", "plans"),
    card("B1", "Activities & Sports", "Free Time", "wolę odpoczywać aktywnie", "I prefer active rest",
         "Wolę odpoczywać aktywnie niż siedzieć cały dzień w domu.", "I prefer active rest to sitting at home all day.", "leisure", "opinion"),

    card("B1", "Activities & Sports", "Sports", "rozgrzewka", "warm-up",
         "Przed treningiem zawsze robię krótką rozgrzewkę.", "Before training I always do a short warm-up.", "sport", "health"),
    card("B1", "Activities & Sports", "Sports", "kontuzja", "injury",
         "Po kontuzji przez miesiąc nie mogłem biegać.", "After the injury I couldn't run for a month.", "sport", "health"),
    card("B1", "Activities & Sports", "Sports", "poprawić kondycję", "to improve fitness",
         "Chcę poprawić kondycję przed wakacjami.", "I want to improve my fitness before the holidays.", "sport", "health"),
    card("B1", "Activities & Sports", "Sports", "kibicować komuś", "to cheer for someone",
         "Kibicujemy naszej drużynie w każdym meczu.", "We cheer for our team in every match.", "sport", "people"),
    card("B1", "Activities & Sports", "Sports", "rywalizować z kimś", "to compete with someone",
         "Nie lubię rywalizować z przyjaciółmi.", "I don't like competing with friends.", "sport", "verb"),
    card("B1", "Activities & Sports", "Sports", "grać fair", "to play fair",
         "Najważniejsze jest grać fair.", "The most important thing is to play fair.", "sport", "values"),

    card("B1", "Places & Directions", "City Life", "dzielnica", "district / neighborhood",
         "Mieszkam w spokojnej dzielnicy blisko centrum.", "I live in a quiet district near the center.", "city", "places"),
    card("B1", "Travel & Transport", "City Life", "komunikacja miejska", "public transport",
         "Komunikacja miejska działa tu bardzo dobrze.", "Public transport works very well here.", "city", "transport"),
    card("B1", "Places & Directions", "City Life", "tereny zielone", "green areas",
         "W mojej okolicy brakuje terenów zielonych.", "There are not enough green areas in my area.", "city", "nature"),
    card("B1", "Places & Directions", "City Life", "ruch uliczny", "traffic",
         "Ruch uliczny jest największy rano.", "Traffic is heaviest in the morning.", "city", "transport"),
    card("B1", "Places & Directions", "City Life", "łatwy dojazd", "easy access / commute",
         "Wybrałem to mieszkanie ze względu na łatwy dojazd.", "I chose this flat because of the easy commute.", "city", "housing"),
    card("B1", "Places & Directions", "City Life", "miasto przyjazne mieszkańcom", "a resident-friendly city",
         "Chciałabym mieszkać w mieście przyjaznym mieszkańcom.", "I would like to live in a resident-friendly city.", "city", "opinion"),

    card("B1", "Travel & Transport", "Travel", "nocleg", "accommodation",
         "Czy nocleg jest wliczony w cenę wycieczki?", "Is accommodation included in the trip price?", "travel", "hotel"),
    card("B1", "Travel & Transport", "Travel", "bagaż podręczny", "hand luggage",
         "W bagażu podręcznym mam tylko dokumenty i laptop.", "In my hand luggage I only have documents and a laptop.", "travel", "airport"),
    card("B1", "Travel & Transport", "Travel", "zwiedzać z przewodnikiem", "to tour with a guide",
         "Wolimy zwiedzać z przewodnikiem, bo znamy wtedy więcej historii.", "We prefer touring with a guide because then we learn more history.", "travel", "culture"),
    card("B1", "Travel & Transport", "Travel", "wyjazd spełnił moje oczekiwania", "the trip met my expectations",
         "Wyjazd spełnił moje oczekiwania, chociaż hotel był mały.", "The trip met my expectations, although the hotel was small.", "travel", "opinion"),
    card("B1", "Travel & Transport", "Travel", "poza sezonem", "off season",
         "Najchętniej podróżuję poza sezonem.", "I prefer traveling off season.", "travel", "time"),
    card("B1", "Travel & Transport", "Travel", "zarezerwować z wyprzedzeniem", "to book in advance",
         "Bilety trzeba zarezerwować z wyprzedzeniem.", "Tickets need to be booked in advance.", "travel", "planning"),

    card("B1", "Work & School", "Education", "zdawać egzamin", "to take / pass an exam",
         "W czerwcu będę zdawać ważny egzamin.", "In June I will take an important exam.", "education", "school"),
    card("B1", "Work & School", "Education", "praca domowa", "homework",
         "Nie zdążyłem zrobić pracy domowej.", "I didn't manage to do the homework.", "education", "school"),
    card("B1", "Work & School", "Education", "zajęcia dodatkowe", "extra classes / activities",
         "Po lekcjach chodzę na zajęcia dodatkowe z matematyki.", "After lessons I go to extra math classes.", "education", "school"),
    card("B1", "Work & School", "Education", "dostać się na studia", "to get into university",
         "Moja siostra dostała się na studia medyczne.", "My sister got into medical studies.", "education", "university"),
    card("B1", "Work & School", "Education", "stypendium", "scholarship",
         "Studentka stara się o stypendium.", "The student is applying for a scholarship.", "education", "money"),
    card("B1", "Work & School", "Education", "uczyć się systematycznie", "to study regularly",
         "Łatwiej zdać egzamin, jeśli uczysz się systematycznie.", "It is easier to pass an exam if you study regularly.", "education", "study"),

    card("B1", "Time & Calendar", "Celebrations", "obchodzić święto", "to celebrate a holiday",
         "Jakie święta obchodzi się w twoim kraju?", "What holidays are celebrated in your country?", "celebrations", "culture"),
    card("B1", "Time & Calendar", "Celebrations", "składać życzenia", "to offer wishes / greetings",
         "W urodziny składamy solenizantowi życzenia.", "On birthdays we offer wishes to the birthday person.", "celebrations", "family"),
    card("B1", "Food & Drink", "Celebrations", "tradycyjne potrawy", "traditional dishes",
         "Na stole pojawiły się tradycyjne potrawy.", "Traditional dishes appeared on the table.", "celebrations", "food"),
    card("B1", "People & Family", "Celebrations", "spotkanie rodzinne", "family gathering",
         "W niedzielę mamy duże spotkanie rodzinne.", "On Sunday we have a big family gathering.", "celebrations", "family"),
    card("B1", "Time & Calendar", "Celebrations", "dzień wolny od pracy", "day off work",
         "Pierwszego listopada wiele osób ma dzień wolny od pracy.", "On November first many people have a day off work.", "celebrations", "work"),
    card("B1", "Religion & Culture", "Celebrations", "podtrzymywać tradycję", "to maintain a tradition",
         "Moja rodzina stara się podtrzymywać tę tradycję.", "My family tries to maintain this tradition.", "celebrations", "culture"),

    card("B1", "Religion & Culture", "Culture", "fabuła", "plot",
         "Fabuła filmu była ciekawa, ale zakończenie mnie rozczarowało.", "The film's plot was interesting, but the ending disappointed me.", "culture", "film"),
    card("B1", "Religion & Culture", "Culture", "bohater powieści", "novel character / protagonist",
         "Główny bohater powieści musi podjąć trudną decyzję.", "The main character of the novel has to make a difficult decision.", "culture", "books"),
    card("B1", "Technology & Media", "Culture", "ekranizacja", "screen adaptation",
         "Ekranizacja różni się od książki.", "The screen adaptation differs from the book.", "culture", "film"),
    card("B1", "Religion & Culture", "Culture", "polecić komuś film", "to recommend a film to someone",
         "Mogę ci polecić świetny film dokumentalny.", "I can recommend a great documentary film to you.", "culture", "recommendation"),
    card("B1", "Religion & Culture", "Culture", "recenzja", "review",
         "Po przeczytaniu recenzji kupiłem bilet.", "After reading the review I bought a ticket.", "culture", "opinion"),
    card("B1", "Religion & Culture", "Culture", "wciągająca książka", "engaging book",
         "To wciągająca książka, którą przeczytałem w dwa dni.", "It is an engaging book that I read in two days.", "culture", "books"),

    card("B1", "Feelings & Qualities", "Emotions", "zdenerwować się", "to get upset / nervous",
         "Zdenerwowałem się, kiedy spóźnił się autobus.", "I got upset when the bus was late.", "emotions", "verb"),
    card("B1", "Feelings & Qualities", "Emotions", "być rozczarowanym", "to be disappointed",
         "Byłam rozczarowana wynikiem meczu.", "I was disappointed with the match result.", "emotions", "opinion"),
    card("B1", "Feelings & Qualities", "Emotions", "zachować spokój", "to stay calm",
         "W trudnej sytuacji trzeba zachować spokój.", "In a difficult situation you need to stay calm.", "emotions", "advice"),
    card("B1", "Feelings & Qualities", "Emotions", "wpaść w dobry nastrój", "to get into a good mood",
         "Po rozmowie z przyjaciółką wpadłam w dobry nastrój.", "After talking with my friend I got into a good mood.", "emotions", "mood"),
    card("B1", "Feelings & Qualities", "Emotions", "mieć mieszane uczucia", "to have mixed feelings",
         "Mam mieszane uczucia co do tej decyzji.", "I have mixed feelings about this decision.", "emotions", "opinion"),
    card("B1", "Feelings & Qualities", "Emotions", "coś poprawiło mi humor", "something improved my mood",
         "Ta wiadomość poprawiła mi humor.", "This message improved my mood.", "emotions", "mood"),

    card("B1", "Technology & Media", "Communication", "wysłać wiadomość", "to send a message",
         "Wyślę ci wiadomość po spotkaniu.", "I will send you a message after the meeting.", "communication", "technology"),
    card("B1", "Technology & Media", "Communication", "odebrać telefon", "to answer the phone",
         "Nie mogłem odebrać telefonu w pracy.", "I couldn't answer the phone at work.", "communication", "phone"),
    card("B1", "Technology & Media", "Communication", "oddzwonić do kogoś", "to call someone back",
         "Oddzwonię do pana za dziesięć minut.", "I will call you back in ten minutes.", "communication", "phone"),
    card("B1", "Technology & Media", "Communication", "załączyć plik", "to attach a file",
         "Proszę załączyć plik do maila.", "Please attach the file to the email.", "communication", "email"),
    card("B1", "Technology & Media", "Communication", "mieć słaby zasięg", "to have poor signal",
         "W górach często mam słaby zasięg.", "In the mountains I often have poor signal.", "communication", "phone"),
    card("B1", "Technology & Media", "Communication", "porozumieć się bez problemu", "to communicate without difficulty",
         "Po kilku miesiącach nauki mogłem porozumieć się bez problemu.", "After a few months of study I could communicate without difficulty.", "communication", "language"),

    card("B2", "Religion & Culture", "Intercultural Life", "dziedzictwo kulturowe", "cultural heritage",
         "Dziedzictwo kulturowe regionu widać w architekturze.", "The region's cultural heritage is visible in the architecture.", "culture", "heritage"),
    card("B2", "Religion & Culture", "Intercultural Life", "różnice kulturowe", "cultural differences",
         "Różnice kulturowe mogą prowadzić do nieporozumień.", "Cultural differences can lead to misunderstandings.", "culture", "society"),
    card("B2", "Religion & Culture", "Intercultural Life", "gościnność", "hospitality",
         "Gościnność jest ważną częścią lokalnej tradycji.", "Hospitality is an important part of the local tradition.", "culture", "people"),
    card("B2", "Religion & Culture", "Intercultural Life", "dostosować się do zwyczajów", "to adapt to customs",
         "Po przeprowadzce trzeba dostosować się do nowych zwyczajów.", "After moving, one needs to adapt to new customs.", "culture", "travel"),
    card("B2", "Religion & Culture", "Intercultural Life", "uniknąć faux pas", "to avoid a faux pas",
         "Przed podróżą warto poznać zasady, żeby uniknąć faux pas.", "Before traveling it is worth learning the rules to avoid a faux pas.", "culture", "manners"),
    card("B2", "Religion & Culture", "Intercultural Life", "spojrzeć z innej perspektywy", "to look from another perspective",
         "Rozmowa pomogła mi spojrzeć z innej perspektywy.", "The conversation helped me look from another perspective.", "culture", "opinion"),

    card("B2", "Travel & Transport", "Advanced Travel", "środek transportu", "means of transport",
         "Najwygodniejszy środek transportu zależy od trasy.", "The most convenient means of transport depends on the route.", "travel", "transport"),
    card("B2", "Travel & Transport", "Advanced Travel", "podróż na własną rękę", "independent travel",
         "Podróż na własną rękę daje więcej swobody.", "Independent travel gives more freedom.", "travel", "planning"),
    card("B2", "Travel & Transport", "Advanced Travel", "utknąć w korku", "to get stuck in traffic",
         "Utknęliśmy w korku i spóźniliśmy się na pociąg.", "We got stuck in traffic and missed the train.", "travel", "transport"),
    card("B2", "Travel & Transport", "Advanced Travel", "zmienić trasę", "to change the route",
         "Musieliśmy zmienić trasę z powodu remontu drogi.", "We had to change the route because of roadworks.", "travel", "transport"),
    card("B2", "Travel & Transport", "Advanced Travel", "przesiadka", "transfer / connection",
         "Mamy tylko dziesięć minut na przesiadkę.", "We have only ten minutes for the transfer.", "travel", "transport"),
    card("B2", "Travel & Transport", "Advanced Travel", "podróżować odpowiedzialnie", "to travel responsibly",
         "Coraz więcej osób chce podróżować odpowiedzialnie.", "More and more people want to travel responsibly.", "travel", "environment"),

    card("B2", "Work & School", "Work", "rzucić się w wir pracy", "to throw oneself into work",
         "Po urlopie rzuciłam się w wir pracy.", "After vacation I threw myself into work.", "work", "idiom"),
    card("B2", "Work & School", "Work", "warunki zatrudnienia", "employment conditions",
         "Przed podpisaniem umowy zapytaj o warunki zatrudnienia.", "Before signing the contract, ask about employment conditions.", "work", "contract"),
    card("B2", "Work & School", "Work", "równowaga między pracą a życiem prywatnym", "work-life balance",
         "Równowaga między pracą a życiem prywatnym jest dla mnie ważna.", "Work-life balance is important to me.", "work", "lifestyle"),
    card("B2", "Work & School", "Work", "mieć doświadczenie w branży", "to have experience in the field",
         "Mam pięć lat doświadczenia w branży IT.", "I have five years of experience in the IT field.", "work", "career"),
    card("B2", "Work & School", "Work", "podjąć wyzwanie", "to take on a challenge",
         "Chętnie podejmę nowe wyzwanie zawodowe.", "I will gladly take on a new professional challenge.", "work", "career"),
    card("B2", "Work & School", "Work", "awansować na stanowisko", "to be promoted to a position",
         "Po dwóch latach awansowała na stanowisko kierowniczki.", "After two years she was promoted to manager.", "work", "career"),

    card("B2", "Home & Daily Life", "Daily Choices", "codzienne nawyki", "daily habits",
         "Codzienne nawyki mają duży wpływ na zdrowie.", "Daily habits have a big influence on health.", "daily-life", "health"),
    card("B2", "Shopping & Money", "Daily Choices", "kupować lokalne produkty", "to buy local products",
         "Staram się kupować lokalne produkty na targu.", "I try to buy local products at the market.", "shopping", "food"),
    card("B2", "Shopping & Money", "Daily Choices", "stosunek jakości do ceny", "value for money",
         "Ten telefon ma dobry stosunek jakości do ceny.", "This phone offers good value for money.", "shopping", "opinion"),
    card("B2", "Home & Daily Life", "Daily Choices", "ograniczać marnowanie jedzenia", "to reduce food waste",
         "Planowanie zakupów pomaga ograniczać marnowanie jedzenia.", "Planning shopping helps reduce food waste.", "daily-life", "food"),
    card("B2", "Home & Daily Life", "Daily Choices", "podejmować świadome decyzje", "to make conscious decisions",
         "Jako konsumenci możemy podejmować świadome decyzje.", "As consumers we can make conscious decisions.", "daily-life", "opinion"),
    card("B2", "Shopping & Money", "Daily Choices", "zwracać uwagę na skład", "to pay attention to ingredients / composition",
         "Przy zakupie kosmetyków zwracam uwagę na skład.", "When buying cosmetics I pay attention to the ingredients.", "shopping", "health"),

    card("B2", "Emergencies & Safety", "Law and Safety", "przestępstwo", "crime",
         "Kradzież jest przestępstwem.", "Theft is a crime.", "law", "safety"),
    card("B2", "Emergencies & Safety", "Law and Safety", "wykroczenie", "minor offense",
         "Za takie wykroczenie można dostać mandat.", "You can get a fine for such a minor offense.", "law", "safety"),
    card("B2", "Emergencies & Safety", "Law and Safety", "zgłosić sprawę na policję", "to report a matter to the police",
         "Świadek powinien zgłosić sprawę na policję.", "A witness should report the matter to the police.", "law", "police"),
    card("B2", "Emergencies & Safety", "Law and Safety", "świadek zdarzenia", "witness of an incident",
         "Świadek zdarzenia opisał sytuację funkcjonariuszom.", "The witness of the incident described the situation to the officers.", "law", "people"),
    card("B2", "Emergencies & Safety", "Law and Safety", "ponieść konsekwencje", "to face consequences",
         "Każdy kierowca musi ponieść konsekwencje niebezpiecznej jazdy.", "Every driver must face the consequences of dangerous driving.", "law", "consequences"),
    card("B2", "Emergencies & Safety", "Law and Safety", "działać zgodnie z prawem", "to act according to the law",
         "Firma musi działać zgodnie z prawem.", "The company must act according to the law.", "law", "work"),

    card("B2", "Feelings & Qualities", "Helping Others", "okazać wsparcie", "to show support",
         "W trudnej chwili warto okazać wsparcie.", "In a difficult moment it is worth showing support.", "helping", "empathy"),
    card("B2", "Administration & Services", "Helping Others", "kampania społeczna", "social campaign",
         "Kampania społeczna zwraca uwagę na samotność seniorów.", "The social campaign draws attention to loneliness among seniors.", "society", "media"),
    card("B2", "People & Family", "Helping Others", "wolontariusz", "volunteer",
         "Wolontariusze pomagają w schronisku.", "Volunteers help at the shelter.", "helping", "people"),
    card("B2", "Feelings & Qualities", "Helping Others", "uwrażliwić kogoś na problem", "to sensitize someone to a problem",
         "Film może uwrażliwić widzów na problem przemocy.", "A film can sensitize viewers to the problem of violence.", "society", "empathy"),
    card("B2", "Administration & Services", "Helping Others", "działać na rzecz społeczności", "to act for the benefit of the community",
         "Organizacja działa na rzecz lokalnej społeczności.", "The organization acts for the benefit of the local community.", "society", "community"),
    card("B2", "Feelings & Qualities", "Helping Others", "nie pozostawać obojętnym", "not to remain indifferent",
         "Nie możemy pozostawać obojętni wobec krzywdy innych.", "We cannot remain indifferent to the harm of others.", "society", "empathy"),

    card("B2", "Food & Drink", "Food", "regionalna kuchnia", "regional cuisine",
         "Regionalna kuchnia często pokazuje historię miejsca.", "Regional cuisine often shows the history of a place.", "food", "culture"),
    card("B2", "Food & Drink", "Food", "przepis rodzinny", "family recipe",
         "To przepis rodzinny przekazywany od lat.", "This is a family recipe passed down for years.", "food", "family"),
    card("B2", "Food & Drink", "Food", "doprawić do smaku", "to season to taste",
         "Zupę trzeba doprawić do smaku.", "The soup needs to be seasoned to taste.", "food", "cooking"),
    card("B2", "Food & Drink", "Food", "danie wegetariańskie", "vegetarian dish",
         "Restauracja ma kilka dań wegetariańskich.", "The restaurant has several vegetarian dishes.", "food", "restaurant"),
    card("B2", "Food & Drink", "Food", "niebo w gębie", "delicious / heavenly taste",
         "Ten sernik to niebo w gębie.", "This cheesecake tastes heavenly.", "food", "idiom"),
    card("B2", "Food & Drink", "Food", "delektować się posiłkiem", "to savor a meal",
         "Lubię delektować się posiłkiem bez pośpiechu.", "I like savoring a meal without rushing.", "food", "lifestyle"),

    card("B2", "Clothing & Appearance", "Fashion", "ubiór formalny", "formal clothing",
         "Na rozmowę kwalifikacyjną wybrałem ubiór formalny.", "For the job interview I chose formal clothing.", "fashion", "work"),
    card("B2", "Clothing & Appearance", "Fashion", "być na bakier z modą", "to be out of step with fashion",
         "Mój brat jest trochę na bakier z modą.", "My brother is a bit out of step with fashion.", "fashion", "idiom"),
    card("B2", "Clothing & Appearance", "Fashion", "wyrażać siebie przez ubranie", "to express oneself through clothing",
         "Wiele osób lubi wyrażać siebie przez ubranie.", "Many people like expressing themselves through clothing.", "fashion", "identity"),
    card("B2", "Clothing & Appearance", "Fashion", "strój służbowy", "work uniform / business attire",
         "W tej firmie obowiązuje strój służbowy.", "In this company business attire is required.", "fashion", "work"),
    card("B2", "Clothing & Appearance", "Fashion", "dobrać dodatki", "to choose accessories",
         "Do sukienki trudno dobrać dodatki.", "It is hard to choose accessories for the dress.", "fashion", "shopping"),
    card("B2", "Clothing & Appearance", "Fashion", "pierwsze wrażenie", "first impression",
         "Pierwsze wrażenie często zależy od sposobu ubierania się.", "The first impression often depends on the way someone dresses.", "fashion", "people"),

    card("B2", "Weather & Nature", "Environment", "środowisko naturalne", "natural environment",
         "Musimy lepiej chronić środowisko naturalne.", "We need to protect the natural environment better.", "environment", "nature"),
    card("B2", "Weather & Nature", "Environment", "zmiana klimatu", "climate change",
         "Zmiana klimatu wpływa na rolnictwo.", "Climate change affects agriculture.", "environment", "climate"),
    card("B2", "Weather & Nature", "Environment", "segregować odpady", "to sort waste",
         "W domu staramy się segregować odpady.", "At home we try to sort waste.", "environment", "home"),
    card("B2", "Weather & Nature", "Environment", "oszczędzać energię", "to save energy",
         "Nowe okna pomagają oszczędzać energię.", "New windows help save energy.", "environment", "home"),
    card("B2", "Weather & Nature", "Environment", "zanieczyszczenie powietrza", "air pollution",
         "Zanieczyszczenie powietrza jest poważnym problemem zimą.", "Air pollution is a serious problem in winter.", "environment", "city"),
    card("B2", "Weather & Nature", "Environment", "żyć bardziej ekologicznie", "to live more ecologically",
         "Chcemy żyć bardziej ekologicznie i mniej kupować.", "We want to live more ecologically and buy less.", "environment", "lifestyle"),

    card("B2", "Technology & Media", "Science and Inventions", "wynalazek", "invention",
         "Internet to wynalazek, który zmienił codzienne życie.", "The internet is an invention that changed daily life.", "technology", "science"),
    card("B2", "Technology & Media", "Science and Inventions", "potrzeba jest matką wynalazku", "necessity is the mother of invention",
         "Jak mówi przysłowie, potrzeba jest matką wynalazku.", "As the proverb says, necessity is the mother of invention.", "technology", "proverb"),
    card("B2", "Technology & Media", "Science and Inventions", "ułatwiać życie", "to make life easier",
         "Aplikacje mobilne mogą ułatwiać życie.", "Mobile apps can make life easier.", "technology", "daily-life"),
    card("B2", "Technology & Media", "Science and Inventions", "wprowadzić na rynek", "to launch on the market",
         "Firma chce wprowadzić na rynek nowy produkt.", "The company wants to launch a new product on the market.", "technology", "business"),
    card("B2", "Technology & Media", "Science and Inventions", "sztuczna inteligencja", "artificial intelligence",
         "Sztuczna inteligencja pomaga analizować duże zbiory danych.", "Artificial intelligence helps analyze large data sets.", "technology", "science"),
    card("B2", "Technology & Media", "Science and Inventions", "rozwiązać praktyczny problem", "to solve a practical problem",
         "Dobry wynalazek powinien rozwiązać praktyczny problem.", "A good invention should solve a practical problem.", "technology", "problem-solving"),
]


def main() -> None:
    phrases = json.loads(ASSET_PHRASES.read_text(encoding="utf-8"))
    existing = {normalize(item["polish"]) for item in phrases}
    additions = [item for item in NEW_CARDS if normalize(item["polish"]) not in existing]
    phrases.extend(additions)

    output = json.dumps(phrases, ensure_ascii=False, indent=2) + "\n"
    ASSET_PHRASES.write_text(output, encoding="utf-8")
    DOCS_PHRASES.write_text(output, encoding="utf-8")

    digest = hashlib.sha256(output.encode("utf-8")).hexdigest()
    database = json.loads(DATABASE.read_text(encoding="utf-8"))
    database["dataVersion"] = max(int(database.get("dataVersion", 0)), 4) + 1
    database["phraseCount"] = len(phrases)
    database["phrasesSha256"] = digest
    database["phrasesSizeBytes"] = len(output.encode("utf-8"))
    database["releaseNotes"] = (
        f"Adds {len(additions)} original B1/B2 speaking-practice cards across "
        "biography, leisure, sport, city life, travel, education, culture, work, "
        "law, social issues, food, fashion, environment, and technology."
    )
    DATABASE.write_text(json.dumps(database, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(f"existing={len(existing)} additions={len(additions)} total={len(phrases)}")
    print(f"sha256={digest}")


if __name__ == "__main__":
    main()
