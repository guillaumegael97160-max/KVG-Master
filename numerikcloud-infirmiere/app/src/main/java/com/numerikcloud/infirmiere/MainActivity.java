package com.numerikcloud.master;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;

public class MainActivity extends Activity {
    // Numerik'Cloud MASTER exclusivement; ne pas employer l'URL Infirmière ou KVG.
    private static final String START_URL = "https://script.google.com/macros/s/AKfycbxFC1GhpRoJC7RqkYOB13aFLzq2Pe8FVJCUMt6hPKiggyslYa-h033BOf4Q0byAfIHgHQ/exec";
    private static final int SELECT_FILE = 301;
    private WebView web;
    private FrameLayout root;
    private LinearLayout loading;
    private LinearLayout errorPanel;
    private ProgressBar loadingProgress;
    private android.webkit.ValueCallback<Uri[]> uploadCallback;
    private long backMillis = 0;
    private boolean mainPageError = false;
    private FrameLayout intro;
    private int introIndex = 0;
    private final String[] introTitles={"Vos archives dans votre cloud","Une armoire numérique dans votre téléphone","Retrouvez vos documents partout","Gestion centralisée et sécurisée"};
    private final String[] introDetails={"Votre espace Master, accessible où vous soyez.","Fini les classeurs encombrants : tout reste organisé.","Voyage, urgence, déplacement : vos fichiers vous accompagnent.","Pilotez les dossiers et vos espaces Numerik’Cloud."};

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        immersive();
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        buildLoading();
        buildError();
        setContentView(root);
        showIntro();
        buildRefreshButton();
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                mainPageError = false;
                errorPanel.setVisibility(View.GONE);
                loading.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                loading.setVisibility(View.GONE);
                if (mainPageError) errorPanel.setVisibility(View.VISIBLE);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) mainPageError = true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return handleNonWeb(req.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNonWeb(Uri.parse(url));
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int progress) {
                loadingProgress.setProgress(progress);
            }
            @Override public boolean onShowFileChooser(WebView view, android.webkit.ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (uploadCallback != null) uploadCallback.onReceiveValue(null);
                uploadCallback = callback;
                try {
                    Intent chooser = params.createIntent();
                    chooser.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(chooser, SELECT_FILE);
                    return true;
                } catch (Exception e) {
                    uploadCallback = null;
                    Toast.makeText(MainActivity.this, "Sélection de fichier indisponible", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
        web.setDownloadListener(new DownloadListener() {
            @Override public void onDownloadStart(String url, String ua, String contentDisposition, String mime, long contentLength) {
                try {
                    if (!url.startsWith("https://")) { Toast.makeText(MainActivity.this, "Téléchargement non autorisé", Toast.LENGTH_SHORT).show(); return; }
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setMimeType(mime);
                    request.addRequestHeader("User-Agent", ua);
                    String cookie = CookieManager.getInstance().getCookie(url);
                    if (cookie != null) request.addRequestHeader("Cookie", cookie);
                    request.setTitle(URLUtil.guessFileName(url, contentDisposition, mime));
                    request.setDescription("Document Numerik’Cloud");
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalFilesDir(MainActivity.this, Environment.DIRECTORY_DOWNLOADS,
                        URLUtil.guessFileName(url, contentDisposition, mime));
                    ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
                    Toast.makeText(MainActivity.this, "Téléchargement en cours", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Téléchargement impossible : ouvrez le document depuis le site", Toast.LENGTH_LONG).show();
                }
            }
        });
        if (savedState != null) web.restoreState(savedState);
    }

    private boolean handleNonWeb(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null || scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("about")) {
            if (scheme != null && scheme.equalsIgnoreCase("http")) {
                Toast.makeText(this, "Connexion non sécurisée bloquée", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        }
        try {
            if (scheme.equalsIgnoreCase("intent")) {
                Intent i = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                if (i.resolveActivity(getPackageManager()) != null) startActivity(i);
                else if (i.getStringExtra("browser_fallback_url") != null) web.loadUrl(i.getStringExtra("browser_fallback_url"));
                return true;
            }
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            if (i.resolveActivity(getPackageManager()) != null) startActivity(i);
            else Toast.makeText(this, "Aucune application pour ouvrir ce lien", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(this, "Impossible d’ouvrir ce lien", Toast.LENGTH_SHORT).show(); }
        return true;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private TextView makeText(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(sp); t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }
    private void showIntro() {
        intro = new FrameLayout(this);
        root.addView(intro, new FrameLayout.LayoutParams(-1,-1));
        renderIntro();
    }
    private void renderIntro() {
        intro.removeAllViews();
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xFF06152E,0xFF0964A4,0xFF102849});
        intro.setBackground(background);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24),dp(38),dp(24),dp(42));
        content.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1,-1);
        intro.addView(content,cp);

        TextView cloud = makeText("☁", 80, 0xFFFFD878, false);
        content.addView(cloud,new LinearLayout.LayoutParams(-1,dp(104)));
        TextView name = makeText("Numerik’Cloud", 37, Color.WHITE,true);
        content.addView(name,new LinearLayout.LayoutParams(-1,dp(59)));

        TextView master = makeText("M A S T E R",28,0xFFFFD870,true);
        GradientDrawable pill = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xFF071D44,0xFF0C3B7E});
        pill.setCornerRadius(dp(25)); pill.setStroke(dp(2),0xFFECC06C);
        master.setBackground(pill);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1,dp(62));
        mp.topMargin=dp(6); mp.bottomMargin=dp(20); mp.leftMargin=dp(30);mp.rightMargin=dp(30);
        content.addView(master,mp);

        TextView icon = makeText(new String[]{"☁  ↑","▣  ☁","✦  ☁","▤  ✓"}[introIndex],62,0xFFFFD270,false);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1,dp(124));
        ip.topMargin=dp(10); content.addView(icon,ip);

        TextView title = makeText(introTitles[introIndex],24,Color.WHITE,true);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,dp(88));
        content.addView(title,tp);
        TextView info=makeText(introDetails[introIndex],15,0xFFE0E8F8,false);
        LinearLayout.LayoutParams dpInfo=new LinearLayout.LayoutParams(-1,dp(70));
        dpInfo.bottomMargin=dp(10);
        content.addView(info,dpInfo);

        Button next=new Button(this);
        next.setText(introIndex == introTitles.length-1?"OUVRIR MON ESPACE MASTER":"SUIVANT  ›");
        next.setTextColor(0xFF071F3D);
        next.setAllCaps(false);
        next.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFD168));
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(55));
        np.topMargin=dp(17);
        content.addView(next,np);
        next.setOnClickListener(v->nextIntro());

        TextView skip=makeText("Accéder directement au site",13,0xFFEBEFF8,false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(50));
        content.addView(skip,sp);
        skip.setOnClickListener(v->finishIntro());
    }
    private void nextIntro() { if (++introIndex >= introTitles.length) finishIntro(); else renderIntro(); }
    private void finishIntro() {
        if (intro != null) { root.removeView(intro); intro = null; }
        web.loadUrl(START_URL);
    }
    private void buildLoading() {
        loading=new LinearLayout(this);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(dp(25),dp(20),dp(25),dp(20));
        loading.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF081A36,0xFF0C5B98}));
        loading.addView(makeText("☁",92,0xFFFFD376,false),new LinearLayout.LayoutParams(-1,dp(135)));
        loading.addView(makeText("Numerik’Cloud",35,Color.WHITE,true),new LinearLayout.LayoutParams(-1,dp(67)));
        loading.addView(makeText("MASTER",29,0xFFFFD376,true),new LinearLayout.LayoutParams(-1,dp(55)));
        loading.addView(makeText("VOS ARCHIVES DANS VOTRE CLOUD",13,0xFFECF0F8,true),new LinearLayout.LayoutParams(-1,dp(60)));
        loadingProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        loadingProgress.setMax(100);
        loadingProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFFFD376));
        LinearLayout.LayoutParams progress=new LinearLayout.LayoutParams(dp(210),dp(6));
        progress.gravity=Gravity.CENTER_HORIZONTAL;
        progress.topMargin=dp(19);
        loading.addView(loadingProgress,progress);
        root.addView(loading,new FrameLayout.LayoutParams(-1,-1));
    }
    private void buildRefreshButton(){
        TextView refresh=makeText("↻",28,Color.WHITE,true);
        refresh.setContentDescription("Actualiser Numerik’Cloud Master");
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xED093064);
        bg.setCornerRadius(dp(100));
        bg.setStroke(dp(2),0xFFFFD16A);
        refresh.setBackground(bg);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(43),dp(43),Gravity.TOP|Gravity.RIGHT);
        p.topMargin=dp(13);p.rightMargin=dp(13);
        root.addView(refresh,p);
        refresh.setOnClickListener(v->{web.reload();Toast.makeText(this,"Actualisation Numerik’Cloud Master",Toast.LENGTH_SHORT).show();});
    }
    private void buildError() {
        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setPadding(dp(25),dp(25),dp(25),dp(25));
        errorPanel.setBackgroundColor(Color.WHITE);
        errorPanel.addView(makeText("Connexion indisponible", 21, Color.rgb(32,42,60), true));
        errorPanel.addView(makeText("Vérifiez votre connexion internet, puis réessayez.", 15, Color.DKGRAY, false));
        Button reload = new Button(this);
        reload.setText("Réessayer");
        reload.setOnClickListener(v -> {
            mainPageError = false;
            errorPanel.setVisibility(View.GONE);
            web.loadUrl(START_URL);
        });
        errorPanel.addView(reload);
        errorPanel.setVisibility(View.GONE);
        root.addView(errorPanel, new FrameLayout.LayoutParams(-1, -1));
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SELECT_FILE && uploadCallback != null) {
            Uri[] uris = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            uploadCallback.onReceiveValue(uris);
            uploadCallback = null;
        }
    }
    @Override public void onBackPressed() {
        if (intro != null) { if (introIndex > 0) { introIndex--; renderIntro(); } else finishIntro(); return; }
        if (web.canGoBack()) web.goBack();
        else if (System.currentTimeMillis()-backMillis<1800) super.onBackPressed();
        else { backMillis=System.currentTimeMillis(); Toast.makeText(this,"Appuyez à nouveau pour quitter",Toast.LENGTH_SHORT).show(); }
    }
    @Override protected void onPause() { super.onPause(); web.onPause(); CookieManager.getInstance().flush(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); immersive(); }
    @Override protected void onSaveInstanceState(Bundle b) { web.saveState(b); super.onSaveInstanceState(b); }
    @Override protected void onDestroy() { if(web!=null){ web.destroy(); } super.onDestroy(); }
    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }
}