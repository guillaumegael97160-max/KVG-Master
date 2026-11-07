package com.laboratoirekvg.larentree;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.WindowInsets;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

/** Une page Chromium integree dans KVG Master : aucune barre Chrome ni onglet externe. */
public class BrowserActivity extends Activity {
    public static final String EXTRA_URL = "kvg.guadeloupe.secondary_url";
    private static final String HOME = "https://script.google.com/macros/s/AKfycbyACsMvk1luR3c7ClDbIA-8Q1GmiF1VnYP3hLs9WigTzJbb6rk-SPeZRtHK7xHStekJGg/exec";
    private static final int FILE_PICKER = 703;
    private WebView browser;
    private ProgressBar progress;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.browser_internal);
        if (Build.VERSION.SDK_INT >= 35) {
            View root = findViewById(R.id.inner_root);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
            root.requestApplyWindowInsets();
        }

        browser = findViewById(R.id.inner_webview);
        progress = findViewById(R.id.inner_progress);
        findViewById(R.id.inner_back).setOnClickListener(v -> goBackOrClose());
        findViewById(R.id.inner_reload).setOnClickListener(v -> browser.reload());

        WebSettings s = browser.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setLoadsImagesAutomatically(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true);
        browser.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) return false;
                try {
                    Intent external = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(external);
                } catch (Exception error) {
                    Toast.makeText(BrowserActivity.this, "Lien non pris en charge", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        browser.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_PICKER);
                    return true;
                } catch (ActivityNotFoundException ex) {
                    fileCallback = null;
                    Toast.makeText(BrowserActivity.this, "Selection de fichier indisponible", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
        browser.setDownloadListener((url, ua, disp, mime, bytes) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setTitle(URLUtil.guessFileName(url, disp, mime));
                if (mime != null && !mime.isEmpty()) request.setMimeType(mime);
                if (Build.VERSION.SDK_INT >= 29) {
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                            URLUtil.guessFileName(url, disp, mime));
                }
                if (ua != null) request.addRequestHeader("User-Agent", ua);
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) request.addRequestHeader("Cookie", cookies);
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                dm.enqueue(request);
                Toast.makeText(this, "Telechargement lance", Toast.LENGTH_SHORT).show();
            } catch (Exception ex) {
                Toast.makeText(this, "Telechargement impossible", Toast.LENGTH_LONG).show();
            }
        });

        String input = getIntent().getStringExtra(EXTRA_URL);
        if (input == null || input.isEmpty()) input = HOME;
        Uri uri = Uri.parse(input);
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) input = HOME;
        if (state == null || browser.restoreState(state) == null) browser.loadUrl(input);
    }

    private void goBackOrClose() {
        if (browser != null && browser.canGoBack()) browser.goBack();
        else finish();
    }

    @Override
    public void onBackPressed() { goBackOrClose(); }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (browser != null) browser.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onActivityResult(int code, int result, Intent intent) {
        super.onActivityResult(code, result, intent);
        if (code == FILE_PICKER && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, intent));
            fileCallback = null;
        }
    }

    @Override
    protected void onDestroy() {
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
        if (browser != null) { browser.stopLoading(); browser.destroy(); }
        super.onDestroy();
    }
}
