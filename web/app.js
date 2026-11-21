
(function(){
'use strict';
const S={token:'',profile:null,data:null,week:[],view:'home',weekDate:'',selectedDate:'',taskFilter:'actives',busy:false};
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
function loading(v){S.busy=v;$('loading').classList.toggle('hidden',!v)}
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
 const y=Number(today().slice(0,4)),d=await api('chargerDonnees',S.token,y);
 if(!d||!Array.isArray(d.presences)||!Array.isArray(d.competences)||!Array.isArray(d.objectifs))throw new Error('Mise à jour du Code.gs nécessaire : données Bachelor ou présences manquantes.');
 S.data=d;if(!S.weekDate)S.weekDate=d.aujourdHui;if(!S.selectedDate)S.selectedDate=d.aujourdHui;
 S.week=await api('listerPlanningSemaineV3',S.token,S.weekDate);render();
 }catch(err){if(/Session expirée/i.test(err.message))logout();t(err.message,'error');throw err}finally{loading(false)}}
const b=(action,label,id='',type='secondary')=>`<button type="button" class="btn sm ${type}" data-action="${esc(action)}" data-id="${esc(id)}">${label}</button>`;
const statusBadge=raw=>{const v=String(raw||'');const cls=v==='Non déclarée'?'blue':/confirmée|déclarée|terminée|atteint|autonome|acquise/i.test(v)?'ok':/absen|annul|réviser/i.test(v)?'red':/attente|cours|progression/i.test(v)?'warn':'blue';return `<span class="chip ${cls}">${esc(v||'À venir')}</span>`};
const head=(over,title,desc,actions='')=>`<div class="page-head"><div><div class="eyebrow">${esc(over)}</div><h1>${esc(title)}</h1><p>${esc(desc)}</p></div>${actions?`<div class="toolbar">${actions}</div>`:''}</div>`;
function nav(){let items=[['home','⌂','Accueil'],['planning','▦','Planning'],['tasks','☑','Mes missions'],['formation','★','Compétences & objectifs'],['suivi','▤','Suivi']];if(isAdmin())items.push(['admin','⚙','Administration']);$('nav').innerHTML=items.map(([k,ic,n])=>`<button type="button" class="navbutton ${S.view===k?'active':''}" data-view="${k}" aria-current="${S.view===k?'page':'false'}"><span>${ic}</span>${esc(n)}</button>`).join('')}
function render(){if(!S.data)return;nav();$('content').innerHTML=({home,planning,tasks,formation,suivi,admin:adminPage}[S.view]||home)()}
function shiftsCard(shifts,d){return shifts?.length?shifts.map(s=>`<div class="shift"><div><strong>${esc(slotName(s.code))}</strong><div class="sub">${esc(s.site)} · ${esc(s.accompagne)}</div>${s.consignes?`<div class="sub">${esc(s.consignes)}</div>`:''}</div>${isAdmin()?b('assign','Modifier',d+'|'+s.code,'ghost'):''}</div>`).join(''):'<div class="empty">Formation CFA ou repos, pas de présence à déclarer.</div>'}
function presenceWidget(){let d=S.data,p=currentPresence(),shifts=getWeekDay(today())?.creneaux||[];
 if(!shifts.length)return '<div class="notice">Aucune présence à cocher aujourd’hui : CFA ou repos.</div>';
 const locked=!!p?.corrigePar;
 return `<div class="presence-box ${locked?'locked':''}"><input id="presenceCheck" type="checkbox" ${activePresence(p)?'checked':''} ${isAdmin()||locked||S.busy?'disabled':''} aria-label="Je suis présente aujourd’hui"><label for="presenceCheck">${activePresence(p)?'Présence enregistrée ✓':'Je suis présente aujourd’hui'}<small>${locked?'Présence corrigée par Vanessa.':isAdmin()?'Déclaration de Malory.':'Une seule case à cocher pour la journée, sans code ni entrée/sortie.'}</small></label></div>
 <div class="footnote">${p?.declareLe?`Déclaration : ${esc(p.declareLe)} · `:''}${p?.statut?`Statut : ${esc(p.statut)}. `:''}Cette case confirme une présence déclarée, pas les heures réellement effectuées.</div>`}
