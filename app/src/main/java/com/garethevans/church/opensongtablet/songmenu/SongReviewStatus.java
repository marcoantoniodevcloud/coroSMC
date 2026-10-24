package com.garethevans.church.opensongtablet.songmenu;

import android.content.Context;
import android.content.SharedPreferences;

import com.garethevans.church.opensongtablet.songprocessing.Song;

public final class SongReviewStatus {

    private static final String PREFS = "song_review_status";
    private static java.util.Map<String, String> attention;

    private SongReviewStatus() {
    }

    public static boolean isReviewed(Context context, Song song) {
        return preferences(context).getBoolean(key(song), false);
    }

    public static boolean toggle(Context context, Song song) {
        SharedPreferences preferences = preferences(context);
        String key = key(song);
        boolean reviewed = !preferences.getBoolean(key, false);
        preferences.edit().putBoolean(key, reviewed).apply();
        return reviewed;
    }

    public static void setReviewed(Context context, Song song, boolean reviewed) {
        preferences(context).edit().putBoolean(key(song), reviewed).apply();
    }

    public static synchronized String attentionNote(Context context, Song song) {
        if (isReviewed(context, song)) return "";
        if (attention == null) {
            attention = new java.util.HashMap<>();
            try (java.io.InputStream stream = context.getAssets().open("DefaultSongs/revision-cantos.json")) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; int n;
                while ((n = stream.read(buffer)) != -1) out.write(buffer, 0, n);
                org.json.JSONArray songs = new org.json.JSONObject(out.toString("UTF-8")).getJSONArray("songs");
                for (int i = 0; i < songs.length(); i++) {
                    org.json.JSONObject entry = songs.getJSONObject(i);
                    if (!entry.optBoolean("needsAttention")) continue;
                    org.json.JSONArray files = entry.getJSONArray("files");
                    for (int j = 0; j < files.length(); j++) {
                        String path = files.getString(j); int slash = path.lastIndexOf('/');
                        String folder = slash < 0 ? "" : path.substring(0, slash);
                        attention.put(SongReviewKey.create(folder, path.substring(slash + 1), ""), entry.optString("notes"));
                    }
                }
            } catch (Exception ignored) { /* Custom libraries may not have an audit report. */ }
        }
        String note = attention.get(key(song));
        return note == null ? "" : note;
    }

    private static String key(Song song) {
        return SongReviewKey.create(song.getFolder(), song.getFilename(), song.getTitle());
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
