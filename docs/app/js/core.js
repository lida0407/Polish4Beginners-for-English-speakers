// Polish4Beginners web app: the shared core. Every view imports from here.
// Contract for view authors: web-build/CORE_API.md.
import * as L from './logic.js';

export { L };
export const APP_VERSION = '1.50';
export const LEVELS = ['A1', 'A2', 'B1', 'B2', 'C1'];
export const THEMES = ['Komiks', 'Książka', 'Atrament'];
export const SPEEDS = { slowest: 0.55, slow: 0.75, normal: 1, fast: 1.2, fastest: 1.4 };

// ---------------------------------------------------------------- DOM builder

/** h('div', {class: 'card', onclick: fn}, 'text', node, [more], null) -> Element */
export function h(tag, attrs, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === null || v === undefined || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
    else if (k === 'dataset') Object.assign(el.dataset, v);
    else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k === 'value' || k === 'checked' || k === 'disabled' || k === 'selected') el[k] = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  const add = (kid) => {
    if (kid === null || kid === undefined || kid === false) return;
    if (Array.isArray(kid)) kid.forEach(add);
    else el.append(kid instanceof Node ? kid : document.createTextNode(String(kid)));
  };
  kids.forEach(add);
  return el;
}

// ---------------------------------------------------------------------- store
// Everything the learner owns lives in localStorage under "p4b:<key>" as JSON.
// Each key carries a last-changed time so sign-in sync can merge per key.

const PREFIX = 'p4b:';
const listeners = new Set();
let stamps = readRaw('_t', {});

function readRaw(key, def) {
  try {
    const raw = localStorage.getItem(PREFIX + key);
    return raw === null ? def : JSON.parse(raw);
  } catch (e) {
    return def;
  }
}

export const store = {
  get(key, def = null) {
    return readRaw(key, def);
  },
  set(key, value, { silent = false, at = Date.now() } = {}) {
    localStorage.setItem(PREFIX + key, JSON.stringify(value));
    stamps[key] = at;
    localStorage.setItem(PREFIX + '_t', JSON.stringify(stamps));
    if (!silent) listeners.forEach((fn) => fn(key, value));
  },
  /** store.update('fav', (list) => [...list, id], []) */
  update(key, fn, def = null) {
    const next = fn(this.get(key, def));
    this.set(key, next);
    return next;
  },
  on(fn) {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },
  stamp(key) {
    return stamps[key] || 0;
  },
  /** Every synced key -> {v, t}. Keys starting with "_" stay on this device. */
  dump() {
    const out = {};
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i);
      if (!k.startsWith(PREFIX)) continue;
      const key = k.slice(PREFIX.length);
      if (key.startsWith('_')) continue;
      out[key] = { v: localStorage.getItem(k), t: stamps[key] || 0 };
    }
    return out;
  },
  /** Take a remote dump; newer remote keys win. Returns the keys that changed. */
  merge(remote) {
    const changed = [];
    for (const [key, entry] of Object.entries(remote || {})) {
      if (!entry || typeof entry.v !== 'string' || key.startsWith('_')) continue;
      if ((entry.t || 0) > (stamps[key] || 0)) {
        localStorage.setItem(PREFIX + key, entry.v);
        stamps[key] = entry.t;
        changed.push(key);
      }
    }
    if (changed.length) {
      localStorage.setItem(PREFIX + '_t', JSON.stringify(stamps));
      listeners.forEach((fn) => fn('*', changed));
    }
    return changed;
  },
};

const DEFAULT_SETTINGS = { theme: 'Komiks', speed: 'normal', level: 'A1', voicePl: '', voiceEn: '', showEnglish: true };

export function settings() {
  return { ...DEFAULT_SETTINGS, ...store.get('settings', {}) };
}

export function setSetting(key, value) {
  store.set('settings', { ...settings(), [key]: value });
  if (key === 'theme') applyTheme();
}

export function applyTheme() {
  const theme = THEMES.includes(settings().theme) ? settings().theme : 'Komiks';
  document.documentElement.dataset.theme = theme;
}

