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
    const chemin = new URL(request.url);
    if (chemin.pathname === '/api') return appelerPortail(request, chemin.origin);
    if (!['GET', 'HEAD'].includes(request.method)) {
      return new Response(null, { status: 405, headers: { Allow: 'GET, HEAD' } });
    }
    if (chemin.pathname === '/exec/') {
      return Response.redirect(new URL('/exec', chemin), 308);
    }
    const fichier = fichiers.get(chemin.pathname);
    if (!fichier) return new Response(null, { status: 404 });
    const url = new URL(fichier, origine);
    const version = chemin.searchParams.get('v');
    if (version && /^[0-9]{1,8}$/.test(version)) url.searchParams.set('v', version);
    const reponse = await fetch(url.toString(), { method: request.method, redirect: 'manual' });
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

async function appelerPortail(request, origineSite) {
  const headers = { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' };
  const erreur = (status, message) => Response.json({ok:false, message}, {status, headers});
  if (request.method !== 'POST') return erreur(405, 'Méthode indisponible.');
  const origin = request.headers.get('Origin');
  if (origin && origin !== origineSite) return erreur(403, 'Origine inconnue.');
  if (Number(request.headers.get('Content-Length')) > 90000) return erreur(413, 'Requête trop volumineuse.');
  const contenu = await request.text();
  if (contenu.length > 90000) return erreur(413, 'Requête trop volumineuse.');
  try {
    const r = await fetch('https://script.google.com/macros/s/AKfycbyezwCIzg2WYtyrKUfM6LfQnveCGH2mlLou-q1-8tWAEwxG41AcQ0YwpTbrkIkmH6ZN/exec', {
      method: 'POST',
      headers: {'Content-Type': 'text/plain;charset=UTF-8'},
      body: contenu,
      redirect: 'follow'
    });
    if (!r.ok) return erreur(502, 'Le service KVG est momentanément indisponible.');
    const data = await r.json();
    return Response.json(data, {headers});
  } catch {
    return erreur(502, 'Le service KVG est momentanément indisponible.');
  }
}
