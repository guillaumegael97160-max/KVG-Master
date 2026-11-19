/**
 * Appeler après la sauvegarde d'un événement sur le serveur Apps Script.
 * Le titre est un libellé générique et l'identifiant est opaque.
 * Un suffixe _ garde ces fonctions privées pour google.script.run.
 */
function kvgPushProjet_(project, id, title) {
  const projects = ['gua', 'mq', 'gestion-mq', 'commandes', 'coffres',
    'sante-plus', 'instagram', 'carrefour', 'malory'];
  if (!projects.includes(project) || typeof id !== 'string' || !id || id.length > 150 ||
      typeof title !== 'string' || !title || title.length > 120) {
    throw new Error('Événement KVG invalide');
  }

  const settings = PropertiesService.getScriptProperties();
  const firebaseProject = settings.getProperty('KVG_FCM_PROJECT_ID');
  const token = settings.getProperty('KVG_FCM_DEVICE_TOKEN');
  if (!firebaseProject || !/^[a-z][a-z0-9-]+$/.test(firebaseProject) || !token) {
    throw new Error('Notifications KVG à configurer');
  }

  const response = UrlFetchApp.fetch(
    'https://fcm.googleapis.com/v1/projects/' + firebaseProject + '/messages:send', {
      method: 'post',
      contentType: 'application/json',
      headers: { Authorization: 'Bearer ' + ScriptApp.getOAuthToken() },
      payload: JSON.stringify({
        message: {
          token: token,
          data: {project: project, id: id, title: title},
          android: {priority: 'HIGH', ttl: '86400s'}
        }
      }),
      muteHttpExceptions: true
    });
  if (response.getResponseCode() !== 200) {
    throw new Error('Envoi FCM en échec (HTTP ' + response.getResponseCode() + ')');
  }
}

function kvgPushTest_() {
  kvgPushProjet_('carrefour', Utilities.getUuid(), 'Test des notifications KVG');
}