// ----------------------------------------------------------------------- data

/**
 * data.phrases[]        cards: {id, polish, english, phonetic, examplePolish, exampleEnglish, level, category, notes?, declension?, tags?}
 * data.topics[]         {name, count, levels: {A1: n, ...}}, in deck order
 * data.dialogs[]        {id, title, titlePolish, level, scenario, description, roles: {A, B}, lines: [{speaker, polish, english, note?}]}
 * data.guide            {title, chapters: [{id, title, blocks: [...]}], course: {units: [...]}}
 * data.grammarLessons[] {unit, level, scenario, topic, focus, rule, pattern, examples: [{polish, english}], check: {prompt, answer, hint}}
 * data.alphabet[]       {letter, sound, example, english}
 */
export const data = { phrases: [], topics: [], dialogs: [], guide: null, grammarLessons: [], alphabet: [], byId: new Map(), byPolish: new Map() };

async function getJson(url) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`${url}: ${res.status}`);
  return res.json();
}

export async function loadData() {
  const [phrases, dialogs, guide, lessons, alphabet] = await Promise.all([
    getJson('../phrases.json'), getJson('../dialogs.json'), getJson('data/grammar_guide.json'),
    getJson('data/grammar_lessons.json'), getJson('data/alphabet.json'),
  ]);
  const topics = new Map();
  phrases.forEach((p) => {
    // Same key the Android app stores progress under.
    p.id = L.cardKey(p.coreIndex || 0, p.category, p.polish);
    data.byId.set(p.id, p);
    const plain = L.normalizeHeadword(p.polish);
    if (!data.byPolish.has(plain)) data.byPolish.set(plain, p);
    const t = topics.get(p.category) || { name: p.category, count: 0, levels: {} };
    t.count++;
    t.levels[p.level] = (t.levels[p.level] || 0) + 1;
    topics.set(p.category, t);
  });
  Object.assign(data, { phrases, dialogs, guide, grammarLessons: lessons, alphabet, topics: [...topics.values()] });
  return data;
}

/** Find the deck card for a Polish word or phrase, or null. */
export function findCard(polish) {
  return data.byPolish.get(L.normalizeHeadword(polish || '')) || null;
}

// --------------------------------------------------------- memory, favourites

/** Leitner memory of one card: {box, dueAt}. box 0 = never graded. */
export function memOf(card) {
  const m = store.get('mem', {})[card.id];
  return m ? { box: m[0], dueAt: m[1] } : { box: 0, dueAt: 0 };
}

/** 0 = new, 1 = due for review, 2 = learnt (scheduled ahead). */
export function statusOf(card) {
  const m = memOf(card);
  return L.statusOf(m.box, m.dueAt, Date.now());
}

/** Record "Umiem!" (true) or "Jeszcze nie" (false) for a card. */
export function grade(card, got) {
  const now = Date.now();
  const box = L.nextBox(memOf(card).box, got);
  store.update('mem', (mem) => ({ ...mem, [card.id]: [box, L.nextDueAt(box, got, now)] }), {});
}

export function memoryCounts() {
  const counts = [0, 0, 0];
  const mem = store.get('mem', {});
  const now = Date.now();
  for (const p of data.phrases) {
    const m = mem[p.id];
    counts[m ? L.statusOf(m[0], m[1], now) : 0]++;
  }
  return counts; // [new, due, learnt]
}

export function isFav(id) {
  return store.get('fav', []).includes(id);
}

export function toggleFav(id) {
  store.update('fav', (list) => (list.includes(id) ? list.filter((x) => x !== id) : [...list, id]), []);
  return isFav(id);
}

/** Favourite cards, newest first. A favourite can be a deck card id or a free phrase {polish, english}. */
export function favourites() {
  return store.get('fav', []).map((f) => (typeof f === 'string' ? data.byId.get(f) : f)).filter(Boolean).reverse();
}

