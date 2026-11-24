
// Guide view: chapter browser and single-chapter reader.
import { h, ui, L, data, go, speak, stopSpeaking } from '../core.js';
import { openWord, tappable } from '../word.js';

// Render an individual span according to its styling flags.
function renderSpan(s) {
  if (!s) return null;
  if (s.pl) {
    const plNode = ui.pl(s.t);
    return s.b ? h('b', {}, plNode) : plNode;
  }
  return s.b ? h('b', {}, s.t) : s.t;
}

/** Render a single content block from the guide; reused by the course. */
export function renderBlock(block, { rows, words } = {}) {
  if (!block) return h('div');

  if (block.type === 'h') {
    return h('h3', {}, block.text || '');
  }

  if (block.type === 'p') {
    return h('p', {}, ...(block.spans || []).map(renderSpan));
  }

  if (block.type === 'list') {
    const tag = block.ordered ? 'ol' : 'ul';
    const items = (block.items || []).map((spans) => h('li', {}, ...spans.map(renderSpan)));
    return h(tag, {}, ...items);
  }

  if (block.type === 'table') {
    const allRows = block.rows || [];
    const tableRows = rows && Array.isArray(rows) && rows.length === 2
      ? allRows.slice(rows[0], rows[1])
      : allRows;
    const thead = h('thead', {}, h('tr', {}, ...(block.header || []).map((th) => h('th', {}, th))));
    const tbody = h('tbody', {}, ...tableRows.map((row) =>
      h('tr', {}, ...row.map((cell) => h('td', {}, ...(cell || []).map(renderSpan))))
    ));
    return h('div', { class: 'table-wrap' }, h('table', {}, thead, tbody));
  }

  if (block.type === 'study') {
    const allEntries = block.entries || [];
    const entries = Array.isArray(words)
      ? allEntries.filter((w) => words.includes(w))
      : allEntries;
    const chips = entries.map((entry) =>
      h('button', {
        class: 'chip',
        type: 'button',
        onclick: () => openWord({ polish: entry }),
      }, ui.pl(entry))
    );
    return h('div', { class: 'chips' }, ...chips);
  }

  if (block.type === 'dialogue') {
    const playBtn = ui.button('Play', {
      small: true,
      onclick: async () => {
        for (const line of block.lines || []) {
          const ok = await speak(line);
          if (!ok) break;
        }
      },
    });
    return ui.card({},
      h('div', { class: 'spread' }, h('h3', {}, block.title || ''), playBtn),
      h('div', { class: 'stack' }, ...(block.lines || []).map((line) => tappable(line, { tag: 'div' }))),
      block.meaning ? h('p', { class: 'muted small' }, block.meaning) : null
    );
  }

  if (block.type === 'exercise') {
    const items = (block.items || []).map((item) => {
      const input = h('input', { class: 'input', type: 'text', placeholder: 'Your answer...' });
      const verdict = h('div', { class: 'verdict' });
      const check = () => {
        const typed = input.value.trim();
        const res = L.checkAnswer(typed, ...(item.a || []), item.full);
        verdict.className = 'verdict ' + (res !== L.ANSWER_WRONG ? 'ok' : 'no');
        if (res === L.ANSWER_EXACT) {
          verdict.textContent = 'Correct';
        } else if (res === L.ANSWER_DIACRITICS) {
          verdict.textContent = `Right, but watch the Polish letters: ${item.full}`;
        } else if (res === L.ANSWER_WORD_ORDER || res === L.ANSWER_CLOSE) {
          verdict.textContent = `Almost: ${item.full}`;
        } else {
          verdict.textContent = `Not quite: ${item.full}`;
        }
      };
      input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') check();
      });
      const checkBtn = ui.button('Check', { small: true, onclick: check });
      return h('div', { class: 'stack' },
        h('p', {}, item.q),
        h('div', { class: 'hstack' }, input, checkBtn),
        verdict
      );
    });
    return ui.card({},
      h('h3', {}, block.prompt || ''),
      h('div', { class: 'stack' }, ...items)
    );
  }

  return h('div');
}

export default function view(ctx) {
  ctx.onLeave?.(() => stopSpeaking());
  const chapters = (data.guide && data.guide.chapters) || [];

  // Single chapter reader.
  if (ctx.params && ctx.params.length > 0) {
    const chapterId = ctx.params[0];
    const idx = chapters.findIndex((c) => String(c.id) === String(chapterId));
    if (idx === -1) {
      go('#/guide');
      return h('div');
    }
    const chapter = chapters[idx];
    const header = ui.header(chapter.title, { back: '#/guide', kicker: `Chapter ${chapter.id}` });
    const blocks = (chapter.blocks || []).map((b) => renderBlock(b));
    const prose = h('article', { class: 'prose' }, ...blocks);
    const prev = idx > 0 ? chapters[idx - 1] : null;
    const next = idx < chapters.length - 1 ? chapters[idx + 1] : null;
    const navRow = h('div', { class: 'spread' },
      prev ? ui.button('Previous chapter', { onclick: () => go(`#/guide/${encodeURIComponent(prev.id)}`) }) : h('span'),
      next ? ui.button('Next chapter', { kind: 'primary', onclick: () => go(`#/guide/${encodeURIComponent(next.id)}`) }) : h('span')
    );
    return h('div', { class: 'stack' }, header, prose, navRow);
  }

  // Chapters list.
  const title = (data.guide && data.guide.title) || 'Grammar & Vocabulary Guide';
  const header = ui.header(title, { kicker: 'Reference' });
  const desc = h('p', { class: 'muted' }, 'The full guide, chapter by chapter. For step-by-step lessons with quizzes use the course.');
  const courseBtn = ui.button('Go to course', { kind: 'primary', onclick: () => go('#/course') });
  const rows = chapters.map((ch) =>
    ui.row(`${ch.id} · ${ch.title}`, '→', () => go(`#/guide/${encodeURIComponent(ch.id)}`))
  );
  const grid = h('div', { class: 'grid grid-2' }, ...rows);

  return h('div', { class: 'stack' }, header, desc, courseBtn, grid);
}
