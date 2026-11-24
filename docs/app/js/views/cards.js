
import { h, ui, L, LEVELS, data, statusOf, go } from '../core.js';
import { openWord, phraseTools } from '../word.js';

const STATUS_NAMES = ['new', 'to review', 'learnt'];

export default function view(ctx) {
  ctx.state.q = ctx.state.q ?? '';
  ctx.state.level = ctx.state.level ?? 'All';
  ctx.state.topic = ctx.state.topic ?? 'All';
  ctx.state.shown = ctx.state.shown ?? 40;

  const header = ui.header('Cards', {
    kicker: `${data.phrases.length} flashcards`,
  });

  const searchInput = h('input', {
    class: 'input',
    type: 'search',
    placeholder: 'Search: dworzec, to eat…',
    value: ctx.state.q,
    oninput: (e) => {
      ctx.state.q = e.target.value;
      ctx.state.shown = 40;
      renderResults();
    },
  });

  const levelChips = h('div', { class: 'chips' },
    ['All', ...LEVELS].map((lvl) => ui.chip(lvl, {
      on: ctx.state.level === lvl,
      onclick: () => {
        ctx.state.level = lvl;
        ctx.state.shown = 40;
        ctx.refresh();
      },
    }))
  );

  const topicSelect = h('select', {
    class: 'input',
    'aria-label': 'Select topic',
    onchange: (e) => {
      ctx.state.topic = e.target.value;
      ctx.state.shown = 40;
      ctx.refresh();
    },
  },
    h('option', { value: 'All', selected: ctx.state.topic === 'All' }, 'All topics'),
    data.topics.map((t) => h('option', {
      value: t.name,
      selected: ctx.state.topic === t.name,
    }, t.name))
  );

  const resultsContainer = h('div', { class: 'stack' });

  function renderResults() {
    const cleanQ = L.stripPolishDiacritics((ctx.state.q || '').trim().toLowerCase());
    const filtered = data.phrases.filter((c) => {
      if (ctx.state.level !== 'All' && c.level !== ctx.state.level) return false;
      if (ctx.state.topic !== 'All' && c.category !== ctx.state.topic) return false;
      if (cleanQ) {
        const pl = L.stripPolishDiacritics((c.polish || '').toLowerCase());
        const en = L.stripPolishDiacritics((c.english || '').toLowerCase());
        if (!pl.includes(cleanQ) && !en.includes(cleanQ)) return false;
      }
      return true;
    });

    const countLine = h('div', { class: 'spread' },
      h('span', { class: 'small muted' }, `${filtered.length} ${filtered.length === 1 ? 'card' : 'cards'}`),
      ctx.state.topic !== 'All'
        ? ui.button('Study this topic', {
            kind: 'primary',
            small: true,
            onclick: () => go('#/session/all/' + encodeURIComponent(ctx.state.topic)),
          })
        : null
    );

    if (filtered.length === 0) {
      resultsContainer.replaceChildren(countLine, ui.empty('No cards match.'));
      return;
    }

    const visibleCards = filtered.slice(0, ctx.state.shown);
    const cardNodes = visibleCards.map((card) => {
      const statusText = STATUS_NAMES[statusOf(card)] || 'new';

      return ui.card({ onclick: () => openWord(card) },
        h('div', { class: 'stack', style: { gap: '6px' } },
          h('div', { class: 'spread' },
            ui.pl(card.polish, 'b'),
            phraseTools(card, { onchange: () => renderResults() })
          ),
          h('div', {}, card.english),
          card.phonetic ? h('div', { class: 'phonetic' }, card.phonetic) : null,
          h('div', { class: 'hstack', style: { gap: '8px', marginTop: '2px' } },
            h('span', { class: 'tag' }, card.level),
            h('span', { class: 'small muted' }, statusText)
          )
        )
      );
    });

    const grid = h('div', { class: 'grid grid-2' }, cardNodes);

    const moreButton = filtered.length > ctx.state.shown
      ? h('div', { class: 'center', style: { marginTop: '8px' } },
          ui.button('Show more', {
            onclick: () => {
              ctx.state.shown += 40;
              renderResults();
            },
          })
        )
      : null;

    resultsContainer.replaceChildren(...[countLine, grid, moreButton].filter(Boolean));
  }

  renderResults();

  return h('div', { class: 'stack' },
    header,
    searchInput,
    levelChips,
    topicSelect,
    resultsContainer
  );
}
