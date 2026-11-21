
(function(){
'use strict';
const S={token:'',profile:null,data:null,week:[],view:'home',weekDate:'',selectedDate:'',taskFilter:'toutes',busy:false};
const $=id=>document.getElementById(id);
const esc=v=>String(v===undefined||v===null?'':v).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const frDate=d=>{const p=String(d||'').slice(0,10).split('-');return p.length===3?p[2]+'/'+p[1]+'/'+p[0]:String(d||'')};
const niceDate=d=>{try{return new Intl.DateTimeFormat('fr-FR',{weekday:'long',day:'numeric',month:'long'}).format(new Date(d+'T12:00:00'))}catch(_){return d}};
const weekDay=d=>{try{return new Intl.DateTimeFormat('fr-FR',{weekday:'long',day:'numeric',month:'short'}).format(new Date(d+'T12:00:00'))}catch(_){return d}};
const today=()=>S.data?.aujourdHui||new Intl.DateTimeFormat('en-CA',{timeZone:'America/Guadeloupe',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
const isAdmin=()=>!!S.profile?.admin;
const currentPresence=()=>S.data?.presences?.find(p=>p.date===today());
const activePresence=p=>p&&['Présence déclarée','Présence confirmée'].includes(p.statut);
const slotName=s=>s==='MATIN'?'08h30 – 12h30':s==='SIEGE'?'15h00 – 17h00':s==='SOIR'?'17h00 – 19h00':String(s);
const getWeekDay=d=>S.week.find(x=>x.date===d);
const t=(text,kind='')=>{const n=$('toast');n.textContent=text;n.style.background=kind==='error'?'#a23e4d':'#0c4850';n.classList.remove('hidden');clearTimeout(t.timer);t.timer=setTimeout(()=>n.classList.add('hidden'),3800)};
let attenteChargement;
function loading(enCours){
 S.busy=enCours;
 const indicateur=$('loading');
 if(!enCours){
   clearTimeout(attenteChargement);
   attenteChargement=undefined;
   indicateur.classList.add('hidden');
   indicateur.textContent='';
   return;
 }
 if(attenteChargement===undefined&&indicateur.classList.contains('hidden')){
   attenteChargement=setTimeout(()=>{
     attenteChargement=undefined;
     if(S.busy){indicateur.classList.remove('hidden');indicateur.textContent='Traitement en cours…'}
   },650);
 }
}
const BACKEND='https://script.google.com/macros/s/AKfycbyezwCIzg2WYtyrKUfM6LfQnveCGH2mlLou-q1-8tWAEwxG41AcQ0YwpTbrkIkmH6ZN/exec';
const inApp=location.origin==='https://appassets.androidplatform.net';
if(inApp)document.documentElement.classList.add('app-native');
let phone=null, device=null;
const pendingPhone=new Map();
function mobileCall(action,detail={}){
 return new Promise((resolve,reject)=>{
  if(!phone){reject(new Error('Connexion au téléphone indisponible.'));return}
  const id=crypto.randomUUID(), timer=setTimeout(()=>{pendingPhone.delete(id);reject(new Error('Le délai de réponse a expiré.'));},120000);
  pendingPhone.set(id,{resolve,reject,timer});phone.postMessage(JSON.stringify({id,action,...detail}));
 });
}
window.addEventListener('message',event=>{
 if(!inApp || event.data!=='kvg-mobile' || !event.ports.length || phone)return;
 phone=event.ports[0];phone.onmessage=async ev=>{
  let msg;try{msg=JSON.parse(ev.data)}catch(_){return}
  if(msg.type==='ready'){
   device=msg;
   document.querySelectorAll('[data-bio]').forEach(el=>el.hidden=!device.available);
   if(msg.resume){loading(true);try{await ouvrirProfil(await api('reprendreAccesMobile',msg.resume.role,device.id,msg.resume.secret));}
   catch(err){if(/Accès mobile expiré/.test(err.message))await mobileCall('forget').catch(()=>{});logout();const out=$('loginMsg');out.textContent=err.message;out.classList.remove('hidden');out.classList.add('error')}
   finally{loading(false)}}return;
  }
  const req=pendingPhone.get(msg.id);if(!req)return;clearTimeout(req.timer);pendingPhone.delete(msg.id);
  if(msg.ok)req.resolve(msg.data);else req.reject(new Error(msg.message||'Opération interrompue.'));
 };phone.start();phone.postMessage(JSON.stringify({action:'ready'}));
});
async function api(name,...args){
 if(inApp && phone)return mobileCall('api',{name,args});
 const endpoint=location.hostname.endsWith('.pages.dev')?new URL('api',document.baseURI).href:BACKEND;
 const response=await fetch(endpoint,{method:'POST',headers:{'Content-Type':'text/plain;charset=UTF-8'},credentials:'omit',referrerPolicy:'no-referrer',body:JSON.stringify({action:name,args}),redirect:'follow'});
 let data;try{data=await response.json()}catch(_){throw new Error('Le service KVG ne répond pas. Réessaie dans un instant.');}
 if(!response.ok||!data.ok)throw new Error(data.message||'Le service KVG est momentanément indisponible.');
 return data.data;
}
async function ouvrirProfil(res){
 if(!res?.jeton||!res?.profil)throw new Error('Connexion incomplète.');
 S.token=res.jeton;S.profile=res.profil;
 if(S.profile.role==='GUILLAUME')ouvrirAcces();
 else{$('profileName').textContent=res.profil.nom;await refresh();$('auth').classList.add('hidden');$('app').classList.remove('hidden')}
 $('loginMsg').classList.add('hidden');$('loginForm').reset();
}
async function lierEmpreinte(){
 if(S.busy||!device||!S.token)return;
 loading(true);
 try{
  // Obtenir le consentement biométrique avant de remplacer l'accès de cet appareil.
  await mobileCall('authorizeSave');
  const cred=await api('activerAccesMobile',S.token,device.id);
  await mobileCall('save',{credential:cred});t('Déverrouillage biométrique activé sur ce téléphone.');
 }catch(err){t(err.message,'error')}finally{loading(false)}
}
async function quitterCompte(){
 if(S.busy)return;loading(true);
 try{
  if(device){await mobileCall('forget');if(S.token)await api('retirerAccesMobile',S.token,device.id).catch(()=>{})}
  if(S.token)await api('deconnexion',S.token).catch(()=>{});
 }finally{logout();loading(false)}
}

async function refresh(){if(!S.token)return;loading(true);try{
 const y=Number(today().slice(0,4)),d=await api('chargerDonnees',S.token,y,3);
 if(!d||!Array.isArray(d.presences)||!Array.isArray(d.competences)||!Array.isArray(d.objectifs))throw new Error('Mise à jour du Code.gs nécessaire : données Bachelor ou présences manquantes.');
 S.data=d;if(!S.weekDate)S.weekDate=d.aujourdHui;if(!S.selectedDate)S.selectedDate=d.aujourdHui;
 S.week=await api('listerPlanningSemaineV3',S.token,S.weekDate);render();
 }catch(err){if(/Session expirée/i.test(err.message))logout();t(err.message,'error');throw err}finally{loading(false)}}
const b=(action,label,id='',type='secondary')=>`<button type="button" class="btn sm ${type}" data-action="${esc(action)}" data-id="${esc(id)}">${label}</button>`;
const statusBadge=raw=>{const v=String(raw||'');const cls=v==='Non déclarée'?'blue':/confirmée|déclarée|terminée|atteint|autonome|acquise/i.test(v)?'ok':/absen|annul|réviser/i.test(v)?'red':/attente|cours|progression/i.test(v)?'warn':'blue';return `<span class="chip ${cls}">${esc(v||'À venir')}</span>`};
const head=(over,title,desc,actions='')=>`<div class="page-head"><div><div class="eyebrow">${esc(over)}</div><h1>${esc(title)}</h1><p>${esc(desc)}</p></div>${actions?`<div class="toolbar">${actions}</div>`:''}</div>`;
function nav(){let items=[['home','⌂','Accueil'],['defis','◇','Mon parcours'],['resultats','↗','Mon évolution'],['planning','▦','Planning'],['tasks','☑','Vue trimestrielle'],['formation','★','Compétences & objectifs'],['suivi','▤','Suivi']];if(isAdmin())items.push(['admin','⚙','Administration']);$('nav').innerHTML=items.map(([k,ic,n])=>`<button type="button" class="navbutton ${S.view===k?'active':''}" data-view="${k}" aria-current="${S.view===k?'page':'false'}"><span>${ic}</span>${esc(n)}</button>`).join('')}
function render(){if(!S.data)return;nav();$('content').innerHTML=({home,defis:defisPage,resultats:evolutionPage,planning,tasks,formation,suivi,admin:adminPage}[S.view]||home)()}
function shiftsCard(shifts,d){return shifts?.length?shifts.map(s=>`<div class="shift"><div><strong>${esc(slotName(s.code))}</strong><div class="sub">${esc(s.site)} · ${esc(s.accompagne)}</div>${s.consignes?`<div class="sub">${esc(s.consignes)}</div>`:''}</div>${isAdmin()?b('assign','Modifier',d+'|'+s.code,'ghost'):''}</div>`).join(''):'<div class="empty">Formation CFA ou repos, pas de présence à déclarer.</div>'}
function presenceWidget(){let d=S.data,p=currentPresence(),shifts=getWeekDay(today())?.creneaux||[];
 if(!shifts.length)return '<div class="notice">Aucune présence à cocher aujourd’hui : CFA ou repos.</div>';
 const locked=!!p?.corrigePar;
 return `<div class="presence-box ${locked?'locked':''}"><input id="presenceCheck" type="checkbox" ${activePresence(p)?'checked':''} ${isAdmin()||locked||S.busy?'disabled':''} aria-label="Je suis présente aujourd’hui"><label for="presenceCheck">${activePresence(p)?'Présence enregistrée ✓':'Je suis présente aujourd’hui'}<small>${locked?'Présence corrigée par Vanessa.':isAdmin()?'Déclaration de Malory.':'Une seule case à cocher pour la journée, sans code ni entrée/sortie.'}</small></label></div>
 <div class="footnote">${p?.declareLe?`Déclaration : ${esc(p.declareLe)} · `:''}${p?.statut?`Statut : ${esc(p.statut)}. `:''}Cette case confirme une présence déclarée, pas les heures réellement effectuées.</div>`}
function home(){const d=S.data,day=getWeekDay(d.aujourdHui),shifts=day?.creneaux||[];
 const open=elementsTrimestre(trimestreDate(today())).taches.filter(x=>['À faire','En cours','À reprendre','À valider'].includes(x.statut)).sort((a,z)=>a.date.localeCompare(z.date));
 return accueilDefis()+`<div class="twocol"><section class="card"><div class="card-head"><h2>Aujourd’hui</h2>${b('navplanning','Planning complet','','ghost')}</div>${shifts.length?`<p>${esc(shifts.map(c=>c.debut+'–'+c.fin).join(' · '))}</p>`:''}${presenceWidget()}</section>
 <section class="card"><div class="card-head"><h2>Mes priorités</h2>${b('navtasks','Toutes les missions','','ghost')}</div>${open.length?open.slice(0,4).map(x=>`<div class="item"><strong>${esc(x.titre)}</strong><div class="small muted">${esc(periodeTache(x))} · ${esc(x.categorie)}</div><div class="chips">${statusBadge(x.statut)}</div></div>`).join(''):'<div class="empty">Aucune mission en attente.</div>'}</section></div>
 `}
const axesDefis=[['vente','Vente','#08776f'],['assurance','Assurance','#a7600b'],['negociation','Négociation','#6551ae']];
const argent=v=>Number(v).toLocaleString('fr-FR',{maximumFractionDigits:2})+' €';
const chiffre=v=>Number(v).toLocaleString('fr-FR',{maximumFractionDigits:1});
const libMois=m=>new Intl.DateTimeFormat('fr-FR',{month:'short',year:'numeric'}).format(new Date(m+'-15T12:00:00'));
function noteMois(n){const valeurs=axesDefis.map(([k])=>n?.[k]).filter(v=>v!==''&&v!=null).map(Number);return valeurs.length?valeurs.reduce((a,b)=>a+b,0)/valeurs.length:null}
function derniereNote(p){return p.notes.slice().sort((a,b)=>b.mois.localeCompare(a.mois))[0]}
function prochainDefi(p){const m=today().slice(0,7);return p.defis.find(d=>d.mois>=m&&d.statut!=='Validé')||p.defis.find(d=>d.statut!=='Validé')||p.defis[p.defis.length-1]}
function boutonDefi(d,lib='Découvrir le défi'){return b('ouvrirDefi',lib,d.id)}
function ligneMetrique(val,lib,suite=''){return `<div class="dk-stat"><strong>${esc(val)}</strong><span>${esc(lib)}</span>${suite?`<small>${esc(suite)}</small>`:''}</div>`}
function tempsVenteDefis(p){
 const jours=p.jours.filter(j=>j.valide&&j.minutes!=null&&j.minutes>0);
 const minutes=jours.reduce((n,j)=>n+j.minutes,0),unites=jours.reduce((n,j)=>n+j.quantite,0);
 return {minutes,unites,cadence:minutes?unites*60/minutes:null};
}
function valeurGain(d){return 2+(Number(d.bonus)||0)}
function gainDefi(d){const pts=valeurGain(d);return '+'+chiffre(pts)+' pts · '+argent(pts/2)}
function accueilDefis(){
 const p=S.data.parcours,d=prochainDefi(p),s=p.solde,n=derniereNote(p),acquis=p.defis.filter(x=>x.statut==='Validé').length;
 const source=S.data.vendeuse||{}, connu=source.etat==='relie'||p.jours.some(j=>j.valide), fiable=source.etat!=='indisponible'&&connu;
 const reste=100-s.quantite%100, engag=d.statut==='En cours';
 return `<section class="dk-hero vh-hero"><div class="dk-hero-copy"><div class="dk-kicker">PARCOURS DE MALORY · ${acquis} / 24 DÉFIS VALIDÉS</div><h1>${esc(d.titre)}</h1><p>${esc(d.taches[0])}</p><div class="vh-hero-foot"><span class="vh-prize"><b>${gainDefi(d)}</b> ${d.statut==='Validé'?'validés':'à la validation'}</span>${boutonDefi(d,engag?'Continuer mon défi':d.statut==='À découvrir'?'Découvrir mon défi':'Voir mon défi')}</div></div></section>
 <div class="dk-home-grid"><section class="card vh-level"><div class="card-head"><h2>Mon prochain palier</h2><b class="vh-chip">100 pts · 50 €</b></div><p>${fiable?`Encore <strong>${reste} déos</strong> avant ton prochain bonus.`:'Chaque série de 100 déos vendus débloque un bonus.'}</p>${fiable?`<progress max="100" value="${s.quantite%100}" aria-label="Déos vers le prochain palier"></progress><small>${s.quantite%100} / 100 déos · ${s.paliers} palier${s.paliers>1?'s':''} atteint${s.paliers>1?'s':''}</small>`:`<small>${source.etat==='indisponible'?'Actualisation des ventes indisponible.':'Progression disponible dès la liaison du compte vendeuse ou la validation des ventes.'}</small>`}</section>
 <section class="card"><div class="card-head"><h2>Mes acquis</h2>${b('navresultats','Mon évolution','','ghost')}</div><div class="vh-two">${ligneMetrique(source.etat==='indisponible'?'—':s.points+' pts','Bonus validés',source.etat==='indisponible'?'Actualisation indisponible':argent(s.euros)+' cumulés')}${ligneMetrique(n?chiffre(noteMois(n))+' / 100':'— / 100','Note de compétences',n?libMois(n.mois):'Première évaluation à venir')}</div></section></div>
 <section class="card"><div class="card-head"><h2>Les primes à viser</h2>${b('navdefis','Les 24 étapes','','ghost')}</div><div class="vh-primes">${p.defis.filter(x=>x.bonus).map(x=>`<button type="button" data-action="ouvrirDefi" data-id="${esc(x.id)}"><small>${esc(x.niveau)} · ${esc(libMois(x.mois))}</small><b>${esc(x.titre)}</b><strong>${chiffre(x.bonus)} pts · ${argent(x.bonus/2)}</strong><span>${x.statut==='Validé'?'Prime validée':'Prime commerciale'} · +1 € pour le défi</span></button>`).join('')}</div></section>`+tableauVendeuse(true);
}
function tableauVendeuse(apercu=false){
 const source=S.data.vendeuse||{etat:'nonRelie',lignes:[],mois:[],bilans:{}}, mois=apercu?today().slice(0,7):S.statsMois||today().slice(0,7);
 const titre=apercu?'Mon tableau de vente':'Tableau KVG Vendeuse',actions=apercu?b('navsuivi','Voir le tableau','','ghost'):`<label class="pc-month">Mois<input type="month" id="statsMois" value="${esc(mois)}"></label>`;
 const debut=`<section class="card vh-sales"><div class="card-head"><div><small class="vh-meta">${apercu?'KVG VENDEUSE · '+esc(moisLisible(mois)):'VENTES PERSONNELLES'}</small><h2>${titre}</h2></div>${actions}</div>`;
 if(source.etat!=='relie')return debut+`<p class="vh-notice">${source.etat==='indisponible'?'La lecture de KVG Vendeuse est momentanément indisponible.':'Le compte KVG Vendeuse de Malory reste à relier dans Administration → Compte vendeuse.'}</p></section>`;
 if(!source.mois.includes(mois))return debut+'<p class="muted">Aucun tableau KVG Vendeuse disponible pour ce mois.</p></section>';
 const lignes=source.lignes.filter(x=>x.date.startsWith(mois)).sort((a,b)=>b.date.localeCompare(a.date)),bilan=source.bilans[mois]||{};
 const total=k=>lignes.reduce((n,x)=>n+(x[k]||0),0),unites=total('total'),heures=total('heures'),pts=bilan.retenus??total('points');
 const metriques=ligneMetrique(chiffre(unites),'Déos vendus')+ligneMetrique(chiffre(pts)+' pts','Points vendeuse')+ligneMetrique(dureeTexte(Math.round(heures*60)),'Heures de vente')+ligneMetrique(heures?chiffre(unites/heures):'—','Déos par heure','Repère · 7 déos / h');
 if(apercu)return debut+`<div class="dk-stats">${metriques}</div></section>`;
 const cols=[['date','Date'],['boutique','Boutique'],['matin','Ventes matin'],['apres','Ventes après-midi'],['total','Ventes'],['heures','Heures'],['objectif','Objectif'],['statut','Statut'],['points','Points']];
 const cell=(x,k)=>k==='date'?frDate(x.date):k==='heures'&&x[k]!=null?dureeTexte(Math.round(x[k]*60)):x[k]??'—';
 return debut+`<div class="dk-stats">${metriques}</div>${lignes.length?`<div class="tablewrap vh-ledger"><table><caption>Journées enregistrées · ${esc(moisLisible(mois))}</caption><thead><tr>${cols.map(c=>`<th scope="col">${c[1]}</th>`).join('')}</tr></thead><tbody>${lignes.map(x=>`<tr>${cols.map(([k,lib])=>`<td data-label="${lib}">${esc(cell(x,k))}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`:'<p class="muted">Aucune journée de vente enregistrée pour ce mois.</p>'}<div class="vh-bilan">${[['score','Score vendeuse'],['ca','CA HT indicatif'],['marge','Marge indicative']].filter(([k])=>bilan[k]!=null).map(([k,lib])=>`<span>${lib} <b>${k==='score'?chiffre(bilan[k]):argent(bilan[k])}</b></span>`).join('')}</div></section>`;
}
async function choisirVendeuse(){
 if(S.profile?.role!=='GUILLAUME'||S.busy)return;
 loading(true);
 try{
   const d=await api('comptesVendeuseMalory',S.token);
   modal(`<h2>Compte KVG Vendeuse de Malory</h2><p class="muted">Les ventes, les heures et les points de ce compte seront visibles dans son parcours.</p><form id="liaisonForm"><label>Compte personnel<select name="vendeuse"><option value="">Aucun compte relié</option>${d.comptes.map(c=>`<option value="${esc(c.id)}" ${c.id===d.id?'selected':''}>${esc(c.nom)} · ${esc(c.login)}</option>`).join('')}</select></label><p role="alert" id="liaisonMsg"></p>${formButtons('Enregistrer la liaison')}</form>`,root=>{
   root.querySelector('form').onsubmit=async e=>{e.preventDefault();if(S.busy)return;loading(true);try{await api('relierVendeuseMalory',S.token,new FormData(e.currentTarget).get('vendeuse'),d.revision);closeModal();t('Liaison enregistrée.')}catch(err){root.querySelector('#liaisonMsg').textContent=err.message}finally{loading(false)}};
   });
 }catch(err){t(err.message,'error')}finally{loading(false)}
}
function detailDefi(d){
 const action=isAdmin()?(d.statut==='Validé'?b('reprendreDefi','Rouvrir le défi',d.id,'ghost'):b('validerDefi','Valider le résultat',d.id)+(d.statut==='À valider'?b('reprendreDefi','Demander une précision',d.id,'ghost'):'')):['À découvrir','À reprendre'].includes(d.statut)?b('demarrerDefi','Me lancer',d.id)+b('soumettreDefi','Présenter mon résultat',d.id,'ghost'):d.statut==='En cours'?b('soumettreDefi','Présenter mon résultat',d.id):'';
 return `<section class="card dk-detail" id="defiActif"><div class="card-head"><p class="dk-kicker">ÉTAPE ${d.numero} / 24 · ${esc(libMois(d.mois))}</p>${statusBadge(d.statut)}</div><h2>${esc(d.titre)}</h2><p class="muted">${esc(d.competence)} · Niveau ${esc(d.niveau)}</p><div class="dk-gain"><b>${gainDefi(d)}</b> · ${d.statut==='Validé'?'gain validé':'à gagner après validation'}${d.bonus?`<small> · dont ${chiffre(d.bonus)} pts de prime commerciale</small>`:''}${d.id==='palier-100'?'<small> · 100 pts (50 €) supplémentaires au palier de vente</small>':''}</div><h3>À réaliser</h3><ol>${d.taches.map(x=>`<li>${esc(x)}</li>`).join('')}</ol>${d.preuve?`<p><b>Résultat de Malory</b><br>${esc(d.preuve)}</p>`:''}${d.retour?`<p><b>Retour de Vanessa</b><br>${esc(d.retour)}</p>`:''}<div class="item-actions">${action}</div></section>`;
}
function defisPage(){
 const p=S.data.parcours,focus=p.defis.find(d=>d.id===S.defi)||prochainDefi(p),an=S.anneeDefis||focus.annee;
 const liste=p.defis.filter(d=>d.annee===an), choisi=focus.annee===an?focus:liste[0];
 const cycle=(a,b2)=>`<section class="dk-cycle"><div class="dk-cycle-head"><h3>${esc(a.competence)}</h3><span>${esc(a.niveau)}</span></div>${[a,b2].map(d=>`<button class="dk-step ${d.id===choisi.id?'selected':''}" type="button" aria-pressed="${d.id===choisi.id}" data-action="ouvrirDefi" data-id="${d.id}"><i>${d.statut==='Validé'?'✓':String(d.numero).padStart(2,'0')}</i><span><small>${esc(libMois(d.mois))}</small><b>${esc(d.titre)}</b><strong class="vh-stepgain">${gainDefi(d)}</strong></span><em>${d.statut==='À découvrir'?'Voir':esc(d.statut)} ›</em></button>`).join('')}</section>`;
 return head('Formation en entreprise','Mon parcours sur deux ans','Un défi par mois. Deux mois pour exercer chaque compétence.')+`<div class="dk-tabs">${[1,2].map(i=>`<button data-action="anneeDefis" data-id="${i}" aria-pressed="${an===i}">Année ${i} · ${i===1?'Découvrir et pratiquer':'Développer et transmettre'}</button>`).join('')}</div>${detailDefi(choisi)}<div class="dk-cycles">${liste.filter((_,i)=>i%2===0).map((d,i)=>cycle(d,liste[i*2+1])).join('')}</div>`;
}
function formulaireDefi(action,id){const d=S.data.parcours.defis.find(x=>x.id===id);if(!d)return;
 const op={soumettreDefi:'soumettre',validerDefi:'valider',reprendreDefi:'reprendre'}[action],labels={soumettre:'Présenter mon résultat',valider:'Valider le résultat',reprendre:'Demander une reprise'};
 formModal(labels[op],d.titre,field('texte',op==='soumettre'?'Qu’as-tu réalisé ? Indique un résultat ou une référence.':op==='valider'?'Quel résultat as-tu vérifié ?':'Que faut-il reprendre ?',op==='soumettre'?d.preuve:'','textarea',null,true),f=>api('agirDefiMalory',S.token,{...f,id,action:op,revision:d.revision}),labels[op]);
}
function tableauPoints(p){if(S.data.vendeuse?.etat==='indisponible')return '<section class="card"><h2>Points et bonus</h2><p>Les ventes sont momentanément indisponibles. Le solde sera affiché après actualisation.</p></section>';const s=p.solde,{minutes,unites,cadence}=tempsVenteDefis(p);
 return `<section class="card"><div class="card-head"><h2>Points et bonus</h2><strong class="dk-amount">${s.points} pts · ${argent(s.euros)}</strong></div><div class="dk-line"><span>Paliers de 100 déos · 50 € par palier</span><b>${s.paliers*100} pts</b></div><div class="dk-line"><span>Objectifs validés · 1 € par objectif</span><b>${s.pointsObjectifs} pts</b></div><div class="dk-line"><span>Primes commerciales validées</span><b>${s.pointsPrimes} pts</b></div><details class="dk-info"><summary>Voir les trois primes commerciales</summary>${p.defis.filter(d=>d.bonus).map(d=>`<div class="dk-line"><span>${esc(d.titre)}<small>${esc(d.statut)}</small></span><b>${d.bonus} pts · ${argent(d.bonus/2)}</b></div>`).join('')}</details><p class="small muted">1 point vaut 0,50 €. La date de versement se consulte dans Suivi.</p></section><section class="card"><div class="card-head"><h2>Mon temps de vente</h2>${isAdmin()?b('jourVentes','Valider une journée','','ghost'):''}</div><div class="dk-stats">${ligneMetrique(minutes?dureeTexte(minutes):'—','Temps de vente renseigné')}${ligneMetrique(cadence==null?'—':chiffre(cadence),'Déos vendus par heure','Sur les journées validées avec durée')}${ligneMetrique('7 déos / h','Repère KVG Vendeuse')}</div>${minutes?`<p class="small">${unites} déos sur ${dureeTexte(minutes)} · volume de référence pour ce temps : ${chiffre(minutes*7/60)} déos.</p>`:'<p class="small muted">Vanessa peut renseigner le temps réellement passé à vendre lors de la validation des ventes.</p>'}</section>`;
}
function courbeDefis(mois,series,max,legende){
 const w=600,h=176,x=i=>42+i*48,y=v=>135-(v/max)*115;
 let svg=`<svg viewBox="0 0 ${w} ${h}" role="img" aria-label="${esc(legende)}">`;
 for(const v of [0,max/2,max])svg+=`<line x1="42" x2="574" y1="${y(v)}" y2="${y(v)}" stroke="#dce6e4"/><text x="34" y="${y(v)+4}" text-anchor="end">${chiffre(v)}</text>`;
 mois.forEach((m,i)=>{if(i%2===0||i===11)svg+=`<text x="${x(i)}" y="158" text-anchor="middle">${esc(libMois(m).split(' ')[0])}</text>`});
 for(const serie of series){let prev=null;serie.vals.forEach((v,i)=>{if(v==null){prev=null;return}if(prev)svg+=`<line x1="${prev[0]}" y1="${prev[1]}" x2="${x(i)}" y2="${y(v)}" stroke="${serie.c}" stroke-width="3"/>`;svg+=`<circle cx="${x(i)}" cy="${y(v)}" r="4" fill="${serie.c}"><title>${esc(libMois(mois[i])+' · '+serie.nom+' · '+v)}</title></circle>`;prev=[x(i),y(v)]})}
 return svg+'</svg>';
}
function evolutionPage(){
 const p=S.data.parcours,n=derniereNote(p),an=S.anCourbe||prochainDefi(p).annee,mois=p.defis.filter(d=>d.annee===an).map(d=>d.mois),notes=new Map(p.notes.map(n=>[n.mois,n]));
 const series=axesDefis.map(([k,nom,c])=>({nom,c,vals:mois.map(m=>{const v=notes.get(m)?.[k];return v==null||v===''?null:Number(v)})}));
 const ventes=mois.map(m=>{const j=p.jours.filter(v=>v.date.startsWith(m));return j.length?j.reduce((s,d)=>s+d.quantite,0):null});
 const lignes=mois.map((m,i)=>`<tr><td>${esc(libMois(m))}</td><td>${ventes[i]??'—'}</td>${series.map(s=>`<td>${s.vals[i]??'—'}</td>`).join('')}</tr>`).join('');
 return head('Mon évolution','Compétences et résultats','Suis mois après mois ton aisance commerciale et les ventes enregistrées.')+`<section class="card"><div class="card-head"><h2>Mon évaluation de compétences</h2><strong class="dk-amount">${n?chiffre(noteMois(n)):'—'} / 100</strong></div><p class="muted">${n?esc(libMois(n.mois))+' · ':''}Moyenne des compétences notées du mois. Chaque compétence a le même poids.</p><div class="dk-stats">${axesDefis.map(([k,nom])=>ligneMetrique(n&&n[k]!==''&&n[k]!=null?n[k]+' / 100':'—',nom)).join('')}</div>${n?.avis?`<p class="q-note">${esc(n.avis)}</p>`:''}</section><section class="card dk-charts"><div class="card-head"><h2>Évolution mensuelle</h2><div class="dk-tabs">${[1,2].map(i=>`<button data-action="anCourbe" data-id="${i}" aria-pressed="${i===an}">Année ${i}</button>`).join('')}</div></div><h3>Compétences sur 100</h3><div class="dk-legend">${series.map(s=>`<span><i style="background:${s.c}"></i>${esc(s.nom)}</span>`).join('')}</div>${courbeDefis(mois,series,100,'Évolution des compétences sur 100')}${series.every(s=>s.vals.every(v=>v==null))?'<p class="small muted">La première évaluation apparaîtra ici.</p>':''}<h3>Déodorants vendus par mois</h3>${courbeDefis(mois,[{nom:'Déos enregistrés',c:'#08776f',vals:ventes}],Math.max(10,Math.ceil(Math.max(0,...ventes)/10)*10),'Quantités de déodorants enregistrées par mois')}${ventes.every(v=>v==null)?'<p class="small muted">La courbe démarre à la première vente saisie.</p>':''}<details class="dk-info"><summary>Lire les valeurs mois par mois</summary><div class="tablewrap"><table><thead><tr><th>Mois</th><th>Déos</th><th>Vente /100</th><th>Assurance /100</th><th>Négociation /100</th></tr></thead><tbody>${lignes}</tbody></table></div></details></section>`+tableauPoints(p)+`<section class="card"><div class="card-head"><h2>Journal des ventes</h2>${S.data.vendeuse?.etat==='relie'?b('navsuivi','Tableau KVG Vendeuse'):b('sale','Saisir une vente')}</div>${p.jours.length?p.jours.slice().reverse().map(j=>`<div class="dk-line"><span>${frDate(j.date)} · ${j.quantite} déos<small>${j.source==='vendeuse'?'KVG Vendeuse':j.valide?'Validés':'En attente de validation'}${j.minutes?' · '+dureeTexte(j.minutes):''}</small></span>${isAdmin()&&j.source!=='vendeuse'?b('jourVentes',j.valide?'Modifier':'Valider',j.date,'ghost'):''}</div>`).join(''):'<p class="muted">Aucune vente enregistrée pour le moment.</p>'}</section>`;
}
function validerJourForm(id){
 if(!isAdmin())return;const jours=S.data.parcours.jours;
 const ouvrir=(date)=>{const j=jours.find(x=>x.date===date)||{quantite:0,revision:'',minutes:null};formModal('Valider les ventes du '+frDate(date),j.quantite+' déos enregistrés sur cette journée.',`<label>Temps de vente réel en minutes (facultatif)<input min="1" max="1440" step="1" type="number" name="minutes" value="${j.minutes??''}" placeholder="Ex. 240 pour 4 h"></label>`,f=>api('validerJourVentesMalory',S.token,{...f,date,quantite:j.quantite,revision:j.revision}),'Confirmer la journée')};
 if(id){ouvrir(id);return}modal(`<h2>Choisir une journée de vente</h2><form id="choixJour"><label>Date<input type="date" name="date" required max="${today()}" value="${today()}"></label>${formButtons('Continuer')}</form>`,root=>root.querySelector('form').onsubmit=e=>{e.preventDefault();ouvrir(new FormData(e.currentTarget).get('date'))});
}
async function evalGael(){
 if(S.profile?.role!=='GUILLAUME'||S.busy)return;loading(true);
 try{const data=await api('mesNotesGael',S.token);const mois=data.mois.filter(x=>x<=data.jour.slice(0,7)).reverse();if(!mois.length){t('Le parcours n’a pas encore commencé.');return}
 const ouvrir=(m)=>{const n=data.notes.find(x=>x.mois===m)||{};modal(`<h2>Évaluer les compétences de Malory</h2><p class="muted">Note uniquement les compétences observées ce mois-ci.</p><form id="notesForm"><label>Mois<select name="mois">${mois.map(x=>`<option value="${x}" ${m===x?'selected':''}>${esc(libMois(x))}</option>`).join('')}</select></label><div class="dk-stats">${axesDefis.map(([k,nom])=>`<label>${nom} / 100<input type="number" min="0" max="100" step="1" name="${k}" value="${esc(n[k]??'')}" placeholder="Non notée"></label>`).join('')}</div>${field('avis','Observation pour Malory (facultative)',n.avis||'','textarea')}<p class="small" id="noteMsg" role="alert"></p>${formButtons('Enregistrer l’évaluation')}</form>`,root=>{const form=root.querySelector('form');form.elements.mois.onchange=e=>ouvrir(e.target.value);form.onsubmit=async e=>{e.preventDefault();if(S.busy)return;loading(true);try{await api('noterCompetencesGael',S.token,{...Object.fromEntries(new FormData(form)),revision:n.revision||''});closeModal();t('Évaluation enregistrée pour Malory.')}catch(err){root.querySelector('#noteMsg').textContent=err.message}finally{loading(false)}}})};ouvrir(mois[0]);
 }catch(e){t(e.message,'error')}finally{loading(false)}
}

function trimestreDate(date) {
 const an=String(date||'').slice(0,4), mois=Number(String(date||'').slice(5,7));
 return mois>=1&&mois<=12 ? an+'-T'+Math.ceil(mois/3) : '';
}
function trimestreCourant(){return S.trimestre||trimestreDate(today())}
const moisTrimestres=['janvier – mars','avril – juin','juillet – septembre','octobre – décembre'];
function bornesTrimestre(q){
 const [an,numero]=q.split('-T'),mois=Number(numero)*3;
 return {debut:an+'-'+String(mois-2).padStart(2,'0')+'-01',fin:new Date(Date.UTC(Number(an),mois,0)).toISOString().slice(0,10)};
}
function libTrimestre(q){const [an,n]=q.split('-T');return 'T'+n+' '+an+' · '+moisTrimestres[Number(n)-1]}
function choixTrimestre(q){
 const an=Number(q.slice(0,4)), options=[];
 for(let y=Math.max(2000,an-2);y<=Math.min(2099,an+3);y++)for(let n=1;n<=4;n++){
   const k=y+'-T'+n;options.push(`<option value="${k}" ${k===q?'selected':''}>${esc(libTrimestre(k))}</option>`);
 }
 return `<label>Trimestre<select required name="trimestre">${options.join('')}</select></label>`;
}
function toutesDirectives(){return S.data.directivesParcours||S.data.directives}
function periodeTache(x){return x.date?frDate(x.date):libTrimestre(x.trimestre)}
function elementsTrimestre(q){
 const {debut,fin}=bornesTrimestre(q);
 const objectifs=objectifsTries().filter(o=>(debutObjectif(o)||o.echeance)<=fin&&o.echeance>=debut);
 const taches=toutesDirectives().filter(x=>(x.trimestre||trimestreDate(x.date))===q&&!/num[ée]ri[kq].?cloud|num[ée]rikcloud|numericloud/i.test([x.titre,x.quoi,x.categorie].join(' ')));
 return {objectifs,taches};
}
function minutesDuree(horaire){const [h,m]=horaire.split(':').map(Number);return h*60+m}
function dureeEntreprise(j){return j.creneaux.reduce((n,c)=>n+minutesDuree(c.fin)-minutesDuree(c.debut),0)}
function dureeTexte(minutes){const h=Math.floor(minutes/60),m=minutes%60;return h+' h'+(m?' '+String(m).padStart(2,'0'):'')}
function minutesJour(j,fiche){
 const i=new Date(j.date+'T12:00:00Z').getUTCDay();
 if(j.creneaux.length||![1,2].includes(i))return dureeEntreprise(j);
 const v=fiche?.[i===1?'minutesLundi':'minutesMardi'];return v==null||v===''?null:Number(v);
}
function noteSemaine(f){return f?.appreciation?`<p class="q-note">${esc(f.appreciation)}</p>${f.priorite?`<p class="q-note"><b>Prochaine priorité</b><br>${esc(f.priorite)}</p>`:''}`:'<p class="small muted">Vanessa n’a pas encore donné son appréciation pour cette semaine.</p>'}
function planning(){
 const jours=S.week;if(!jours.length)return head('Semaine de travail','Mon planning','Aucune semaine disponible.');
 const lundi=jours[0].date, fiche=(S.data.semaines||[]).find(n=>n.lundi===lundi);
 const entreprise=jours.reduce((n,j)=>n+dureeEntreprise(j),0), saisi=fiche?.minutesLundi!=null&&fiche.minutesLundi!=='';
 const cfa=saisi?Number(fiche.minutesLundi)+Number(fiche.minutesMardi):S.data.heuresCFA*60;
 const periode='Du '+frDate(lundi)+' au '+frDate(jours[jours.length-1].date);
 const controles=`${b('prevweek','← Semaine','','ghost')}<input aria-label="Choisir une semaine" type="date" id="weekPicker" class="date-in" style="width:auto" value="${esc(S.weekDate)}">${b('nextweek','Semaine →','','ghost')}`;
 const resume=j=>{
   const minutes=minutesJour(j,fiche), cfa=minutes===null||(!j.creneaux.length&&[1,2].includes(new Date(j.date+'T12:00:00Z').getUTCDay()));
   const lieu=j.creneaux.length?[...new Set(j.creneaux.map(c=>!c.site||c.site==='À affecter'?'Boutique':c.site))].join(' · '):cfa?'Formation CFA':'Repos';
   const contenu=`<strong>${esc(weekDay(j.date))}${j.date===today()?'<small>Aujourd’hui</small>':''}</strong><span>${esc(lieu)}</span><b>${minutes===null?'À répartir':dureeTexte(minutes)}</b>`;
   return j.creneaux.length?`<details class="vh-day"><summary>${contenu}</summary><div class="vh-slots">${shiftsCard(j.creneaux,j.date)}</div></details>`:`<div class="vh-day vh-fixed">${contenu}</div>`;
 };
 return head('Planning','Ma semaine',periode,controles)+`<section class="card vh-week"><div class="vh-weektotal"><div><small>Total prévu</small><strong>${dureeTexte(entreprise+cfa)}</strong></div><p><b>${dureeTexte(entreprise)}</b> en entreprise · <b>${dureeTexte(cfa)}</b> au CFA</p></div>${jours.map(resume).join('')}${saisi?'':'<p class="small muted">Les heures de CFA du lundi et du mardi restent à répartir.</p>'}</section><section class="card"><div class="card-head"><h2>Appréciation de Vanessa</h2>${isAdmin()?b('weekly','Renseigner la semaine'):''}</div>${noteSemaine(fiche)}<div class="item-actions">${b('quarter','Voir le trimestre',trimestreDate(S.weekDate),'ghost')}${isAdmin()?b('taskWeek','+ Confier une tâche',trimestreDate(S.weekDate),'ghost'):''}</div></section>`;
}
function formulaireSemaine(){
 if(!isAdmin()||!S.week.length)return;
 const lundi=S.week[0].date,fiche=S.data.semaines.find(n=>n.lundi===lundi)||{};
 const duree=(nom,jour)=>{const v=fiche[nom];return `<label>CFA ${jour} (heures)<input name="${nom}" type="text" inputmode="text" placeholder="Ex. 5:30" pattern="([0-9]|1[0-6])(:[0-5][0-9])?" value="${v==null||v===''?'':Math.floor(v/60)+':'+String(v%60).padStart(2,'0')}"></label>`};
 formModal('Semaine du '+frDate(lundi),'Malory pourra lire votre appréciation dans son planning et son bilan trimestriel.',field('appreciation','Appréciation de la semaine',fiche.appreciation,'textarea')+field('priorite','Priorité pour la semaine suivante (facultatif)',fiche.priorite,'textarea')+`<fieldset class="pc-plan"><legend>Répartition des heures de CFA</legend><p class="small muted">Renseignez les deux jours au format h:mm, ou laissez-les vides pour conserver le forfait de ${Number(S.data.heuresCFA)} h.</p><div class="form-grid">${duree('minutesLundi','lundi')}${duree('minutesMardi','mardi')}</div></fieldset>`,f=>{
 for(const k of ['minutesLundi','minutesMardi'])if(f[k]!=='')f[k]=minutesDuree(f[k].includes(':')?f[k]:f[k]+':00');
 return api('enregistrerSemaineMalory',S.token,{...f,lundi,revision:fiche.revision||''});});
}
function carteTache(x){
 return `<article class="item"><div class="item-head"><strong>${esc(x.titre)}</strong>${statusBadge(x.statut)}</div><div class="small muted">${esc(x.categorie)}${x.priorite==='Haute'?' · Priorité haute':''}${x.date?' · Échéance '+frDate(x.date)+(x.heure?' à '+esc(x.heure):''):''}</div><p>${esc(x.quoi)}</p>${x.comment?`<p><b>Comment faire</b> · ${esc(x.comment)}</p>`:''}${x.attendu?`<p><b>Résultat attendu</b> · ${esc(x.attendu)}</p>`:''}${x.rapport?`<p><b>Compte rendu</b> · ${esc(x.rapport)}</p>`:''}<div class="item-actions">${taskActions(x)}</div></article>`;
}
function tasks(){
 const q=trimestreCourant(),an=Number(q.slice(0,4)),{debut,fin}=bornesTrimestre(q),{objectifs,taches}=elementsTrimestre(q);
 const notes=(S.data.semaines||[]).filter(n=>n.appreciation&&n.lundi<=fin&&new Date(Date.parse(n.lundi+'T12:00:00Z')+6*86400000).toISOString().slice(0,10)>=debut).sort((a,b)=>b.lundi.localeCompare(a.lundi));
 const stats=bilanPeriode(debut,fin);
 const annee=`${an>2000?b('quarter','← '+(an-1),(an-1)+q.slice(4),'ghost'):''}${an<2099?b('quarter',(an+1)+' →',(an+1)+q.slice(4),'ghost'):''}`;
 const trimestres=[1,2,3,4].map(n=>{const cle=an+'-T'+n,e=elementsTrimestre(cle);return `<button type="button" class="q-tab" data-action="quarter" data-id="${cle}" aria-pressed="${q===cle}"><b>Trimestre ${n}</b><span>${moisTrimestres[n-1]}</span><small>${e.objectifs.length} objectif(s) · ${e.taches.length} tâche(s)</small></button>`}).join('');
 const list=taches.filter(x=>S.taskFilter==='actives'?!['Terminée','Annulée'].includes(x.statut):S.taskFilter==='terminees'?x.statut==='Terminée':true).sort((a,b)=>(a.date||fin).localeCompare(b.date||fin)||a.titre.localeCompare(b.titre,'fr'));
 return head('Parcours de Malory','Vue trimestrielle','Choisissez un trimestre pour retrouver les objectifs, les tâches et les appréciations.',isAdmin()?b('newTask','+ Confier une tâche')+b('planGoals','Planifier des objectifs','','ghost'):'')+`<section class="card"><div class="card-head"><h2>Vue générale · ${an}</h2><div class="toolbar">${annee}</div></div><div class="q-grid">${trimestres}</div></section><section class="card"><p class="eyebrow">${esc(libTrimestre(q))}</p><h2>Bilan du trimestre</h2><div class="q-stats">${carteChiffre(objectifs.filter(acquis).length+' / '+objectifs.length,'Objectifs acquis')}${carteChiffre(taches.filter(t=>t.statut==='Terminée').length+' / '+taches.filter(t=>t.statut!=='Annulée').length,'Tâches terminées (hors annulations)')}${carteChiffre(stats.ventes,'Unités vendues')}${carteChiffre(stats.presents,'Jours de présence')}${carteChiffre(stats.absents,'Absences constatées')}</div></section><section class="card"><div class="card-head"><h2>Tâches confiées par Vanessa</h2><select aria-label="Filtrer les tâches" id="taskFilter">${[['toutes','Toutes'],['actives','À faire et en cours de validation'],['terminees','Terminées']].map(([k,t])=>`<option value="${k}" ${S.taskFilter===k?'selected':''}>${t}</option>`).join('')}</select></div>${list.length?list.map(carteTache).join(''):'<div class="empty">Aucune tâche pour cette sélection.</div>'}</section>`+tableauxObjectifs(objectifs)+`<section class="card"><div class="card-head"><h2>Appréciations hebdomadaires</h2>${isAdmin()?b('selectWeek','Choisir une semaine',q,'ghost'):''}</div>${notes.length?notes.map(n=>`<article class="item"><b>Semaine du ${frDate(n.lundi)}</b>${noteSemaine(n)}${b('openWeek','Ouvrir la semaine',n.lundi,'ghost')}</article>`).join(''):'<p class="muted">Aucune appréciation enregistrée sur ce trimestre.</p>'}</section>`+`<section class="card"><div class="card-head"><h2>Défis mensuels de ce trimestre</h2>${b('navdefis','Parcours complet','','ghost')}</div>${S.data.parcours.defis.filter(d=>trimestreDate(d.mois+'-01')===q).map(d=>`<div class="dk-line"><span>${esc(d.titre)}<small>${esc(libMois(d.mois))} · ${esc(d.statut)}</small></span>${boutonDefi(d,'Voir')}</div>`).join('')||'<p class="muted">Ce trimestre est en dehors du parcours de deux ans.</p>'}</section>`;
}
function taskActions(x){let r='';if(isAdmin()){if(x.statut==='À valider')r+=b('approveTask','✓ Valider',x.id)+b('reopenTask','Demander une correction',x.id,'ghost');if(['À faire','En cours','À reprendre'].includes(x.statut))r+=b('editTask','Modifier',x.id,'ghost');if(!['Terminée','Annulée'].includes(x.statut))r+=b('cancelTask','Annuler',x.id,'danger');}else{if(['À faire','À reprendre'].includes(x.statut))r+=b('startTask','Démarrer',x.id,'ghost');if(['À faire','En cours','À reprendre'].includes(x.statut))r+=b('completeTask','J’ai terminé ✓',x.id);}return r}
function objectifsTries(){return S.data.objectifs.slice().sort((a,b)=>a.echeance.localeCompare(b.echeance)||a.titre.localeCompare(b.titre,'fr'))}
function acquis(o){return o.statut==='Atteint'&&Number(o.progression)===100}
function debutObjectif(o){
  if(o.debut)return o.debut;
  const trim=/^TRI-(\d{4})-T([1-4])$/.exec(o.id),ann=/^ANN-(\d{4})-\d{4}$/.exec(o.id);
  if(trim)return trim[1]+'-'+String(3*Number(trim[2])-2).padStart(2,'0')+'-01';
  return ann?ann[1]+'-09-01':'';
}
function bilanPeriode(debut,fin){
  const res={ventes:0,presents:0,absents:0,heures:0,boutiques:{}};
  for(const j of S.data.activite){if(j.date<debut||j.date>fin)continue;
    res.ventes+=j.ventes;
    if(['Présence déclarée','Présence confirmée'].includes(j.presence)){res.presents++;res.heures+=Number(j.heures)||0}
    if(j.presence==='Absence constatée')res.absents++;
    for(const [k,q] of Object.entries(j.boutiques))res.boutiques[k]=(res.boutiques[k]||0)+q;
  }return res;
}
function moisLisible(m){return new Intl.DateTimeFormat('fr-FR',{month:'long',year:'numeric'}).format(new Date(m+'-15T12:00:00'))}
function carteChiffre(n,lib){return `<div class="pc-number"><strong>${esc(n)}</strong><span>${esc(lib)}</span></div>`}
function repereObjectif(o){const debut=debutObjectif(o);if(!debut)return '';const stats=bilanPeriode(debut,o.echeance),bloc=S.data.competences.find(c=>c.id===o.bloc);const q=stats.ventes;
 return `<div class="pc-repere">${bloc?`<div class="small"><b>${esc(bloc.bloc)}</b> · ${esc(bloc.titre)}</div>`:''}<div class="small muted">Du ${frDate(debut)} au ${frDate(o.echeance)}</div><div class="pc-facts"><span><b>${q}${o.cible?' / '+Number(o.cible):''}</b> unités vendues</span><span><b>${stats.presents}</b> j de présence</span><span><b>${stats.absents}</b> j d’absence constatée</span></div>${o.cible?`<progress max="${Number(o.cible)}" value="${Math.min(q,o.cible)}" aria-label="Cible de vente"></progress><small>${q>=o.cible?'Cible de vente atteinte':(o.cible-q)+' unités restantes pour la cible de vente'}</small>`:''}</div>`}
function tableauxObjectifs(list=objectifsTries()){const ouverts=list.filter(x=>!acquis(x)),clos=list.filter(acquis).reverse();
 function section(titre,items,vide){return `<section class="pc-section"><div class="card-head"><h2>${titre} <span class="pc-count">${items.length}</span></h2></div>${items.length?`<div class="pc-goals">${items.map(goalCard).join('')}</div>`:`<div class="empty">${vide}</div>`}</section>`}
 return section('Objectifs à atteindre',ouverts,'Aucun objectif à atteindre pour cette sélection.')+section('Objectifs acquis',clos,'Les objectifs validés par Vanessa apparaîtront ici avec leur bilan.');}
function apercuParcours(){const list=elementsTrimestre(trimestreDate(today())).objectifs,n=list.filter(acquis).length,next=list.find(x=>!acquis(x)),m=today().slice(0,7),stats=bilanPeriode(m+'-01',today()),pay=S.data.paiements.find(p=>p.mois===m);
 return `<section class="card pc-spot"><div class="card-head"><h2>Mon parcours Bachelor</h2>${b('quarter','Voir le trimestre',trimestreDate(today()),'ghost')}</div><div class="pc-facts"><span><b>${n} / ${list.length}</b> objectifs acquis</span><span><b>${stats.ventes}</b> unités vendues ce mois</span></div>${next?`<div class="pc-next"><small>Prochain objectif à travailler · échéance ${frDate(next.echeance)}</small><strong>${esc(next.titre)}</strong></div>`:''}<div class="item-actions">${b('navsuivi','Ventes, présences et paiements','','ghost')}${isAdmin()?b('planGoals','Planifier des objectifs'):''}</div><p class="footnote">Paiement de ${esc(moisLisible(m))} · ${pay?.effective?'versé le '+frDate(pay.effective):pay?.prevue?'prévu le '+frDate(pay.prevue):'date à renseigner'}</p></section>`}
function paiementForm(){if(!isAdmin())return;const mois=S.statsMois||today().slice(0,7),p=S.data.paiements.find(x=>x.mois===mois);formModal('Dates du paiement',moisLisible(mois),field('prevue','Date prévue',p?.prevue||'','date')+field('effective','Date du versement effectué',p?.effective||'','date'),f=>api('enregistrerPaiementParcours',S.token,{...f,mois,revision:p?.revision||''}));}
function planifierForm(){if(!isAdmin())return;const blocs=S.data.competences;let lot='';
 const ligne=()=>`<fieldset class="pc-plan"><legend>Objectif</legend><label>Intitulé<input name="titre" required minlength="6" maxlength="180" placeholder="Ex. Préparer une animation en pharmacie"></label><label>Compétence du Bachelor<select name="bloc" required><option value="">Choisir une compétence</option>${blocs.map(c=>`<option value="${esc(c.id)}">${esc(c.id+' · '+c.titre)}</option>`).join('')}</select></label>${choixTrimestre(trimestreCourant())}<label>Résultat attendu<input name="attendu" required minlength="8" maxlength="700" placeholder="Ex. Présenter le bilan de l’animation à Vanessa"></label><label>Cible de vente en unités (facultative)<input name="cible" type="number" min="1" step="1" inputmode="numeric"></label><button class="btn sm ghost" type="button" data-remove>Retirer cet objectif</button></fieldset>`;
 modal(`<h2>Planifier des objectifs</h2><p class="muted">Chaque objectif a sa compétence, sa période et son résultat attendu.</p><form id="planForm"><div id="planRows">${ligne()}</div><button type="button" class="btn sm secondary" data-add>+ Ajouter un objectif</button><p class="small" id="planError" role="alert"></p>${formButtons('Enregistrer le planning')}</form>`,root=>{
 const rows=root.querySelector('#planRows'),form=root.querySelector('#planForm');
 const sync=()=>{const sets=[...rows.children];sets.forEach((el,i)=>{el.querySelector('legend').textContent='Objectif '+(i+1);el.querySelector('[data-remove]').hidden=sets.length===1});root.querySelector('[data-add]').disabled=sets.length>=30};sync();
 root.querySelector('[data-add]').onclick=()=>{rows.insertAdjacentHTML('beforeend',ligne());sync();rows.lastElementChild.querySelector('input').focus()};
 rows.onclick=e=>{if(e.target.closest('[data-remove]')&&rows.children.length>1){e.target.closest('fieldset').remove();sync()}};
 form.onsubmit=async ev=>{ev.preventDefault();if(S.busy)return;const liste=[...rows.children].map(el=>Object.fromEntries([...el.querySelectorAll('[name]')].map(n=>[n.name,n.value])));for(const objectif of liste)Object.assign(objectif,bornesTrimestre(objectif.trimestre));const error=root.querySelector('#planError');
 if(!lot)lot=window.crypto.randomUUID();loading(true);try{await api('planifierObjectifs',S.token,lot,liste);closeModal();S.trimestre=liste[0].trimestre;S.view='tasks';await refresh();t(liste.length+' objectif(s) planifié(s).')}catch(e){if(error.isConnected)error.textContent=e.message;else t(e.message,'error')}finally{loading(false)}};
 });}


function modifierObjectifPlanifie(id){const x=S.data.objectifs.find(o=>o.id===id);if(!x||!x.bloc)return goalForm(id);
 const q=trimestreDate(x.debut),bornes=q?bornesTrimestre(q):{};
 const opts=S.data.competences.map(c=>({value:c.id,label:c.id+' · '+c.titre}));
 formModal('Modifier l’objectif',x.titre,field('titre','Intitulé',x.titre,'text',null,true)+field('bloc','Compétence',x.bloc,'select',opts,true)+(x.debut===bornes.debut&&x.echeance===bornes.fin?choixTrimestre(q):`<div class="form-grid">${field('debut','Date de début',x.debut,'date',null,true)}${field('fin','Échéance',x.echeance,'date',null,true)}</div>`)+field('indicateurs','Résultat attendu',x.indicateurs,'textarea',null,true)+field('cible','Cible de vente en unités (facultative)',x.cible||'','number')+`<div class="form-grid">${field('progression','Avancement (%)',x.progression,'number',null,true)}${field('statut','Statut',x.statut,'select',['À démarrer','En cours','Atteint','À réviser'],true)}</div>`+field('preuve','Preuves / bilan',x.preuve||'','textarea'),f=>api('mettreAJourObjectifBachelor',S.token,{...f,...(f.trimestre?bornesTrimestre(f.trimestre):{}),id,description:x.description,progression:f.statut==='Atteint'?100:f.progression}));}

function goalCard(o){return `<div class="card"><div class="goal-info"><span class="chip blue">Objectif ${esc(o.type.toLowerCase())} · ${esc(o.periode)}</span>${statusBadge(o.statut)}</div><div class="goal-title">${esc(o.titre)}</div><p class="muted" style="margin-top:8px">${esc(o.description)}</p><div class="criteria"><strong>Résultats attendus</strong><div style="margin-top:6px">${esc(o.indicateurs)}</div></div><div style="display:flex;justify-content:space-between;font-size:12px;color:var(--muted);margin-top:13px"><span>Progression</span><strong>${Number(o.progression)||0} %</strong></div><div class="progress"><span style="width:${Math.min(100,Math.max(0,Number(o.progression)||0))}%"></span></div>${repereObjectif(o)}${o.preuve?`<p class="small" style="margin-top:9px"><strong>Bilan :</strong> ${esc(o.preuve)}</p>`:''}${isAdmin()?`<div class="item-actions">${b('editGoal','Mettre à jour',o.id)}</div>`:''}</div>`}
function formation(){const d=S.data;return head('Bachelor communication, digital & événementiel','Mon parcours de formation','Objectifs suivis en entreprise avec Vanessa.',isAdmin()?b('planGoals','+ Planifier des objectifs'):'')+`<div class="card"><div class="card-head"><h2>Objectifs de formation</h2>${b('navtasks','Consulter par trimestre','','ghost')}</div></div>`+
 ` <section class="card"><div class="card-head"><div><h2>Compétences du Bachelor</h2><p class="small muted">4 blocs communs et 2 options possibles selon le parcours du CFA.</p></div><a class="small" href="https://www.francecompetences.fr/recherche/rncp/39249/" target="_blank" rel="noopener">Voir le référentiel RNCP</a></div>
 ${(d.competences||[]).map(c=>`<div class="item"><div class="skill"><div><strong>${esc(c.titre)}</strong><div class="small muted">${esc(c.bloc)}</div><p>${esc(c.application)}</p><div class="chips">${statusBadge(c.niveau)}${c.evaluateur?`<span class="chip">Évaluée par ${esc(c.evaluateur)}</span>`:''}</div>${isAdmin()?b('editSkill','Évaluer',c.id,'ghost'):''}</div></div>${c.preuve?`<p class="small"><strong>Exemple :</strong> ${esc(c.preuve)}</p>`:''}</div>`).join('')}</section>`}
function suivi(){
 const d=S.data,m=S.statsMois||today().slice(0,7),s=bilanPeriode(m+'-01',m+'-31'),pay=d.paiements.find(x=>x.mois===m);
 const pres=d.presences.filter(x=>x.date.startsWith(m)).sort((a,b)=>b.date.localeCompare(a.date)),loc=d.ventes.filter(x=>x.date.startsWith(m)),bilans=d.evaluations.filter(x=>x.mois===m);
 const journal=loc.length?`<details class="dk-info"><summary>Déclarations dans le parcours (${loc.length})</summary>${loc.map(x=>`<div class="dk-line"><span>${frDate(x.date)} · ${esc(x.boutique)}<small>${esc(x.commentaire)}</small></span><b>${esc(x.quantite)} déos</b>${isAdmin()?b('deleteSale','Supprimer',x.id,'danger'):''}</div>`).join('')}</details>`:'';
 return head('Suivi','Mes résultats',moisLisible(m))+tableauVendeuse()+`<section class="card"><div class="card-head"><h2>Présences et paiement</h2>${isAdmin()?b('payDate','Dates de paiement','','ghost'):''}</div><div class="vh-bilan"><span>Présences <b>${s.presents} j</b></span><span>Absences constatées <b>${s.absents} j</b></span><span>Paiement prévu <b>${pay?.prevue?frDate(pay.prevue):'À renseigner'}</b></span><span>Versé le <b>${pay?.effective?frDate(pay.effective):'—'}</b></span></div>${pres.length?`<details class="dk-info"><summary>Historique des présences (${pres.length})</summary>${pres.map(x=>`<div class="dk-line"><span>${frDate(x.date)}</span>${statusBadge(x.statut)}</div>`).join('')}</details>`:''}${journal}${bilans.length?`<details class="dk-info"><summary>Bilan mensuel</summary>${bilans.map(x=>`<p>${esc(x.bilan)} ${esc(x.objectifs)}</p>`).join('')}</details>`:''}<div class="item-actions">${isAdmin()?b('correctPresence','Corriger une présence','','ghost')+b('evalMonth','Bilan mensuel','','ghost'):''}${d.vendeuse?.etat!=='relie'?b('sale','Déclarer une vente dans le parcours','','ghost'):''}</div></section>`;
}
function adminPage(){if(!isAdmin())return home();const d=S.data;const pending=toutesDirectives().filter(x=>x.statut==='À valider');return head('Maître d’apprentissage','Administration Vanessa','Affectations, missions, évaluation et correction des présences.',b('newTask','+ Créer une mission'))+
 `<div class="twocol"><div class="card"><h2>Organisation du travail</h2><p class="muted">Programmer la boutique de Malory et préciser ses tâches.</p><div class="item-actions">${b('navplanning','Modifier le planning')}${b('newTask','Ajouter une tâche','','ghost')}</div></div><div class="card"><h2>Formation Bachelor</h2><p class="muted">Planifier plusieurs objectifs et valider les acquis de Malory.</p><div class="item-actions">${b('planGoals','Planifier des objectifs')}${b('navformation','Suivre les objectifs','','ghost')}${b('correctPresence','Corriger une présence','','ghost')}</div></div></div>
 <section class="card"><h2>Missions en attente de validation (${pending.length})</h2>${pending.length?pending.slice(0,30).map(x=>`<div class="item"><strong>${esc(x.titre)}</strong><div class="small muted">${esc(periodeTache(x))} · ${esc(x.rapport)}</div><div class="item-actions">${b('approveTask','Valider',x.id)}${b('reopenTask','Demander une correction',x.id,'ghost')}</div></div>`).join(''):'<div class="empty">Aucune validation en attente.</div>'}</section>`}
function modal(html,callback){const root=$('modalRoot');root.innerHTML=`<div class="modalbg"><div class="modal" role="dialog" aria-modal="true">${html}</div></div>`;root.querySelector('.modalbg').addEventListener('click',ev=>{if(ev.target.classList.contains('modalbg'))closeModal()});root.querySelectorAll('[data-close]').forEach(x=>x.addEventListener('click',closeModal));if(callback)callback(root)}
function closeModal(){$('modalRoot').innerHTML=''}
function field(name,label,value='',type='text',options=null,required=false){let html='';const req=required?'required':'';if(type==='textarea')html=`<textarea name="${esc(name)}" ${req}>${esc(value)}</textarea>`;else if(type==='select')html=`<select name="${esc(name)}" ${req}>${(options||[]).map(v=>{const k=typeof v==='object'?v.value:v,txt=typeof v==='object'?v.label:v;return `<option value="${esc(k)}" ${String(k)===String(value)?'selected':''}>${esc(txt)}</option>`}).join('')}</select>`;else html=`<input type="${esc(type)}" name="${esc(name)}" value="${esc(value)}" ${req}>`;return `<div class="form-field"><label>${esc(label)}</label>${html}</div>`}
const formButtons=label=>`<div class="modal-actions"><button class="btn ghost" type="button" data-close>Annuler</button><button class="btn" type="submit">${esc(label)}</button></div>`;
function formModal(title,description,inputs,onSubmit,submitText='Enregistrer'){modal(`<h2>${esc(title)}</h2><p class="muted" style="margin-top:6px">${esc(description)}</p><form id="editorForm">${inputs}${formButtons(submitText)}</form>`,root=>root.querySelector('#editorForm').addEventListener('submit',async ev=>{ev.preventDefault();if(S.busy)return;loading(true);try{await onSubmit(Object.fromEntries(new FormData(ev.currentTarget).entries()));closeModal();t('Enregistré avec succès');await refresh()}catch(ex){t(ex.message,'error')}finally{loading(false)}}))}
function assignment(ref){const [date,code]=ref.split('|');const saturday=new Date(date+'T12:00:00').getDay()===6;const s=getWeekDay(date)?.creneaux?.find(x=>x.code===code);const sites=code==='SIEGE'?['Siège KVG']:saturday?S.data.pointsSamedi:S.data.boutiques.filter(x=>x!=='Siège KVG');formModal('Affectation du '+frDate(date),slotName(code)+' · choisir le lieu de travail.',field('site','Lieu',sites.includes(s?.site)?s.site:sites[0],'select',sites,true)+field('consignes','Consignes (facultatif)',s?.consignes||'','textarea'),f=>api('programmerCreneauV3',S.token,{date,code,...f}),'Valider le planning')}
function taskForm(obj){if(!isAdmin())return;const types=['Communication Instagram','Mailing et promotion','Présentation / formation de gamme','Prise de rendez-vous','Appel pharmacie','Relance pharmacie','Appel distributeur','Contrôle stock officine','Réassort pharmacie','Commande client','Classement de documents KVG','Étiquetage / numéro de lot','Analyse des ventes','Autre'];const categories=[...new Set([...(S.data?.categories||[]),...types])].filter(x=>!/num[ée]ri[kq].?cloud/i.test(x));const validTypes=types.filter(x=>categories.includes(x));formModal(obj?'Modifier la mission':'Nouvelle mission pour Malory','Choisissez un trimestre et indiquez à Malory quoi faire et comment s’y prendre. Vous pouvez préciser une échéance.',
 `<div class="form-grid">${choixTrimestre(obj?.trimestre||trimestreDate(obj?.date)||trimestreCourant())}${field('date','Échéance (facultative)',obj?.date||'','date')}${field('heure','Heure limite (facultative)',obj?.heure||'','time')}${field('categorie','Type',validTypes.includes(obj?.categorie)?obj.categorie:validTypes[0],'select',validTypes,true)}${field('priorite','Priorité',obj?.priorite||'Normale','select',['Normale','Haute'])}</div>`+
 field('titre','Mission',obj?.titre||'','text',null,true)+field('quoi','Quoi faire ?',obj?.quoi||'','textarea',null,true)+field('comment','Comment faire ?',obj?.comment||'','textarea',null,true)+field('attendu','Résultat attendu',obj?.attendu||'','textarea')+'<details class="q-details"><summary>Lieu, contact et validation</summary>'+field('pourQui','Pour qui ?',obj?.pourQui||'KVG')+field('contact','Contact',obj?.contact||'')+field('canal','Où / canal',obj?.canal||'')+field('lieu','Lieu',obj?.lieu||'')+field('supervision','Validation Vanessa',obj?.supervision===false?'false':'true','select',[{value:'true',label:'Oui'},{value:'false',label:'Non'}])+'</details>',
 async f=>{await api(obj?'modifierDirectiveV3':'creerDirectiveV3',S.token,...(obj?[obj.id]:[]),{...f,supervision:f.supervision==='true'});S.trimestre=f.trimestre;S.taskFilter='toutes';S.view='tasks'},'Enregistrer la mission')}
function reportTask(id){const x=toutesDirectives().find(t=>t.id===id);formModal('Terminer la mission',x?.titre||'Mission',field('rapport','Qu’as-tu réalisé ? (quelques mots)',x?.rapport||'','textarea',null,true),f=>api('completerDirectiveV3',S.token,id,'SOUMETTRE',f.rapport),'Soumettre à Vanessa')}
function skillForm(id){const x=S.data.competences.find(v=>v.id===id);if(!x)return;formModal('Évaluer une compétence',x.titre,field('niveau','Progression en entreprise',x.niveau,'select',['À découvrir','En progression','Autonome en entreprise'],true)+field('preuve','Exemple concret / livrable',x.preuve||'','textarea'),f=>api('evaluerCompetenceBachelor',S.token,{id,...f}),'Mettre à jour')}
function goalForm(id){const x=S.data.objectifs.find(v=>v.id===id);if(!x)return;formModal('Objectif '+x.type.toLowerCase(),x.periode+' · échéance '+frDate(x.echeance),field('titre','Intitulé',x.titre,'text',null,true)+field('description','Action à mener',x.description,'textarea',null,true)+field('indicateurs','Indicateurs mesurables',x.indicateurs,'textarea',null,true)+`<div class="form-grid">${field('progression','Avancement (%)',x.progression,'number',null,true)}${field('statut','Statut',x.statut,'select',['À démarrer','En cours','Atteint','À réviser'],true)}</div>`+field('preuve','Preuves / bilan',x.preuve||'','textarea'),f=>api('mettreAJourObjectifBachelor',S.token,{id,...f,progression:f.statut==='Atteint'?100:f.progression}),'Enregistrer l’objectif')}
function correctPresence(){formModal('Correction de présence','Seule Vanessa peut régulariser une date passée, avec une justification.',field('date','Date',today(),'date',null,true)+field('statut','Statut','Présence confirmée','select',['Présence confirmée','Absence constatée','Non déclarée'],true)+field('motif','Motif / justificatif','','textarea',null,true),f=>api('corrigerPresenceSimple',S.token,f),'Enregistrer la correction')}
function saleForm(){const stores=S.data.boutiques.filter(x=>x!=='Siège KVG');formModal('Déclarer des ventes','Total de déodorants vendus, par boutique.',field('date','Date',today(),'date',null,true)+field('boutique','Boutique',stores[0],'select',stores,true)+field('quantite','Quantité',0,'number',null,true)+field('commentaire','Retour client / observation','','textarea'),f=>api('enregistrerVente',S.token,f),'Enregistrer la vente')}
function monthlyForm(){const scores=['ponctualite','organisation','rigueur','vente','connaissances','etiquetage','communication','autonomie'];formModal('Bilan mensuel interne','Noter chaque critère de 1 à 5. Le suivi Bachelor se trouve dans l’onglet Compétences.',field('mois','Mois',today().slice(0,7),'month',null,true)+`<div class="form-grid">${scores.map(k=>field(k,k[0].toUpperCase()+k.slice(1),'3','select',['1','2','3','4','5'],true)).join('')}</div>`+field('bilan','Bilan','','textarea')+field('objectifs','Axes de progrès du mois suivant','','textarea'),f=>api('enregistrerEvaluation',S.token,f),'Enregistrer le bilan')}
function changeWeek(delta){const dt=new Date((S.weekDate||today())+'T12:00:00');dt.setDate(dt.getDate()+delta*7);const d=[dt.getFullYear(),String(dt.getMonth()+1).padStart(2,'0'),String(dt.getDate()).padStart(2,'0')].join('-');S.weekDate=d;refresh().catch(()=>{})}
async function doAction(act,id){try{
 if(act==='navplanning'){S.view='planning';render();return}if(act==='navtasks'){S.view='tasks';render();return}if(act==='navformation'){S.view='formation';render();return}
 if(act==='prevweek'||act==='nextweek'){changeWeek(act==='prevweek'?-1:1);return}
 if(act==='navdefis'||act==='navresultats'){S.view=act==='navdefis'?'defis':'resultats';render();window.scrollTo({top:0});return}
 if(act==='demarrerDefi'){if(S.busy)return;const d=S.data.parcours.defis.find(x=>x.id===id);loading(true);await api('agirDefiMalory',S.token,{id,action:'demarrer',revision:d.revision});await refresh();t('Défi en cours.');return}
 if(act==='ouvrirDefi'){const d=S.data.parcours.defis.find(x=>x.id===id);if(d){S.defi=id;S.anneeDefis=d.annee;S.view='defis';render();$('defiActif')?.scrollIntoView({block:'start',behavior:'smooth'})}return}
 if(act==='anneeDefis'){S.anneeDefis=Number(id);S.defi=S.data.parcours.defis.find(d=>d.annee===S.anneeDefis).id;render();return}
 if(act==='anCourbe'){S.anCourbe=Number(id);render();return}
 if(['soumettreDefi','validerDefi','reprendreDefi'].includes(act)){formulaireDefi(act,id);return}
 if(act==='jourVentes'){validerJourForm(id);return}
 if(act==='quarter'){if(/^20\d{2}-T[1-4]$/.test(id)){S.trimestre=id;S.view='tasks';render()}return}
 if(act==='weekly'){formulaireSemaine();return}
 if(act==='taskWeek'){S.trimestre=id;taskForm();return}
 if(act==='openWeek'||act==='selectWeek'){const q=trimestreDate(today());S.weekDate=act==='openWeek'?id:id===q?today():bornesTrimestre(id).debut;S.view='planning';await refresh();return}
 if(act==='assign'){assignment(id);return}if(act==='newTask'){taskForm();return}if(act==='editTask'){taskForm(toutesDirectives().find(x=>x.id===id));return}
 if(act==='completeTask'){reportTask(id);return}if(act==='editSkill'){skillForm(id);return}if(act==='editGoal'){modifierObjectifPlanifie(id);return}
 if(act==='planGoals'){planifierForm();return}if(act==='payDate'){paiementForm();return}if(act==='navsuivi'){S.view='suivi';render();return}
 if(act==='sale'){saleForm();return}if(act==='correctPresence'){correctPresence();return}if(act==='evalMonth'){monthlyForm();return}
 const direct={startTask:['completerDirectiveV3',id,'DEMARRER',''],approveTask:['completerDirectiveV3',id,'VALIDER',''],reopenTask:['completerDirectiveV3',id,'REOUVRIR','À revoir par Malory'],cancelTask:['completerDirectiveV3',id,'ANNULER',''],approveRoutine:['validerMission',id],deleteSale:['supprimerVente',id]};
 if(direct[act]){if(['cancelTask','reopenTask','deleteSale'].includes(act)&&!confirm('Confirmer cette modification ?'))return;loading(true);await api(direct[act][0],S.token,...direct[act].slice(1));if(act==='approveRoutine'&&S.routines?.[trimestreCourant()])S.routines[trimestreCourant()]=await api('lireRoutinesTrimestre',S.token,trimestreCourant());t('Modification enregistrée');await refresh()}
 }catch(ex){t(ex.message,'error')}finally{loading(false)}}
function messageAcces(message, erreur=false){
 const el=$('accessMsg');el.textContent=message;el.classList.toggle('error',erreur);el.classList.toggle('hidden',!message);
}
function voirNouveau(visible){
 for(const id of ['newAccess','confirmAccess'])$(id).type=visible?'text':'password';
 $('showAccess').textContent=visible?'Masquer':'Afficher';
 $('showAccess').setAttribute('aria-pressed',String(visible));
}
function selectionAcces(){
 const compte=$('accessTarget'),perso=compte.value==='GUILLAUME';
 $('currentAccessWrap').classList.toggle('hidden',!perso);$('currentAccess').required=perso;
 for(const id of ['currentAccess','newAccess','confirmAccess'])$(id).value='';
 voirNouveau(false);messageAcces('');$('saveAccess').disabled=!compte.value;
}
function ouvrirAcces(){
 $('accessForm').reset();selectionAcces();
 $('auth').classList.add('hidden');$('app').classList.add('hidden');$('accessPanel').classList.remove('hidden');
 $('accessTarget').focus();
}
$('lierVendeuse').addEventListener('click',choisirVendeuse);
$('notesGael').addEventListener('click',evalGael);
$('accessTarget').addEventListener('change',selectionAcces);
$('showAccess').addEventListener('click',()=>voirNouveau($('newAccess').type==='password'));
$('generateAccess').addEventListener('click',()=>{
 if(!$('accessTarget').value){$('accessTarget').focus();messageAcces('Choisis d’abord un compte.');return}
 const alphabet='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
 const bytes=crypto.getRandomValues(new Uint8Array(20));
 const mot=Array.from(bytes,n=>alphabet[n&63]).join('');
 $('newAccess').value=mot;$('confirmAccess').value=mot;voirNouveau(true);
 messageAcces('Mot de passe généré. Copie-le avant de l’enregistrer.');
});
$('accessForm').addEventListener('submit',async e=>{
 e.preventDefault();if(S.busy)return;
 const cible=$('accessTarget').value,mot=$('newAccess').value;
 if(mot!==$('confirmAccess').value){messageAcces('Les deux mots de passe doivent être identiques.',true);$('confirmAccess').focus();return}
 if(mot.trim().length<8||new TextEncoder().encode(mot).length>72){messageAcces('Utilise au moins 8 caractères, dans la limite de 72 octets.',true);return}
 loading(true);messageAcces('');
 try{
   await api('definirMotDePasseCompte',S.token,cible,mot,$('currentAccess').value);
   if(cible==='GUILLAUME'){logout();$('role').value='GUILLAUME';$('loginMsg').classList.remove('hidden','error');$('loginMsg').textContent='Ton mot de passe a été modifié. Reconnecte-toi.';return}
   const nom=cible==='MALORY'?'Malory':'Vanessa';
   $('accessForm').reset();selectionAcces();messageAcces('Mot de passe de '+nom+' enregistré. Il est utilisable dès maintenant.');
 }catch(err){
   if(/Session expirée/i.test(err.message)){logout();$('role').value='GUILLAUME';$('loginMsg').classList.remove('hidden');$('loginMsg').textContent=err.message}
   else messageAcces(err.message,true);
 }finally{loading(false)}
});
$('exitAccess').addEventListener('click',quitterCompte);

function logout(){$('accessForm').reset();selectionAcces();$('accessPanel').classList.add('hidden');S.statsMois='';S.trimestre='';S.defi='';S.anCourbe=0;S.anneeDefis=0;S.token='';S.profile=null;S.data=null;S.week=[];S.weekDate='';S.selectedDate='';S.view='home';$('app').classList.add('hidden');$('auth').classList.remove('hidden');$('loginForm').reset();closeModal()}
$('loginForm').addEventListener('submit',async ev=>{ev.preventDefault();if(S.busy)return;const formulaireConnexion=ev.currentTarget;const f=new FormData(formulaireConnexion),el=$('loginMsg');el.classList.remove('error');el.classList.remove('hidden');el.textContent='Connexion en cours…';loading(true);try{await ouvrirProfil(await api('connexion',f.get('role'),f.get('pin')));}catch(x){el.classList.add('error');el.textContent='Connexion impossible : '+x.message;logout();el.classList.remove('hidden')}finally{loading(false)}});
$('signout').addEventListener('click',quitterCompte);
document.querySelectorAll('[data-bio]').forEach(el=>el.addEventListener('click',lierEmpreinte));
$('reload').addEventListener('click',()=>refresh().catch(()=>{}));
$('nav').addEventListener('click',ev=>{const n=ev.target.closest('[data-view]');if(n){S.view=n.dataset.view;render();window.scrollTo({top:0,behavior:'smooth'})}});
$('content').addEventListener('click',ev=>{const x=ev.target.closest('button[data-action]');if(x)doAction(x.dataset.action,x.dataset.id)});
$('content').addEventListener('change',async ev=>{const el=ev.target;try{
 if(el.id==='statsMois'&&/^20\d{2}-(0[1-9]|1[0-2])$/.test(el.value)){S.statsMois=el.value;render();return}
 if(el.id==='taskFilter'){S.taskFilter=el.value;render();return}
 
 if(el.id==='weekPicker'){S.weekDate=el.value;await refresh();return}
 if(el.id==='presenceCheck'){loading(true);try{await api('cocherPresenceSimple',S.token,el.checked);t('Présence mise à jour');await refresh()}catch(x){t(x.message,'error');await refresh()}finally{loading(false)}return}
 if(el.dataset.mission){loading(true);await api('cocherMission',S.token,el.dataset.mission,el.checked,'');if(S.routines?.[trimestreCourant()])S.routines[trimestreCourant()]=await api('lireRoutinesTrimestre',S.token,trimestreCourant());t('Mission cochée');await refresh()}
 }catch(x){t(x.message,'error');refresh().catch(()=>{})}finally{loading(false)}});
window.addEventListener('error',ev=>{const msg=$('loginMsg');if(!$('auth').classList.contains('hidden')){msg.classList.remove('hidden');msg.classList.add('error');msg.textContent='Erreur de l’interface : '+ev.message}else t('Erreur de l’interface : '+ev.message,'error')});
})();
