package com.garethevans.church.opensongtablet.songmenu;

import android.content.Context;
import android.content.SharedPreferences;

import com.garethevans.church.opensongtablet.songprocessing.Song;

public final class SongReviewStatus {

    private static final String PREFS = "song_review_status";

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

    private static String key(Song song) {
        return SongReviewKey.create(song.getFolder(), song.getFilename(), song.getTitle());
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
