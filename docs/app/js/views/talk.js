
// Conversations list and detail view.
import { h, ui, data, store, settings, speak, stopSpeaking, go } from '../core.js';
import { tappable, phraseTools, toggleFavItem } from '../word.js';

export default function view(ctx) {
  if (ctx.params[0]) {
    const dialog = data.dialogs.find((d) => d.id === ctx.params[0]);
    if (!dialog) {
      return h('div', { class: 'view' },
        ui.header('Conversations', { back: '#/talk' }),
        ui.empty('Conversation not found.', ui.button('Back to conversations', { onclick: () => go('#/talk') })),
      );
    }
    return renderDetail(ctx, dialog);
  }
  return renderList(ctx);
}

function renderList(ctx) {
  const total = data.dialogs.length;
  const header = ui.header('Conversations', { kicker: `${total} everyday dialogues` });

  // Scenario counts
  const scenarioCounts = new Map();
  data.dialogs.forEach((d) => {
    scenarioCounts.set(d.scenario, (scenarioCounts.get(d.scenario) || 0) + 1);
  });
  const scenarios = [...scenarioCounts.keys()];

  const selectedScenarios = store.get('talkFilter', []) || [];
  if (ctx.state.filterOpen === undefined) ctx.state.filterOpen = false;
  if (ctx.state.level === undefined) ctx.state.level = 'All';
  if (ctx.state.page === undefined) ctx.state.page = 0;

  // Expandable multi-select filter
  let scenarioFilter;
  if (!ctx.state.filterOpen) {
    const label = selectedScenarios.length === 0
      ? `▸ All scenarios (${scenarios.length})`
      : `▸ ${selectedScenarios.length} of ${scenarios.length} scenarios`;
    scenarioFilter = ui.row(label, null, () => {
      ctx.state.filterOpen = true;
      ctx.refresh();
    });
  } else {
    const chips = scenarios.map((sc) => {
      const on = selectedScenarios.includes(sc);
      return ui.chip(sc, {
        on,
        count: scenarioCounts.get(sc),
        onclick: () => {
          let next;
          if (on) {
            next = selectedScenarios.filter((s) => s !== sc);
          } else {
            next = [...selectedScenarios, sc];
          }
          store.set('talkFilter', next);
          ctx.state.page = 0;
          ctx.refresh();
        },
      });
    });

    const showAllBtn = ui.button('Show all', {
      onclick: () => {
        store.set('talkFilter', []);
        ctx.state.page = 0;
        ctx.refresh();
      },
    });

    const doneBtn = ui.button('Done', {
      kind: 'primary',
      onclick: () => {
        ctx.state.filterOpen = false;
        ctx.refresh();
      },
    });

    scenarioFilter = ui.card({},
      h('div', { class: 'chips' }, ...chips),
      h('div', { class: 'hstack', style: { marginTop: '10px' } }, showAllBtn, doneBtn),
    );
  }

  // Level selector chips
  const presentLevels = [];
  data.dialogs.forEach((d) => {
    if (d.level && !presentLevels.includes(d.level)) presentLevels.push(d.level);
  });
  const levelChips = ['All', ...presentLevels].map((lvl) => ui.chip(lvl, {
    on: ctx.state.level === lvl,
    onclick: () => {
      ctx.state.level = lvl;
      ctx.state.page = 0;
      ctx.refresh();
    },
  }));

  // Filter dialogue cards
  const filtered = data.dialogs.filter((d) => {
    if (selectedScenarios.length > 0 && !selectedScenarios.includes(d.scenario)) {
      return false;
    }
    if (ctx.state.level !== 'All' && d.level !== ctx.state.level) {
      return false;
    }
    return true;
  });

  const totalPages = Math.max(1, Math.ceil(filtered.length / 10));
  if (ctx.state.page >= totalPages) ctx.state.page = totalPages - 1;
  if (ctx.state.page < 0) ctx.state.page = 0;

  if (filtered.length === 0) {
    return h('div', { class: 'view' },
      header,
      scenarioFilter,
      h('div', { class: 'chips' }, ...levelChips),
      ui.empty('No conversations match the selected filters.'),
    );
  }

  const pageItems = filtered.slice(ctx.state.page * 10, (ctx.state.page + 1) * 10);
  const cards = pageItems.map((d) => ui.card({ onclick: () => go('#/talk/' + encodeURIComponent(d.id)) },
    h('h3', {}, d.title),
    ui.pl(d.titlePolish, 'div'),
    h('div', { class: 'hstack' },
      h('span', { class: 'tag' }, d.level),
      h('span', { class: 'small muted' }, d.scenario),
    ),
    d.description ? h('p', { class: 'small', style: { margin: '6px 0 0' } }, d.description) : null,
  ));

  const pager = h('div', { class: 'spread' },
    ui.button('Back', {
      disabled: ctx.state.page === 0,
      onclick: () => {
        ctx.state.page = Math.max(0, ctx.state.page - 1);
        ctx.refresh();
      },
    }),
    h('span', { class: 'small muted' }, `Page ${ctx.state.page + 1} of ${totalPages}`),
    ui.button('Next', {
      disabled: ctx.state.page >= totalPages - 1,
      onclick: () => {
        ctx.state.page = Math.min(totalPages - 1, ctx.state.page + 1);
        ctx.refresh();
      },
    }),
  );

  return h('div', { class: 'view' },
    header,
    scenarioFilter,
    h('div', { class: 'chips' }, ...levelChips),
    h('div', { class: 'grid grid-2' }, ...cards),
    pager,
  );
}

