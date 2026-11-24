
// Course view: unit map, lesson study and interactive quizzes.
import { h, ui, L, store, data, go, speak, stopSpeaking } from '../core.js';
import { renderBlock } from './guide.js';

export default function view(ctx) {
  ctx.onLeave?.(() => stopSpeaking());
  const units = (data.guide && data.guide.course && data.guide.course.units) || [];
  const allLessons = [];
  for (const u of units) {
    for (const l of u.lessons || []) {
      allLessons.push({ lesson: l, unit: u });
    }
  }

  const courseProgress = store.get('course', { done: {}, last: null }) || { done: {}, last: null };
  const doneMap = courseProgress.done || {};

  // Course map.
  if (!ctx.params || ctx.params.length === 0) {
    const total = allLessons.length;
    const doneCount = allLessons.filter(({ lesson }) => doneMap[lesson.id] !== undefined).length;
    const header = ui.header('Grammar course', { kicker: `${doneCount} of ${total} lessons done` });
    const progress = ui.progress(total > 0 ? doneCount / total : 0);

    const firstUndone = allLessons.find(({ lesson }) => doneMap[lesson.id] === undefined);
    const continueBtn = firstUndone
      ? ui.button(`Continue: ${firstUndone.lesson.title}`, {
          kind: 'primary',
          block: true,
          onclick: () => go(`#/course/${encodeURIComponent(firstUndone.lesson.id)}`),
        })
      : null;

    const sections = units.map((u) => {
      const rows = (u.lessons || []).map((l) => {
        const isDone = doneMap[l.id] !== undefined;
        const right = isDone ? `✓ ${doneMap[l.id]}%` : '→';
        const sub = (l.quiz && l.quiz.length > 0) ? `${l.quiz.length} questions` : 'Reading';
        return ui.row(l.title, right, () => go(`#/course/${encodeURIComponent(l.id)}`), sub);
      });
      return ui.section(u.title, ...rows);
    });

    return h('div', { class: 'stack' }, header, progress, continueBtn, ...sections);
  }

  // Single lesson.
  const lessonId = ctx.params[0];
  let found = null;
  for (const u of units) {
    const l = (u.lessons || []).find((x) => String(x.id) === String(lessonId));
    if (l) {
      found = { lesson: l, unit: u };
      break;
    }
  }

  if (!found) {
    go('#/course');
    return h('div');
  }

  const { lesson, unit } = found;
  const chapter = (data.guide && data.guide.chapters && data.guide.chapters[unit.chapter]) || null;

  // Track last opened lesson.
  if (courseProgress.last !== lessonId) {
    store.update('course', (c) => ({
      ...(c || {}),
      done: (c && c.done) || {},
      last: lessonId,
    }), { done: {}, last: lessonId });
  }

  if (!ctx.state.phase) ctx.state.phase = 'learn';

  // Learn phase.
  if (ctx.state.phase === 'learn') {
    const header = ui.header(lesson.title, { back: '#/course', kicker: unit.title });
    const rendered = (lesson.refs || []).map((ref) => {
      const blk = chapter?.blocks?.[ref.b];
      return blk ? renderBlock(blk, { rows: ref.rows, words: ref.words }) : null;
    }).filter(Boolean);
    const prose = h('article', { class: 'prose' }, ...rendered);

    const quizCount = (lesson.quiz || []).length;
    const actionBtn = quizCount > 0
      ? ui.button(`Start the quiz · ${quizCount} questions`, {
          kind: 'primary',
          block: true,
          onclick: () => {
            ctx.state.phase = 'quiz';
            ctx.state.q = 0;
            ctx.state.score = 0;
            delete ctx.state.picked;
            delete ctx.state.checked;
            delete ctx.state.typed;
            delete ctx.state.answerRes;
            ctx.refresh();
          },
        })
      : ui.button('Mark as done', {
          kind: 'primary',
          block: true,
          onclick: () => {
            store.update('course', (c) => {
              const current = c || { done: {}, last: lessonId };
              const done = { ...(current.done || {}) };
              done[lessonId] = Math.max(done[lessonId] !== undefined ? done[lessonId] : -1, 100);
              return { ...current, done, last: lessonId };
            }, { done: {}, last: lessonId });
            ctx.state.phase = 'done';
            ctx.state.score = 0;
            ctx.refresh();
          },
        });

    return h('div', { class: 'stack' }, header, prose, actionBtn);
  }

  // Quiz phase.
  if (ctx.state.phase === 'quiz') {
    const qIdx = ctx.state.q || 0;
    const totalQ = (lesson.quiz || []).length;
    const q = lesson.quiz[qIdx];

    if (!q) {
      ctx.state.phase = 'done';
      return view(ctx);
    }

    const head = h('div', { class: 'stack' },
      ui.progress(qIdx / totalQ),
      h('div', { class: 'kicker' }, `Question ${qIdx + 1} of ${totalQ}`),
      h('h2', {}, q.prompt)
    );

    let body = null;
    let answered = false;

    if (q.kind === 'mc') {
      answered = ctx.state.picked !== undefined;
      const optionButtons = (q.options || []).map((opt) => {
        let cls = 'option';
        if (answered) {
          if (opt === q.answer) cls += ' right';
          else if (opt === ctx.state.picked) cls += ' wrong';
        }
        const btn = ui.button(opt, {
          block: true,
          disabled: answered,
          onclick: () => {
            if (ctx.state.picked !== undefined) return;
            ctx.state.picked = opt;
            if (opt === q.answer) {
              ctx.state.score = (ctx.state.score || 0) + 1;
            }
            ctx.refresh();
          },
        });
        btn.className += ' ' + cls;
        return btn;
      });

      const isRight = ctx.state.picked === q.answer;
      const verdictNode = answered
        ? h('div', { class: 'stack' },
            h('div', { class: 'verdict ' + (isRight ? 'ok' : 'no') }, isRight ? 'Correct' : 'Not quite'),
            q.explain ? h('p', { class: 'muted' }, q.explain) : null
          )
        : null;

      body = h('div', { class: 'stack' }, ...optionButtons, verdictNode);
    } else {
      answered = ctx.state.checked === true;
      const check = () => {
        if (ctx.state.checked) return;
        const typed = (ctx.state.typed || '').trim();
        const res = L.checkAnswer(typed, ...(q.accepted || []));
        const right = res !== L.ANSWER_WRONG;
        if (right) {
          ctx.state.score = (ctx.state.score || 0) + 1;
        }
        ctx.state.checked = true;
        ctx.state.answerRes = res;
        ctx.refresh();
      };

      const input = h('input', {
        class: 'input',
        type: 'text',
        placeholder: 'Type your answer...',
        disabled: answered,
        value: ctx.state.typed || '',
        oninput: (e) => { ctx.state.typed = e.target.value; },
        onkeydown: (e) => { if (e.key === 'Enter') check(); },
      });

      const checkBtn = ui.button('Check', {
        disabled: answered,
        onclick: check,
      });

      let typeVerdict = null;
      if (answered) {
        const res = ctx.state.answerRes;
        const right = res !== L.ANSWER_WRONG;
        let text = '';
        if (res === L.ANSWER_EXACT) {
          text = `Correct: ${q.model}`;
        } else if (res === L.ANSWER_DIACRITICS) {
          text = `Right, but watch the Polish letters: ${q.model}`;
        } else if (res === L.ANSWER_WORD_ORDER || res === L.ANSWER_CLOSE) {
          text = `Almost: ${q.model}`;
        } else {
          text = `Not quite: ${q.model}`;
        }
        typeVerdict = h('div', { class: 'verdict ' + (right ? 'ok' : 'no') }, text);
      }

      body = h('div', { class: 'stack' },
        h('div', { class: 'hstack' }, input, checkBtn),
        typeVerdict
      );
    }

    let actionRow = null;
    if (answered) {
      const isLast = qIdx === totalQ - 1;
      const sayBtn = (q.say && q.say.trim())
        ? ui.button('🔊', { onclick: () => speak(q.say) })
        : h('span');
      const nextBtn = ui.button(isLast ? 'Finish' : 'Next', {
        kind: 'primary',
        onclick: () => {
          stopSpeaking();
          if (isLast) {
            ctx.state.phase = 'done';
          } else {
            ctx.state.q = qIdx + 1;
            delete ctx.state.picked;
            delete ctx.state.checked;
            delete ctx.state.typed;
            delete ctx.state.answerRes;
          }
          ctx.refresh();
        },
      });
      actionRow = h('div', { class: 'spread' }, sayBtn, nextBtn);
    }

    return h('div', { class: 'stack' }, head, body, actionRow);
  }

  // Done phase.
  const n = (lesson.quiz || []).length;
  const score = ctx.state.score || 0;
  const percent = n > 0 ? Math.round((score / n) * 100) : 100;

  if (!ctx.state.saved) {
    ctx.state.saved = true;
    store.update('course', (c) => {
      const current = c || { done: {}, last: lessonId };
      const done = { ...(current.done || {}) };
      const prev = done[lessonId] !== undefined ? done[lessonId] : -1;
      done[lessonId] = Math.max(prev, percent);
      return { ...current, done, last: lessonId };
    }, { done: {}, last: lessonId });
  }

  const header = ui.header(n > 0 ? `${score} of ${n} correct` : 'Lesson completed', { kicker: 'Lesson finished' });
  const currentIdx = allLessons.findIndex(({ lesson: l }) => l.id === lessonId);
  const nextItem = currentIdx >= 0 && currentIdx < allLessons.length - 1 ? allLessons[currentIdx + 1] : null;

  const buttons = [
    nextItem
      ? ui.button('Next lesson', { kind: 'primary', block: true, onclick: () => go(`#/course/${encodeURIComponent(nextItem.lesson.id)}`) })
      : null,
    n > 0
      ? ui.button('Try again', {
          block: true,
          onclick: () => {
            ctx.state.phase = 'learn';
            ctx.state.score = 0;
            ctx.state.q = 0;
            delete ctx.state.saved;
            delete ctx.state.picked;
            delete ctx.state.checked;
            delete ctx.state.typed;
            delete ctx.state.answerRes;
            ctx.refresh();
          },
        })
      : null,
    ui.button('Back to the course', { block: true, onclick: () => go('#/course') }),
  ].filter(Boolean);

  return h('div', { class: 'stack' }, header, ...buttons);
}
