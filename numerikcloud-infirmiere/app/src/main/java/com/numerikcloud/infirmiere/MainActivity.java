package com.numerikcloud.infirmiere;

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

public class MainActivity extends Activity {
    // Adresse exacte de NumerikCloud Infirmière (distincte de KVG et Master).
    private static final String START_URL = "https://script.google.com/macros/s/AKfycbzOBx77cV58KWbfK_O62SltpLSvRYGEw3sakHkRM69iS9ImRuiKU4zP0vo2JpiWzzn9pA/exec";
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
    private TextView refreshControl;
    private int introIndex = 0;
    private final int[] introImages = {R.drawable.intro_1, R.drawable.intro_2, R.drawable.intro_3, R.drawable.intro_4};

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
        buildRefreshControl();
        setContentView(root);
        showIntro();
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
        intro.setBackgroundColor(Color.rgb(245,247,251));
        root.addView(intro, new FrameLayout.LayoutParams(-1,-1));
        renderIntro();
    }
    private void renderIntro() {
        intro.removeAllViews();
        ImageView image = new ImageView(this);
        image.setImageResource(introImages[introIndex]);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        intro.addView(image, new FrameLayout.LayoutParams(-1,-1));
        image.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            @Override public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) { startX=e.getX(); startY=e.getY(); return true; }
                if (e.getAction() == android.view.MotionEvent.ACTION_UP) {
                    float dx=e.getX()-startX;
                    if (Math.abs(dx)>dp(55) && Math.abs(dx)>Math.abs(e.getY()-startY)) {
                        if(dx<0)nextIntro(); else if(introIndex>0){introIndex--;renderIntro();}
                    } else if(e.getY()<v.getHeight()*0.75f) nextIntro();
                    return true;
                }
                return true;
            }
        });
        Button next = new Button(this);
        next.setText(introIndex == introImages.length - 1 ? "COMMENCER" : "SUIVANT  ›");
        next.setTextColor(Color.WHITE);
        next.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(15,110,180)));
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        np.bottomMargin = dp(22);
        intro.addView(next, np);
        next.setOnClickListener(v -> nextIntro());
        if (introIndex > 0) {
            Button back = new Button(this);
            back.setText("‹");
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(56), dp(52), Gravity.BOTTOM | Gravity.LEFT);
            bp.bottomMargin = dp(22); bp.leftMargin = dp(12);
            intro.addView(back, bp);
            back.setOnClickListener(v -> { introIndex--; renderIntro(); });
        }
        if (introIndex < introImages.length - 1) {
            TextView skip = new TextView(this);
            skip.setText("Passer"); skip.setTextSize(16); skip.setTextColor(Color.rgb(20,85,150));
            skip.setPadding(dp(14),dp(14),dp(14),dp(14));
            FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);
            sp.topMargin=dp(16); sp.rightMargin=dp(8);
            intro.addView(skip,sp);
            skip.setOnClickListener(v -> finishIntro());
        }
    }
    private void nextIntro() { if (++introIndex >= introImages.length) finishIntro(); else renderIntro(); }
    private void finishIntro() {
        if (intro != null) { root.removeView(intro); intro = null; }
        // Actualisation toujours visible apres l'introduction, jamais pendant les diapos.
        if (refreshControl != null) refreshControl.setVisibility(View.VISIBLE);
        // La session WebView V2 est preservee. Ne jamais vider cookies, cache ou stockage.
        if (web.getUrl() == null || web.getUrl().isEmpty()) web.loadUrl(START_URL);
    }
    /**
     * Petit cercle d'actualisation en haut a gauche.
     * Un simple reload() conserve les cookies et l'authentification de la V2.
     * Aucune ouverture de navigateur externe.
     */
    private void buildRefreshControl() {
        refreshControl = new TextView(this);
        refreshControl.setText("\u21bb");
        refreshControl.setTextSize(26);
        refreshControl.setTextColor(Color.rgb(18, 101, 174));
        refreshControl.setGravity(Gravity.CENTER);
        refreshControl.setContentDescription("Actualiser la page NumerikCloud");
        refreshControl.setTypeface(null, android.graphics.Typeface.BOLD);
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        background.setColor(Color.argb(232, 255, 255, 255));
        background.setStroke(dp(1), Color.rgb(211, 226, 240));
        refreshControl.setBackground(background);
        refreshControl.setElevation(dp(3));
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP | Gravity.LEFT);
        rp.leftMargin = dp(8);
        rp.topMargin = dp(10);
        root.addView(refreshControl, rp);
        refreshControl.setVisibility(View.GONE);
        refreshControl.setOnClickListener(v -> {
            refreshControl.animate().rotationBy(360).setDuration(350).start();
            if (web.getUrl() == null || web.getUrl().isEmpty()) web.loadUrl(START_URL);
            else web.reload();
        });
    }

    private void buildLoading() {
        loading = new LinearLayout(this);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setBackgroundColor(Color.rgb(249, 249, 247));
        loading.setGravity(Gravity.CENTER);
        ImageView image = new ImageView(this);
        image.setImageResource(com.numerikcloud.infirmiere.R.drawable.logo_numerikcloud);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        loading.addView(image, new LinearLayout.LayoutParams(dp(290), dp(315)));
        TextView label = makeText("INFIRMIÈRE", 22, Color.rgb(32, 42, 60), true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(44));
        lp.topMargin = dp(8);
        loading.addView(label, lp);
        TextView subtitle = makeText("Vos archives dans votre cloud", 13, Color.rgb(105, 110, 121), false);
        loading.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(35)));
        loadingProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loadingProgress.setMax(100);
        LinearLayout.LayoutParams bar = new LinearLayout.LayoutParams(dp(210), dp(5));
        bar.topMargin = dp(22);
        loading.addView(loadingProgress, bar);
        root.addView(loading, new FrameLayout.LayoutParams(-1,-1));
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