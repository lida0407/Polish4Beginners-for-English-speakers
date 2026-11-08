# Polish live-call courses

Lessons for a Gemini voice tutor. Each lesson is about ten minutes: teach, drill, role-play, recap.
Built by `gemini-courses/build.py`; the JSON beside this file is what an app loads.

## Live calls: Polish in real situations

The main path. Each call is a scene with a character: a mission to complete, a complication, natural conversation, then feedback. Grammar and vocabulary lessons are linked as optional preparation.

12 units, 48 lessons.

- **First days in Kraków** (A1): Hello, neighbour with Pan Marek; Your first coffee with Ola; A new colleague with Ania; How are you? with Kasia
- **Numbers and shopping** (A1): Tram tickets with Pan Zbyszek; At the market with Pani Basia; Swapping numbers with Kasia; Fresh bread with Ola
- **Food and eating out** (A1): Lunch at a milk bar with Pani Halina; Dinner at Tomek's with Tomek; Party shopping with Kasia; The wrong order with Ola
- **Getting around** (A1): Which way to the station? with Pan Marek; A ticket to Warsaw with Pani Grażyna; Taxi to the hotel with Pan Zbyszek; Where are you? with Kasia
- **Home** (A1): Viewing a flat with Pani Basia; Show me your room with Kasia; A morning run with Tomek; No hot water with Pan Marek
- **Time and plans** (A1): When are you free? with Kasia; Weekend in the mountains? with Tomek; Booking the doctor with Pani Krysia; A birthday invitation with Ania
- **Health** (A2): At the doctor's with Doktor Nowak; At the pharmacy with Pani Ela; Calling in sick with Ania; At the gym with Tomek
- **What happened?** (A2): How was your weekend? with Kasia; A trip to Gdańsk with Pan Marek; You won't believe it! with Tomek; A stolen wallet with Sierżant Lis
- **Shops and services** (A2): Returning a jacket with Pani Monika; Sending a parcel with Pani Wiesia; Opening a bank account with Pan Tadeusz; At the hairdresser's with Pani Jola
- **Work and study** (A2): A job interview with Pani Dorota; Signing up for a Polish course with Pan Paweł; Can you help me? with Ania; Moving a meeting with Ania
- **Travel** (A2): Checking in with Pan Adam; Planning a trip with Tomek; What will the weather be like? with Pan Marek; A problem with the meal with Pan Kuba
- **Opinions and culture** (A2): Film night with Kasia; Christmas Eve with Pan Marek; Kraków or Warsaw? with Ania; If you could… with Kasia

## Polish grammar by conversation

The grammar course, rebuilt for speaking: each lesson explains one point, drills it aloud, then uses it in a role-play.

14 units, 36 lessons.

- **Sounds and spelling** · Sounds and spelling
- **Sentence foundations** · Sentence foundations, Statements, questions and negatives, Formal and informal address
- **Gender, number and agreement** · Gender, number and agreement
- **The seven cases** · The seven cases, Singular models, Plural models, Negation and quantity, Prepositions and cases
- **Pronouns and possession** · Pronouns and possession, Possessives
- **Adjectives and adverbs** · Adjectives and adverbs
- **Verbs and tenses** · Present tense, Past tense, Future tense, Modal patterns
- **Aspect and motion** · Aspect, Motion
- **Requests, conditions and complex sentences** · Requests and commands, Conditional, Connectors and relative clauses
- **Numbers, dates and time** · Cardinal numbers, Days and months
- **Verb bank and case patterns** · Verb bank and case patterns (1/6), Verb bank and case patterns (2/6), Verb bank and case patterns (3/6), Verb bank and case patterns (4/6), Verb bank and case patterns (5/6), Verb bank and case patterns (6/6)
- **Practical sentence patterns** · Practical sentence patterns
- **Common errors** · Common errors
- **Practice and answers** · Practice A: Supply the correct noun form, Practice B: Supply agreement, Practice C: Choose a verb form, Practice D: Translate

## Polish vocabulary A1

Everyday A1 words by topic, about ten a lesson, each lesson ending in a role-play that uses them.

18 units, 115 lessons.

