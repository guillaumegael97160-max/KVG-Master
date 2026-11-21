package fr.kvg.malory;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.Key;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;

final class AccesBiometrique {
 interface Retour { void ok(Cipher cipher); void erreur(String message); }
 private static final String ALIAS="kvg-malory.biometrie.v1";
 private final Activity activity;
 private final SharedPreferences prefs;
 private CancellationSignal signal;
 private Cipher autorise;
 AccesBiometrique(Activity activity){this.activity=activity;prefs=activity.getSharedPreferences("acces",Context.MODE_PRIVATE);}
 String appareil(){String id=prefs.getString("appareil",null);if(id==null){id=UUID.randomUUID().toString().replace("-","");prefs.edit().putString("appareil",id).commit();}return id;}
 boolean configure(){return prefs.contains("contenu")&&prefs.contains("iv");}
 boolean disponible(){
  if(Build.VERSION.SDK_INT>=30)return activity.getSystemService(BiometricManager.class).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)==BiometricManager.BIOMETRIC_SUCCESS;
  if(Build.VERSION.SDK_INT==29)return activity.getSystemService(BiometricManager.class).canAuthenticate()==BiometricManager.BIOMETRIC_SUCCESS;
  FingerprintManager fm=activity.getSystemService(FingerprintManager.class);return fm!=null&&fm.isHardwareDetected()&&fm.hasEnrolledFingerprints();
 }
 private Key cle(boolean creer)throws Exception{
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
  if(ks.containsAlias(ALIAS))return ks.getKey(ALIAS,null);
  if(!creer)throw new IllegalStateException();
  KeyGenParameterSpec.Builder b=new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
   .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
   .setUserAuthenticationRequired(true).setInvalidatedByBiometricEnrollment(true);
  if(Build.VERSION.SDK_INT>=30)b.setUserAuthenticationParameters(0,KeyProperties.AUTH_BIOMETRIC_STRONG);
  KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");gen.init(b.build());return gen.generateKey();
 }
 void demander(boolean enregistrer,Retour retour){
  annuler();
  try{
   if(!disponible()){retour.erreur("Configure une empreinte dans les réglages Android ou utilise ton mot de passe.");return;}
   Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
   if(enregistrer)c.init(Cipher.ENCRYPT_MODE,cle(true));
   else c.init(Cipher.DECRYPT_MODE,cle(false),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));
   BiometricPrompt.Builder b=new BiometricPrompt.Builder(activity).setTitle(enregistrer?"Activer le déverrouillage":"Déverrouiller KVG Malory")
    .setSubtitle(enregistrer?"Confirme ton identité pour ce compte sur ce téléphone.":"Utilise la biométrie de ce téléphone.")
    .setNegativeButton("Annuler",activity.getMainExecutor(),(d,w)->retour.erreur("Authentification annulée."));
   if(Build.VERSION.SDK_INT>=30)b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG);
   CancellationSignal demande=new CancellationSignal();signal=demande;
   b.build().authenticate(new BiometricPrompt.CryptoObject(c),demande,activity.getMainExecutor(),new BiometricPrompt.AuthenticationCallback(){
    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r){if(signal!=demande)return;signal=null;Cipher chiffre=r.getCryptoObject().getCipher();if(enregistrer)autorise=chiffre;retour.ok(chiffre);}
    @Override public void onAuthenticationError(int code,CharSequence texte){if(signal!=demande)return;signal=null;retour.erreur(texte.toString());}
   });
  }catch(Exception ex){oublier();retour.erreur("Le déverrouillage doit être réactivé. Connecte-toi avec ton mot de passe.");}
 }
 void sauvegarder(JSONObject acces)throws Exception{
  Cipher c=autorise;autorise=null;if(c==null)throw new IllegalStateException();
  if(!acces.getString("role").matches("MALORY|VANESSA|GUILLAUME")||!acces.getString("secret").matches("[0-9a-f]{64}"))throw new IllegalArgumentException();
  byte[] chiffre=c.doFinal(acces.toString().getBytes(StandardCharsets.UTF_8));
  if(!prefs.edit().putString("contenu",Base64.encodeToString(chiffre,Base64.NO_WRAP)).putString("iv",Base64.encodeToString(c.getIV(),Base64.NO_WRAP)).commit())throw new IllegalStateException();
 }
 JSONObject ouvrir(Cipher c)throws Exception{return new JSONObject(new String(c.doFinal(Base64.decode(prefs.getString("contenu",""),Base64.NO_WRAP)),StandardCharsets.UTF_8));}
 void annuler(){autorise=null;if(signal!=null){CancellationSignal s=signal;signal=null;s.cancel();}}
 void oublier(){annuler();prefs.edit().remove("contenu").remove("iv").commit();try{KeyStore k=KeyStore.getInstance("AndroidKeyStore");k.load(null);k.deleteEntry(ALIAS);}catch(Exception ignored){}}
}
