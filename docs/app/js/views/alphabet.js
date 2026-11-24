
// Alphabet view: letter tiles and pronunciation examples.
import { h, ui, data, speak, stopSpeaking } from '../core.js';

export default function view(ctx) {
  ctx.onLeave?.(() => stopSpeaking());
  const items = data.alphabet || [];
  const sel = typeof ctx.state.sel === 'number' ? ctx.state.sel : 0;
  const current = items[sel] || items[0] || { letter: '', sound: '', example: '', english: '' };

  const header = ui.header('Alphabet', { kicker: `${items.length} letters and sounds` });

  const hearBtn = ui.button('🔊 Hear the example', {
    kind: 'primary',
    onclick: () => speak(current.example),
  });

  const detailCard = ui.card({ class: 'card-soft' },
    h('div', { class: 'stack center' },
      h('div', { class: 'flash-front', style: { fontSize: '3rem' } }, current.letter),
      h('div', { class: 'muted' }, current.sound),
      h('div', {}, ui.pl(current.example), h('span', { class: 'muted' }, ` · ${current.english}`)),
      hearBtn
    )
  );

  const tiles = items.map((item, i) => {
    const selected = i === sel;
    return ui.card({
      class: 'center' + (selected ? ' card-hero' : ''),
      onclick: () => {
        ctx.state.sel = i;
        speak(item.example);
        ctx.refresh();
      },
    },
      h('h2', {}, item.letter),
      h('div', { class: 'small' }, item.sound)
    );
  });

  const grid = h('div', { class: 'grid grid-tiles' }, ...tiles);

  return h('div', { class: 'stack' }, header, detailCard, grid);
}
