package com.laboratoirekvg.labmaster;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;

public class MainActivity extends Activity {
    private static final String SITE =
        "https://script.google.com/macros/s/AKfycbyACsMvk1luR3c7ClDbIA-8Q1GmiF1VnYP3hLs9WigTzJbb6rk-SPeZRtHK7xHStekJGg/exec";
    private static final String RELOAD = "com.laboratoirekvg.labmaster.RELOAD";
    private static final String EXTERNAL = "com.laboratoirekvg.labmaster.EXTERNAL";
    private static final int PENDING_FLAGS =
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.button_refresh).setOnClickListener(v -> openIntegrated());
        findViewById(R.id.button_menu).setOnClickListener(v -> openExternal());
        findViewById(R.id.button_retry).setOnClickListener(v -> openIntegrated());
        if (EXTERNAL.equals(getIntent().getAction())) openExternal();
        else openIntegrated();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (EXTERNAL.equals(intent.getAction())) openExternal();
        else if (RELOAD.equals(intent.getAction())) openIntegrated();
    }

    private Bitmap refreshIcon() {
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5.5f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawArc(14, 14, 50, 50, -60, 305, false, paint);
        canvas.drawLine(47, 12, 50, 25, paint);
        canvas.drawLine(50, 25, 36, 24, paint);
        return bitmap;
    }

    private PendingIntent command(String action, int requestCode) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, requestCode, intent, PENDING_FLAGS);
    }

    private void openIntegrated() {
        CustomTabColorSchemeParams scheme = new CustomTabColorSchemeParams.Builder()
            .setToolbarColor(Color.rgb(20, 17, 13))
            .setNavigationBarColor(Color.rgb(20, 17, 13))
            .build();

        CustomTabsIntent customTab = new CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(scheme)
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setActionButton(refreshIcon(), "Actualiser", command(RELOAD, 1), true)
            .addMenuItem("Ouvrir dans Chrome", command(EXTERNAL, 2))
            .build();
        try {
            customTab.intent.setPackage("com.android.chrome");
            customTab.launchUrl(this, Uri.parse(SITE));
        } catch (Exception chromeNotFound) {
            try {
                customTab.intent.setPackage(null);
                customTab.launchUrl(this, Uri.parse(SITE));
            } catch (Exception noBrowser) {
                Toast.makeText(this,
                    "Chrome ou un navigateur compatible est requis pour la connexion Google.",
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openExternal() {
        Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(SITE));
        browser.setPackage("com.android.chrome");
        try {
            startActivity(browser);
        } catch (ActivityNotFoundException missing) {
            browser.setPackage(null);
            try {
                startActivity(browser);
            } catch (ActivityNotFoundException none) {
                Toast.makeText(this, "Navigateur indisponible", Toast.LENGTH_LONG).show();
            }
        }
    }
}
