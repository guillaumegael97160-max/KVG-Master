// L'adresse publique suit la version du portail publiée par GitHub Pages.
const origine = 'https://guillaumegael97160-max.github.io/KVG-Master/malory/';
const fichiers = new Map([
  ['/', 'index.html'],
  ['/index.html', 'index.html'],
  ['/exec', 'index.html'],
  ['/style.css', 'style.css'],
  ['/app.js', 'app.js'],
  ['/couverture.webp', 'couverture.webp'],
  ['/KVG-Malory.apk', 'KVG-Malory.apk'],
]);

export default {
  async fetch(request) {
    if (!['GET', 'HEAD'].includes(request.method)) {
      return new Response(null, { status: 405, headers: { Allow: 'GET, HEAD' } });
    }
    const chemin = new URL(request.url);
    if (chemin.pathname === '/exec/') {
      return Response.redirect(new URL('/exec', chemin), 308);
    }
    const fichier = fichiers.get(chemin.pathname);
    if (!fichier) return new Response(null, { status: 404 });
    const url = new URL(fichier, origine);
    const version = chemin.searchParams.get('v');
    if (version && /^[0-9]{1,8}$/.test(version)) url.searchParams.set('v', version);
    const reponse = await fetch(url, { method: request.method, redirect: 'error' });
    if (!reponse.ok) return new Response(null, { status: reponse.status });
    const entetes = new Headers(reponse.headers);
    entetes.set('X-Content-Type-Options', 'nosniff');
    if (fichier === 'index.html') entetes.set('Cache-Control', 'no-cache');
    if (fichier.endsWith('.apk')) {
      entetes.set('Content-Type', 'application/vnd.android.package-archive');
      entetes.set('Content-Disposition', 'attachment; filename="KVG-Malory.apk"');
    }
    return new Response(reponse.body, { status: reponse.status, headers: entetes });
  },
};
