package com.laboratoirekvg.master;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ProjectsActivity extends Activity {
    private record Project(String id, String name, int group, String defaultUrl) {}
    private static final String[] GROUPS = {"Notifications", "Ventes", "Pharmacies", "Concours", "Équipe"};
    private static final Project[] PROJECTS = {
        new Project("gua", "KVG Vendeuses Guadeloupe", 1, ""),
        new Project("mq", "KVG Vendeuses Martinique", 1, ""),
        new Project("gestion-mq", "KVG Martinique Gestion", 1, ""),
        new Project("commandes", "Commandes Déo An Nou Pharmacie", 2,
            "https://script.google.com/macros/s/AKfycbySl2Zf5hZ6snDCgP3vTl_K5anRr-PP3hQQ91XKAf8qUI0TMUXpq9xl4iMJv-Hj8tKi/exec"),
        new Project("coffres", "Coffres Déo An Nou Pharmacies", 2, ""),
        new Project("sante-plus", "Pharmacie Santé Plus", 2, ""),
        new Project("instagram", "Concours Instagram Déo An Nou", 3,
            "https://script.google.com/macros/s/AKfycbzBCJsekdU3PmkPqcDK9pPRSXDTzaPS7t9kfNrcVZtbcpik1FbcG96LSKbGcTCq1H14/exec"),
        new Project("carrefour", "Concours Carrefour", 3, ""),
        new Project("malory", "KVG Malory", 4, "")
    };
    private LinearLayout content;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        content.setBackgroundColor(0xff11100e);
        scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        content.removeAllViews();
        int group = getIntent().getIntExtra("group", 0);
        if (group < 0 || group >= GROUPS.length) group = 0;
        button("‹ Retour à KVG Master", this::finish);
        label(GROUPS[group], 26);
        if (group == 0) {
            showNotifications();
            return;
        }
        for (Project project : PROJECTS) {
            if (project.group() != group) continue;
            Button link = button(project.name(), () -> open(project));
            link.setOnLongClickListener(v -> { configure(project); return true; });
        }
        label("Appui long pour modifier le lien d’un projet.", 14);
    }

    private Button button(String caption, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(caption);
        button.setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
        content.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, -2));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void label(String caption, int size) {
        TextView view = new TextView(this);
        view.setTextColor(0xffddb563);
        view.setTextSize(size);
        view.setText(caption);
        content.addView(view);
    }

    private SharedPreferences links() {
        return getSharedPreferences("kvg_project_links", MODE_PRIVATE);
    }

    private void open(Project project) {
        String url = links().getString(project.id(), project.defaultUrl());
        if (url.isEmpty()) {
            configure(project);
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "Navigateur indisponible", Toast.LENGTH_LONG).show();
        }
    }

    private void configure(Project project) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine();
        input.setText(links().getString(project.id(), project.defaultUrl()));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(project.name())
            .setView(input).setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer", null).show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String url = input.getText().toString().trim();
            Uri uri = Uri.parse(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || url.contains("\\")) {
                input.setError("Entre une adresse HTTPS valide");
                return;
            }
            links().edit().putString(project.id(), url).apply();
            dialog.dismiss();
            open(project);
        });
    }

    private void showNotifications() {
        button("Configurer les alertes Android", () -> {
            if (FirebaseApp.getApps(this).isEmpty()) {
                label("Firebase doit être configuré dans cette version Android.", 15);
                return;
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
                return;
            }
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(this, task -> {
                if (!task.isSuccessful()) {
                    Toast.makeText(this, "Service de notifications indisponible", Toast.LENGTH_LONG).show();
                    return;
                }
                EditText token = new EditText(this);
                token.setText(task.getResult());
                token.setInputType(InputType.TYPE_NULL);
                token.setTextIsSelectable(true);
                new AlertDialog.Builder(this).setTitle("Jeton de cet appareil")
                    .setMessage("À enregistrer dans la configuration privée de l’émetteur KVG.")
                    .setView(token).setPositiveButton("Fermer", null).show();
            });
        });
        JSONArray inbox = KvgMessagingService.readInbox(this);
        if (inbox.length() == 0) label("Aucune notification reçue sur cet appareil.", 15);
        for (int i = 0; i < inbox.length(); i++) {
            JSONObject event = inbox.optJSONObject(i);
            if (event == null) continue;
            for (Project project : PROJECTS) {
                if (project.id().equals(event.optString("project"))) {
                    button(project.name() + " · " + event.optString("title"), () -> open(project));
                    break;
                }
            }
        }
    }
}
