// App shell: navigation, hash router, start-up.
import { h, ui, loadData, applyTheme, store, stopSpeaking } from './core.js';
import { initSync, account, onAccount } from './sync.js';

// route name -> view module. Each default-exports view(ctx) -> Node.
const ROUTES = {
  home: () => import('./views/home.js'),
  cards: () => import('./views/cards.js'),
  session: () => import('./views/session.js'),
  listen: () => import('./views/listen.js'),
  talk: () => import('./views/talk.js'),
  guide: () => import('./views/guide.js'),
  course: () => import('./views/course.js'),
  grammar: () => import('./views/grammar.js'),
  alphabet: () => import('./views/alphabet.js'),
  words: () => import('./views/words.js'),
  more: () => import('./views/more.js'),
  settings: () => import('./views/settings.js'),
};

// [route, icon, label, where]: where '' = both, 'desk' = sidebar only, 'phone' = tab bar only
const NAV = [
  ['home', '🏠', 'Home', ''], ['cards', '🃏', 'Cards', ''], ['listen', '🎧', 'Listen', ''], ['talk', '💬', 'Talk', ''],
  ['course', '🎓', 'Course', 'desk'], ['guide', '📖', 'Guide', 'desk'], ['grammar', '🧩', 'Grammar', 'desk'],
  ['alphabet', '🔤', 'Alphabet', 'desk'], ['words', '⭐', 'My words', 'desk'], ['settings', '⚙️', 'Settings', 'desk'],
  ['more', '☰', 'More', 'phone'],
];
// Routes that live under "More" on a phone light that tab up.
const UNDER_MORE = ['course', 'guide', 'grammar', 'alphabet', 'words', 'settings'];

const main = document.getElementById('main');
const nav = document.getElementById('nav');
let leaveHandlers = [];
let current = { path: '', state: {} };
let renderSeq = 0;

function parseHash() {
  const raw = location.hash.replace(/^#\/?/, '');
  const [path, query = ''] = raw.split('?');
  const parts = path.split('/').filter(Boolean).map(decodeURIComponent);
  return { name: parts[0] || 'home', params: parts.slice(1), query: new URLSearchParams(query), path };
}

function drawNav(active) {
  nav.replaceChildren(
    h('a', { class: 'wordmark', href: '#/home' }, 'Mój polski', h('small', {}, 'Polish4Beginners')),
    ...NAV.map(([route, icon, label, where]) => h('a', {
      class: `nav-item ${where}${route === active || (route === 'more' && UNDER_MORE.includes(active)) ? ' on' : ''}`,
      href: `#/${route}`, 'aria-current': route === active ? 'page' : null,
    }, h('span', { class: 'nav-icon', 'aria-hidden': 'true' }, icon), label)),
    h('div', { class: 'nav-foot' }, accountBadge()),
  );
}

function accountBadge() {
  if (account.status === 'in') {
    return h('a', { class: 'hstack', href: '#/settings', style: { textDecoration: 'none', color: 'inherit' } },
      account.user.photo ? h('img', { class: 'avatar', src: account.user.photo, alt: '', referrerpolicy: 'no-referrer' }) : null,
      h('span', {}, account.user.name, h('br'), h('span', { class: 'small' }, 'Progress synced')));
  }
  if (account.status === 'out') return ui.button('Sign in to sync', { small: true, onclick: () => (location.hash = '#/settings') });
  return h('span', { class: 'small' }, 'Progress is saved in this browser');
}

async function render({ keepScroll = false } = {}) {
  const route = parseHash();
  const loader = ROUTES[route.name];
  if (!loader) return (location.hash = '#/home');
  const seq = ++renderSeq;
  const samePath = route.path === current.path;
  const scroll = window.scrollY;
  if (!samePath) {
    leaveHandlers.forEach((fn) => fn());
    leaveHandlers = [];
    stopSpeaking();
    ui.closeSheet();
    document.body.classList.remove('focus-mode');
    current = { path: route.path, state: {} };
  }
  const ctx = {
    name: route.name,
    params: route.params,
    query: route.query,
    state: current.state,                       // survives refresh(), reset when the route changes
    refresh: () => render({ keepScroll: true }),
    onLeave: (fn) => leaveHandlers.push(fn),     // runs once when the learner leaves this route
    focusMode: (on) => document.body.classList.toggle('focus-mode', on), // hide the tab bar on phones
  };
  let node;
  try {
    const mod = await loader();
    node = await mod.default(ctx);
  } catch (e) {
    console.error(e);
    node = ui.empty(`Something went wrong on this screen: ${e.message}`, ui.button('Back home', { onclick: () => (location.hash = '#/home') }));
  }
  if (seq !== renderSeq) return;
  const view = h('div', { class: `view view-${route.name}`, tabindex: '-1' }, node);
  main.replaceChildren(view);
  drawNav(route.name);
  if (keepScroll && samePath) window.scrollTo(0, scroll);
  else {
    window.scrollTo(0, 0);
    view.focus({ preventScroll: true });
  }
}

async function registerWorker() {
  if (!('serviceWorker' in navigator) || location.hostname === 'localhost' || location.hostname === '127.0.0.1') return;
  try {
    const { build } = await (await fetch('build.json', { cache: 'no-store' })).json();
    const reg = await navigator.serviceWorker.register(`sw.js?b=${build}`);
    reg.addEventListener('updatefound', () => {
      const fresh = reg.installing;
      fresh?.addEventListener('statechange', () => {
        if (fresh.state === 'activated' && navigator.serviceWorker.controller) ui.toast('Updated. Reload to get the new version.');
      });
    });
  } catch (e) { /* offline: the installed worker keeps serving */ }
}

async function start() {
  applyTheme();
  drawNav(parseHash().name);
  try {
    await loadData();
  } catch (e) {
    main.replaceChildren(ui.empty('Could not load the lessons. Check your connection and reload.', ui.button('Reload', { kind: 'primary', onclick: () => location.reload() })));
    return;
  }
  window.addEventListener('hashchange', () => render());
  onAccount(() => drawNav(parseHash().name));
  // A sync from another device changed stored data: redraw what is on screen.
  store.on((key) => key === '*' && (applyTheme(), render({ keepScroll: true })));
  await render();
  initSync();
  registerWorker();
}

start();