function home(){const d=S.data,day=getWeekDay(d.aujourdHui),shifts=day?.creneaux||[];
 const open=d.directives.filter(x=>['À faire','En cours','À reprendre','À valider'].includes(x.statut)).sort((a,z)=>a.date.localeCompare(z.date));
 const p=currentPresence(),done=d.directives.filter(x=>x.statut==='Terminée').length;
 return `<div class="hero"><div class="eyebrow" style="color:#8ee8d5">ESPACE ${isAdmin()?'VANESSA':'MALORY'}</div><h1>${isAdmin()?'Suivi de Malory':'Bonjour Malory 👋'}</h1><p class="muted" style="margin-top:8px">${esc(niceDate(d.aujourdHui))} · Bachelor Communication, Digital & Événementiel</p><div class="chips"><span class="chip">24 h en entreprise</span><span class="chip">11 h de CFA</span><span class="chip">RNCP 39249</span></div></div>
 ${apercuParcours()}
 <div class="statgrid"><div class="stat primary"><b>${shifts.length?(shifts.reduce((v,s)=>v+(s.code==='MATIN'?4:2),0))+' h':'CFA / Repos'}</b><span>Heures prévues aujourd’hui</span></div><div class="stat"><b>${open.length}</b><span>Missions en attente</span></div><div class="stat"><b>${done}</b><span>Missions terminées (année)</span></div></div>
 <div class="twocol"><section class="card"><div class="card-head"><h2>Aujourd’hui</h2>${b('navplanning','Planning complet','','ghost')}</div>${shiftsCard(shifts,d.aujourdHui)}${presenceWidget()}</section>
 <section class="card"><div class="card-head"><h2>Mes priorités</h2>${b('navtasks','Toutes les missions','','ghost')}</div>${open.length?open.slice(0,4).map(x=>`<div class="item"><strong>${esc(x.titre)}</strong><div class="small muted">${frDate(x.date)} · ${esc(x.categorie)}</div><div class="chips">${statusBadge(x.statut)}</div></div>`).join(''):'<div class="empty">Aucune mission en attente.</div>'}</section></div>
 `}
function planning(){const d=S.data;return head('Semaine de travail','Mon planning','Lieux, horaires et accompagnement visibles en un coup d’œil.',`${b('prevweek','←','','ghost')}<input type="date" id="weekPicker" class="date-in" style="width:auto" value="${esc(S.weekDate)}">${b('nextweek','→','','ghost')}`)+
 `<div class="notice" style="margin-bottom:16px">Mercredi et vendredi : 08h30–12h30 en boutique, 15h–17h au siège, 17h–19h en boutique. Jeudi et samedi : 08h30–12h30. Le samedi, Malory travaille sur le stand en autonomie.</div>
 <section class="card">${S.week.map(x=>`<div class="day"><div class="day-label"><strong>${esc(weekDay(x.date))}</strong><span>${esc(frDate(x.date))}</span>${x.date===d.aujourdHui?'<div class="chips"><span class="chip ok">Aujourd’hui</span></div>':''}</div><div>${x.creneaux.length?shiftsCard(x.creneaux,x.date):`<span class="chip blue">${[1,2].includes(new Date(x.date+'T12:00:00').getDay())?'Formation CFA':'Repos'}</span>`}${x.date===d.aujourdHui&&x.creneaux.length?`<div class="small muted" style="margin-top:7px">${activePresence(currentPresence())?'✓ Présence enregistrée':'Présence pas encore déclarée'}</div>`:''}</div></div>`).join('')}</section>`}
function taskActions(x){let r='';if(isAdmin()){if(x.statut==='À valider')r+=b('approveTask','✓ Valider',x.id);if(['À faire','En cours','À reprendre'].includes(x.statut))r+=b('editTask','Modifier',x.id,'ghost');if(!['Terminée','Annulée'].includes(x.statut))r+=b('cancelTask','Annuler',x.id,'danger');}else{if(['À faire','À reprendre'].includes(x.statut))r+=b('startTask','Démarrer',x.id,'ghost');if(['À faire','En cours','À reprendre'].includes(x.statut))r+=b('completeTask','J’ai terminé ✓',x.id);}return r}
function tasks(){let d=S.data;const active=x=>['À faire','En cours','À reprendre','À valider'].includes(x.statut);let list=d.directives.filter(x=>!/num[ée]ri[kq].?cloud|num[ée]rikcloud|numericloud/i.test([x.titre,x.quoi,x.categorie].join(' '))).filter(x=>S.taskFilter==='actives'?active(x):S.taskFilter==='terminees'?x.statut==='Terminée':true).sort((a,z)=>a.date.localeCompare(z.date));
 let routines=d.missions.filter(x=>x.date===S.selectedDate&&!/num[ée]ri[kq].?cloud|num[ée]rikcloud|numericloud|fabrication|formulation/i.test(x.titre));
 return head('Travail confié par Vanessa','Mes missions','Une liste simple : les consignes, puis la case pour terminer.',isAdmin()?b('newTask','+ Ajouter une mission'):'' )+
 `<div class="card"><div class="card-head"><h2>Consignes particulières</h2><select id="taskFilter" class="date-in" style="width:auto"><option value="actives" ${S.taskFilter==='actives'?'selected':''}>À faire / En cours</option><option value="terminees" ${S.taskFilter==='terminees'?'selected':''}>Terminées</option><option value="toutes" ${S.taskFilter==='toutes'?'selected':''}>Toutes</option></select></div>${list.length?list.slice(0,100).map(x=>`<div class="item"><div class="item-head"><strong>${esc(x.titre)}</strong><span class="small muted">${frDate(x.date)}</span></div><div class="chips">${statusBadge(x.statut)}<span class="chip">${esc(x.categorie)}</span></div><p><strong>À faire :</strong> ${esc(x.quoi||x.titre)}</p><p><strong>Comment :</strong> ${esc(x.comment)}</p>${x.attendu?`<p><strong>Résultat attendu :</strong> ${esc(x.attendu)}</p>`:''}${x.rapport?`<p><strong>Compte rendu :</strong> ${esc(x.rapport)}</p>`:''}<div class="item-actions">${taskActions(x)}</div></div>`).join(''):'<div class="empty">Aucune mission pour ce filtre.</div>'}</div>
 <section class="card"><div class="card-head"><h2>Routine du jour</h2><input type="date" id="routineDate" class="date-in" style="width:auto" value="${esc(S.selectedDate)}"></div>${routines.length?routines.map(x=>`<div class="item"><label style="display:flex;gap:12px;align-items:flex-start"><input data-mission="${esc(x.id)}" type="checkbox" style="width:21px;height:21px;accent-color:var(--teal);flex:none;margin-top:2px" ${['Fait','À valider'].includes(x.statut)?'checked':''} ${x.statut==='Fait'&&!isAdmin()?'disabled':''}><span><strong>${esc(x.titre)}</strong><span class="small muted" style="display:block">${esc(x.creneau)} · ${esc(x.lieu)}</span><span class="small muted" style="display:block;margin-top:3px">${esc(x.consigne)}</span>${x.statut==='À valider'&&isAdmin()?`<span style="display:block;margin-top:8px">${b('approveRoutine','Valider ✓',x.id)}</span>`:''}</span></label></div>`).join(''):'<div class="empty">Aucune routine ce jour-là.</div>'}</section>`}

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
function tableauxObjectifs(){const list=objectifsTries(),ouverts=list.filter(x=>!acquis(x)),clos=list.filter(acquis).reverse();
 function section(titre,items,vide){return `<section class="pc-section"><div class="card-head"><h2>${titre} <span class="pc-count">${items.length}</span></h2></div>${items.length?`<div class="pc-goals">${items.map(goalCard).join('')}</div>`:`<div class="empty">${vide}</div>`}</section>`}
 return section('Objectifs à atteindre',ouverts,'Tous les objectifs planifiés sont acquis.')+section('Objectifs acquis',clos,'Les objectifs validés par Vanessa apparaîtront ici avec leur bilan.');}
