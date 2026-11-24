// The word lookup sheet and tappable Polish text, shared by every view.
import { h, ui, L, store, settings, findCard, speak, listen, canListen, isFav, lists, addToList, noteFor, setNote, copyText } from './core.js';

/** Favourites hold deck card ids, or {polish, english} for phrases that are not deck cards. */
function favKey(item) {
  return item.id || null;
}

export function isFavItem(item) {
  const fav = store.get('fav', []);
  return item.id ? fav.includes(item.id) : fav.some((f) => typeof f === 'object' && f.polish === item.polish);
}

export function toggleFavItem(item) {
  store.update('fav', (fav) => {
    if (isFavItem(item)) return fav.filter((f) => (typeof f === 'string' ? f !== favKey(item) : f.polish !== item.polish));
    return [...fav, item.id || { polish: item.polish, english: item.english || '' }];
  }, []);
  return isFavItem(item);
}

/** A row of small tools for one word or phrase: play, favourite. Refreshes itself. */
export function phraseTools(item, { onchange } = {}) {
  const star = ui.button(isFavItem(item) ? '★' : '☆', {
    small: true, title: 'Favourite',
    onclick: (e) => {
      e.stopPropagation();
      star.textContent = toggleFavItem(item) ? '★' : '☆';
      if (onchange) onchange();
    },
  });
  return h('span', { class: 'hstack' },
    ui.button('🔊', { small: true, title: 'Listen', onclick: (e) => { e.stopPropagation(); speak(item.polish); } }), star);
}

/**
 * Open the lookup sheet for a Polish word or phrase.
 * item: {polish, english?, context? (the sentence it came from), id? (deck card id), level?}
 */
export function openWord(item) {
  const card = item.id ? item : findCard(item.polish);
  const word = { ...item, english: item.english || card?.english || '', id: item.id || card?.id };
  const body = h('div', { class: 'stack' });

  const draw = () => {
    const note = noteFor(word.polish);
    const noteBox = h('textarea', { class: 'input', placeholder: 'Your note, or paste what the AI told you', 'aria-label': 'Note' }, note);
    const verdict = h('div', { class: 'small muted' });
    const sayIt = async () => {
      verdict.textContent = 'Listening… say it in Polish';
      try {
        const heard = await listen('pl-PL');
        if (!heard.length) return (verdict.textContent = 'I did not hear anything. Try again.');
        const best = heard.map((t) => [L.pronunciationScore(word.polish, t), t]).sort((a, b) => b[0] - a[0])[0];
        verdict.replaceChildren(h('b', { class: `verdict ${best[0] >= 80 ? 'ok' : 'no'}` }, `${best[0]}% `), `I heard: “${best[1]}”`);
      } catch (e) {
        verdict.textContent = e.message === 'not-allowed' ? 'Microphone access was blocked for this site.' : 'Speech recognition is not available in this browser. Try Chrome.';
      }
    };
    const chooseList = () => {
      const names = lists().map((l) => l.name);
      const picker = h('div', { class: 'stack' },
        h('h3', {}, 'Add to a list'),
        names.map((name) => ui.row(name, '+', () => { addToList(name, word); ui.toast(`Added to ${name}`); openWord(item); })),
        h('form', { class: 'hstack', onsubmit: (e) => {
          e.preventDefault();
          const name = e.target.elements.name.value.trim();
          if (!name) return;
          addToList(name, word);
          ui.toast(`Added to ${name}`);
          openWord(item);
        } }, h('input', { class: 'input', name: 'name', placeholder: 'New list name', style: { flex: 1 } }), ui.button('Create', { kind: 'primary' })),
      );
      picker.querySelector('form button').type = 'submit';
      body.replaceChildren(picker);
    };
    body.replaceChildren(...[
      h('div', {}, ui.pl(word.polish, 'h2'), word.phonetic || card?.phonetic ? h('div', { class: 'phonetic' }, word.phonetic || card.phonetic) : null),
      word.english ? h('div', { class: 'flash-back' }, word.english) : h('div', { class: 'muted' }, 'Not in the word deck. Use Ask AI for an explanation.'),
      word.context && L.normalizeSpoken(word.context) !== L.normalizeSpoken(word.polish) ? h('div', { class: 'small muted' }, 'From: ', ui.pl(word.context)) : null,
      card?.examplePolish ? h('div', { class: 'small' }, ui.pl(card.examplePolish), h('br'), card.exampleEnglish) : null,
      h('div', { class: 'hstack' },
        ui.button('🔊 Play', { kind: 'primary', onclick: () => speak(word.polish) }),
        ui.button(isFavItem(word) ? '★ Saved' : '☆ Favourite', { onclick: () => { toggleFavItem(word); draw(); } }),
        ui.button('+ List', { onclick: chooseList }),
        canListen() ? ui.button('🎤 Say it', { onclick: sayIt }) : null,
        ui.button('🤖 Ask AI', { title: 'Copy a prompt for ChatGPT, Claude or Gemini', onclick: async () => {
          await copyText(L.explainPrompt(word.polish, word.english, word.context || '', word.level || card?.level || settings().level));
          ui.toast('Prompt copied. Paste it into any AI chat.');
        } }),
      ),
      verdict,
      h('div', { class: 'stack' }, h('div', { class: 'section-title' }, 'Note'), noteBox,
        h('div', {}, ui.button('Save note', { small: true, onclick: () => { setNote(word.polish, noteBox.value); ui.toast('Note saved'); } })))
    ].filter(Boolean));
  };
  draw();
  ui.sheet(body);
}

/**
 * Polish text where every word can be tapped to open the lookup sheet.
 * tappable('Ile to kosztuje?', {english: 'How much is it?'}) -> <span class="pl">…</span>
 */
export function tappable(text, { english = '', tag = 'span' } = {}) {
  const parts = String(text).split(/([\p{L}\p{M}'’-]+)/u);
  return h(tag, { class: 'pl', lang: 'pl' }, parts.map((part, i) => (i % 2
    ? h('span', { class: 'w', onclick: (e) => { e.stopPropagation(); openWord({ polish: part, context: text }); } }, part)
    : part)));
}
