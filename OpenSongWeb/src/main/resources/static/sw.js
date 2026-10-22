const CACHE_NAME = "opensongweb-shell-v1";
const APP_SHELL = ["/", "/styles.css", "/app.js", "/manifest.webmanifest", "/icons/opensongweb.svg"];

self.addEventListener("install", event => {
    event.waitUntil(caches.open(CACHE_NAME).then(cache => cache.addAll(APP_SHELL)));
    self.skipWaiting();
});

self.addEventListener("activate", event => {
    event.waitUntil(
        caches.keys().then(names => Promise.all(
            names.filter(name => name !== CACHE_NAME).map(name => caches.delete(name))
        ))
    );
    self.clients.claim();
});

self.addEventListener("fetch", event => {
    const requestUrl = new URL(event.request.url);
    if (event.request.method !== "GET" || requestUrl.pathname.startsWith("/api/")) {
        return;
    }
    event.respondWith(
        fetch(event.request).catch(() => caches.match(event.request))
    );
});
