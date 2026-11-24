
// Words view: favourites, custom word lists and notes, with export and import.
import { h, ui, L, store, favourites, lists, noteFor, downloadText, go, plural } from '../core.js';
import { openWord, phraseTools } from '../word.js';

function detectSeparator(firstLine) {
  const counts = { ',': 0, ';': 0, '\t': 0 };
  let inQuotes = false;
  for (let i = 0; i < firstLine.length; i++) {
    const ch = firstLine[i];
    if (ch === '"') inQuotes = !inQuotes;
    else if (!inQuotes && ch in counts) counts[ch]++;
  }
  if (counts['\t'] > counts[','] && counts['\t'] > counts[';']) return '\t';
  if (counts[';'] > counts[',']) return ';';
  return ',';
}

function parseDelimited(text) {
  const firstLine = text.split(/\r?\n/)[0] || '';
  const sep = detectSeparator(firstLine);
  const rows = [];
  let row = [];
  let cell = '';
  let inQuotes = false;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (ch === '"') {
      if (inQuotes && text[i + 1] === '"') {
        cell += '"';
        i++;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (ch === sep && !inQuotes) {
      row.push(cell.trim());
      cell = '';
    } else if ((ch === '\n' || (ch === '\r' && text[i + 1] === '\n')) && !inQuotes) {
      if (ch === '\r') i++;
      row.push(cell.trim());
      if (row.some((c) => c.length > 0)) rows.push(row);
      row = [];
      cell = '';
    } else {
      cell += ch;
    }
  }
  row.push(cell.trim());
  if (row.some((c) => c.length > 0)) rows.push(row);
  if (rows.length && rows[0][0]?.toLowerCase() === 'polish') {
    rows.shift();
  }
  return rows.map(([polish, english = '']) => ({ polish: polish || '', english: english || '' })).filter((x) => x.polish);
}

function exportMarkdown(title, items) {
  const slug = title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'words';
  let md = `# ${title}\n\n| Polish | English | Note |\n| --- | --- | --- |\n`;
  for (const it of items) {
    const pl = L.markdownCell(it.polish);
    const en = L.markdownCell(it.english || '');
    const nt = L.markdownCell(noteFor(it.polish) || '');
    md += `| ${pl} | ${en} | ${nt} |\n`;
  }
  downloadText(`polish4beginners-${slug}.md`, md, 'text/markdown');
}

function exportCsv(title, items) {
  const slug = title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'list';
  let csv = 'polish,english\n';
  for (const it of items) {
    const pl = (it.polish || '').replace(/"/g, '""');
    const en = (it.english || '').replace(/"/g, '""');
    csv += `"${pl}","${en}"\n`;
  }
  downloadText(`polish4beginners-${slug}.csv`, csv, 'text/csv');
}

export default function view(ctx) {
  const listName = ctx.params?.[0];
  if (listName) {
    return renderListDetail(ctx, listName);
  }
  return renderMainWords(ctx);
}

function renderMainWords(ctx) {
  const favs = favourites();
  const allLists = lists();
  const notes = store.get('notes', {});
  const noteEntries = Object.entries(notes).filter(([_, text]) => text && typeof text === 'string' && text.trim());

  let favContent;
  if (!favs.length) {
    favContent = ui.empty('No favourites yet. Tap ☆ on any card or conversation line.');
  } else {
    const actions = h('div', { class: 'hstack' },
      ui.button('Study', { kind: 'primary', onclick: () => go('#/session/fav') }),
      ui.button('Listen', {
        onclick: () => {
          store.set('listen', { ...store.get('listen', {}), source: 'fav' });
          go('#/listen');
        },
      }),
      ui.button('Export .md', { onclick: () => exportMarkdown('Favourites', favs) }),
    );
    const grid = h('div', { class: 'grid grid-2' },
      favs.map((item) => ui.card({ onclick: () => openWord(item) },
        h('div', { class: 'spread' },
          ui.pl(item.polish),
          phraseTools(item, { onchange: ctx.refresh }),
        ),
        item.english ? h('div', { class: 'muted small' }, item.english) : null,
      )),
    );
    favContent = h('div', { class: 'stack' }, actions, grid);
  }

  const listRows = allLists.map((l) =>
    ui.row(l.name, plural(l.items.length, 'word'), () => go(`#/words/${encodeURIComponent(l.name)}`)),
  );

  const createList = () => {
    const name = (ctx.state.newListName || '').trim();
    if (!name) return;
    const cur = lists();
    if (cur.some((l) => l.name.toLowerCase() === name.toLowerCase())) {
      ui.toast('A list with that name already exists');
      return;
    }
    store.set('lists', [...cur, { name, items: [] }]);
    ctx.state.newListName = '';
    ctx.refresh();
  };

  const nameInput = h('input', {
    class: 'input',
    type: 'text',
    placeholder: 'New list name',
    value: ctx.state.newListName || '',
    oninput: (e) => { ctx.state.newListName = e.target.value; },
    onkeydown: (e) => { if (e.key === 'Enter') createList(); },
  });

  const fileInput = h('input', {
    type: 'file',
    accept: '.csv,.tsv,.txt,text/csv',
    style: { display: 'none' },
    onchange: async () => {
      const file = fileInput.files?.[0];
      if (!file) return;
      const listName = file.name.replace(/\.[^.]+$/, '').trim();
      if (!listName) return;
      const text = await file.text();
      const rows = parseDelimited(text);
      if (!rows.length) {
        ui.toast('No words found in file');
        fileInput.value = '';
        return;
      }
      const cur = lists();
      let target = cur.find((l) => l.name.toLowerCase() === listName.toLowerCase());
      if (!target) {
        target = { name: listName, items: [] };
        cur.push(target);
      }
      const existing = new Set(target.items.map((it) => L.normalizeHeadword(it.polish)));
      let added = 0;
      for (const item of rows) {
        const norm = L.normalizeHeadword(item.polish);
        if (!existing.has(norm)) {
          existing.add(norm);
          target.items.push(item);
          added++;
        }
      }
      store.set('lists', cur);
      fileInput.value = '';
      ui.toast(`${added} words imported`);
      ctx.refresh();
    },
  });

  const listForm = h('div', { class: 'hstack' },
    nameInput,
    ui.button('Create list', { onclick: createList }),
  );

  const importTools = h('div', { class: 'hstack' },
    ui.button('Import CSV', { onclick: () => fileInput.click() }),
    ui.button('Download template', {
      onclick: () => downloadText('polish4beginners-list-template.csv', 'polish,english\ndzień dobry,good morning\n', 'text/csv'),
    }),
    fileInput,
  );

  const notesKids = noteEntries.length > 0
    ? ui.section('Notes',
        ...noteEntries.map(([headword, text]) =>
          ui.row(headword, '→', () => openWord({ polish: headword }), L.notePreview(text, 110)),
        ),
      )
    : null;

  return h('div', { class: 'stack' },
    ui.header('My words', { kicker: 'Favourites, lists and notes' }),
    ui.section('★ Favourites', favContent),
    ui.section('Word lists',
      listRows.length ? listRows : h('p', { class: 'muted small' }, 'No custom lists yet.'),
      listForm,
      importTools,
    ),
    notesKids,
  );
}

function renderListDetail(ctx, listName) {
  const allLists = lists();
  const list = allLists.find((l) => l.name === listName);
  if (!list) {
    return h('div', { class: 'stack' },
      ui.header('Word list', { back: '#/words' }),
      ui.empty('List not found.', ui.button('Back to lists', { onclick: () => go('#/words') })),
    );
  }

  const addWord = () => {
    const pl = (ctx.state.addPolish || '').trim();
    const en = (ctx.state.addEnglish || '').trim();
    if (!pl) return;
    const cur = lists();
    const target = cur.find((l) => l.name === list.name);
    if (target) {
      const norm = L.normalizeHeadword(pl);
      if (target.items.some((it) => L.normalizeHeadword(it.polish) === norm)) {
        ui.toast('Word is already in this list');
        return;
      }
      target.items.push({ polish: pl, english: en });
      store.set('lists', cur);
      ctx.state.addPolish = '';
      ctx.state.addEnglish = '';
      ctx.refresh();
    }
  };

  const renameList = () => {
    const nextName = window.prompt('New list name', list.name);
    if (!nextName) return;
    const trimmed = nextName.trim();
    if (!trimmed || trimmed === list.name) return;
    const cur = lists();
    if (cur.some((l) => l.name.toLowerCase() === trimmed.toLowerCase())) {
      ui.toast('A list with that name already exists');
      return;
    }
    const item = cur.find((l) => l.name === list.name);
    if (item) {
      item.name = trimmed;
      store.set('lists', cur);
      go(`#/words/${encodeURIComponent(trimmed)}`);
    }
  };

  const deleteList = () => {
    if (window.confirm(`Delete list "${list.name}"?`)) {
      const next = lists().filter((l) => l.name !== list.name);
      store.set('lists', next);
      go('#/words');
    }
  };

  const removeWord = (item) => {
    const cur = lists();
    const target = cur.find((l) => l.name === list.name);
    if (target) {
      target.items = target.items.filter((it) => it.polish !== item.polish);
      store.set('lists', cur);
      ctx.refresh();
    }
  };

  const actions = h('div', { class: 'hstack' },
    ui.button('Study', { kind: 'primary', onclick: () => go(`#/session/list/${encodeURIComponent(list.name)}`) }),
    ui.button('Listen', {
      onclick: () => {
        store.set('listen', { ...store.get('listen', {}), source: `list:${list.name}` });
        go('#/listen');
      },
    }),
    ui.button('Export .md', { onclick: () => exportMarkdown(list.name, list.items) }),
    ui.button('Export CSV', { onclick: () => exportCsv(list.name, list.items) }),
    ui.button('Rename', { onclick: renameList }),
    ui.button('Delete list', { kind: 'danger', onclick: deleteList }),
  );

  const addForm = h('div', { class: 'grid grid-3' },
    h('input', {
      class: 'input',
      placeholder: 'Polish',
      value: ctx.state.addPolish || '',
      oninput: (e) => { ctx.state.addPolish = e.target.value; },
      onkeydown: (e) => { if (e.key === 'Enter') addWord(); },
    }),
    h('input', {
      class: 'input',
      placeholder: 'English',
      value: ctx.state.addEnglish || '',
      oninput: (e) => { ctx.state.addEnglish = e.target.value; },
      onkeydown: (e) => { if (e.key === 'Enter') addWord(); },
    }),
    ui.button('Add', { kind: 'primary', onclick: addWord }),
  );

  const itemsGrid = list.items.length === 0
    ? ui.empty('No words in this list yet.')
    : h('div', { class: 'grid grid-2' },
        list.items.map((item) => ui.card({ onclick: () => openWord(item) },
          h('div', { class: 'spread' },
            ui.pl(item.polish),
            h('span', { class: 'hstack' },
              phraseTools(item, { onchange: ctx.refresh }),
              ui.button('Remove', {
                small: true,
                onclick: (e) => {
                  e.stopPropagation();
                  removeWord(item);
                },
              }),
            ),
          ),
          item.english ? h('div', { class: 'muted small' }, item.english) : null,
        )),
      );

  return h('div', { class: 'stack' },
    ui.header(list.name, { back: '#/words', kicker: plural(list.items.length, 'word') }),
    actions,
    addForm,
    itemsGrid,
  );
}
