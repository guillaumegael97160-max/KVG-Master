package com.laboratoirekvg.master;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.CancellationSignal;
import android.widget.VideoView;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
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
import android.text.InputType;

public class MainActivity extends Activity {
    private static final String SETTINGS = "kvg_master_black_gold_settings";
    private static final String URL_KEY = "kvg_master_exec_url";
    // Intentionally blank until the owner supplies the confirmed KVG MASTER deployment URL.
    // Never insert an /exec URL from KVG Vendeuses, Gestion, Martinique, or old builds here.
    private static final String CONFIRMED_MASTER_URL = "";
    private static final int PICK_FILE = 902;
    private static final int GOLD = Color.rgb(214, 176, 91);
    private static final int BLACK = Color.rgb(6, 6, 6);
    private static final int[] INTRO_IMAGES = {
        R.drawable.intro_0, R.drawable.intro_1, R.drawable.intro_2,
        R.drawable.intro_3, R.drawable.intro_4, R.drawable.intro_5
    };

    private final Handler ui = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private FrameLayout introLayer;
    private FrameLayout loadingLayer;
    private FrameLayout setupLayer;
    private ImageView introImage;
    private WebView webView;
    private ProgressBar progressBar;
    private TextView refreshButton;
    private ValueCallback<Uri[]> pendingFiles;
    private String currentMasterUrl;
    private boolean showingIntro = true;
    private boolean webLoaded = false;
    private boolean pageError = false;
    private int slideIndex = 0;
    private static final int DEVICE_CODE = 955;
    private boolean biometricUnlocked = false;
    private boolean authInProgress = false;
    private boolean credentialInProgress = false;
    private boolean introFinished = false;
    private VideoView introVideo;
    private CancellationSignal biometricCancel;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableFullScreen();
        currentMasterUrl = getSharedPreferences(SETTINGS, MODE_PRIVATE)
                .getString(URL_KEY, CONFIRMED_MASTER_URL);
        createScreen();
        buildWebView();
        showIntro();
    }

    private int dp(float n) { return Math.round(getResources().getDisplayMetrics().density * n); }

    private void enableFullScreen() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        getWindow().setNavigationBarColor(BLACK);
        getWindow().setStatusBarColor(BLACK);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enableFullScreen();
    }

    private GradientDrawable bg(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private void createScreen() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BLACK);
        setContentView(root);
        webView = new WebView(this);
        webView.setBackgroundColor(BLACK);
        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        webParams.topMargin = dp(41);
        root.addView(webView, webParams);

        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(Color.rgb(12, 11, 9));
        root.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(41), Gravity.TOP));
        View goldEdge = new View(this);
        goldEdge.setBackgroundColor(GOLD);
        bar.addView(goldEdge, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2), Gravity.TOP));

        TextView brand = new TextView(this);
        brand.setText("LABORATOIRE  KVG   ·   GESTION");
        brand.setTextColor(GOLD);
        brand.setTextSize(10);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.setLetterSpacing(0.11f);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams titlePos = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(39), Gravity.TOP | Gravity.LEFT);
        titlePos.leftMargin = dp(13);
        bar.addView(brand, titlePos);

        refreshButton = new TextView(this);
        refreshButton.setText("⟳");
        refreshButton.setTextSize(24);
        refreshButton.setGravity(Gravity.CENTER);
        refreshButton.setTextColor(GOLD);
        refreshButton.setContentDescription("Actualiser le centre de contrôle KVG Master");
        FrameLayout.LayoutParams refreshPos = new FrameLayout.LayoutParams(dp(43), dp(39),
                Gravity.TOP | Gravity.RIGHT);
        refreshPos.rightMargin = dp(2);
        bar.addView(refreshButton, refreshPos);
        refreshButton.setOnClickListener(v -> refreshSite());
        refreshButton.setOnLongClickListener(v -> { showMasterUrlSetup(); return true; });

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(false);
        if (Build.VERSION.SDK_INT >= 21) progressBar.setProgressTintList(
                android.content.res.ColorStateList.valueOf(GOLD));
        FrameLayout.LayoutParams line = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2), Gravity.TOP);
        line.topMargin = dp(39);
        bar.addView(progressBar, line);
        progressBar.setVisibility(View.GONE);

        loadingLayer = new FrameLayout(this);
        loadingLayer.setBackgroundColor(BLACK);
        FrameLayout.LayoutParams loaderPos = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        loaderPos.topMargin = dp(41);
        root.addView(loadingLayer, loaderPos);
        createLoadingContents();

        setupLayer = new FrameLayout(this);
        setupLayer.setBackgroundColor(BLACK);
        root.addView(setupLayer, loaderPos);
        setupLayer.setVisibility(View.GONE);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void createLoadingContents() {
        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setGravity(Gravity.CENTER);
        middle.setPadding(dp(24), dp(24), dp(24), dp(24));
        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        loadingLayer.addView(middle, place);
        ImageView logo = new ImageView(this);
        // The real child's photo is packaged after the private local APK signing step.
        try (InputStream input = getAssets().open("chargement_fille.jpg")) {
            logo.setImageBitmap(BitmapFactory.decodeStream(input));
        } catch (Exception ex) {
            logo.setImageResource(R.mipmap.ic_launcher);
        }
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(110), dp(110));
        logoParams.gravity = Gravity.CENTER;
        middle.addView(logo, logoParams);
        TextView t = text("LABORATOIRE KVG  ·  GESTION", 18, GOLD, true);
        t.setLetterSpacing(0.19f);
        LinearLayout.LayoutParams tx = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(47));
        tx.topMargin = dp(12);
        middle.addView(t, tx);
        TextView subtitle = text("Connexion au centre de contrôle…", 13, Color.rgb(210, 200, 175), false);
        middle.addView(subtitle);
        ProgressBar spin = new ProgressBar(this);
        spin.setIndeterminate(true);
        if (Build.VERSION.SDK_INT >= 21) spin.setIndeterminateTintList(
                android.content.res.ColorStateList.valueOf(GOLD));
        LinearLayout.LayoutParams spinParams = new LinearLayout.LayoutParams(dp(32),dp(32));
        spinParams.gravity = Gravity.CENTER;
        spinParams.topMargin = dp(30);
        middle.addView(spin, spinParams);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void buildWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int progress) {
                progressBar.setProgress(progress);
                progressBar.setVisibility(progress >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override public boolean onShowFileChooser(WebView web, ValueCallback<Uri[]> callback,
                    FileChooserParams parameters) {
                if (pendingFiles != null) pendingFiles.onReceiveValue(null);
                pendingFiles = callback;
                try {
                    Intent chooser = parameters.createIntent();
                    startActivityForResult(chooser, PICK_FILE);
                    return true;
                } catch (ActivityNotFoundException ex) {
                    pendingFiles = null;
                    Toast.makeText(MainActivity.this, "Sélecteur de fichiers indisponible", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("https://") || url.startsWith("http://")) return false;
                if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("whatsapp:")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                    catch (Exception e) { Toast.makeText(MainActivity.this, "Aucune application disponible", Toast.LENGTH_SHORT).show(); }
                }
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                pageError = false;
                webLoaded = false;
                if (!showingIntro && setupLayer.getVisibility() != View.VISIBLE)
                    loadingLayer.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (pageError) return;
                webLoaded = true;
                if (!showingIntro) loadingLayer.setVisibility(View.GONE);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (!req.isForMainFrame()) return;
                pageError = true;
                webLoaded = false;
                if (!showingIntro) showLoadError();
            }
        });
        webView.setDownloadListener(new DownloadListener() {
            @Override public void onDownloadStart(String url, String userAgent,
                    String contentDisposition, String mimeType, long contentLength) {
                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
                    request.addRequestHeader("User-Agent", userAgent);
                    request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalFilesDir(MainActivity.this,
                            Environment.DIRECTORY_DOWNLOADS,
                            URLUtil.guessFileName(url, contentDisposition, mimeType));
                    ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
                    Toast.makeText(MainActivity.this, "Téléchargement lancé", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Téléchargement indisponible", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private boolean isValidMasterUrl(String url) {
        if (url == null) return false;
        return url.matches("https://script\\.google\\.com/macros/s/[A-Za-z0-9_-]+/exec(?:\\?.*)?");
    }

    private void startMasterSite() {
        if (!isValidMasterUrl(currentMasterUrl)) {
            if (!showingIntro) showMasterUrlSetup();
            return;
        }
        setupLayer.setVisibility(View.GONE);
        pageError = false;
        webLoaded = false;
        loadingLayer.setVisibility(View.VISIBLE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(currentMasterUrl);
    }

    // A native Android launch video, never injected into a remote webpage.
    private File introFile() {
        try {
            File f = new File(getCacheDir(), "kvg_master_intro.mp4");
            if (!f.exists() || f.length()<1000) {
                try(InputStream in=getAssets().open("introduction.mp4");
                    FileOutputStream out=new FileOutputStream(f)) {
                    byte[] bytes=new byte[32768]; int n;
                    while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
                }
            }
            return f;
        } catch(Exception ex){ return null; }
    }

    private void showIntro() {
        showingIntro = true;
        introFinished = false;
        introLayer = new FrameLayout(this);
        introLayer.setBackgroundColor(BLACK);
        introVideo = new VideoView(this);
        introLayer.addView(introVideo,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        TextView skip=text("PASSER ›",13,GOLD,true);
        skip.setBackground(bg(Color.argb(218,0,0,0),GOLD,12));
        FrameLayout.LayoutParams b=new FrameLayout.LayoutParams(dp(110),dp(42),Gravity.RIGHT|Gravity.TOP);
        b.setMargins(0,dp(15),dp(15),0);
        introLayer.addView(skip,b);
        skip.setOnClickListener(v->completeIntro());
        root.addView(introLayer,new FrameLayout.LayoutParams(-1,-1));
        File video=introFile();
        if(video==null){ui.postDelayed(this::completeIntro,600);return;}
        introVideo.setOnCompletionListener(mp->completeIntro());
        introVideo.setOnErrorListener((mp,what,extra)->{completeIntro();return true;});
        introVideo.setVideoPath(video.getAbsolutePath());
        introVideo.setOnPreparedListener(mp->{mp.setLooping(false);introVideo.start();});
    }

    private void completeIntro() {
        if(introFinished)return;
        introFinished=true;
        showingIntro=false;
        if(introVideo!=null){introVideo.stopPlayback();introVideo=null;}
        if(introLayer!=null){root.removeView(introLayer);introLayer=null;}
        loadingLayer.setVisibility(View.VISIBLE);
        showBiometricUnlock();
    }

    private void showBiometricUnlock() {
        if(!introFinished||biometricUnlocked||authInProgress||credentialInProgress)return;
        KeyguardManager km=(KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE);
        if(km==null||!km.isDeviceSecure()) {
            new AlertDialog.Builder(this).setTitle("Accès sécurisé")
              .setMessage("Activez une empreinte ou un code de verrouillage Android avant d'utiliser KVG Gestion.")
              .setPositiveButton("OK",(d,w)->{}).show();return;
        }
        if(Build.VERSION.SDK_INT>=30){
            BiometricManager bm=getSystemService(BiometricManager.class);
            if(bm!=null&&bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)==BiometricManager.BIOMETRIC_SUCCESS){
                authenticateFingerprint();return;
            }
        } else if(Build.VERSION.SDK_INT>=28) {
            android.hardware.fingerprint.FingerprintManager fm =
                (android.hardware.fingerprint.FingerprintManager)getSystemService(Context.FINGERPRINT_SERVICE);
            if(fm!=null&&fm.isHardwareDetected()&&fm.hasEnrolledFingerprints()){
                authenticateFingerprint();return;
            }
        }
        unlockWithDeviceCode();
    }

    private void authenticateFingerprint() {
        authInProgress=true;
        biometricCancel=new CancellationSignal();
        BiometricPrompt prompt=new BiometricPrompt.Builder(this)
            .setTitle("KVG Master Gestion")
            .setSubtitle("Utilisez votre empreinte digitale")
            .setNegativeButton("Code du téléphone",getMainExecutor(),(dialog,which)->{
                authInProgress=false;unlockWithDeviceCode();
            }).build();
        prompt.authenticate(biometricCancel,getMainExecutor(),new BiometricPrompt.AuthenticationCallback(){
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){
                authInProgress=false;unlockSuccess();
            }
            @Override public void onAuthenticationError(int code,CharSequence message){
                authInProgress=false;
                if(code!=BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED)
                    Toast.makeText(MainActivity.this,"Empreinte non reconnue. Réessayez ou utilisez le code du téléphone.",Toast.LENGTH_LONG).show();
            }
        });
    }

    private void unlockWithDeviceCode() {
        if(credentialInProgress)return;
        KeyguardManager km=(KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE);
        Intent intent=km==null?null:km.createConfirmDeviceCredentialIntent(
                "KVG Master Gestion","Confirmez le code du téléphone pour ouvrir KVG Gestion.");
        if(intent==null){Toast.makeText(this,"Sécurisez d'abord le téléphone.",Toast.LENGTH_LONG).show();return;}
        credentialInProgress=true;
        startActivityForResult(intent,DEVICE_CODE);
    }

    private void unlockSuccess() {
        biometricUnlocked=true;
        if(isValidMasterUrl(currentMasterUrl))startMasterSite();
        else showMasterUrlSetup();
    }

    private void relock() {
        biometricUnlocked=false;
        if(webView!=null)webView.setVisibility(View.INVISIBLE);
        if(loadingLayer!=null)loadingLayer.setVisibility(View.VISIBLE);
    }

    private void showMasterUrlSetup() {
        setupLayer.removeAllViews();
        setupLayer.setVisibility(View.VISIBLE);
        loadingLayer.setVisibility(View.GONE);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(22), dp(15), dp(22), dp(24));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        setupLayer.addView(panel, panelParams);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        LinearLayout.LayoutParams logoP = new LinearLayout.LayoutParams(dp(124),dp(124));
        logoP.gravity = Gravity.CENTER;
        panel.addView(logo, logoP);
        TextView title = text("KVG  MASTER", 22, GOLD, true);
        title.setLetterSpacing(.16f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1,dp(54));
        panel.addView(title,titleParams);
        TextView explanation = text(
                "Première connexion au centre de contrôle.\nCollez l'adresse /exec exacte de KVG MASTER.\nAucune autre application KVG ne sera utilisée.",
                13, Color.rgb(219,211,192), false);
        explanation.setLineSpacing(dp(4), 1f);
        panel.addView(explanation, new LinearLayout.LayoutParams(-1,dp(105)));
        EditText urlField = new EditText(this);
        urlField.setSingleLine(false);
        urlField.setMaxLines(3);
        urlField.setTextSize(13);
        urlField.setTextColor(Color.WHITE);
        urlField.setHintTextColor(Color.LTGRAY);
        urlField.setHint("https://script.google.com/macros/s/.../exec");
        urlField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlField.setPadding(dp(15),dp(13),dp(15),dp(13));
        urlField.setBackground(bg(Color.rgb(20,18,16), GOLD, 12));
        if (isValidMasterUrl(currentMasterUrl)) urlField.setText(currentMasterUrl);
        LinearLayout.LayoutParams urlPos = new LinearLayout.LayoutParams(-1,dp(100));
        urlPos.topMargin = dp(12);
        panel.addView(urlField,urlPos);
        Button save = new Button(this);
        save.setAllCaps(false);
        save.setText("Connecter KVG MASTER");
        save.setTextColor(BLACK);
        save.setTextSize(14);
        save.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        save.setBackground(bg(GOLD,0,12));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1,dp(50));
        saveParams.topMargin = dp(20);
        panel.addView(save, saveParams);
        save.setOnClickListener(v -> {
            String entered = urlField.getText().toString().trim();
            if (!isValidMasterUrl(entered)) {
                urlField.setError("Coller uniquement le lien /exec de KVG Master.");
                return;
            }
            currentMasterUrl = entered;
            getSharedPreferences(SETTINGS,MODE_PRIVATE).edit().putString(URL_KEY,entered).apply();
            startMasterSite();
        });
    }

    private void showLoadError() {
        loadingLayer.removeAllViews();
        loadingLayer.setVisibility(View.VISIBLE);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(24),dp(28),dp(24),dp(28));
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        loadingLayer.addView(box, new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        box.addView(text("Connexion au centre de contrôle indisponible", 17, GOLD,true));
        TextView note = text("Vérifiez la connexion Internet et le déploiement KVG MASTER.",13,Color.WHITE,false);
        LinearLayout.LayoutParams noteP = new LinearLayout.LayoutParams(-1,-2);
        noteP.topMargin = dp(18);
        box.addView(note,noteP);
        Button retry = new Button(this);
        retry.setText("Réessayer");
        retry.setAllCaps(false);
        retry.setTextColor(BLACK);
        retry.setBackground(bg(GOLD,0,10));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(180),dp(48));
        rp.topMargin = dp(26);
        box.addView(retry,rp);
        retry.setOnClickListener(v -> {
            loadingLayer.removeAllViews();
            createLoadingContents();
            startMasterSite();
        });
        TextView reconfigure = text("Modifier l'adresse Master",13,GOLD,false);
        LinearLayout.LayoutParams reconfig = new LinearLayout.LayoutParams(-1,dp(50));
        reconfig.topMargin = dp(18);
        box.addView(reconfigure,reconfig);
        reconfigure.setOnClickListener(v -> showMasterUrlSetup());
    }

    private void refreshSite() {
        if (!isValidMasterUrl(currentMasterUrl)) { showMasterUrlSetup(); return; }
        if (webView != null) {
            Toast.makeText(this,"Actualisation KVG MASTER",Toast.LENGTH_SHORT).show();
            webView.reload();
        }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == DEVICE_CODE) {
            credentialInProgress=false;
            if(resultCode==RESULT_OK)unlockSuccess();
            return;
        }
        if (requestCode == PICK_FILE && pendingFiles != null) {
            Uri[] picked = WebChromeClient.FileChooserParams.parseResult(resultCode,data);
            pendingFiles.onReceiveValue(picked);
            pendingFiles = null;
        }
    }

    @Override public void onBackPressed() {
        if (showingIntro) { completeIntro(); return; }
        if (setupLayer.getVisibility() == View.VISIBLE) {
            if (isValidMasterUrl(currentMasterUrl)) { setupLayer.setVisibility(View.GONE); return; }
            new AlertDialog.Builder(this)
                    .setMessage("L'application nécessite le lien exact de KVG MASTER.")
                    .setPositiveButton("OK",(d,w) -> {})
                    .show();
            return;
        }
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onResume() {
        super.onResume();
        enableFullScreen();
        if (webView != null && biometricUnlocked) webView.onResume();
        if (introFinished && !biometricUnlocked && !authInProgress && !credentialInProgress)
            ui.postDelayed(this::showBiometricUnlock,240);
    }
    @Override protected void onPause() {
        if (!authInProgress && !credentialInProgress) relock();
        if (webView != null) webView.onPause();
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (biometricCancel != null) biometricCancel.cancel();
        if(introVideo!=null)introVideo.stopPlayback();
        if (pendingFiles != null) { pendingFiles.onReceiveValue(null); pendingFiles=null; }
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
