package com.laboratoirekvg.master;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class KvgMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL = "kvg_projects";
    private static final String PROJECT_IDS = "gua|mq|gestion-mq|commandes|coffres|sante-plus|instagram|carrefour|malory";

    static JSONArray readInbox(Context context) {
        try {
            return new JSONArray(prefs(context).getString("events", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("kvg_notification_inbox", MODE_PRIVATE);
    }

    @Override public void onMessageReceived(RemoteMessage message) {
        Map<String, String> data = message.getData();
        String project = data.getOrDefault("project", "");
        String id = data.getOrDefault("id", "");
        String title = data.getOrDefault("title", "");
        if (!project.matches(PROJECT_IDS) || id.isEmpty() || id.length() > 150
                || title.isEmpty() || title.length() > 120) return;

        JSONArray previous = readInbox(this);
        for (int i = 0; i < previous.length(); i++) {
            JSONObject saved = previous.optJSONObject(i);
            if (saved != null && id.equals(saved.optString("id"))
                    && project.equals(saved.optString("project"))) return;
        }
        try {
            JSONObject event = new JSONObject().put("id", id).put("project", project).put("title", title);
            JSONArray updated = new JSONArray().put(event);
            for (int i = 0; i < Math.min(previous.length(), 199); i++) updated.put(previous.get(i));
            prefs(this).edit().putString("events", updated.toString()).apply();
        } catch (JSONException e) {
            return;
        }

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Projets KVG", NotificationManager.IMPORTANCE_HIGH));
        Intent intent = new Intent(this, ProjectsActivity.class);
        PendingIntent open = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        manager.notify(project + ":" + id, 1, new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("KVG Master")
            .setContentText("Nouvelle activité dans tes projets")
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build());
    }
}
