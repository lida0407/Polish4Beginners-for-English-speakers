
import { h, ui, L, settings, data, memOf, statusOf, grade, favourites, lists, noteFor, speak, go } from '../core.js';
import { phraseTools, tappable, openWord } from '../word.js';

function shuffle(arr) {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

export default function view(ctx) {
  ctx.focusMode(true);

  const kind = ctx.params[0] || 'topic';
  const val = ctx.params[1] !== undefined ? ctx.params[1] : 'All';

  // Build deck once per session
  if (ctx.state.deck === undefined) {
    let deck = [];
    const lvl = settings().level;

    if (kind === 'topic') {
      const pool = data.phrases.filter((c) => c.level === lvl && (val === 'All' || c.category === val));
      const due = shuffle(pool.filter((c) => statusOf(c) === 1));
      const fresh = shuffle(pool.filter((c) => statusOf(c) === 0));
      deck = [...due, ...fresh];
      if (deck.length === 0) {
        const learnt = pool.filter((c) => statusOf(c) === 2);
        learnt.sort((a, b) => (memOf(a).dueAt || 0) - (memOf(b).dueAt || 0));
        deck = learnt;
      }
      deck = deck.slice(0, 10);
    } else if (kind === 'all') {
      const pool = data.phrases.filter((c) => c.level === lvl && c.category === val);
      deck = shuffle(pool);
    } else if (kind === 'status') {
      const st = Number(val);
      const pool = data.phrases.filter((c) => statusOf(c) === st);
      deck = shuffle(pool).slice(0, 50);
    } else if (kind === 'fav') {
      deck = favourites();
    } else if (kind === 'list') {
      const list = lists().find((l) => l.name === val);
      deck = list ? [...list.items] : [];
    }

    ctx.state.deck = deck;
    ctx.state.index = 0;
    ctx.state.revealed = false;
    ctx.state.known = 0;
    ctx.state.done = false;
    ctx.state.showEnFirst = deck.map(() => Math.random() < 0.5);
  }

  // Set up keyboard shortcuts once
  if (!ctx.state.hasKeyHandler) {
    ctx.state.hasKeyHandler = true;
    const onKeyDown = (e) => {
      const tag = e.target?.tagName?.toLowerCase();
      if (tag === 'input' || tag === 'textarea' || tag === 'select' || e.target?.isContentEditable) {
        return;
      }
      const st = ctx.state;
      if (st.done || !st.deck || !st.deck.length) return;
      const current = st.deck[st.index];
      if (!current) return;

      if (!st.revealed) {
        if (e.key === ' ' || e.key === 'Enter') {
          e.preventDefault();
          st.revealed = true;
          ctx.refresh();
        }
      } else {
        if (e.key === '1' || e.key === 'ArrowLeft') {
          e.preventDefault();
          if (current.id) grade(current, false);
          if (st.index + 1 >= st.deck.length) {
            st.done = true;
          } else {
            st.index++;
            st.revealed = false;
          }
          ctx.refresh();
        } else if (e.key === '2' || e.key === 'ArrowRight') {
          e.preventDefault();
          if (current.id) grade(current, true);
          st.known++;
          if (st.index + 1 >= st.deck.length) {
            st.done = true;
          } else {
            st.index++;
            st.revealed = false;
          }
          ctx.refresh();
        }
      }
    };
    document.addEventListener('keydown', onKeyDown);
    ctx.onLeave(() => {
      document.removeEventListener('keydown', onKeyDown);
    });
  }

  // Handle empty deck
  if (!ctx.state.deck.length) {
    return ui.empty(
      'Nothing to study here yet.',
      ui.button('Back home', { kind: 'primary', onclick: () => go('#/home') })
    );
  }

  // Finish screen
  if (ctx.state.done) {
    return h('div', { class: 'stack center', style: { padding: '40px 16px' } },
      h('div', { class: 'kicker' }, 'Well done!'),
      h('h1', {}, 'Lesson finished'),
      h('p', { class: 'muted' }, `${ctx.state.known} of ${ctx.state.deck.length} known`),
      h('div', { class: 'hstack', style: { justifyContent: 'center', marginTop: '12px' } },
        ui.button('Again', {
          kind: 'primary',
          onclick: () => {
            delete ctx.state.deck;
            delete ctx.state.index;
            delete ctx.state.revealed;
            delete ctx.state.known;
            delete ctx.state.done;
            delete ctx.state.showEnFirst;
            ctx.refresh();
          },
        }),
        ui.button('Back home', { onclick: () => go('#/home') })
      )
    );
  }

  // Active card screen
  const card = ctx.state.deck[ctx.state.index];
  const isEnFirst = Boolean(ctx.state.showEnFirst[ctx.state.index]);

  const reveal = () => {
    if (!ctx.state.revealed) {
      ctx.state.revealed = true;
      ctx.refresh();
    }
  };

  const topRow = h('div', { class: 'spread' },
    h('div', { style: { flex: '1 1 180px' } }, ui.progress(ctx.state.index / ctx.state.deck.length)),
    h('span', { class: 'small muted' }, `card ${ctx.state.index + 1} / ${ctx.state.deck.length}`),
    h('a', { class: 'back', href: '#/home', 'aria-label': 'Close' }, '×')
  );

  let flashContent;
  if (!ctx.state.revealed) {
    const frontText = isEnFirst
      ? h('div', { class: 'flash-front' }, card.english)
      : h('div', { class: 'flash-front' }, ui.pl(card.polish));
    flashContent = [
      frontText,
      h('div', { class: 'flash-hint' }, 'Odwróć kartę · tap to flip'),
    ];
  } else {
    const plNode = tappable(card.polish, { tag: 'div' });
    plNode.className = 'pl flash-front';

    const userNote = L.notePreview(noteFor(card.polish), 160);

    flashContent = [
      plNode,
      card.phonetic ? h('div', { class: 'phonetic' }, card.phonetic) : null,
      h('div', { class: 'flash-back' }, card.english),
      card.examplePolish
        ? h('div', { class: 'stack', style: { gap: '2px', marginTop: '6px' } },
            tappable(card.examplePolish, { tag: 'div' }),
            card.exampleEnglish ? h('div', { class: 'small muted' }, card.exampleEnglish) : null
          )
        : null,
      card.notes ? h('div', { class: 'small muted' }, card.notes) : null,
      userNote
        ? h('div', { class: 'small', style: { marginTop: '4px' } },
            h('span', { class: 'muted' }, 'Your note: '),
            userNote
          )
        : null,
    ];
  }

  const flashCard = ui.card({
    class: 'flash',
    onclick: reveal,
  }, ...flashContent);

  const toolsRow = h('div', { class: 'hstack', style: { justifyContent: 'center' } },
    phraseTools(card),
    ui.button('Read PL', { small: true, onclick: () => speak(card.polish) }),
    ui.button('More', { small: true, onclick: () => openWord(card) })
  );

  const onGrade = (got) => {
    if (card.id) grade(card, got);
    if (got) ctx.state.known++;
    if (ctx.state.index + 1 >= ctx.state.deck.length) {
      ctx.state.done = true;
    } else {
      ctx.state.index++;
      ctx.state.revealed = false;
    }
    ctx.refresh();
  };

  const buttonArea = !ctx.state.revealed
    ? ui.button('Pokaż odpowiedź · show answer', {
        kind: 'primary',
        block: true,
        onclick: reveal,
      })
    : h('div', { class: 'grid grid-2' },
        ui.button('Jeszcze nie · not yet', {
          kind: 'ghost',
          block: true,
          onclick: () => onGrade(false),
        }),
        ui.button('Umiem! · got it', {
          kind: 'lime',
          block: true,
          onclick: () => onGrade(true),
        })
      );

  return h('div', { class: 'stack', style: { maxWidth: '640px', margin: '0 auto', width: '100%' } },
    topRow,
    flashCard,
    toolsRow,
    buttonArea
  );
}