function renderDetail(ctx, dialog) {
  if (ctx.state.showEn === undefined) {
    ctx.state.showEn = settings().showEnglish ?? true;
  }
  if (ctx.state.role === undefined) {
    ctx.state.role = 'off';
  }
  if (ctx.state.playingIndex === undefined) {
    ctx.state.playingIndex = -1;
  }
  if (ctx.state.playing === undefined) {
    ctx.state.playing = false;
  }
  if (ctx.state.runId === undefined) {
    ctx.state.runId = 0;
  }
  if (!ctx.state.revealed) {
    ctx.state.revealed = new Set();
  }

  // Audio and loop cleanup on route leave
  if (!ctx.state._leaveHook) {
    ctx.state._leaveHook = true;
    ctx.onLeave(() => {
      ctx.state.playing = false;
      ctx.state.runId = (ctx.state.runId || 0) + 1;
      stopSpeaking();
    });
  }

  function pause() {
    ctx.state.playing = false;
    ctx.state.runId = (ctx.state.runId || 0) + 1;
    stopSpeaking();
    ctx.refresh();
  }

  async function playDialog(runId) {
    const startIdx = ctx.state.playingIndex >= 0 && ctx.state.playingIndex < dialog.lines.length
      ? ctx.state.playingIndex
      : 0;

    for (let i = startIdx; i < dialog.lines.length; i++) {
      if (!ctx.state.playing || ctx.state.runId !== runId) break;

      ctx.state.playingIndex = i;
      ctx.refresh();

      requestAnimationFrame(() => {
        document.querySelector('.line.now')?.scrollIntoView({ block: 'center', behavior: 'smooth' });
      });

      const line = dialog.lines[i];
      const isLearner = ctx.state.role === line.speaker;

      if (isLearner) {
        // Wait 4 seconds for learner to practice speaking
        await new Promise((resolve) => setTimeout(resolve, 4000));
        if (!ctx.state.playing || ctx.state.runId !== runId) break;
        ctx.state.revealed.add(i);
        ctx.refresh();
      } else {
        const ok = await speak(line.polish);
        if (!ok || !ctx.state.playing || ctx.state.runId !== runId) break;
      }

      await new Promise((resolve) => setTimeout(resolve, 500));
      if (!ctx.state.playing || ctx.state.runId !== runId) break;
    }

    if (ctx.state.runId === runId) {
      ctx.state.playing = false;
      if (ctx.state.playingIndex >= dialog.lines.length - 1) {
        ctx.state.playingIndex = -1;
      }
      ctx.refresh();
    }
  }

  function togglePlay() {
    if (ctx.state.playing) {
      pause();
    } else {
      ctx.state.playing = true;
      const runId = ++ctx.state.runId;
      playDialog(runId);
    }
  }

  const roleAPolish = (dialog.roles?.A || 'A').split(' · ')[0].trim();
  const roleBPolish = (dialog.roles?.B || 'B').split(' · ')[0].trim();

  const controls = h('div', { class: 'hstack' },
    ui.button(ctx.state.playing ? '⏸ Pause' : '▶ Play conversation', {
      kind: 'primary',
      onclick: togglePlay,
    }),
    ui.button(ctx.state.showEn ? 'Hide English' : 'Show English', {
      onclick: () => {
        ctx.state.showEn = !ctx.state.showEn;
        ctx.refresh();
      },
    }),
    h('span', { class: 'small muted' }, 'Practise as:'),
    ui.chip('Off', {
      on: ctx.state.role === 'off',
      onclick: () => {
        ctx.state.role = 'off';
        ctx.state.revealed = new Set();
        ctx.refresh();
      },
    }),
    ui.chip(roleAPolish, {
      on: ctx.state.role === 'A',
      onclick: () => {
        ctx.state.role = 'A';
        ctx.state.revealed = new Set();
        ctx.refresh();
      },
    }),
    ui.chip(roleBPolish, {
      on: ctx.state.role === 'B',
      onclick: () => {
        ctx.state.role = 'B';
        ctx.state.revealed = new Set();
        ctx.refresh();
      },
    }),
  );

  // Selected phrase floating action bar
  function handleSelection() {
    const sel = window.getSelection() ? window.getSelection().toString().trim() : '';
    if (sel.length >= 2 && sel.length <= 80) {
      if (ctx.state.selection !== sel) {
        ctx.state.selection = sel;
        ctx.refresh();
      }
    }
  }

  const selectionBar = ctx.state.selection
    ? h('div', { class: 'card card-soft spread' },
        h('div', { class: 'hstack' },
          h('span', { class: 'small muted' }, 'Selected:'),
          ui.pl(ctx.state.selection),
        ),
        h('div', { class: 'hstack' },
          ui.button('★ Save phrase', {
            kind: 'lime',
            small: true,
            onclick: () => {
              toggleFavItem({ polish: ctx.state.selection, english: '' });
              ui.toast('Saved to favourites');
              ctx.state.selection = '';
              ctx.refresh();
            },
          }),
          ui.button('Dismiss', {
            small: true,
            onclick: () => {
              ctx.state.selection = '';
              ctx.refresh();
            },
          }),
        ),
      )
    : null;

  // Dialogue lines
  const lineNodes = dialog.lines.map((line, i) => {
    const roleLabel = line.speaker === 'B' ? (dialog.roles?.B || 'B') : (dialog.roles?.A || 'A');
    const isLearner = ctx.state.role === line.speaker;
    const isCovered = isLearner && !ctx.state.revealed.has(i);

    let lineContent;
    if (isCovered) {
      lineContent = [
        h('div', { class: 'line-who' }, roleLabel),
        h('div', { class: 'flash-hint' }, 'Your turn · tap to reveal'),
        line.english ? h('div', { class: 'small muted' }, line.english) : null,
      ];
    } else {
      lineContent = [
        h('div', { class: 'line-who' }, roleLabel),
        tappable(line.polish, { tag: 'div' }),
        ctx.state.showEn && line.english ? h('div', { class: 'small muted' }, line.english) : null,
        line.note ? h('div', { class: 'small muted', style: { fontStyle: 'italic' } }, line.note) : null,
        phraseTools({ polish: line.polish, english: line.english }),
      ];
    }

    return h('div', {
      class: 'line' + (line.speaker === 'B' ? ' b' : '') + (i === ctx.state.playingIndex ? ' now' : ''),
      onclick: isCovered
        ? () => {
            ctx.state.revealed.add(i);
            ctx.refresh();
          }
        : null,
      style: isCovered ? { cursor: 'pointer' } : null,
    }, ...lineContent);
  });

  const linesContainer = h('div', {
    class: 'stack',
    onmouseup: handleSelection,
    ontouchend: handleSelection,
  }, ...lineNodes);

  return h('div', { class: 'view' },
    ui.header(dialog.title, { back: '#/talk', kicker: `${dialog.scenario} · ${dialog.level}` }),
    ui.pl(dialog.titlePolish, 'h2'),
    controls,
    selectionBar,
    linesContainer,
  );
}