function apercuParcours(){const list=objectifsTries(),n=list.filter(acquis).length,next=list.find(x=>!acquis(x)),m=today().slice(0,7),stats=bilanPeriode(m+'-01',today()),pay=S.data.paiements.find(p=>p.mois===m);
 return `<section class="card pc-spot"><div class="card-head"><h2>Mon parcours Bachelor</h2>${b('navformation','Voir les objectifs','','ghost')}</div><div class="pc-facts"><span><b>${n} / ${list.length}</b> objectifs acquis</span><span><b>${stats.ventes}</b> unités vendues ce mois</span></div>${next?`<div class="pc-next"><small>Prochain objectif à travailler · échéance ${frDate(next.echeance)}</small><strong>${esc(next.titre)}</strong></div>`:''}<div class="item-actions">${b('navsuivi','Ventes, présences et paiements','','ghost')}${isAdmin()?b('planGoals','Planifier des objectifs'):''}</div><p class="footnote">Paiement de ${esc(moisLisible(m))} · ${pay?.effective?'versé le '+frDate(pay.effective):pay?.prevue?'prévu le '+frDate(pay.prevue):'date à renseigner'}</p></section>`}
function tableauActivite(){const m=S.statsMois||today().slice(0,7),from=m+'-01',end=m+'-31',s=bilanPeriode(from,end),note=S.data.notesVente.find(x=>x.mois===m&&x.note>0),pay=S.data.paiements.find(x=>x.mois===m);
 const comptes=Object.entries(s.boutiques).sort((a,b)=>b[1]-a[1]);
 const mois=Array.from({length:6},(_,i)=>{const dt=new Date(Number(m.slice(0,4)),Number(m.slice(5))-6+i,15);const k=dt.getFullYear()+'-'+String(dt.getMonth()+1).padStart(2,'0');return {k,n:bilanPeriode(k+'-01',k+'-31').ventes}});const max=Math.max(1,...mois.map(x=>x.n));
 return `<section class="card"><div class="card-head"><h2>Résultats de Malory</h2><label class="pc-month">Mois <input id="statsMois" type="month" value="${esc(m)}"></label></div><div class="pc-numbers">${carteChiffre(s.ventes,'Unités vendues')}${carteChiffre(note?note.note+' / 5':'—','Note de vente de Vanessa')}${carteChiffre(s.presents,'Jours de présence')}${carteChiffre(s.absents,'Absences constatées')}</div><p class="footnote">${s.heures} h prévues sur les journées de présence du mois.</p><div class="pc-two"><div><h3>Ventes sur six mois</h3>${mois.map(x=>`<div class="pc-bar"><span>${esc(moisLisible(x.k))}</span><i><i style="width:${100*x.n/max}%"></i></i><b>${x.n}</b></div>`).join('')}</div><div><h3>Ventes par boutique</h3>${comptes.length?comptes.map(([k,v])=>`<div class="pc-line"><span>${esc(k.slice(2))}</span><b>${v}</b></div>`).join(''):'<p class="small muted">Aucune vente saisie pour ce mois.</p>'}</div></div></section>
 <section class="card"><div class="card-head"><div><h2>Calendrier de paiement</h2><p class="small muted">${esc(moisLisible(m))}</p></div>${isAdmin()?b('payDate',pay?'Modifier les dates':'Renseigner les dates'):''}</div><div class="pc-two"><div><small>Date prévue</small><strong class="pc-date">${pay?.prevue?frDate(pay.prevue):'À renseigner'}</strong></div><div><small>Versement effectué le</small><strong class="pc-date">${pay?.effective?frDate(pay.effective):'Non renseigné'}</strong></div></div>${S.data.paiements.length?`<details><summary>Toutes les dates de paiement</summary>${S.data.paiements.slice().sort((a,b)=>b.mois.localeCompare(a.mois)).map(p=>`<div class="pc-line"><b>${esc(moisLisible(p.mois))}</b><span>Prévu ${p.prevue?frDate(p.prevue):'—'} · Versé ${p.effective?frDate(p.effective):'—'}</span></div>`).join('')}</details>`:''}</section>`}
