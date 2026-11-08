# Polish live-call courses for a Gemini voice tutor

Lesson content for a voice app where Gemini plays a Polish tutor on a live
call. Each lesson is about ten minutes: teach, drill aloud, role-play, recap.

| Course | Units | Lessons | What it covers |
|---|---|---|---|
| `grammar` | 14 | 35 | The grammar course rebuilt for speaking: sounds, sentence basics, gender, the cases, pronouns, adjectives, tenses, aspect and motion, requests and conditionals, numbers and time, verb + case patterns, sentence patterns, common errors, translation sprints |
| `vocab-a1` | 18 | 115 | A1 words by topic, about ten a lesson, each lesson ending in a role-play |
| `vocab-a2` | 20 | 94 | A2 words by topic, building on A1 (no word is taught twice) |

## Files

- `out/polish_live_courses.json`: everything an app loads.
- `out/COURSES.md`: the catalogue, for reading.
- `scenarios.json`: the hand-written role-plays and goals.
- `build.py`: regenerates `out/` from the Polish4Beginners deck and grammar
  guide. Run `python3 gemini-courses/build.py` after changing either.

## Lesson shape

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
