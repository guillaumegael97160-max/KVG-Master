package com.laboratoirekvg.larentree;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowInsets;
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
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final String START_URL = "https://script.google.com/macros/s/AKfycbyACsMvk1luR3c7ClDbIA-8Q1GmiF1VnYP3hLs9WigTzJbb6rk-SPeZRtHK7xHStekJGg/exec";
    private static final String PREFS = "kvg_guadeloupe_compact_preferences";
    private static final String PREF_DESKTOP = "desktop_mode";
    private static final int REQUEST_FILE = 602;

    private static final int MENU_REFRESH = 1;
    private static final int MENU_DESKTOP = 2;
    private static final int MENU_BROWSER = 3;
    private static final int MENU_SHARE = 4;
    private static final int MENU_HOME = 5;

    private WebView webView;
    private ProgressBar progressBar;
    private View errorPanel;
    private ValueCallback<Uri[]> pendingFileCallback;
    private String defaultUserAgent;
    private boolean desktopMode;
    private boolean pageHasError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Android 15+ applique l'affichage bord à bord aux applis qui ciblent l'API 35.
        // Conserver la barre de navigation Android en dehors des commandes de l'application.
        if (Build.VERSION.SDK_INT >= 35) {
            View root = findViewById(R.id.app_root);
            root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                return windowInsets;
            });
            root.requestApplyInsets();
        }

        progressBar = findViewById(R.id.page_progress);
        errorPanel = findViewById(R.id.error_panel);
        webView = findViewById(R.id.web_view);

        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        // Les tablettes utilisent le mode ordinateur dès le premier lancement.
        // Il reste activable / désactivable dans le menu sur tous les appareils.
        boolean isTablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        desktopMode = preferences.getBoolean(PREF_DESKTOP, isTablet);

        initializeWebView();

        findViewById(R.id.button_refresh).setOnClickListener(view -> reload());
        findViewById(R.id.button_menu).setOnClickListener(v ->
                openInside(webView.getUrl() == null ? START_URL : webView.getUrl()));
        findViewById(R.id.button_logo).setOnLongClickListener(v -> {
            desktopMode = !desktopMode;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_DESKTOP, desktopMode).apply();
            applyDesktopMode(true);
            Toast.makeText(this, desktopMode ? "Affichage ordinateur" : "Affichage téléphone", Toast.LENGTH_SHORT).show();
            return true;
        });
        findViewById(R.id.button_retry).setOnClickListener(view -> reload());
        findViewById(R.id.button_browser).setOnClickListener(view -> openInside(START_URL));

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(START_URL);
        }
    }

    private void initializeWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        defaultUserAgent = settings.getUserAgentString();
        applyDesktopMode(false);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                pageHasError = false;
                errorPanel.setVisibility(View.GONE);
                progressBar.setProgress(5);
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                if (!pageHasError) {
                    errorPanel.setVisibility(View.GONE);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    pageHasError = true;
                    progressBar.setVisibility(View.GONE);
                    errorPanel.setVisibility(View.VISIBLE);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                progressBar.setProgress(progress);
                if (progress >= 100 && !pageHasError) {
                    progressBar.setVisibility(View.GONE);
                }
            }

            // Permet de joindre une image / un document à un formulaire HTML.
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (pendingFileCallback != null) {
                    pendingFileCallback.onReceiveValue(null);
                }
                pendingFileCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    startActivityForResult(intent, REQUEST_FILE);
                    return true;
                } catch (ActivityNotFoundException exception) {
                    pendingFileCallback = null;
                    Toast.makeText(MainActivity.this, "Sélecteur de fichier indisponible", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
                Toast.makeText(this, "Ce fichier doit être téléchargé depuis le navigateur", Toast.LENGTH_LONG).show();
                openExternal(START_URL);
                return;
            }
            // Sans permission de stockage sur Android 10+, DownloadManager enregistre dans Téléchargements.
            if (Build.VERSION.SDK_INT < 29) {
                openExternal(url);
                return;
            }
            try {
                DownloadManager.Request download = new DownloadManager.Request(Uri.parse(url));
                download.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                download.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                download.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                        URLUtil.guessFileName(url, contentDisposition, mimeType));
                download.setMimeType(mimeType);
                download.addRequestHeader("User-Agent", userAgent == null ? defaultUserAgent : userAgent);
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) download.addRequestHeader("Cookie", cookies);
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                dm.enqueue(download);
                Toast.makeText(this, "Téléchargement lancé", Toast.LENGTH_SHORT).show();
            } catch (Exception exception) {
                Toast.makeText(this, "Ouverture du téléchargement dans le navigateur", Toast.LENGTH_LONG).show();
                openExternal(url);
            }
        });
    }

    private boolean handleNavigation(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null) return true;
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            // Google interdit souvent la connexion dans les navigateurs intégrés (WebView).
            String host = uri.getHost();
            if (host != null && (host.equalsIgnoreCase("accounts.google.com")
                    || host.toLowerCase(Locale.ROOT).endsWith(".accounts.google.com"))) {
                openInside(uri.toString());
                return true;
            }
            return false; // rester dans l'application pour le site et ses redirections
        }
        if ("intent".equalsIgnoreCase(scheme)) {
            try {
                Intent intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    String fallback = intent.getStringExtra("browser_fallback_url");
                    if (fallback != null) openExternal(fallback);
                }
            } catch (Exception ignored) {
                Toast.makeText(this, "Lien externe indisponible", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        openExternal(uri.toString());
        return true;
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        Menu items = menu.getMenu();
        items.add(0, MENU_REFRESH, 0, "Actualiser");
        MenuItem desktop = items.add(0, MENU_DESKTOP, 1, "Mode ordinateur");
        desktop.setCheckable(true);
        desktop.setChecked(desktopMode);
        items.add(0, MENU_HOME, 2, "Retour à l’accueil");
        items.add(0, MENU_BROWSER, 3, "Ouvrir dans Chrome / navigateur");
        items.add(0, MENU_SHARE, 4, "Partager le lien");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case MENU_REFRESH:
                    reload();
                    return true;
                case MENU_DESKTOP:
                    desktopMode = !desktopMode;
                    getSharedPreferences(PREFS, MODE_PRIVATE)
                            .edit().putBoolean(PREF_DESKTOP, desktopMode).apply();
                    applyDesktopMode(true);
                    return true;
                case MENU_HOME:
                    pageHasError = false;
                    errorPanel.setVisibility(View.GONE);
                    webView.loadUrl(START_URL);
                    return true;
                case MENU_BROWSER:
                    openInside(webView.getUrl() == null ? START_URL : webView.getUrl());
                    return true;
                case MENU_SHARE:
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("text/plain");
                    share.putExtra(Intent.EXTRA_TEXT, START_URL);
                    startActivity(Intent.createChooser(share, "Partager l’application"));
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void applyDesktopMode(boolean reload) {
        if (desktopMode) {
            // Un user-agent bureau permet une vraie demande de site ordinateur.
            webView.getSettings().setUserAgentString(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
        } else {
            webView.getSettings().setUserAgentString(defaultUserAgent);
        }
        if (reload) reload();
    }

    private void reload() {
        pageHasError = false;
        errorPanel.setVisibility(View.GONE);
        if (webView.getUrl() == null) webView.loadUrl(START_URL);
        else webView.reload();
    }

    /** Page supplementaire interne, sans lancer l'application Chrome. */
    private void openInside(String requestedUrl) {
        String url = requestedUrl == null ? START_URL : requestedUrl;
        Uri uri;
        try { uri = Uri.parse(url); } catch (Exception error) { uri = Uri.parse(START_URL); }
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            uri = Uri.parse(START_URL);
        }
        Intent page = new Intent(this, BrowserActivity.class);
        page.putExtra(BrowserActivity.EXTRA_URL, uri.toString());
        startActivity(page);
    }

    private void openExternal(String url) {
        try {
            Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            browser.addCategory(Intent.CATEGORY_BROWSABLE);
            browser.setPackage("com.android.chrome");
            try {
                startActivity(browser);
            } catch (ActivityNotFoundException chromeAbsent) {
                browser.setPackage(null);
                startActivity(browser);
            }
        } catch (Exception exception) {
            Toast.makeText(this, "Aucun navigateur compatible trouvé", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FILE && pendingFileCallback != null) {
            pendingFileCallback.onReceiveValue(
                    WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            pendingFileCallback = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (errorPanel.getVisibility() == View.VISIBLE) {
            errorPanel.setVisibility(View.GONE);
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (pendingFileCallback != null) {
            pendingFileCallback.onReceiveValue(null);
            pendingFileCallback = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
