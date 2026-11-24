
// Grammar basics view: quick scenario lessons and check questions.
import { h, ui, L, data, speak, stopSpeaking } from '../core.js';
import { tappable } from '../word.js';

export default function view(ctx) {
  ctx.onLeave?.(() => stopSpeaking());
  const allLessons = data.grammarLessons || [];
  const levelsPresent = [...new Set(allLessons.map((l) => l.level).filter(Boolean))];

  ctx.state.level = ctx.state.level || 'All';
  const levelChips = ['All', ...levelsPresent].map((lvl) =>
    ui.chip(lvl, {
      on: ctx.state.level === lvl,
      onclick: () => {
        ctx.state.level = lvl;
        ctx.refresh();
      },
    })
  );

  const lessons = ctx.state.level === 'All'
    ? allLessons
    : allLessons.filter((l) => l.level === ctx.state.level);

  const header = ui.header('Grammar basics', { kicker: `${allLessons.length} quick lessons` });

  const cards = lessons.map((lesson) => {
    const isOpen = ctx.state.open === lesson.unit;
    const toggleOpen = () => {
      ctx.state.open = isOpen ? null : lesson.unit;
      ctx.refresh();
    };

    const headRow = h('button', {
      class: 'row',
      type: 'button',
      onclick: toggleOpen,
    },
      h('span', { class: 'row-main' },
        h('span', { class: 'hstack', style: { alignItems: 'center' } },
          h('span', { class: 'tag' }, lesson.unit),
          h('h3', { style: { margin: 0 } }, lesson.topic)
        ),
        h('span', { class: 'muted small' }, lesson.focus)
      ),
      h('span', { class: 'row-right' }, isOpen ? '▲' : '▼')
    );

    const cardChildren = [headRow];

    if (isOpen) {
      const ruleSection = h('div', { class: 'stack' },
        h('div', { class: 'kicker' }, 'Rule'),
        h('p', {}, lesson.rule)
      );

      const patternSection = h('div', { class: 'stack' },
        h('div', { class: 'kicker' }, 'Pattern'),
        ui.pl(lesson.pattern, 'p')
      );

      const readBtn = ui.button('Read examples', {
        small: true,
        onclick: async () => {
          for (const ex of lesson.examples || []) {
            const ok = await speak(ex.polish);
            if (!ok) break;
          }
        },
      });

      const exItems = (lesson.examples || []).map((ex) =>
        h('div', { class: 'stack' },
          tappable(ex.polish, { tag: 'div' }),
          h('div', { class: 'muted small' }, ex.english)
        )
      );

      const exSection = h('div', { class: 'stack' },
        h('div', { class: 'spread' },
          h('div', { class: 'kicker' }, 'Examples'),
          readBtn
        ),
        ...exItems
      );

      let tries = 0;
      const input = h('input', { class: 'input', type: 'text', placeholder: 'Your answer...' });
      const verdict = h('div', { class: 'verdict' });
      const doCheck = () => {
        const typed = input.value.trim();
        if (!typed) return;
        const res = L.checkAnswer(typed, lesson.check.answer);
        const ok = res !== L.ANSWER_WRONG;
        if (ok) {
          verdict.className = 'verdict ok';
          verdict.textContent = `Correct: ${lesson.check.answer}`;
        } else {
          tries++;
          verdict.className = 'verdict no';
          if (tries >= 2) {
            verdict.textContent = `Answer: ${lesson.check.answer}`;
          } else if (lesson.check.hint) {
            verdict.textContent = `Not quite. Hint: ${lesson.check.hint}`;
          } else {
            verdict.textContent = 'Not quite. Try again';
          }
        }
      };

      input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') doCheck();
      });

      const checkBtn = ui.button('Check', { small: true, onclick: doCheck });
      const checkSection = h('div', { class: 'stack' },
        h('div', { class: 'kicker' }, 'Check yourself'),
        ui.pl(lesson.check.prompt, 'p'),
        h('div', { class: 'hstack' }, input, checkBtn),
        verdict
      );

      cardChildren.push(h('div', { class: 'stack', style: { marginTop: '12px' } },
        ruleSection,
        patternSection,
        exSection,
        checkSection
      ));
    }

    return ui.card({}, ...cardChildren);
  });

  return h('div', { class: 'stack' },
    header,
    h('div', { class: 'chips' }, ...levelChips),
    h('div', { class: 'stack' }, ...cards)
  );
}
