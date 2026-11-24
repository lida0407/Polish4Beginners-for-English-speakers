
// Settings view: account sync, theme, learning level, voices, backup and about.
import { h, ui, store, settings, setSetting, data, voicesFor, speak, canSpeak, downloadText, APP_VERSION, LEVELS, THEMES, SPEEDS } from '../core.js';
import { account, onAccount, signIn, signOut, deleteAccount } from '../sync.js';

export default function view(ctx) {
  // Subscribe to sync account updates once
  if (!ctx.state.subscribedAccount) {
    ctx.state.subscribedAccount = true;
    const unsub = onAccount(() => ctx.refresh());
    ctx.onLeave(unsub);
  }

  // Listen once for delayed voice loading
  if (typeof window !== 'undefined' && window.speechSynthesis && !ctx.state.subscribedVoices) {
    ctx.state.subscribedVoices = true;
    const onVoices = () => ctx.refresh();
    window.speechSynthesis.addEventListener('voiceschanged', onVoices);
    ctx.onLeave(() => window.speechSynthesis.removeEventListener('voiceschanged', onVoices));
  }

  // Fetch build info once
  if (ctx.state.build === undefined) {
    ctx.state.build = null;
    fetch('build.json', { cache: 'no-store' })
      .then((res) => (res.ok ? res.json() : null))
      .then((b) => {
        ctx.state.build = b;
        ctx.refresh();
      })
      .catch(() => {});
  }

  // 1. Account
  const accountKids = [h('h2', {}, 'Account')];
  if (account.status === 'off') {
    accountKids.push(
      h('p', { class: 'muted' }, 'Your progress is saved in this browser. Sign-in is not switched on for this site yet.'),
    );
  } else if (account.status === 'out') {
    const handleSignIn = async (provider) => {
      try {
        await signIn(provider);
      } catch (e) {
        ui.toast(e.message || 'Could not sign in');
      }
    };
    accountKids.push(
      h('p', {}, 'Sign in to keep your progress on every device.'),
      h('div', { class: 'hstack' },
        ui.button('Continue with Google', { kind: 'primary', onclick: () => handleSignIn('google') }),
        ui.button('Continue with GitHub', { onclick: () => handleSignIn('github') }),
      ),
      h('p', { class: 'small muted' },
        'We store your study progress and the name on your account, nothing else. ',
        h('a', { href: '../privacy.html', target: '_blank', rel: 'noopener noreferrer' }, 'Privacy policy'),
      ),
    );
  } else if (account.status === 'in') {
    const syncTime = account.lastSync ? ` ${new Date(account.lastSync).toLocaleTimeString()}` : '';
    accountKids.push(
      h('div', { class: 'hstack' },
        account.user?.photo ? h('img', { class: 'avatar', src: account.user.photo, alt: '', referrerpolicy: 'no-referrer' }) : null,
        h('div', {},
          h('div', { style: { fontWeight: '700' } }, account.user?.name || 'Signed in'),
          h('div', { class: 'small muted' }, `Progress synced${syncTime}`),
        ),
      ),
      account.error ? h('div', { class: 'verdict no' }, account.error) : null,
      h('div', { class: 'hstack' },
        ui.button('Sign out', {
          onclick: async () => {
            await signOut();
            ctx.refresh();
          },
        }),
        ui.button('Delete my account and synced data', {
          kind: 'danger',
          small: true,
          onclick: async () => {
            if (window.confirm('Delete your synced account and data? Progress on this device will be kept.')) {
              await deleteAccount();
              ctx.refresh();
            }
          },
        }),
      ),
    );
  } else if (account.status === 'error') {
    accountKids.push(h('p', { class: 'verdict no' }, account.error || 'An error occurred.'));
  }

  // 2. Theme
  const themeColors = { Komiks: '#6c4dff', 'Książka': '#8a2c1f', Atrament: '#000000' };
  const curTheme = settings().theme;
  const themeChips = THEMES.map((name) => {
    const swatch = h('span', {
      class: `swatch${curTheme === name ? ' on' : ''}`,
      style: { background: themeColors[name], width: '16px', height: '16px', display: 'inline-block', verticalAlign: 'middle', marginRight: '8px' },
    });
    return ui.chip(h('span', { style: { display: 'inline-flex', alignItems: 'center' } }, swatch, name), {
      on: curTheme === name,
      onclick: () => {
        setSetting('theme', name);
        ctx.refresh();
      },
    });
  });

  // 3. Level
  const curLevel = settings().level;
  const levelChips = LEVELS.map((l) => ui.chip(l, {
    on: curLevel === l,
    onclick: () => {
      setSetting('level', l);
      ctx.refresh();
    },
  }));

  // 4. Reading voice
  const speedLabels = {
    slowest: 'Slowest',
    slow: 'Slow',
    normal: 'Normal',
    fast: 'Fast',
    fastest: 'Fastest',
  };
  const curSpeed = settings().speed;
  const speedChips = Object.keys(SPEEDS).map((k) => ui.chip(speedLabels[k] || k, {
    on: curSpeed === k,
    onclick: () => {
      setSetting('speed', k);
      ctx.refresh();
    },
  }));

  const voiceKids = [h('h2', {}, 'Reading voice')];
  if (!canSpeak()) {
    voiceKids.push(h('p', { class: 'muted' }, 'Your browser cannot read aloud.'));
  } else {
    voiceKids.push(
      h('div', { class: 'stack' },
        h('div', { class: 'small muted' }, 'Speed'),
        h('div', { class: 'chips' }, ...speedChips),
      ),
    );

    const plVoices = voicesFor('pl');
    const plSelect = h('select', {
      class: 'input',
      'aria-label': 'Polish voice',
      value: settings().voicePl || '',
      onchange: (e) => {
        setSetting('voicePl', e.target.value);
        ctx.refresh();
      },
    },
      h('option', { value: '' }, 'Automatic'),
      plVoices.map((v) => h('option', { value: v.voiceURI }, `${v.name} (${v.lang})`)),
    );

    voiceKids.push(
      h('div', { class: 'stack' },
        h('div', { class: 'small muted' }, 'Polish voice'),
        plSelect,
      ),
    );

    if (plVoices.length === 0) {
      voiceKids.push(
        h('p', { class: 'small muted' },
          'This device has no Polish voice installed. On Android and iPhone add one in the system text-to-speech settings; on desktop Chrome and Edge include one.',
        ),
      );
    }

    const enVoices = voicesFor('en');
    const enSelect = h('select', {
      class: 'input',
      'aria-label': 'English voice',
      value: settings().voiceEn || '',
      onchange: (e) => {
        setSetting('voiceEn', e.target.value);
        ctx.refresh();
      },
    },
      h('option', { value: '' }, 'Automatic'),
      enVoices.map((v) => h('option', { value: v.voiceURI }, `${v.name} (${v.lang})`)),
    );

    voiceKids.push(
      h('div', { class: 'stack' },
        h('div', { class: 'small muted' }, 'English voice'),
        enSelect,
      ),
    );

    voiceKids.push(
      h('div', { class: 'hstack' },
        ui.button('Test', { onclick: () => speak('Dzień dobry! Jak się masz?') }),
        ui.chip('Show English by default', {
          on: !!settings().showEnglish,
          onclick: () => {
            setSetting('showEnglish', !settings().showEnglish);
            ctx.refresh();
          },
        }),
      ),
    );
  }

  // 5. Backup
  const backupFileInput = h('input', {
    type: 'file',
    accept: '.json,application/json',
    style: { display: 'none' },
    onchange: async () => {
      const file = backupFileInput.files?.[0];
      if (!file) return;
      try {
        const text = await file.text();
        const parsed = JSON.parse(text);
        if (!parsed || parsed.app !== 'polish4beginners' || !parsed.keys) {
          ui.toast('Invalid backup file');
          backupFileInput.value = '';
          return;
        }
        const changed = store.merge(parsed.keys);
        if (changed.length) {
          ui.toast(`${changed.length} items restored`);
        } else {
          ui.toast('Nothing newer in that file');
        }
        backupFileInput.value = '';
        ctx.refresh();
      } catch (e) {
        ui.toast('Could not read backup file');
        backupFileInput.value = '';
      }
    },
  });

  const exportBackup = () => {
    const date = new Date().toISOString().slice(0, 10);
    const payload = JSON.stringify({
      app: 'polish4beginners',
      format: 1,
      exportedAt: Date.now(),
      keys: store.dump(),
    });
    downloadText(`polish4beginners-backup-${date}.json`, payload, 'application/json');
  };

  // 6. About
  const b = ctx.state.build;
  const androidUrl = '../'; // the download page; switch to the Play listing once it is live
  const aboutKids = [
    h('h2', {}, 'About'),
    h('p', {}, `Polish4Beginners web ${APP_VERSION}`),
  ];
  if (b && b.androidVersion) {
    aboutKids.push(
      h('p', { class: 'small muted' }, `Matches Android app ${b.androidVersion} · word deck v${b.dataVersion} · build ${b.build}`),
    );
  }
  aboutKids.push(
    h('p', { class: 'small muted' }, `${data.phrases.length} flashcards, ${data.dialogs.length} conversations`),
    h('div', { class: 'hstack' },
      h('a', { class: 'btn btn-sm', href: androidUrl, target: '_blank', rel: 'noopener noreferrer' }, 'Android app'),
      h('a', { class: 'btn btn-sm', href: '../privacy.html', target: '_blank', rel: 'noopener noreferrer' }, 'Privacy'),
      ui.button('Check for updates', { small: true, onclick: () => location.reload() }),
    ),
  );

  return h('div', { class: 'stack' },
    ui.header('Settings'),
    h('div', { class: 'grid grid-2' },
      ui.card({}, ...accountKids),
      ui.card({},
        h('h2', {}, 'Theme'),
        h('div', { class: 'chips' }, ...themeChips),
      ),
      ui.card({},
        h('h2', {}, 'Level'),
        h('div', { class: 'chips' }, ...levelChips),
      ),
      ui.card({}, ...voiceKids),
      ui.card({},
        h('h2', {}, 'Backup'),
        h('div', { class: 'hstack' },
          ui.button('Export my progress', { onclick: exportBackup }),
          ui.button('Import a backup', { onclick: () => backupFileInput.click() }),
          backupFileInput,
        ),
        h('p', { class: 'small muted' }, 'Newer entries win, nothing is deleted.'),
      ),
      ui.card({}, ...aboutKids),
    ),
  );
}
