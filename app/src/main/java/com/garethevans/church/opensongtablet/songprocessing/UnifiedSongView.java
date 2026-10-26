package com.garethevans.church.opensongtablet.songprocessing;

import android.annotation.SuppressLint;
import android.content.Context;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.garethevans.church.opensongtablet.R;
import com.garethevans.church.opensongtablet.interfaces.MainActivityInterface;
import org.json.JSONArray;
import org.json.JSONObject;

/** Primary text view backed by the identical HTML, font and JS/CSS used by OpenSongWeb. */
public final class UnifiedSongView {
    private UnifiedSongView() { }
    private static String color(int color) { return String.format(java.util.Locale.ROOT, "#%06X", color & 0xffffff); }

    @SuppressLint("SetJavaScriptEnabled")
    public static WebView create(Context context, MainActivityInterface main) {
        Song song = main.getSong();
        String lyrics = song.getLyrics();
        if (lyrics == null) lyrics = "";
        String chordPro = main.getConvertChoPro().fromOpenSongToChordPro(lyrics);
        JSONObject data = new JSONObject();
        try {
            data.put("title", song.getTitle());
            data.put("content", chordPro);
            data.put("author", song.getAuthor());
            data.put("key", song.getKey());
            data.put("fontSize", 20);
            JSONObject labels = new JSONObject();
            labels.put("chords", context.getString(R.string.chords));
            labels.put("melody", context.getString(R.string.song_view_melody));
            labels.put("size", context.getString(R.string.song_view_size));
            labels.put("viewOptions", context.getString(R.string.song_view_options));
            labels.put("done", context.getString(R.string.song_view_done));
            labels.put("modes", new JSONArray(new String[]{context.getString(R.string.song_view_inline),
                    context.getString(R.string.song_view_hidden), context.getString(R.string.song_view_separate)}));
            data.put("labels", labels);
        } catch (org.json.JSONException error) { throw new IllegalStateException(error); }
        WebView web = new WebView(context);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        boolean allowZoom = main.getPreferences().getMyPreferenceBoolean("allowPinchToZoom", true);
        web.getSettings().setSupportZoom(allowZoom);
        web.getSettings().setBuiltInZoomControls(allowZoom);
        web.getSettings().setDisplayZoomControls(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setBlockNetworkLoads(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return true; }
            @Override public void onPageFinished(WebView view, String url) {
                if (url.equals("file:///android_asset/song-view/index.html"))
                    view.evaluateJavascript("SongView.load(" + data + ")", null);
            }
        });
        web.loadUrl("file:///android_asset/song-view/index.html");
        return web;
    }
}