function paiementForm(){if(!isAdmin())return;const mois=S.statsMois||today().slice(0,7),p=S.data.paiements.find(x=>x.mois===mois);formModal('Dates du paiement',moisLisible(mois),field('prevue','Date prévue',p?.prevue||'','date')+field('effective','Date du versement effectué',p?.effective||'','date'),f=>api('enregistrerPaiementParcours',S.token,{...f,mois,revision:p?.revision||''}));}
function planifierForm(){if(!isAdmin())return;const blocs=S.data.competences;let lot='';
 const ligne=()=>`<fieldset class="pc-plan"><legend>Objectif</legend><label>Intitulé<input name="titre" required minlength="6" maxlength="180" placeholder="Ex. Préparer une animation en pharmacie"></label><label>Compétence du Bachelor<select name="bloc" required><option value="">Choisir une compétence</option>${blocs.map(c=>`<option value="${esc(c.id)}">${esc(c.id+' · '+c.titre)}</option>`).join('')}</select></label><div class="form-grid"><label>Début<input name="debut" required type="date" value="${today()}"></label><label>Échéance<input name="fin" required type="date"></label></div><label>Résultat attendu<input name="attendu" required minlength="8" maxlength="700" placeholder="Ex. Présenter le bilan de l’animation à Vanessa"></label><label>Cible de vente en unités (facultative)<input name="cible" type="number" min="1" step="1" inputmode="numeric"></label><button class="btn sm ghost" type="button" data-remove>Retirer cet objectif</button></fieldset>`;
 modal(`<h2>Planifier des objectifs</h2><p class="muted">Chaque objectif a sa compétence, sa période et son résultat attendu.</p><form id="planForm"><div id="planRows">${ligne()}</div><button type="button" class="btn sm secondary" data-add>+ Ajouter un objectif</button><p class="small" id="planError" role="alert"></p>${formButtons('Enregistrer le planning')}</form>`,root=>{
 const rows=root.querySelector('#planRows'),form=root.querySelector('#planForm');
 const sync=()=>{const sets=[...rows.children];sets.forEach((el,i)=>{el.querySelector('legend').textContent='Objectif '+(i+1);el.querySelector('[data-remove]').hidden=sets.length===1});root.querySelector('[data-add]').disabled=sets.length>=30};sync();
 root.querySelector('[data-add]').onclick=()=>{rows.insertAdjacentHTML('beforeend',ligne());sync();rows.lastElementChild.querySelector('input').focus()};
 rows.onclick=e=>{if(e.target.closest('[data-remove]')&&rows.children.length>1){e.target.closest('fieldset').remove();sync()}};
 form.onsubmit=async ev=>{ev.preventDefault();if(S.busy)return;const liste=[...rows.children].map(el=>Object.fromEntries([...el.querySelectorAll('[name]')].map(n=>[n.name,n.value])));const error=root.querySelector('#planError');if(liste.some(x=>x.fin<x.debut)){error.textContent='L’échéance doit suivre ou correspondre au début de chaque objectif.';return}
 if(!lot)lot=window.crypto.randomUUID();loading(true);try{await api('planifierObjectifs',S.token,lot,liste);closeModal();S.view='formation';await refresh();t(liste.length+' objectif(s) planifié(s).')}catch(e){if(error.isConnected)error.textContent=e.message;else t(e.message,'error')}finally{loading(false)}};
 });}


