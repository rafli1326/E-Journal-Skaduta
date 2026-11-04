const CACHE_NAME = 'ejournal-skaduta-v7';
const ASSETS = [
  './',
  './index.html',
  './manifest.json',
  './favicon.png',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './logo_smk2_tanjung.png',
  './fonts/Roboto-Regular.ttf',
  './fonts/Roboto-Bold.ttf'
];

self.addEventListener('install', (event) => {
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(ASSETS)).catch(() => {})
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      const keys = await caches.keys();
      let hadOldCache = false;
      for (const key of keys) {
        if (key !== CACHE_NAME) {
          await caches.delete(key);
          hadOldCache = true;
        }
      }
      await self.clients.claim();
      if (hadOldCache) {
        const windowClients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
        for (const client of windowClients) {
          if ('navigate' in client) {
            client.navigate(client.url).catch(() => {});
          }
        }
      }
    })()
  );
});

// Network-First strategy: hanya intersep request GET same-origin
self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;

  let reqUrl;
  try {
    reqUrl = new URL(event.request.url);
  } catch {
    return;
  }

  // Security: jangan pernah cache atau intersep request lintas domain (cross-origin) atau skema non-http(s)
  if (reqUrl.origin !== self.location.origin || (reqUrl.protocol !== 'http:' && reqUrl.protocol !== 'https:')) {
    return;
  }

  event.respondWith(
    fetch(event.request)
      .then((networkResponse) => {
        if (networkResponse && networkResponse.status === 200 && networkResponse.type === 'basic') {
          const responseClone = networkResponse.clone();
          caches.open(CACHE_NAME).then((cache) => {
            cache.put(event.request, responseClone).catch(() => {});
          });
        }
        return networkResponse;
      })
      .catch(() => caches.match(event.request, { ignoreSearch: true }))
  );
});