/** The learner's own word lists: [{name, items: [{polish, english}]}]. */
export function lists() {
  return store.get('lists', []);
}

export function addToList(name, item) {
  store.update('lists', (all) => {
    const next = all.map((l) => ({ ...l, items: [...l.items] }));
    let list = next.find((l) => l.name === name);
    if (!list) next.push((list = { name, items: [] }));
    if (!list.items.some((x) => x.polish === item.polish)) list.items.push({ polish: item.polish, english: item.english || '' });
    return next;
  }, []);
}

export function noteFor(polish) {
  return store.get('notes', {})[L.normalizeHeadword(polish)] || '';
}

export function setNote(polish, text) {
  store.update('notes', (notes) => {
    const next = { ...notes };
    const key = L.normalizeHeadword(polish);
    if (text && text.trim()) next[key] = text.trim();
    else delete next[key];
    return next;
  }, {});
}

// --------------------------------------------------------------------- speech

let voices = [];
function refreshVoices() {
  voices = window.speechSynthesis ? window.speechSynthesis.getVoices() : [];
}
if (window.speechSynthesis) {
  refreshVoices();
  window.speechSynthesis.addEventListener('voiceschanged', refreshVoices);
}

export const canSpeak = () => 'speechSynthesis' in window;
export const voicesFor = (lang) => voices.filter((v) => v.lang.toLowerCase().startsWith(lang.slice(0, 2).toLowerCase()));

let speakToken = 0;
/** Read text aloud. lang 'pl-PL' (default) or 'en-US'. Resolves when it ends, is cut off, or cannot play. */
export function speak(text, { lang = 'pl-PL', rate } = {}) {
  return new Promise((resolve) => {
    if (!canSpeak() || !text) return resolve(false);
    const token = ++speakToken;
    window.speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(String(text).replace(/[_…]+/g, ' '));
    const s = settings();
    const wanted = lang.startsWith('pl') ? s.voicePl : s.voiceEn;
    const pool = voicesFor(lang);
    u.voice = pool.find((v) => v.voiceURI === wanted) || pool.find((v) => v.localService) || pool[0] || null;
    u.lang = lang;
    u.rate = rate || SPEEDS[s.speed] || 1;
    const done = (ok) => () => resolve(ok && token === speakToken);
    u.onend = done(true);
    u.onerror = done(false);
    window.speechSynthesis.speak(u);
  });
}

export function stopSpeaking() {
  speakToken++;
  if (canSpeak()) window.speechSynthesis.cancel();
}

const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
export const canListen = () => !!Recognition;

/** Listen once. Resolves with what was heard (best guesses first, [] if nothing); rejects with Error(code) when the mic cannot be used. */
export function listen(lang = 'pl-PL') {
  return new Promise((resolve, reject) => {
    if (!Recognition) return reject(new Error('unsupported'));
    stopSpeaking();
    const rec = new Recognition();
    rec.lang = lang;
    rec.maxAlternatives = 5;
    rec.interimResults = false;
    let heard = [];
    rec.onresult = (e) => {
      heard = [...e.results[0]].map((alt) => alt.transcript);
    };
    rec.onerror = (e) => (e.error === 'no-speech' ? resolve([]) : reject(new Error(e.error)));
    rec.onend = () => resolve(heard);
    rec.start();
  });
}

/** plural(1, 'word') -> '1 word'; plural(3, 'word') -> '3 words' */
export const plural = (n, word) => `${n} ${word}${n === 1 ? '' : 's'}`;

// ---------------------------------------------------------------- navigation

/** go('#/cards') or go('#/session/Food') */
export function go(hash) {
  location.hash = hash;
}

export async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch (e) {
    const area = h('textarea', { style: { position: 'fixed', opacity: 0 } }, text);
    document.body.append(area);
    area.select();
    const ok = document.execCommand('copy');
    area.remove();
    return ok;
  }
}