function modifierObjectifPlanifie(id){const x=S.data.objectifs.find(o=>o.id===id);if(!x||!x.bloc)return goalForm(id);
 const opts=S.data.competences.map(c=>({value:c.id,label:c.id+' · '+c.titre}));
 formModal('Modifier l’objectif',x.titre,field('titre','Intitulé',x.titre,'text',null,true)+field('bloc','Compétence',x.bloc,'select',opts,true)+`<div class="form-grid">${field('debut','Date de début',x.debut,'date',null,true)}${field('fin','Échéance',x.echeance,'date',null,true)}</div>`+field('indicateurs','Résultat attendu',x.indicateurs,'textarea',null,true)+field('cible','Cible de vente en unités (facultative)',x.cible||'','number')+`<div class="form-grid">${field('progression','Avancement (%)',x.progression,'number',null,true)}${field('statut','Statut',x.statut,'select',['À démarrer','En cours','Atteint','À réviser'],true)}</div>`+field('preuve','Preuves / bilan',x.preuve||'','textarea'),f=>api('mettreAJourObjectifBachelor',S.token,{...f,id,description:x.description,progression:f.statut==='Atteint'?100:f.progression}));}

function goalCard(o){return `<div class="card"><div class="goal-info"><span class="chip blue">Objectif ${esc(o.type.toLowerCase())} · ${esc(o.periode)}</span>${statusBadge(o.statut)}</div><div class="goal-title">${esc(o.titre)}</div><p class="muted" style="margin-top:8px">${esc(o.description)}</p><div class="criteria"><strong>Résultats attendus</strong><div style="margin-top:6px">${esc(o.indicateurs)}</div></div><div style="display:flex;justify-content:space-between;font-size:12px;color:var(--muted);margin-top:13px"><span>Progression</span><strong>${Number(o.progression)||0} %</strong></div><div class="progress"><span style="width:${Math.min(100,Math.max(0,Number(o.progression)||0))}%"></span></div>${repereObjectif(o)}${o.preuve?`<p class="small" style="margin-top:9px"><strong>Bilan :</strong> ${esc(o.preuve)}</p>`:''}${isAdmin()?`<div class="item-actions">${b('editGoal','Mettre à jour',o.id)}</div>`:''}</div>`}
function formation(){const d=S.data;return head('Bachelor communication, digital & événementiel','Mon parcours de formation','Objectifs suivis en entreprise avec Vanessa.',isAdmin()?b('planGoals','+ Planifier des objectifs'):'')+tableauxObjectifs()+
 ` <section class="card"><div class="card-head"><div><h2>Compétences du Bachelor</h2><p class="small muted">4 blocs communs et 2 options possibles selon le parcours du CFA.</p></div><a class="small" href="https://www.francecompetences.fr/recherche/rncp/39249/" target="_blank" rel="noopener">Voir le référentiel RNCP</a></div>
 ${(d.competences||[]).map(c=>`<div class="item"><div class="skill"><div><strong>${esc(c.titre)}</strong><div class="small muted">${esc(c.bloc)}</div><p>${esc(c.application)}</p><div class="chips">${statusBadge(c.niveau)}${c.evaluateur?`<span class="chip">Évaluée par ${esc(c.evaluateur)}</span>`:''}</div>${isAdmin()?b('editSkill','Évaluer',c.id,'ghost'):''}</div></div>${c.preuve?`<p class="small"><strong>Exemple :</strong> ${esc(c.preuve)}</p>`:''}</div>`).join('')}</section>`}
function suivi(){const d=S.data;return head('Journal de bord','Suivi & résultats','Ventes, présences et dates de paiement.',b('sale','+ Saisir une vente'))+tableauActivite()+
 `<section class="card"><div class="card-head"><h2>Historique des présences · ${esc(d.annee)}</h2>${isAdmin()?b('correctPresence','Corriger une journée','','ghost'):''}</div>${d.presences.length?`<div class="tablewrap"><table><thead><tr><th>Jour</th><th>Statut</th><th>Déclaration</th><th>Correction</th></tr></thead><tbody>${d.presences.slice().sort((x,y)=>y.date.localeCompare(x.date)).slice(0,35).map(x=>`<tr><td>${frDate(x.date)}</td><td>${statusBadge(x.statut)}</td><td>${esc(x.declareLe||'—')}</td><td>${esc(x.corrigePar||'—')}</td></tr>`).join('')}</tbody></table></div>`:'<div class="empty">Aucune présence enregistrée dans le nouveau système.</div>'}<p class="footnote">Les anciens pointages entrée/sortie sont conservés dans la feuille « Pointages » de Google Sheets.</p></section>
 <section class="card"><h2>Dernières ventes · ${esc(d.annee)}</h2>${d.ventes.length?`<div class="tablewrap"><table><thead><tr><th>Date</th><th>Boutique</th><th>Quantité</th><th>Observation</th><th></th></tr></thead><tbody>${d.ventes.slice().sort((x,y)=>y.date.localeCompare(x.date)).slice(0,30).map(x=>`<tr><td>${frDate(x.date)}</td><td>${esc(x.boutique)}</td><td><strong>${esc(x.quantite)}</strong></td><td>${esc(x.commentaire)}</td><td>${isAdmin()?b('deleteSale','Supprimer',x.id,'danger'):''}</td></tr>`).join('')}</tbody></table></div>`:'<div class="empty">Pas encore de ventes enregistrées.</div>'}</section>
 <section class="card"><div class="card-head"><h2>Anciens bilans mensuels</h2>${isAdmin()?b('evalMonth','Évaluer ce mois','','ghost'):''}</div>${d.evaluations.length?d.evaluations.slice().sort((x,y)=>y.mois.localeCompare(x.mois)).slice(0,12).map(x=>`<div class="item"><strong>${esc(x.mois)} · ${esc(x.evaluateur)}</strong><p>${esc(x.bilan)}</p><div class="small muted">Objectifs suivants : ${esc(x.objectifs)}</div></div>`).join(''):'<div class="empty">Aucune évaluation mensuelle enregistrée.</div>'}</section>`}
