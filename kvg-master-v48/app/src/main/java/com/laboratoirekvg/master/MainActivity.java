package com.laboratoirekvg.master;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** KVG MASTER V48. Authentification par secret aléatoire chiffré avec Android Keystore. */
public final class MainActivity extends Activity {
    private static final String PREF = "kvg_master_biometric_v48";
    private static final String OLD_PREF = "kvg_master_black_gold_settings";
    private static final String URL_KEY = "kvg_master_exec_url";
    private static final String KEY_ALIAS = "KVG_MASTER_BIOMETRIC_V48_AES";
    private static final String PINNED_URL = ""; // URL du MASTER seulement. Jamais une URL vendeuse.
    private static final String LOCAL_ID = "bio_device_id";
    private static final String LOCAL_CIPHER = "bio_ciphertext";
    private static final String LOCAL_IV = "bio_iv";
    private static final int PICK_FILE = 902;
    private static final int GOLD = Color.rgb(221, 181, 99);
    private static final int BLACK = Color.rgb(6, 6, 6);
    private static final int[] SLIDES = {R.drawable.intro_0, R.drawable.intro_1,
            R.drawable.intro_2, R.drawable.intro_3, R.drawable.intro_4};
    private final SecureRandom secureRandom = new SecureRandom();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private WebView browser;
    private FrameLayout root, splash;
    private ProgressBar progress;
    private SharedPreferences prefs;
    private ValueCallback<Uri[]> pendingFile;
    private String masterUrl="", pendingSession, pendingDevice, pendingSecret, pendingCipher, pendingIv;
    private boolean introRunning = false, pageReady = false;
    private int slideNumber = 0;
    private ImageView slideImage;
    private CancellationSignal biometricCancel;
    private volatile String bridgeRequest = "";
    private volatile String bridgeResult = "";
    private boolean biometricBusy = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(BLACK);
        getWindow().setNavigationBarColor(BLACK);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        masterUrl = prefs.getString(URL_KEY, "");
        if (masterUrl.isEmpty()) masterUrl = getSharedPreferences(OLD_PREF, MODE_PRIVATE).getString(URL_KEY, PINNED_URL);
        createUI();
        createBrowser();
        startSlides();
    }

    private int dp(float n) {return Math.round(getResources().getDisplayMetrics().density*n);}
    private GradientDrawable background(int color, int stroke){
        GradientDrawable d = new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(11));
        if(stroke!=0)d.setStroke(dp(1),stroke);return d;
    }
    private TextView text(String msg,int sp){TextView t=new TextView(this);t.setText(msg);t.setTextSize(sp);t.setTextColor(GOLD);t.setGravity(Gravity.CENTER);return t;}
    private void createUI(){
        root = new FrameLayout(this);root.setBackgroundColor(BLACK);setContentView(root);
        browser = new WebView(this);browser.setBackgroundColor(BLACK);
        FrameLayout.LayoutParams web = new FrameLayout.LayoutParams(-1,-1);web.topMargin=dp(44);root.addView(browser,web);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setBackgroundColor(0xff11100e);
        TextView title=text("LABORATOIRE KVG · GESTION",12);title.setTypeface(null,Typeface.BOLD);
        bar.addView(title,new LinearLayout.LayoutParams(0,dp(43),1));
        TextView settings=text("⚙",25);settings.setContentDescription("Paramètres biométriques");bar.addView(settings,new LinearLayout.LayoutParams(dp(50),dp(43)));
        settings.setOnClickListener(v->openSettings());
        TextView refresh=text("⟳",26);refresh.setContentDescription("Actualiser KVG Master");bar.addView(refresh,new LinearLayout.LayoutParams(dp(48),dp(43)));
        refresh.setOnClickListener(v->{if(browser!=null)browser.reload();});
        root.addView(bar,new FrameLayout.LayoutParams(-1,dp(43),Gravity.TOP));
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);if(Build.VERSION.SDK_INT>=21)progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(GOLD));
        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-1,dp(2),Gravity.TOP);pp.topMargin=dp(42);root.addView(progress,pp);
    }
    @SuppressLint({"SetJavaScriptEnabled","JavascriptInterface"})
    private void createBrowser(){
        WebSettings s=browser.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setLoadsImagesAutomatically(true);
        s.setAllowFileAccess(false);s.setAllowContentAccess(true);s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSupportMultipleWindows(false);
        s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(browser,true);
        browser.addJavascriptInterface(new BiometricBridge(),"KVGBioAndroid");
        browser.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                Uri uri=request.getUrl();String host=uri.getHost();String scheme=uri.getScheme();
                if("https".equalsIgnoreCase(scheme)&&isGoogleScriptHost(host))return false;
                try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}
                return true;
            }
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){pageReady=false;progress.setVisibility(View.VISIBLE);}
            @Override public void onPageFinished(WebView v,String url){pageReady=true;progress.setVisibility(View.GONE);}
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){
                if(r.isForMainFrame()){progress.setVisibility(View.GONE);Toast.makeText(MainActivity.this,"Connexion KVG indisponible",Toast.LENGTH_LONG).show();}
            }
        });
        browser.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int p){if(p>=100)progress.setVisibility(View.GONE);}
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> cb,FileChooserParams params){
                if(pendingFile!=null)pendingFile.onReceiveValue(null);pendingFile=cb;
                try{startActivityForResult(params.createIntent(),PICK_FILE);return true;}
                catch(Exception err){pendingFile=null;return false;}
            }
        });
        browser.setDownloadListener((url,ua,disp,mime,length)->{
            try{
                DownloadManager.Request r=new DownloadManager.Request(Uri.parse(url));
                String name=android.webkit.URLUtil.guessFileName(url,disp,mime);r.setTitle(name);
                String cookies=CookieManager.getInstance().getCookie(url);if(cookies!=null)r.addRequestHeader("Cookie",cookies);
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalFilesDir(this,Environment.DIRECTORY_DOWNLOADS,name);
                ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
            }catch(Exception e){Toast.makeText(this,"Téléchargement impossible",Toast.LENGTH_LONG).show();}
        });
    }
    private boolean isGoogleScriptHost(String host){
        if(host==null)return false;host=host.toLowerCase(Locale.ROOT);
        return host.equals("script.google.com")||host.equals("script.googleusercontent.com")||host.endsWith(".script.googleusercontent.com")||host.equals("accounts.google.com");
    }
    private boolean trustCurrentSite(){
        try{Uri u=Uri.parse(browser.getUrl());String host=u.getHost();return "https".equals(u.getScheme()) && isGoogleScriptHost(host) && !"accounts.google.com".equals(host);}catch(Exception e){return false;}
    }
    private boolean validMasterUrl(String raw){return raw!=null&&raw.matches("https://script\\.google\\.com/macros/s/[A-Za-z0-9_-]+/exec(?:\\?.*)?");}
    private void launchSite(){
        if(!validMasterUrl(masterUrl)){configureUrl();return;}
        browser.loadUrl(masterUrl);
    }
    private void configureUrl(){
        EditText input=new EditText(this);input.setSingleLine(false);input.setMaxLines(3);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(masterUrl);input.setHint("https://script.google.com/macros/s/.../exec");
        new AlertDialog.Builder(this).setTitle("Lien KVG Master")
                .setMessage("Coller uniquement l'adresse du site KVG Master, pas celle des vendeuses.")
                .setView(input).setPositiveButton("Enregistrer",(dialog,which)->{
                    String entered=input.getText().toString().trim();
                    if(!validMasterUrl(entered)){Toast.makeText(this,"URL Apps Script /exec invalide",Toast.LENGTH_LONG).show();configureUrl();return;}
                    masterUrl=entered;prefs.edit().putString(URL_KEY,entered).apply();launchSite();
                }).setNegativeButton("Annuler",(d,w)->{}).setCancelable(false).show();
    }
    private void startSlides(){
        introRunning=true;slideNumber=0;splash=new FrameLayout(this);splash.setBackgroundColor(BLACK);
        slideImage=new ImageView(this);slideImage.setScaleType(ImageView.ScaleType.FIT_CENTER);slideImage.setImageResource(SLIDES[0]);
        splash.addView(slideImage,new FrameLayout.LayoutParams(-1,-1));
        Button skip=new Button(this);skip.setAllCaps(false);skip.setText("PASSER ›");skip.setTextColor(BLACK);skip.setBackground(background(GOLD,0));
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(dp(106),dp(46),Gravity.RIGHT|Gravity.TOP);sp.setMargins(0,dp(18),dp(18),0);
        splash.addView(skip,sp);skip.setOnClickListener(v->endSlides());
        root.addView(splash,new FrameLayout.LayoutParams(-1,-1));
        ui.postDelayed(nextSlide,2200);
    }
    private final Runnable nextSlide=new Runnable(){@Override public void run(){
        if(!introRunning||slideImage==null)return;slideNumber++;
        if(slideNumber>=SLIDES.length){endSlides();return;}
        slideImage.animate().alpha(0f).setDuration(140).withEndAction(()->{
            if(!introRunning||slideImage==null)return;
            slideImage.setImageResource(SLIDES[slideNumber]);slideImage.animate().alpha(1f).setDuration(180).start();
        }).start();ui.postDelayed(this,2350);
    }};
    private void endSlides(){
        if(!introRunning)return;introRunning=false;ui.removeCallbacks(nextSlide);
        root.removeView(splash);splash=null;slideImage=null;launchSite();
    }
    private boolean hasBiometrics(){
        if(Build.VERSION.SDK_INT<28)return false;
        try{
            BiometricManager bm=(BiometricManager)getSystemService(Context.BIOMETRIC_SERVICE);
            if(Build.VERSION.SDK_INT>=30)return bm!=null&&bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)==BiometricManager.BIOMETRIC_SUCCESS;
            android.hardware.fingerprint.FingerprintManager fm=(android.hardware.fingerprint.FingerprintManager)getSystemService(Context.FINGERPRINT_SERVICE);
            return fm!=null&&fm.isHardwareDetected()&&fm.hasEnrolledFingerprints();
        }catch(Exception e){return false;}
    }
    private String newToken(int count){byte[] bytes=new byte[count];secureRandom.nextBytes(bytes);return Base64.encodeToString(bytes,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);}
    private String androidDeviceId(){String id=prefs.getString(LOCAL_ID,"");if(id.isEmpty()){
        id=newToken(24);prefs.edit().putString(LOCAL_ID,id).apply();}return id;
    }
    private boolean isEnrolled(){return !prefs.getString(LOCAL_CIPHER,"").isEmpty()&&!prefs.getString(LOCAL_IV,"").isEmpty();}
    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(KEY_ALIAS))return (SecretKey)ks.getKey(KEY_ALIAS,null);
        KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        KeyGenParameterSpec.Builder b=new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true);
        if(Build.VERSION.SDK_INT>=30)b.setUserAuthenticationParameters(0,KeyProperties.AUTH_BIOMETRIC_STRONG);
        else b.setUserAuthenticationValidityDurationSeconds(-1);
        kg.init(b.build());return kg.generateKey();
    }
    private void requestBiometric(Cipher cipher, boolean enrollment) {
        if(biometricBusy)return;
        biometricBusy=true;
        biometricCancel=new CancellationSignal();
        BiometricPrompt prompt=new BiometricPrompt.Builder(this)
                .setTitle(enrollment?"Activer l'empreinte KVG":"Connexion à KVG Master")
                .setSubtitle("Valider avec votre empreinte digitale")
                .setNegativeButton("Mot de passe de secours",getMainExecutor(),(dialog,which)->{biometricBusy=false;})
                .build();
        prompt.authenticate(new BiometricPrompt.CryptoObject(cipher),biometricCancel,getMainExecutor(),new BiometricPrompt.AuthenticationCallback(){
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){
                biometricBusy=false;
                try{
                    Cipher crypt=result.getCryptoObject().getCipher();
                    if(enrollment){
                        byte[] bytes=crypt.doFinal(pendingSecret.getBytes(StandardCharsets.UTF_8));
                        pendingCipher=Base64.encodeToString(bytes,Base64.NO_WRAP);
                        pendingIv=Base64.encodeToString(crypt.getIV(),Base64.NO_WRAP);
                        publishBridge(new JSONObject().put("kind","enroll").put("sessionToken",pendingSession)
                                .put("deviceId",pendingDevice).put("secret",pendingSecret).put("label",Build.MODEL).toString());
                    }else{
                        byte[] enc=Base64.decode(prefs.getString(LOCAL_CIPHER,""),Base64.DEFAULT);
                        String secret=new String(crypt.doFinal(enc),StandardCharsets.UTF_8);
                        publishBridge(new JSONObject().put("kind","login").put("deviceId",androidDeviceId())
                                .put("secret",secret).toString());
                    }
                }catch(Exception e){notifyFailure("Empreinte valide mais clé inutilisable. Reconnectez-vous avec le mot de passe.");}
            }
            @Override public void onAuthenticationError(int code,CharSequence msg){biometricBusy=false;
                if(code!=BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED)
                    notifyFailure(String.valueOf(msg));
            }
            @Override public void onAuthenticationFailed(){ /* Le système permet de réessayer. */ }
        });
    }
    private String beginBridgeRequest(){
        bridgeRequest=newToken(16);bridgeResult="";return bridgeRequest;
    }
    private void publishBridge(String body){bridgeResult=body;}
    private String takeBridge(String handle){
        if(!handle.equals(bridgeRequest)||bridgeResult.isEmpty())return "";
        String value=bridgeResult;bridgeResult="";bridgeRequest="";return value;
    }
    private void notifyFailure(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void beginEnroll(String sessionToken){
        if(!hasBiometrics()){notifyFailure("Veuillez enregistrer une empreinte dans les paramètres Android.");return;}
        if(sessionToken==null||!sessionToken.matches("[0-9a-fA-F-]{70,80}")){notifyFailure("Connexion Master nécessaire.");return;}
        try{
            pendingSession=sessionToken;pendingDevice=androidDeviceId();pendingSecret=newToken(32);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,getOrCreateKey());
            requestBiometric(cipher,true);
        }catch(Exception e){notifyFailure("Empreinte indisponible : "+e.getClass().getSimpleName());}
    }
    private void beginLogin(){
        if(!isEnrolled()){notifyFailure("Première connexion : utilisez votre mot de passe pour activer l'empreinte.");return;}
        if(!hasBiometrics()){notifyFailure("Empreinte Android indisponible. Utilisez le mot de passe.");return;}
        try{
            SecretKey key=getOrCreateKey();byte[] iv=Base64.decode(prefs.getString(LOCAL_IV,""),Base64.DEFAULT);
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));
            requestBiometric(c,false);
        }catch(Exception e){notifyFailure("Clé biométrique invalidée. Utilisez le mot de passe puis réactivez la biométrie.");}
    }
    private void openSettings(){
        String[] choices={"Tester l’empreinte digitale", "Changer l’adresse KVG Master",
                "Paramètres empreinte dans KVG Master"};
        new AlertDialog.Builder(this).setTitle("Paramètres de connexion KVG")
                .setItems(choices,(d,index)->{
                    if(index==0)beginLogin();
                    else if(index==1)configureUrl();
                    else notifyFailure("Dans KVG Master, appuyez sur « Paramètres empreinte » pour l'activer ou la révoquer.");
                }).show();
    }
    private void localDisable(){
        prefs.edit().remove(LOCAL_CIPHER).remove(LOCAL_IV).apply();
        try{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);ks.deleteEntry(KEY_ALIAS);}catch(Exception ignored){}
    }
    private final class BiometricBridge {
        @JavascriptInterface public boolean available(){return hasBiometrics();}
        @JavascriptInterface public boolean isEnrolled(){return MainActivity.this.isEnrolled();}
        @JavascriptInterface public String getDeviceId(){return androidDeviceId();}
        @JavascriptInterface public String login(){
            String request=beginBridgeRequest();runOnUiThread(()->{if(trustCurrentSite())beginLogin();});return request;
        }
        @JavascriptInterface public String enrollIfNeeded(String session){
            if(MainActivity.this.isEnrolled())return "";
            String request=beginBridgeRequest();runOnUiThread(()->{
                if(!trustCurrentSite())return;
                new AlertDialog.Builder(MainActivity.this).setTitle("Activer l'empreinte digitale ?")
                        .setMessage("La prochaine fois, votre empreinte remplacera le mot de passe de KVG Master. L'empreinte reste dans Android.")
                        .setPositiveButton("Activer",(d,w)->beginEnroll(session))
                        .setNegativeButton("Plus tard",(d,w)->{}).show();
            });return request;
        }
        @JavascriptInterface public String pollResult(String request){return takeBridge(request);}
        @JavascriptInterface public void enrollmentConfirmed(){runOnUiThread(()->{
            if(pendingCipher!=null&&pendingIv!=null){prefs.edit().putString(LOCAL_CIPHER,pendingCipher).putString(LOCAL_IV,pendingIv).apply();
                notifyFailure("Empreinte activée ! Elle remplacera votre mot de passe lors de la prochaine connexion.");}
            pendingSession=null;pendingSecret=null;pendingCipher=null;pendingIv=null;
        });}
        @JavascriptInterface public void enrollmentRejected(String error){runOnUiThread(()->{
            pendingSession=null;pendingSecret=null;pendingCipher=null;pendingIv=null;notifyFailure("Activation refusée : "+error);
        });}
        @JavascriptInterface public void localDisable(){runOnUiThread(()->MainActivity.this.localDisable());}
    }
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);
        if(req==PICK_FILE&&pendingFile!=null){pendingFile.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));pendingFile=null;}
    }
    @Override public void onBackPressed(){if(introRunning){endSlides();return;}
        if(browser!=null&&browser.canGoBack()){browser.goBack();return;}super.onBackPressed();}
    @Override protected void onStop(){if(biometricCancel!=null){biometricCancel.cancel();biometricCancel=null;}biometricBusy=false;super.onStop();}
    @Override protected void onDestroy(){ui.removeCallbacks(nextSlide);if(pendingFile!=null)pendingFile.onReceiveValue(null);
        if(browser!=null){browser.removeJavascriptInterface("KVGBioAndroid");browser.destroy();}super.onDestroy();}
}
