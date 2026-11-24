
// The phone menu with quick links to sections and reference pages.
import { h, ui, go } from '../core.js';

export default function view(ctx) {
  const androidUrl = '../'; // the download page; switch to the Play listing once it is live

  return h('div', { class: 'stack' },
    ui.header('More'),
    h('div', { class: 'stack' },
      ui.row('Grammar course', '→', () => go('#/course'), '52 lessons with quizzes'),
      ui.row('Grammar & vocabulary guide', '→', () => go('#/guide'), 'The full reference'),
      ui.row('Grammar basics', '→', () => go('#/grammar'), '23 quick lessons'),
      ui.row('Alphabet', '→', () => go('#/alphabet'), '39 letters and sounds'),
      ui.row('My words', '→', () => go('#/words'), 'Favourites, lists and notes'),
      ui.row('Settings', '→', () => go('#/settings'), 'Theme, voice, account'),
    ),
    h('div', { style: { marginTop: '8px' } },
      h('a', {
        class: 'btn btn-block',
        href: androidUrl,
        target: '_blank',
        rel: 'noopener noreferrer',
      }, 'Get the Android app'),
    ),
  );
}