function adminPage(){if(!isAdmin())return home();const d=S.data;const pending=d.directives.filter(x=>x.statut==='À valider');return head('Maître d’apprentissage','Administration Vanessa','Affectations, missions, évaluation et correction des présences.',b('newTask','+ Créer une mission'))+
 `<div class="twocol"><div class="card"><h2>Organisation du travail</h2><p class="muted">Programmer la boutique de Malory et préciser ses tâches.</p><div class="item-actions">${b('navplanning','Modifier le planning')}${b('newTask','Ajouter une tâche','','ghost')}</div></div><div class="card"><h2>Formation Bachelor</h2><p class="muted">Planifier plusieurs objectifs et valider les acquis de Malory.</p><div class="item-actions">${b('planGoals','Planifier des objectifs')}${b('navformation','Suivre les objectifs','','ghost')}${b('correctPresence','Corriger une présence','','ghost')}</div></div></div>
 <section class="card"><h2>Missions en attente de validation (${pending.length})</h2>${pending.length?pending.slice(0,30).map(x=>`<div class="item"><strong>${esc(x.titre)}</strong><div class="small muted">${frDate(x.date)} · ${esc(x.rapport)}</div><div class="item-actions">${b('approveTask','Valider',x.id)}${b('reopenTask','Demander une correction',x.id,'ghost')}</div></div>`).join(''):'<div class="empty">Aucune validation en attente.</div>'}</section>`}
function modal(html,callback){const root=$('modalRoot');root.innerHTML=`<div class="modalbg"><div class="modal" role="dialog" aria-modal="true">${html}</div></div>`;root.querySelector('.modalbg').addEventListener('click',ev=>{if(ev.target.classList.contains('modalbg'))closeModal()});root.querySelectorAll('[data-close]').forEach(x=>x.addEventListener('click',closeModal));if(callback)callback(root)}
function closeModal(){$('modalRoot').innerHTML=''}
function field(name,label,value='',type='text',options=null,required=false){let html='';const req=required?'required':'';if(type==='textarea')html=`<textarea name="${esc(name)}" ${req}>${esc(value)}</textarea>`;else if(type==='select')html=`<select name="${esc(name)}" ${req}>${(options||[]).map(v=>{const k=typeof v==='object'?v.value:v,txt=typeof v==='object'?v.label:v;return `<option value="${esc(k)}" ${String(k)===String(value)?'selected':''}>${esc(txt)}</option>`}).join('')}</select>`;else html=`<input type="${esc(type)}" name="${esc(name)}" value="${esc(value)}" ${req}>`;return `<div class="form-field"><label>${esc(label)}</label>${html}</div>`}
const formButtons=label=>`<div class="modal-actions"><button class="btn ghost" type="button" data-close>Annuler</button><button class="btn" type="submit">${esc(label)}</button></div>`;
function formModal(title,description,inputs,onSubmit,submitText='Enregistrer'){modal(`<h2>${esc(title)}</h2><p class="muted" style="margin-top:6px">${esc(description)}</p><form id="editorForm">${inputs}${formButtons(submitText)}</form>`,root=>root.querySelector('#editorForm').addEventListener('submit',async ev=>{ev.preventDefault();if(S.busy)return;loading(true);try{await onSubmit(Object.fromEntries(new FormData(ev.currentTarget).entries()));closeModal();t('Enregistré avec succès');await refresh()}catch(ex){t(ex.message,'error')}finally{loading(false)}}))}
function assignment(ref){const [date,code]=ref.split('|');const saturday=new Date(date+'T12:00:00').getDay()===6;const s=getWeekDay(date)?.creneaux?.find(x=>x.code===code);const sites=code==='SIEGE'?['Siège KVG']:saturday?S.data.pointsSamedi:S.data.boutiques.filter(x=>x!=='Siège KVG');formModal('Affectation du '+frDate(date),slotName(code)+' · choisir le lieu de travail.',field('site','Lieu',sites.includes(s?.site)?s.site:sites[0],'select',sites,true)+field('consignes','Consignes (facultatif)',s?.consignes||'','textarea'),f=>api('programmerCreneauV3',S.token,{date,code,...f}),'Valider le planning')}
function taskForm(obj){const types=['Communication Instagram','Mailing et promotion','Présentation / formation de gamme','Prise de rendez-vous','Appel pharmacie','Relance pharmacie','Appel distributeur','Contrôle stock officine','Réassort pharmacie','Commande client','Classement de documents KVG','Étiquetage / numéro de lot','Analyse des ventes','Autre'];const categories=[...new Set([...(S.data?.categories||[]),...types])].filter(x=>!/num[ée]ri[kq].?cloud/i.test(x));const validTypes=types.filter(x=>categories.includes(x));formModal(obj?'Modifier la mission':'Nouvelle mission pour Malory','Saisir une consigne courte et un résultat vérifiable. Hors fabrication et Numerik’Cloud.',
 `<div class="form-grid">${field('date','Date',obj?.date||today(),'date',null,true)}${field('heure','Heure limite',obj?.heure||'','time')}${field('categorie','Type',validTypes.includes(obj?.categorie)?obj.categorie:validTypes[0],'select',validTypes,true)}${field('priorite','Priorité',obj?.priorite||'Normale','select',['Normale','Haute'])}</div>`+
 field('titre','Mission',obj?.titre||'','text',null,true)+field('quoi','Quoi faire ?',obj?.quoi||'','textarea',null,true)+field('comment','Comment faire ?',obj?.comment||'','textarea',null,true)+field('attendu','Résultat attendu',obj?.attendu||'','textarea')+field('pourQui','Pour qui ?',obj?.pourQui||'KVG')+field('contact','Contact',obj?.contact||'')+field('canal','Où / canal',obj?.canal||'')+field('lieu','Lieu',obj?.lieu||'')+field('supervision','Validation Vanessa',obj?.supervision===false?'false':'true','select',[{value:'true',label:'Oui'},{value:'false',label:'Non'}]),
 f=>api(obj?'modifierDirectiveV3':'creerDirectiveV3',S.token,...(obj?[obj.id]:[]),{...f,supervision:f.supervision==='true'}),'Enregistrer la mission')}
