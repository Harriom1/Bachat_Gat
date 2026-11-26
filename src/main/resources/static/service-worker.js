// Static files only. API calls, uploads and authenticated payloads stay on the network.
const shellCache = 'bachatgat-static-perf-v2';
const shellPages = new Set(['/', '/index.html', '/login.html', '/admin/dashboard.html', '/user/dashboard.html']);

self.addEventListener('install', event => event.waitUntil(self.skipWaiting()));
self.addEventListener('activate', event => event.waitUntil((async () => {
  const keys = await caches.keys();
  await Promise.all(keys.filter(key => key.startsWith('bachatgat-static-') && key !== shellCache)
    .map(key => caches.delete(key)));
  await self.clients.claim();
})()));

self.addEventListener('fetch', event => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== 'GET' || url.origin !== self.location.origin || url.search
      || request.headers.has('Authorization')) return;
  const shell = request.mode === 'navigate' && shellPages.has(url.pathname);
  const asset = url.pathname.startsWith('/assets/perf-v2/');
  if (!shell && !asset) return;

  const storage = caches.open(shellCache).catch(() => null);
  event.respondWith((async () => {
    try {
      // The normal HTTP cache handles immutable assets. This cache exists for offline use.
      const response = await fetch(request);
      if (response.ok && !response.redirected) {
        const copy = response.clone();
        event.waitUntil(storage.then(cache => cache?.put(request, copy)).catch(() => {}));
      }
      if (response.status >= 500) return (await (await storage)?.match(request)) || response;
      return response;
    } catch (error) {
      const saved = await (await storage)?.match(request);
      if (saved) return saved;
      throw error;
    }
  })());
});