export function downloadText(filename, text, type = 'text/markdown') {
  const url = URL.createObjectURL(new Blob([text], { type: `${type};charset=utf-8` }));
  h('a', { href: url, download: filename }).click();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

// ---------------------------------------------------------------- UI helpers

let toastTimer = 0;

export const ui = {
  /** kind: 'primary' | 'lime' | 'ghost' (default) | 'danger'. opts: {onclick, small, block, disabled, title, icon} */
  button(label, { kind = 'ghost', onclick, small, block, disabled, title } = {}) {
    return h('button', { class: `btn btn-${kind}${small ? ' btn-sm' : ''}${block ? ' btn-block' : ''}`, type: 'button', onclick, disabled, title }, label);
  },
  /** A toggle pill. */
  chip(label, { on = false, onclick, count } = {}) {
    return h('button', { class: `chip${on ? ' on' : ''}`, type: 'button', 'aria-pressed': on ? 'true' : 'false', onclick },
      label, count !== undefined ? h('span', { class: 'chip-count' }, count) : null);
  },
  /** A bordered panel with the hard comic shadow. opts: {class, onclick} */
  card(opts, ...kids) {
    const o = opts || {};
    return h(o.onclick ? 'button' : 'div', { class: `card${o.onclick ? ' card-tap' : ''}${o.class ? ' ' + o.class : ''}`, type: o.onclick ? 'button' : null, onclick: o.onclick }, ...kids);
  },
  /** A full-width list row: label on the left, count or arrow on the right. */
  row(label, right, onclick, sub) {
    return h('button', { class: 'row', type: 'button', onclick },
      h('span', { class: 'row-main' }, h('span', { class: 'row-label' }, label), sub ? h('span', { class: 'row-sub' }, sub) : null),
      h('span', { class: 'row-right' }, right ?? '→'));
  },
  section(title, ...kids) {
    return h('section', { class: 'section' }, h('h2', { class: 'section-title' }, title), ...kids);
  },
  /** Page header: title, optional back link ('#/home') and right-side nodes. */
  header(title, { back, kicker, right } = {}) {
    return h('header', { class: 'page-head' },
      back ? h('a', { class: 'back', href: back, 'aria-label': 'Back' }, '←') : null,
      h('div', { class: 'page-head-text' }, kicker ? h('div', { class: 'kicker' }, kicker) : null, h('h1', {}, title)),
      right ? h('div', { class: 'page-head-right' }, right) : null);
  },
  progress(fraction) {
    return h('div', { class: 'progress', role: 'progressbar', 'aria-valuenow': Math.round(fraction * 100) },
      h('div', { class: 'progress-fill', style: { width: `${Math.max(0, Math.min(1, fraction)) * 100}%` } }));
  },
  empty(text, action) {
    return h('div', { class: 'empty' }, h('p', {}, text), action || null);
  },
  /** Polish text, styled as Polish (colour, or bold in the e-ink theme). */
  pl(text, tag = 'span') {
    return h(tag, { class: 'pl', lang: 'pl' }, text);
  },
  toast(text) {
    document.querySelector('.toast')?.remove();
    const el = h('div', { class: 'toast', role: 'status' }, text);
    document.body.append(el);
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.remove(), 2600);
  },
  /** Bottom sheet on phones, centred dialog on desktop. Returns {close}. */
  sheet(content, { onclose } = {}) {
    ui.closeSheet();
    const close = () => {
      wrap.remove();
      document.removeEventListener('keydown', onKey);
      if (onclose) onclose();
    };
    const onKey = (e) => e.key === 'Escape' && close();
    const wrap = h('div', { class: 'sheet-wrap', onclick: (e) => e.target === wrap && close() },
      h('div', { class: 'sheet', role: 'dialog', 'aria-modal': 'true' },
        h('button', { class: 'sheet-close', type: 'button', 'aria-label': 'Close', onclick: close }, '×'), content));
    document.addEventListener('keydown', onKey);
    document.body.append(wrap);
    return { close };
  },
  closeSheet() {
    document.querySelector('.sheet-wrap')?.remove();
  },
};
