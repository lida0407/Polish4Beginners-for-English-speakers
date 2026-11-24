// Optional sign-in (Google or GitHub) and progress sync through Firebase.
// Nothing here loads unless js/firebase-config.js holds a config, so the app
// works fully without an account: progress then stays in this browser.
import { store } from './core.js';
import { firebaseConfig } from './firebase-config.js';

const SDK = 'https://www.gstatic.com/firebasejs/10.14.1';
const watchers = new Set();
let fb = null;          // {auth, db, mods}
let pushTimer = 0;
let applyingRemote = false;

/** account.status: 'off' (not configured) | 'out' | 'in' | 'error' */
export const account = { status: firebaseConfig ? 'out' : 'off', user: null, lastSync: 0, error: '' };

export function onAccount(fn) {
  watchers.add(fn);
  return () => watchers.delete(fn);
}

function emit(patch) {
  Object.assign(account, patch);
  watchers.forEach((fn) => fn(account));
}

async function load() {
  if (fb || !firebaseConfig) return fb;
  const [app, auth, fs] = await Promise.all([
    import(`${SDK}/firebase-app.js`), import(`${SDK}/firebase-auth.js`), import(`${SDK}/firebase-firestore.js`),
  ]);
  const instance = app.initializeApp(firebaseConfig);
  fb = { auth: auth.getAuth(instance), db: fs.getFirestore(instance), authMod: auth, fs };
  return fb;
}

function userDoc(uid) {
  return fb.fs.doc(fb.db, 'users', uid);
}

async function push() {
  if (account.status !== 'in' || !navigator.onLine) return;
  try {
    await fb.fs.setDoc(userDoc(account.user.uid), { keys: store.dump(), updatedAt: Date.now() });
    emit({ lastSync: Date.now(), error: '' });
  } catch (e) {
    emit({ error: e.message });
  }
}

function schedulePush() {
  clearTimeout(pushTimer);
  pushTimer = setTimeout(push, 2500);
}

export async function initSync() {
  if (!firebaseConfig) return;
  try {
    await load();
  } catch (e) {
    return emit({ status: 'error', error: 'Could not load sign-in. Check your connection.' });
  }
  let stopSnapshot = null;
  fb.authMod.onAuthStateChanged(fb.auth, (user) => {
    if (stopSnapshot) stopSnapshot();
    stopSnapshot = null;
    if (!user) return emit({ status: 'out', user: null });
    emit({ status: 'in', user: { uid: user.uid, name: user.displayName || user.email || 'Signed in', photo: user.photoURL || '' } });
    // Remote changes (another device) merge in key by key; anything newer here goes back up.
    stopSnapshot = fb.fs.onSnapshot(userDoc(user.uid), (snap) => {
      if (snap.metadata.hasPendingWrites) return;
      applyingRemote = true;
      const changed = store.merge(snap.exists() ? snap.data().keys : {});
      applyingRemote = false;
      const remote = snap.exists() ? snap.data().keys || {} : {};
      const local = store.dump();
      const ahead = Object.keys(local).some((k) => !remote[k] || local[k].t > (remote[k].t || 0));
      if (ahead) schedulePush();
      else if (changed.length || !account.lastSync) emit({ lastSync: Date.now() });
    }, (e) => emit({ error: e.message }));
  });
  store.on(() => !applyingRemote && account.status === 'in' && schedulePush());
  window.addEventListener('online', schedulePush);
}

/** provider: 'google' | 'github' */
export async function signIn(provider) {
  await load();
  const { authMod, auth } = fb;
  const p = provider === 'github' ? new authMod.GithubAuthProvider() : new authMod.GoogleAuthProvider();
  try {
    await authMod.signInWithPopup(auth, p);
  } catch (e) {
    if (e.code === 'auth/popup-blocked' || e.code === 'auth/operation-not-supported-in-this-environment') {
      return authMod.signInWithRedirect(auth, p);
    }
    if (e.code === 'auth/account-exists-with-different-credential') {
      throw new Error('This email already signed in with the other provider. Use that one.');
    }
    if (e.code !== 'auth/popup-closed-by-user' && e.code !== 'auth/cancelled-popup-request') throw e;
  }
}

export async function signOut() {
  if (fb) await fb.authMod.signOut(fb.auth);
}

/** Remove the synced copy and the account. Progress in this browser is kept. */
export async function deleteAccount() {
  if (account.status !== 'in') return;
  await fb.fs.deleteDoc(userDoc(account.user.uid));
  try {
    await fb.authMod.deleteUser(fb.auth.currentUser);
  } catch (e) {
    await signOut(); // needs a recent sign-in; the data is already gone
  }
}
