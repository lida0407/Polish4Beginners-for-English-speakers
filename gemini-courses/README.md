# Polish live-call courses for a Gemini voice tutor

Lesson content for a voice app where Gemini plays characters on live calls,
modelled on how Duolingo builds its Video Call and Roleplay features: the call
is a real situation with a character, not a vocabulary drill.

| Course | Role | Units | Lessons | What it is |
|---|---|---|---|---|
| `calls` | main path | 12 | 48 | Scenario calls with a recurring cast: order coffee, buy tickets, see the doctor, return a jacket, argue Kraków vs Warsaw. Each call has a mission, a complication, natural conversation and feedback afterwards |
| `grammar` | practice | 14 | 36 | Spoken grammar drills from the grammar course, linked from calls as preparation |
| `vocab-a1` | practice | 18 | 115 | A1 words by topic, linked from calls as preparation |
| `vocab-a2` | practice | 20 | 94 | A2 words by topic |

## How the calls are designed

Taken from Duolingo's description of its Video Call system
(blog.duolingo.com/ai-and-video-call) and Roleplay (blog.duolingo.com/duolingo-max):

- **A character, not a tutor.** Eight recurring people with a personality,
  a backstory and things they love (Ola the barista, Pan Marek the
  neighbour, Kasia, Tomek, Pani Basia at the market, Pan Zbyszek at the kiosk,
  Doktor Nowak, Ania from work), plus one-off characters for single scenes.
  The prompt tells them never to break character.
- **A fixed opener and first question.** Duolingo writes the first question
  separately because asking the model to invent it at the right level
  overloads it. Here both are hand-written at the right level.
- **A mission and a complication.** Three tasks the learner can see
  ("Order a drink", "Ask the price") and one twist ("the cheesecake has sold
  out") to make them adapt.
- **Natural conversation.** React first, then continue; one question per
  turn; rephrase or offer two choices when the learner is lost; follow them
  if they change topic, then come back.
- **No corrections during the call.** Mistakes are recast: the character
  uses the correct form in its reply. Corrections come afterwards.
- **The app ends the call.** Duolingo has its System whisper "say it's time to
  go". Here `coach_notes` give the app two cues to inject: bring in the
  complication after three learner turns, and wrap up after eight (A1) or ten
  (A2).
- **Feedback after the call.** `call_templates.feedback_prompt` turns the
  transcript into: mission ✓/✗, up to three fixes, two phrases for next time,
  accuracy and range scores out of five.
- **Memory.** `call_templates.memory_prompt` extracts facts the learner said
  ("has two dogs", "works in IT"). The app stores them and fills
  `{{memory}}` in the next call, so characters can ask "How are your dogs?"

## Running a call

1. **Before:** show the scene, the character, the mission and the phrases
   (with audio). Offer the linked `prep` lessons as optional warm-ups.
2. **Fill the placeholders** in `system_prompt`:
   - `{{learner_name}}` and `{{learner_gender}}` ("male" or "female");
   - `{m:…|f:…}` tokens: keep the side matching the learner's gender
     (`{m:Co robiłeś?|f:Co robiłaś?}`); the same tokens appear in `opener`,
     `first_question` and `closer`;
   - `{{memory}}`: "What you remember about the learner from earlier calls:
     …" plus the saved facts, or an empty string.
3. **Start** with the filled prompt as the system instruction; the character
   speaks the opener first.
4. **During:** after the learner's turn number given in each `coach_notes`
   entry, append that note to the learner's message (or send it as a system
   turn). Speech in with `pl-PL` recognition, speech out with a Polish voice
   matching `character.voice`.
5. **After:** run `feedback_prompt` and `memory_prompt` on the transcript;
   show the feedback, save the facts and mark the call done.

## Files

- `out/polish_live_courses.json`: everything an app loads.
- `out/COURSES.md`: the catalogue, for reading.
- `scenarios.json`: the hand-written role-plays and goals.
- `build.py`: regenerates `out/` from the Polish4Beginners deck and grammar
  guide. Run `python3 gemini-courses/build.py` after changing either.

## Practice lesson shape (grammar and vocabulary)

```jsonc
{
  "id": "vocab-a1-food-01",          // stable: use it as the progress key
  "title": "Food and drink 1",
  "level": "A1",
  "minutes": 10,
  "goal": "Understand and say 10 food and drink words and use them…",
  "learner_brief": "10 words, then a role-play: ordering at a café.",
  "words": [{"pl": "kawa", "en": "coffee", "note": "feminine",
             "example_pl": "…", "example_en": "…"}],      // vocab lessons
  "summary": "…", "examples": [{"pl": "…", "en": "…"}],     // grammar lessons
  "drills": [{"ask": "…", "answer": "…", "choices": ["…"]}], // grammar lessons
  "roleplay": {"scene": "…", "tutor_role": "…", "learner_role": "…",
               "opener": {"pl": "Dzień dobry! Co podać?", "en": "…"}},
  "checks": [{"ask": "How do you say “coffee”?", "answer": "kawa"}],
  "system_prompt": "You are Ola, a warm Polish tutor on a live voice call…"
}
```

`system_prompt` is complete: send it as the system instruction and start the
call. Everything else is there so the app can show the lesson, score it and
track progress without asking the model.

## Running a live lesson

1. Show `title`, `goal` and `learner_brief` (and `words` for vocabulary) so
   the learner knows what is coming.
2. Start a session with `system_prompt` as the system instruction, then send
   one user turn such as "Start the lesson." The prompt's plan makes the tutor
   greet, teach, check, role-play and recap in that order.
3. Voice in: speech recognition with language `pl-PL` for the learner's
   Polish. Learners mix languages, so allow English too if your recogniser
   supports more than one language.
4. Voice out: speak the tutor's replies; the Polish should go to a Polish voice.
5. Score with `checks`: each has the expected Polish answer. The app can mark a
   lesson done when most checks were answered correctly, without trusting the
   model to keep score. Polish4Beginners' `LearningLogic.checkAnswer` and
   `pronunciationScore` do this kind of lenient matching (letters only, word
   order, near miss) and can be copied.
6. Save progress by lesson `id`.

## On-device models

Prompts are written for a small model: one explicit plan, short turns, a
closed word list, and 1,300–2,100 characters (roughly 350–550 tokens) each,
so there is plenty of room left for the conversation.

Test before relying on it. An on-device model is usually weaker in Polish than
a cloud model: it may mis-inflect words or drift into English. The lesson
data does not depend on the model being right, since `words`, `drills` and
`checks` hold the correct Polish. Use them to correct the model rather than
the other way round. The same prompts work unchanged with a cloud Gemini model
when quality matters more than running offline.

## Where the content comes from

- Grammar lessons: the Polish4Beginners grammar course, which was built from
  the user's grammar and vocabulary guide and checked question by question.
  Notation, the long vocabulary lists and the study-routine chapter are left
  out (vocabulary has its own courses).
- Vocabulary: the A1 and A2 cards of the Polish4Beginners deck, core-frequency
  words first. Cards longer than six words are left out (they are sentences,
  not vocabulary), and a topic with fewer than five words at a level is folded
  into the next level.
- Role-plays and goals: written for this pack (`scenarios.json`). The Polish
  openers avoid gendered forms (pan/pani, robiłeś/robiłaś) so they suit any
  learner.
