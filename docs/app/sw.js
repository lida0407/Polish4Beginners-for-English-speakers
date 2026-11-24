// Offline support. The page registers this as sw.js?b=<build id>, so each build installs
// a fresh worker with its own cache; old caches are dropped on activate.
const BUILD = new URL(self.location.href).searchParams.get('b') || 'dev';
const CACHE = `p4b-${BUILD}`;

self.addEventListener('install', (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE);
    const manifest = await (await fetch('build.json', { cache: 'no-store' })).json();
    const urls = ['./', ...manifest.files.filter((f) => f !== 'sw.js'), '../phrases.json', '../dialogs.json'];
    await Promise.all(urls.map((u) => cache.add(new Request(u, { cache: 'reload' })).catch(() => null)));
    self.skipWaiting();
  })());
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    for (const key of await caches.keys()) if (key.startsWith('p4b-') && key !== CACHE) await caches.delete(key);
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  const url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== self.location.origin || url.pathname.endsWith('build.json')) return;
  event.respondWith((async () => {
    const cache = await caches.open(CACHE);
    const hit = await cache.match(req, { ignoreSearch: url.pathname.endsWith('/') });
    // The word deck updates on its own schedule: serve the cached copy, refresh it in the background.
    if (hit && url.pathname.endsWith('.json')) {
      event.waitUntil(fetch(req).then((res) => res.ok && cache.put(req, res)).catch(() => null));
      return hit;
    }
    if (hit) return hit;
    try {
      const res = await fetch(req);
      if (res.ok) cache.put(req, res.clone());
      return res;
    } catch (e) {
      return (await cache.match('./')) || Response.error();
    }
  })());
});