- **Greetings and essentials** · 7 lessons, 63 words
- **People and family** · 6 lessons, 57 words
- **Numbers and measures** · 5 lessons, 41 words
- **Time and calendar** · 7 lessons, 70 words
- **Food and drink** · 9 lessons, 90 words
- **Home and daily life** · 4 lessons, 31 words
- **Clothing and appearance** · 4 lessons, 33 words
- **Shopping and money** · 7 lessons, 65 words
- **Places and directions** · 6 lessons, 53 words
- **Travel and transport** · 8 lessons, 71 words
- **Health and body** · 7 lessons, 65 words
- **Work and school** · 8 lessons, 72 words
- **Technology and media** · 4 lessons, 35 words
- **Free time and sport** · 5 lessons, 42 words
- **Nature, animals and weather** · 5 lessons, 48 words
- **Feelings and describing people** · 8 lessons, 72 words
- **Everyday verbs** · 8 lessons, 72 words
- **Small words and questions** · 7 lessons, 66 words

## Polish vocabulary A2

A2 words by topic, building on A1, in the same teach, check and role-play format.

20 units, 94 lessons.

- **Greetings and essentials** · 1 lessons, 7 words
- **People and family** · 3 lessons, 23 words
- **Numbers and measures** · 5 lessons, 41 words
- **Time and calendar** · 2 lessons, 11 words
- **Food and drink** · 5 lessons, 50 words
- **Home and daily life** · 7 lessons, 64 words
- **Clothing and appearance** · 2 lessons, 16 words
- **Shopping and money** · 4 lessons, 40 words
- **Places and directions** · 3 lessons, 27 words
- **Travel and transport** · 5 lessons, 43 words
- **Health and body** · 6 lessons, 54 words
- **Work and school** · 9 lessons, 82 words
- **Technology and media** · 4 lessons, 31 words
- **Free time and sport** · 4 lessons, 40 words
- **Nature, animals and weather** · 5 lessons, 49 words
- **Feelings and describing people** · 10 lessons, 97 words
- **Small words and questions** · 4 lessons, 33 words
- **Everyday conversation** · 12 lessons, 116 words
- **Offices and services** · 2 lessons, 18 words
- **Culture and holidays** · 1 lessons, 6 words

## Example: a call's system prompt

```text
You are Ola, 26, barista at Kawiarnia pod Lipą, a small café in Kazimierz, Kraków. You are cheerful, chatty, curious about customers. You love cats, jazz, cinnamon rolls.
Stay in character for the whole call. Never say you are an AI, a tutor or a teacher.
You are on a live voice call in Polish with {{learner_name}}, an English speaker learning Polish at level A1. The learner is {{learner_gender}}: use the matching Polish forms for them. Register: neutral with new customers, informal (ty) with regulars.
Level: Use the present tense and everyday words, sentences of about 8 words, and speak slowly.
Scene, as the learner sees it: Kawiarnia pod Lipą, a small café in Kazimierz; your first visit. The learner is a customer.
The learner's mission (they can see it): 1) Order a drink. 2) Order something to eat. 3) Ask how much it is and pay. Steer the talk towards each task, but let them try first.
Complication: The cheesecake (sernik) has just sold out; choose something else. Bring it in after the first or second task.
Phrases the learner prepared: Poproszę kawę = A coffee, please; Poproszę herbatę z cytryną = Tea with lemon, please; Na miejscu / na wynos = To have here / to take away; Ile płacę? = How much do I pay?; Czy mogę zapłacić kartą? = Can I pay by card?; Dziękuję = Thank you. Use them naturally.
How to talk:
- Speak only Polish. One or two short sentences per turn, then stop and let the learner speak.
- React to what they say first, then move on. Ask at most one question per turn.
- If they seem confused, say it again more simply or offer two choices. If they are lost twice in a row, say the key word in English once, then carry on in Polish.
- If they answer in English, reply in simple Polish and give them the Polish to say.
- Do not correct mistakes directly. Use the correct form naturally in your reply instead (learner: "Poproszę kawa" → you: "Kawę? Proszę bardzo!").
- If they mention something you love, sound pleased. If they want to talk about something else, go with it briefly, then come back to the scene.
Call shape: open with exactly "Dzień dobry! Co podać?". When they answer, ask "Kawa, herbata? Mamy też dzisiaj świeży sernik.". Then work through the mission. When it is done, or when told it is time, finish in character with "Smacznego! Do zobaczenia!"
{{memory}}
```