function reportTask(id){const x=S.data.directives.find(t=>t.id===id);formModal('Terminer la mission',x?.titre||'Mission',field('rapport','Qu’as-tu réalisé ? (quelques mots)',x?.rapport||'','textarea',null,true),f=>api('completerDirectiveV3',S.token,id,'SOUMETTRE',f.rapport),'Soumettre à Vanessa')}
function skillForm(id){const x=S.data.competences.find(v=>v.id===id);if(!x)return;formModal('Évaluer une compétence',x.titre,field('niveau','Progression en entreprise',x.niveau,'select',['À découvrir','En progression','Autonome en entreprise'],true)+field('preuve','Exemple concret / livrable',x.preuve||'','textarea'),f=>api('evaluerCompetenceBachelor',S.token,{id,...f}),'Mettre à jour')}
function goalForm(id){const x=S.data.objectifs.find(v=>v.id===id);if(!x)return;formModal('Objectif '+x.type.toLowerCase(),x.periode+' · échéance '+frDate(x.echeance),field('titre','Intitulé',x.titre,'text',null,true)+field('description','Action à mener',x.description,'textarea',null,true)+field('indicateurs','Indicateurs mesurables',x.indicateurs,'textarea',null,true)+`<div class="form-grid">${field('progression','Avancement (%)',x.progression,'number',null,true)}${field('statut','Statut',x.statut,'select',['À démarrer','En cours','Atteint','À réviser'],true)}</div>`+field('preuve','Preuves / bilan',x.preuve||'','textarea'),f=>api('mettreAJourObjectifBachelor',S.token,{id,...f,progression:f.statut==='Atteint'?100:f.progression}),'Enregistrer l’objectif')}
function correctPresence(){formModal('Correction de présence','Seule Vanessa peut régulariser une date passée, avec une justification.',field('date','Date',today(),'date',null,true)+field('statut','Statut','Présence confirmée','select',['Présence confirmée','Absence constatée','Non déclarée'],true)+field('motif','Motif / justificatif','','textarea',null,true),f=>api('corrigerPresenceSimple',S.token,f),'Enregistrer la correction')}
function saleForm(){const stores=S.data.boutiques.filter(x=>x!=='Siège KVG');formModal('Déclarer des ventes','Total de déodorants vendus, par boutique.',field('date','Date',today(),'date',null,true)+field('boutique','Boutique',stores[0],'select',stores,true)+field('quantite','Quantité',0,'number',null,true)+field('commentaire','Retour client / observation','','textarea'),f=>api('enregistrerVente',S.token,f),'Enregistrer la vente')}
function monthlyForm(){const scores=['ponctualite','organisation','rigueur','vente','connaissances','etiquetage','communication','autonomie'];formModal('Bilan mensuel interne','Noter chaque critère de 1 à 5. Le suivi Bachelor se trouve dans l’onglet Compétences.',field('mois','Mois',today().slice(0,7),'month',null,true)+`<div class="form-grid">${scores.map(k=>field(k,k[0].toUpperCase()+k.slice(1),'3','select',['1','2','3','4','5'],true)).join('')}</div>`+field('bilan','Bilan','','textarea')+field('objectifs','Axes de progrès du mois suivant','','textarea'),f=>api('enregistrerEvaluation',S.token,f),'Enregistrer le bilan')}
function changeWeek(delta){const dt=new Date((S.weekDate||today())+'T12:00:00');dt.setDate(dt.getDate()+delta*7);const d=[dt.getFullYear(),String(dt.getMonth()+1).padStart(2,'0'),String(dt.getDate()).padStart(2,'0')].join('-');S.weekDate=d;refresh().catch(()=>{})}
async function doAction(act,id){try{
 if(act==='navplanning'){S.view='planning';render();return}if(act==='navtasks'){S.view='tasks';render();return}if(act==='navformation'){S.view='formation';render();return}
 if(act==='prevweek'||act==='nextweek'){changeWeek(act==='prevweek'?-1:1);return}
 if(act==='assign'){assignment(id);return}if(act==='newTask'){taskForm();return}if(act==='editTask'){taskForm(S.data.directives.find(x=>x.id===id));return}
 if(act==='completeTask'){reportTask(id);return}if(act==='editSkill'){skillForm(id);return}if(act==='editGoal'){modifierObjectifPlanifie(id);return}
 if(act==='planGoals'){planifierForm();return}if(act==='payDate'){paiementForm();return}if(act==='navsuivi'){S.view='suivi';render();return}
 if(act==='sale'){saleForm();return}if(act==='correctPresence'){correctPresence();return}if(act==='evalMonth'){monthlyForm();return}
 const direct={startTask:['completerDirectiveV3',id,'DEMARRER',''],approveTask:['completerDirectiveV3',id,'VALIDER',''],reopenTask:['completerDirectiveV3',id,'REOUVRIR','À revoir par Malory'],cancelTask:['completerDirectiveV3',id,'ANNULER',''],approveRoutine:['validerMission',id],deleteSale:['supprimerVente',id]};
 if(direct[act]){if(['cancelTask','reopenTask','deleteSale'].includes(act)&&!confirm('Confirmer cette modification ?'))return;loading(true);await api(direct[act][0],S.token,...direct[act].slice(1));t('Modification enregistrée');await refresh()}
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

function logout(){$('accessForm').reset();selectionAcces();$('accessPanel').classList.add('hidden');S.statsMois='';S.token='';S.profile=null;S.data=null;S.week=[];S.weekDate='';S.selectedDate='';S.view='home';$('app').classList.add('hidden');$('auth').classList.remove('hidden');$('loginForm').reset();closeModal()}
$('loginForm').addEventListener('submit',async ev=>{ev.preventDefault();if(S.busy)return;const formulaireConnexion=ev.currentTarget;const f=new FormData(formulaireConnexion),el=$('loginMsg');el.classList.remove('error');el.classList.remove('hidden');el.textContent='Connexion en cours…';loading(true);try{await ouvrirProfil(await api('connexion',f.get('role'),f.get('pin')));}catch(x){el.classList.add('error');el.textContent='Connexion impossible : '+x.message;logout();el.classList.remove('hidden')}finally{loading(false)}});
$('signout').addEventListener('click',quitterCompte);
document.querySelectorAll('[data-bio]').forEach(el=>el.addEventListener('click',lierEmpreinte));
$('reload').addEventListener('click',()=>refresh().catch(()=>{}));
$('nav').addEventListener('click',ev=>{const n=ev.target.closest('[data-view]');if(n){S.view=n.dataset.view;render();window.scrollTo({top:0,behavior:'smooth'})}});
$('content').addEventListener('click',ev=>{const x=ev.target.closest('button[data-action]');if(x)doAction(x.dataset.action,x.dataset.id)});
$('content').addEventListener('change',async ev=>{const el=ev.target;try{
 if(el.id==='statsMois'&&/^20\d{2}-(0[1-9]|1[0-2])$/.test(el.value)){S.statsMois=el.value;render();return}
 if(el.id==='taskFilter'){S.taskFilter=el.value;render();return}
 if(el.id==='routineDate'){S.selectedDate=el.value;render();return}
 if(el.id==='weekPicker'){S.weekDate=el.value;await refresh();return}
 if(el.id==='presenceCheck'){loading(true);try{await api('cocherPresenceSimple',S.token,el.checked);t('Présence mise à jour');await refresh()}catch(x){t(x.message,'error');await refresh()}finally{loading(false)}return}
 if(el.dataset.mission){loading(true);await api('cocherMission',S.token,el.dataset.mission,el.checked,'');t('Mission cochée');await refresh()}
 }catch(x){t(x.message,'error');refresh().catch(()=>{})}finally{loading(false)}});
window.addEventListener('error',ev=>{const msg=$('loginMsg');if(!$('auth').classList.contains('hidden')){msg.classList.remove('hidden');msg.classList.add('error');msg.textContent='Erreur de l’interface : '+ev.message}else t('Erreur de l’interface : '+ev.message,'error')});
})();
