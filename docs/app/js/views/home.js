
import { h, ui, LEVELS, THEMES, setSetting, settings, data, memoryCounts, store, favourites, lists, go, plural } from '../core.js';

const THEME_COLORS = {
  Komiks: '#6c4dff',
  Książka: '#8a2c1f',
  Atrament: '#000000',
};

export default function view(ctx) {
  const s = settings();
  const currentTheme = s.theme;
  const currentLevel = s.level;

  // Header with theme swatches
  const swatches = h('div', { class: 'hstack', style: { gap: '6px' } },
    THEMES.map((name) => h('button', {
      class: `swatch${name === currentTheme ? ' on' : ''}`,
      type: 'button',
      title: name,
      style: { background: THEME_COLORS[name] || '#000000' },
      onclick: () => {
        setSetting('theme', name);
        ctx.refresh();
      },
    }))
  );

  const header = ui.header('Mój polski', {
    kicker: 'Polish4Beginners',
    right: swatches,
  });

  // Main column: level chips, hero card, shortcuts, and topic list
  const levelChips = h('div', { class: 'chips' },
    LEVELS.map((lvl) => ui.chip(lvl, {
      on: lvl === currentLevel,
      onclick: () => {
        setSetting('level', lvl);
        ctx.refresh();
      },
    }))
  );

  const heroCard = ui.card({ class: 'card-hero' },
    h('div', { class: 'stack' },
      h('div', { class: 'kicker' }, "Today's lesson"),
      h('h2', {}, `10 flashcards, level ${currentLevel}`),
      h('div', {},
        ui.button('Zaczynamy · start session', {
          kind: 'lime',
          onclick: () => go('#/session/topic/All'),
        })
      )
    )
  );

  const actionButtons = h('div', { class: 'hstack' },
    ui.button([h('span', { 'aria-hidden': 'true' }, '🎧'), 'Listen'], { onclick: () => go('#/listen') }),
    ui.button([h('span', { 'aria-hidden': 'true' }, '💬'), 'Conversations'], { onclick: () => go('#/talk') }),
    ui.button([h('span', { 'aria-hidden': 'true' }, '🎓'), 'Course'], { onclick: () => go('#/course') }),
  );

  const topicRows = data.topics
    .filter((topic) => (topic.levels?.[currentLevel] || 0) > 0)
    .map((topic) => ui.row(
      topic.name,
      plural(topic.levels[currentLevel], 'card'),
      () => go('#/session/topic/' + encodeURIComponent(topic.name))
    ));

  const topicsSection = ui.section('Topics', h('div', { class: 'grid grid-2' }, topicRows));

  const mainCol = h('div', { class: 'stack' },
    levelChips,
    heroCard,
    actionButtons,
    topicsSection,
  );

  // Side column: Leitner memory stats, course continuation, favourites, word lists
  const [cntNew, cntDue, cntLearnt] = memoryCounts();

  const statCard = ui.card({},
    h('div', { class: 'grid', style: { gridTemplateColumns: 'repeat(3, 1fr)', gap: '4px' } },
      h('button', {
        class: 'stat',
        type: 'button',
        style: { background: 'none', border: 'none', cursor: 'pointer', font: 'inherit', width: '100%' },
        onclick: () => go('#/session/status/0'),
      },
        h('b', {}, cntNew),
        h('span', {}, 'New'),
      ),
      h('button', {
        class: 'stat',
        type: 'button',
        style: { background: 'none', border: 'none', cursor: 'pointer', font: 'inherit', width: '100%' },
        onclick: () => go('#/session/status/1'),
      },
        h('b', {}, cntDue),
        h('span', cntDue > 0 ? { style: { color: 'var(--accent)', fontWeight: '900' } } : {}, 'To review'),
      ),
      h('button', {
        class: 'stat',
        type: 'button',
        style: { background: 'none', border: 'none', cursor: 'pointer', font: 'inherit', width: '100%' },
        onclick: () => go('#/session/status/2'),
      },
        h('b', {}, cntLearnt),
        h('span', {}, 'Learnt'),
      ),
    )
  );

  const courseState = store.get('course', {});
  let courseCard;
  if (courseState?.last) {
    let lessonTitle = '';
    for (const unit of data.guide?.course?.units || []) {
      const found = unit.lessons?.find((l) => l.id === courseState.last);
      if (found) {
        lessonTitle = found.title;
        break;
      }
    }
    courseCard = ui.card({
      class: 'card-soft',
      onclick: () => go('#/course/' + encodeURIComponent(courseState.last)),
    },
      h('div', { class: 'stack' },
        h('div', { class: 'kicker' }, 'Continue the course'),
        h('h3', {}, lessonTitle || 'Current lesson'),
      )
    );
  } else {
    courseCard = ui.card({
      class: 'card-soft',
      onclick: () => go('#/course'),
    },
      h('div', { class: 'stack' },
        h('div', { class: 'kicker' }, 'Grammar course'),
        h('h3', {}, 'Start the grammar course'),
      )
    );
  }

  const favs = favourites();
  const favRow = favs.length > 0
    ? ui.row('★ Favourites', plural(favs.length, 'card'), () => go('#/session/fav'))
    : null;

  const userLists = lists();
  const listSection = userLists.length > 0
    ? ui.section(
        'My word lists',
        ...userLists.map((l) =>
          ui.row(l.name, plural(l.items.length, 'word'), () =>
            go('#/words/' + encodeURIComponent(l.name))
          )
        )
      )
    : null;

  const sideCol = h('div', { class: 'stack' },
    statCard,
    courseCard,
    favRow,
    listSection,
  );

  return h('div', { class: 'stack' },
    header,
    h('div', { class: 'cols' }, mainCol, sideCol),
  );
}
