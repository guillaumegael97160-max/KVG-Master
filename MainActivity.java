package com.laboratoirekvg.larentree;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Toast;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;

/**
 * KVG Master Guadeloupe — acces proprietaire via navigateur securise integre.
 * WebView ne peut pas partager l'authentification Google Chrome.
 * Chrome Custom Tabs reutilise les cookies Chrome, sans lancer une fenetre Chrome classique.
 * La barre de securite du domaine reste sous controle du navigateur.
 */
public class MainActivity extends Activity {
    private static final String SITE_URL =
            "https://script.google.com/macros/s/AKfycbyACsMvk1luR3c7ClDbIA-8Q1GmiF1VnYP3hLs9WigTzJbb6rk-SPeZRtHK7xHStekJGg/exec";
    private boolean opening;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= 35) {
            View root = findViewById(R.id.app_root);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets system = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(system.left, system.top, system.right, system.bottom);
                return insets;
            });
            root.requestApplyInsets();
        }
        findViewById(R.id.open_kvg).setOnClickListener(v -> openInApp());
        if (savedInstanceState == null) {
            new Handler(Looper.getMainLooper()).postDelayed(this::openInApp, 180);
        }
    }

    private void openInApp() {
        if (opening) return;
        opening = true;
        final int dark = Color.rgb(16, 13, 9);
        final int gold = Color.rgb(218, 181, 91);

        CustomTabColorSchemeParams colors = new CustomTabColorSchemeParams.Builder()
                .setToolbarColor(dark)
                .setNavigationBarColor(dark)
                .setSecondaryToolbarColor(dark)
                .build();

        CustomTabsIntent tab = new CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colors)
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                .setShowTitle(false)
                .setUrlBarHidingEnabled(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .build();

        // Utilise Chrome deja connecte au compte Google du proprietaire.
        // Custom Tab = navigateur securise dans la tache Android KVG Master.
        tab.intent.setPackage("com.android.chrome");
        try {
            tab.launchUrl(this, Uri.parse(SITE_URL));
        } catch (ActivityNotFoundException noChrome) {
            tab.intent.setPackage(null);
            try {
                tab.launchUrl(this, Uri.parse(SITE_URL));
            } catch (Exception noBrowser) {
                Toast.makeText(this,
                        "Un navigateur prenant en charge les onglets securises est requis.",
                        Toast.LENGTH_LONG).show();
            }
        } catch (Exception error) {
            Toast.makeText(this,
                    "Ouverture du site indisponible : verifier la connexion.",
                    Toast.LENGTH_LONG).show();
        } finally {
            // Revenir sur le lanceur KVG permet un nouvel essai sans boucle de navigation.
            new Handler(Looper.getMainLooper()).postDelayed(() -> opening = false, 900);
        }
    }
}
