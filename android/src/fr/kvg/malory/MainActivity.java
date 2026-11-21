package fr.kvg.malory;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;

public final class MainActivity extends Activity {
 private static final String ORIGINE="https://appassets.androidplatform.net";
 private final ExecutorService reseau=Executors.newFixedThreadPool(3);
 private final Handler main=new Handler(Looper.getMainLooper());
 private FrameLayout root;
 private WebView web;
 private WebMessagePort canal;
 private AccesBiometrique bio;
 private JSONObject reprise;
 private boolean visible;
 private final Runnable demarrer=()->{if(visible && web==null)ouvrirWeb(null);};

 @Override protected void onCreate(Bundle etat){
  super.onCreate(etat);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
  root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(14,48,43));setContentView(root);
  root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
  bio=new AccesBiometrique(this);
 }
 @Override protected void onStart(){super.onStart();visible=true;accueil("");}
 @Override protected void onStop(){visible=false;main.removeCallbacks(demarrer);bio.annuler();fermerWeb();root.removeAllViews();super.onStop();}
 @Override protected void onDestroy(){reseau.shutdownNow();super.onDestroy();}
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private TextView texte(String s,int taille,int couleur){TextView v=new TextView(this);v.setText(s);v.setTextColor(couleur);v.setTextSize(taille);return v;}
 private Button bouton(String s,boolean principal,View.OnClickListener action){
  Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(principal?Color.rgb(19,51,42):Color.WHITE);GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(12));bg.setColor(principal?Color.rgb(241,221,178):Color.rgb(23,63,56));b.setBackground(bg);b.setOnClickListener(action);return b;
 }
 private void accueil(String message){
  main.removeCallbacks(demarrer);root.removeAllViews();
  ImageView image=new ImageView(this);
  try(InputStream in=getAssets().open("couverture.webp")){image.setImageBitmap(BitmapFactory.decodeStream(in));}catch(IOException ignored){}
  image.setScaleType(ImageView.ScaleType.CENTER_CROP);root.addView(image,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout titres=new LinearLayout(this);titres.setOrientation(LinearLayout.VERTICAL);titres.setPadding(dp(28),dp(54),dp(24),0);
  TextView marque=texte("LABORATOIRE KVG",13,Color.rgb(233,205,150));marque.setLetterSpacing(.12f);titres.addView(marque);
  TextView titre=texte("Le parcours\nde Malory.",45,Color.rgb(255,248,231));titre.setTypeface(Typeface.SERIF);titre.setPadding(0,dp(24),0,dp(10));titres.addView(titre);
  if(!message.isEmpty())titres.addView(texte(message,15,Color.WHITE));
  root.addView(titres,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
  LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.VERTICAL);actions.setPadding(dp(22),dp(15),dp(22),dp(22));
  if(bio.configure()&&bio.disponible()){actions.addView(bouton("Déverrouiller par empreinte",true,v->deverrouiller()));actions.addView(bouton("Utiliser mon mot de passe",false,v->ouvrirWeb(null)));}
  else{actions.addView(bouton("Ouvrir mon espace",true,v->ouvrirWeb(null)));if(message.isEmpty())main.postDelayed(demarrer,1100);}
  root.addView(actions,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
 }
 private void deverrouiller(){
  bio.demander(false,new AccesBiometrique.Retour(){
   public void ok(Cipher c){try{ouvrirWeb(bio.ouvrir(c));}catch(Exception e){bio.oublier();accueil("Connecte-toi pour réactiver ton empreinte.");}}
   public void erreur(String msg){if(visible)accueil(msg);}
  });
 }
 private void fermerWeb(){reprise=null;if(canal!=null){canal.close();canal=null;}if(web!=null){WebView w=web;web=null;root.removeView(w);w.stopLoading();w.destroy();}}
 private void ouvrirWeb(JSONObject cred){
  main.removeCallbacks(demarrer);fermerWeb();reprise=cred;root.removeAllViews();web=new WebView(this);
  WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSaveFormData(false);
  web.setWebChromeClient(new WebChromeClient());
  web.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest req){
    Uri u=req.getUrl();String path=u.getPath();if(!"https".equals(u.getScheme())||!"appassets.androidplatform.net".equals(u.getHost())||u.getPort()!=-1)return bloque();
    String type;
    if("/index.html".equals(path))type="text/html";else if("/style.css".equals(path))type="text/css";else if("/app.js".equals(path))type="application/javascript";else if("/couverture.webp".equals(path))type="image/webp";else return bloque();
    try{return new WebResourceResponse(type,type.equals("image/webp")?null:"UTF-8",getAssets().open(path.substring(1)));}catch(IOException e){return bloque();}
   }
   @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest req){
    Uri u=req.getUrl();if(req.isForMainFrame()&&req.hasGesture()&&"https".equals(u.getScheme())&&"www.francecompetences.fr".equals(u.getHost())){try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}}
    return true;
   }
   @Override public void onPageFinished(WebView w,String url){
    if(w!=web||!(ORIGINE+"/index.html").equals(url)||canal!=null)return;
    WebMessagePort[] ports=w.createWebMessageChannel();canal=ports[0];WebMessagePort courant=canal;
    canal.setWebMessageCallback(new WebMessagePort.WebMessageCallback(){@Override public void onMessage(WebMessagePort port,WebMessage message){traiter(courant,message.getData());}});
    w.postWebMessage(new WebMessage("kvg-mobile",new WebMessagePort[]{ports[1]}),Uri.parse(ORIGINE));
   }
   @Override public void onReceivedError(WebView w,WebResourceRequest request,WebResourceError e){if(w==web&&request.isForMainFrame()){main.post(()->{if(w==web){fermerWeb();accueil("Impossible d’ouvrir l’espace. Réessaie.");}});}}
  });root.addView(web,new FrameLayout.LayoutParams(-1,-1));web.loadUrl(ORIGINE+"/index.html");
 }
 private WebResourceResponse bloque(){return new WebResourceResponse("text/plain","UTF-8",403,"Interdit",null,new ByteArrayInputStream(new byte[0]));}
 private void envoyer(WebMessagePort p,JSONObject msg){if(p==canal&&visible)p.postMessage(new WebMessage(msg.toString()));}
 private void repondre(WebMessagePort p,String id,boolean ok,Object data,String msg){try{envoyer(p,new JSONObject().put("id",id).put("ok",ok).put("data",data).put("message",msg));}catch(JSONException ignored){}}
 private void traiter(WebMessagePort p,String texte){
  if(!visible||p!=canal||texte==null||texte.length()>100000)return;
  try{
   JSONObject req=new JSONObject(texte);String action=req.getString("action"),id=req.optString("id","");
   if(action.equals("ready")){
    JSONObject m=new JSONObject().put("type","ready").put("available",bio.disponible()).put("id",bio.appareil());
    if(reprise!=null)m.put("resume",reprise);reprise=null;envoyer(p,m);return;
   }
   if(action.equals("api")){
    String nom=req.getString("name");JSONArray args=req.getJSONArray("args");
    reseau.execute(()->{try{JSONObject res=ServiceKvg.appeler(nom,args);main.post(()->repondre(p,id,res.optBoolean("ok"),res.opt("data"),res.optString("message","")));}catch(Exception e){main.post(()->repondre(p,id,false,null,"Connexion au service KVG impossible. Vérifie ta connexion et réessaie."));}});return;
   }
   if(action.equals("authorizeSave")){bio.demander(true,new AccesBiometrique.Retour(){public void ok(Cipher c){repondre(p,id,true,true,"");}public void erreur(String msg){repondre(p,id,false,null,msg);}});return;}
   if(action.equals("save")){try{bio.sauvegarder(req.getJSONObject("credential"));repondre(p,id,true,true,"");}catch(Exception e){repondre(p,id,false,null,"Activation interrompue. Réessaie.");}return;}
   if(action.equals("forget")){bio.oublier();repondre(p,id,true,true,"");}
  }catch(Exception e){/* Un message mal formé ne déclenche aucune opération. */}
 }
 @Override public void onBackPressed(){if(web!=null){fermerWeb();accueil("Espace verrouillé.");}else super.onBackPressed();}
}
