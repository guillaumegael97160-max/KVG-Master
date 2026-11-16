/**
 * KVG Master — activation des empreintes Guadeloupe par la Direction (V51).
 * Module séparé, ajouté au projet KVG_MASTER_CONTROLE_v2.
 * Ne modifie ni KVG RH · PROFILS, ni les ventes, ni les historiques.
 * Une feuille de paramètres dédiée est créée lors de la première activation.
 */
var KVG_GUA_BIO_DIR_V51_SHEET='KVG RH · BIOMETRIE';
var KVG_GUA_BIO_DIR_V51_HEADERS=['ID RH','Identifiant','Région','Autorisation','Génération','Modification','Auteur','Note'];
function kvgGuaBioDirV51Auth_(token){
 if(typeof token!=='string'||!token)throw Error('Reconnexion Direction nécessaire.');
 if(typeof kvgV10Owner_!=='function'||typeof kvgRh22Entries_!=='function'||typeof kvgRh22Tab_!=='function')
  throw Error('Modules Direction V19, Gestion directe V10 et RH V22 requis.');
 kvgV10Owner_(token);
 if(typeof kvgRh22Access_==='function')kvgRh22Access_(token);
}
function kvgGuaBioDirV51Profiles_(){
 return kvgRh22Entries_().filter(function(e){return String(e.v[1])==='GUADELOUPE'&&String(e.v[4])!=='SUPPRIME';})
  .map(function(e){return {id:String(e.v[0]||''),login:String(e.v[11]||''),name:String(e.v[2]||''),status:String(e.v[4]||''),configured:!!(e.v[14]&&e.v[15]),book:!!e.v[9],version:Number(e.v[16])||0};})
  .filter(function(p){return p.id&&p.login;})
  .sort(function(a,b){return a.name.localeCompare(b.name,'fr');});
}
function kvgGuaBioDirV51Tab_(create){
 var book=kvgRh22Tab_(KVG_RH22.PROFILE).getParent(),sh=book.getSheetByName(KVG_GUA_BIO_DIR_V51_SHEET);
 if(!sh&&create){sh=book.insertSheet(KVG_GUA_BIO_DIR_V51_SHEET);sh.getRange(1,1,1,8).setValues([KVG_GUA_BIO_DIR_V51_HEADERS]);sh.setFrozenRows(1);}
 return sh;
}
function kvgGuaBioDirV51Records_(sh){
 if(!sh||sh.getLastRow()<2)return [];
 return sh.getRange(2,1,Math.min(500,sh.getLastRow()-1),8).getValues().map(function(r,i){return {row:i+2,id:String(r[0]),login:String(r[1]),region:String(r[2]),allowed:String(r[3])==='OUI',generation:String(r[4]||''),updated:String(r[5]||'')};});
}
/** Lecture seule des autorisations, jamais des empreintes ni des secrets. */
function kvgGuaBioDirV51Lister(token){
 kvgGuaBioDirV51Auth_(token);
 var profiles=kvgGuaBioDirV51Profiles_(),rows=kvgGuaBioDirV51Records_(kvgGuaBioDirV51Tab_(false));
 return {ok:true,version:'51.0',vendeuses:profiles.map(function(p){
  var r=rows.filter(function(x){return x.id===p.id&&x.login===p.login&&x.region==='GUADELOUPE';})[0];
  return {id:p.id,login:p.login,name:p.name,status:p.status,configured:p.configured,book:p.book,
   autorisee:!!(r&&r.allowed),updated:r?r.updated:'',generationActive:!!(r&&r.generation)};
 })};
}
/** Modification Direction seulement ; UUID régénéré à chaque bascule pour révoquer les anciennes associations. */
function kvgGuaBioDirV51Autoriser(token,profileId,enabled){
 kvgGuaBioDirV51Auth_(token);
 if(typeof enabled!=='boolean')throw Error('Autorisation attendue : vrai ou faux.');
 profileId=String(profileId||'').trim();
 if(!/^(?:GUA-[0-9]{3,5}|[A-Za-z0-9_-]{2,36})$/.test(profileId))throw Error('Identifiant RH invalide.');
 var lock=LockService.getScriptLock();lock.waitLock(12000);
 try {
  // Vérifier à nouveau le compte sous verrou.
  var p=kvgGuaBioDirV51Profiles_().filter(function(x){return x.id===profileId;})[0];
  if(!p)throw Error('Compte Guadeloupe introuvable ou supprimé.');
  if(enabled&&(!p.configured||!p.book||p.status!=='ACTIF'))
    throw Error('Activez d’abord le profil et son code personnel dans Gestion des profils.');
  var sh=kvgGuaBioDirV51Tab_(true),rows=kvgGuaBioDirV51Records_(sh);
  var old=rows.filter(function(r){return r.id===p.id&&r.login===p.login&&r.region==='GUADELOUPE';})[0];
  if(old&&old.allowed===enabled)return {ok:true,id:p.id,autorisee:enabled,identique:true};
  var ts=Utilities.formatDate(new Date(),'America/Guadeloupe','yyyy-MM-dd HH:mm:ss');
  var values=[p.id,p.login,'GUADELOUPE',enabled?'OUI':'NON',Utilities.getUuid()+Utilities.getUuid(),ts,'Direction KVG','Accès biométrique individuel'];
  if(old)sh.getRange(old.row,1,1,8).setValues([values]);else sh.appendRow(values);
  try{if(typeof kvgRh22Log_==='function')kvgRh22Log_(p.id,enabled?'EMPREINTE AUTORISÉE':'EMPREINTE RETIRÉE',{autorisee:old?old.allowed:false},{autorisee:enabled},'SESSIONS VENDEUSES INCHANGÉES');}catch(e){Logger.log('Audit RH facultatif : '+String(e));}
  return {ok:true,id:p.id,autorisee:enabled,updated:ts};
 }finally{lock.releaseLock();}
}
function kvgGuaBioDirV51Diagnostic(){
 var d={version:'V51',modules:['kvgV10Owner_','kvgRh22Entries_','kvgRh22Tab_','kvgRh22Access_'].map(function(x){return {nom:x,present:typeof this[x]==='function'};})};
 Logger.log(JSON.stringify(d));return d;
}