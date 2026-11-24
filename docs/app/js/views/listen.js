
// Hands-free listening practice view.
import { h, ui, data, store, settings, canSpeak, speak, stopSpeaking, lists, favourites } from '../core.js';
import { openWord } from '../word.js';

export default function view(ctx) {
  if (!canSpeak()) {
    return h('div', { class: 'view' },
      ui.header('Listen', { kicker: 'Hands-free practice' }),
      ui.empty('This browser cannot read aloud. Speech synthesis is not supported.')
    );
  }

  const saved = store.get('listen', {}) || {};
  const level = settings().level || 'A1';

  if (ctx.state.source === undefined) {
    ctx.state.source = saved.source || 'all';
  }
  if (ctx.state.showEnglish === undefined) {
    ctx.state.showEnglish = saved.showEnglish !== undefined ? saved.showEnglish : (settings().showEnglish ?? true);
  }
  if (ctx.state.index === undefined) ctx.state.index = 0;
  if (ctx.state.playing === undefined) ctx.state.playing = false;
  if (ctx.state.runId === undefined) ctx.state.runId = 0;

  // Resolve items for the active source
  let items = [];
  const src = ctx.state.source;
  if (src === 'fav') {
    items = favourites();
  } else if (src.startsWith('list:')) {
    const listName = src.slice(5);
    const found = lists().find((l) => l.name === listName);
    items = found ? found.items : [];
  } else if (src === 'all') {
    items = data.phrases.filter((p) => p.level === level);
  } else if (src.startsWith('topic:')) {
    const cat = src.slice(6);
    items = data.phrases.filter((p) => p.category === cat && p.level === level);
  }

  if (items.length > 0 && ctx.state.index >= items.length) {
    ctx.state.index = 0;
  }

  // Keep screen awake while playing
  async function acquireWakeLock() {
    try {
      if ('wakeLock' in navigator && !ctx.state.wakeLock) {
        ctx.state.wakeLock = await navigator.wakeLock.request('screen');
      }
    } catch (e) {
      // Ignore wake lock denial
    }
  }

  function releaseWakeLock() {
    if (ctx.state.wakeLock) {
      ctx.state.wakeLock.release().catch(() => {});
      ctx.state.wakeLock = null;
    }
  }

  function stopPlayback() {
    ctx.state.playing = false;
    ctx.state.runId = (ctx.state.runId || 0) + 1;
    stopSpeaking();
    releaseWakeLock();
  }

  // Cancel timers, audio and wake lock when leaving
  if (!ctx.state._leaveHook) {
    ctx.state._leaveHook = true;
    ctx.onLeave(() => {
      stopPlayback();
    });
  }

  function setSource(nextSource) {
    stopPlayback();
    ctx.state.source = nextSource;
    ctx.state.index = 0;
    store.set('listen', { source: nextSource, showEnglish: ctx.state.showEnglish });
    ctx.refresh();
  }

  async function runLoop(runId) {
    while (ctx.state.playing && ctx.state.runId === runId) {
      const cur = items[ctx.state.index];
      if (!cur) break;

      // Polish twice
      let ok = await speak(cur.polish);
      if (!ok || ctx.state.runId !== runId || !ctx.state.playing) break;

      ok = await speak(cur.polish);
      if (!ok || ctx.state.runId !== runId || !ctx.state.playing) break;

      // Optional English reading
      if (ctx.state.showEnglish && cur.english) {
        ok = await speak(cur.english, { lang: 'en-US' });
        if (!ok || ctx.state.runId !== runId || !ctx.state.playing) break;
      }

      await new Promise((resolve) => setTimeout(resolve, 900));
      if (ctx.state.runId !== runId || !ctx.state.playing) break;

      ctx.state.index = (ctx.state.index + 1) % items.length;
      ctx.refresh();
    }

    if (ctx.state.runId === runId) {
      ctx.state.playing = false;
      releaseWakeLock();
      ctx.refresh();
    }
  }

  function togglePlay() {
    if (ctx.state.playing) {
      stopPlayback();
      ctx.refresh();
    } else {
      if (!items.length) return;
      ctx.state.playing = true;
      const runId = ++ctx.state.runId;
      acquireWakeLock();
      ctx.refresh();
      runLoop(runId);
    }
  }

  function prev() {
    if (!items.length) return;
    const nextIdx = (ctx.state.index - 1 + items.length) % items.length;
    ctx.state.index = nextIdx;
    if (ctx.state.playing) {
      stopSpeaking();
      const runId = ++ctx.state.runId;
      ctx.refresh();
      runLoop(runId);
    } else {
      ctx.refresh();
    }
  }

  function next() {
    if (!items.length) return;
    const nextIdx = (ctx.state.index + 1) % items.length;
    ctx.state.index = nextIdx;
    if (ctx.state.playing) {
      stopSpeaking();
      const runId = ++ctx.state.runId;
      ctx.refresh();
      runLoop(runId);
    } else {
      ctx.refresh();
    }
  }

  function jumpTo(targetIdx) {
    ctx.state.index = targetIdx;
    if (ctx.state.playing) {
      stopSpeaking();
      const runId = ++ctx.state.runId;
      ctx.refresh();
      runLoop(runId);
    } else {
      ctx.refresh();
    }
  }

  function toggleEnglish() {
    ctx.state.showEnglish = !ctx.state.showEnglish;
    store.set('listen', { source: ctx.state.source, showEnglish: ctx.state.showEnglish });
    ctx.refresh();
  }

  // Source selection chips
  const sourceChips = [
    ui.chip('★ Favourites', {
      on: ctx.state.source === 'fav',
      onclick: () => setSource('fav'),
    }),
    ...lists().map((l) => ui.chip(l.name, {
      on: ctx.state.source === `list:${l.name}`,
      onclick: () => setSource(`list:${l.name}`),
    })),
    ui.chip(`All ${level}`, {
      on: ctx.state.source === 'all',
      onclick: () => setSource('all'),
    }),
    ...data.topics
      .filter((t) => (t.levels?.[level] || 0) > 0)
      .map((t) => ui.chip(t.name, {
        on: ctx.state.source === `topic:${t.name}`,
        onclick: () => setSource(`topic:${t.name}`),
      })),
  ];

  const header = ui.header('Listen', { kicker: 'Hands-free practice' });
  const chipsWrap = h('div', { class: 'chips' }, ...sourceChips);

  if (items.length === 0) {
    let emptyMsg = 'No cards found for this selection.';
    if (src === 'fav') {
      emptyMsg = 'No favourites yet. Tap ☆ on any card to add it here.';
    } else if (src.startsWith('list:')) {
      emptyMsg = 'This word list is empty. Add words from cards or dialogues.';
    } else if (src.startsWith('topic:')) {
      emptyMsg = `No ${level} cards in this topic yet.`;
    } else if (src === 'all') {
      emptyMsg = `No cards found for level ${level}.`;
    }
    return h('div', { class: 'view' }, header, chipsWrap, ui.empty(emptyMsg));
  }

  const currentItem = items[ctx.state.index];

  // Player card
  const playerCard = ui.card({ class: 'flash' },
    h('div', {
      class: 'flash-front',
      onclick: (e) => {
        e.stopPropagation();
        openWord(currentItem);
      },
    }, ui.pl(currentItem.polish)),
    currentItem.phonetic ? h('div', { class: 'phonetic' }, currentItem.phonetic) : null,
    ctx.state.showEnglish && currentItem.english ? h('div', { class: 'flash-back' }, currentItem.english) : null,
  );

  const positionLabel = h('div', { class: 'small muted', style: { textAlign: 'center' } }, `${ctx.state.index + 1} / ${items.length}`);
  const progressBar = ui.progress((ctx.state.index + 1) / items.length);

  // Centred controls
  const controls = h('div', { class: 'hstack', style: { justifyContent: 'center' } },
    ui.button('Previous', { onclick: prev }),
    ui.button(ctx.state.playing ? '⏸ Pause' : '▶ Play', { kind: 'primary', onclick: togglePlay }),
    ui.button('Next', { onclick: next }),
    ui.button(ctx.state.showEnglish ? 'Hide English' : 'Show English', { onclick: toggleEnglish }),
  );

  // Up next list
  const upNextRows = [];
  const nextCount = Math.min(12, items.length - 1);
  for (let i = 1; i <= nextCount; i++) {
    const targetIdx = (ctx.state.index + i) % items.length;
    const it = items[targetIdx];
    upNextRows.push(
      ui.row(ui.pl(it.polish), it.english || '', () => jumpTo(targetIdx))
    );
  }

  const upNextSection = upNextRows.length > 0
    ? ui.section('Up next', ...upNextRows)
    : null;

  return h('div', { class: 'view' },
    header,
    chipsWrap,
    playerCard,
    positionLabel,
    progressBar,
    controls,
    upNextSection,
  );
}
