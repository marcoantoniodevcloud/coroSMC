package com.garethevans.church.opensongtablet.songprocessing;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import com.garethevans.church.opensongtablet.R;
import com.garethevans.church.opensongtablet.interfaces.MainActivityInterface;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only view backed by the identical JS/CSS assets used by OpenSongWeb. */
public final class UnifiedSongView {
    private UnifiedSongView() { }
    private static String color(int color) { return String.format(java.util.Locale.ROOT, "#%06X", color & 0xffffff); }

    @SuppressLint("SetJavaScriptEnabled")
    public static Dialog show(Context context, MainActivityInterface main) {
        Song song = main.getSong();
        String lyrics = song.getLyrics();
        if (lyrics == null) lyrics = "";
        String chordPro = main.getConvertChoPro().fromOpenSongToChordPro(lyrics);
        JSONObject data = new JSONObject();
        try {
            data.put("title", song.getTitle());
            data.put("content", chordPro);
            data.put("fontSize", main.getPreferences().getMyPreferenceFloat("fontSize", 20f));
            JSONObject theme = new JSONObject();
            theme.put("--song-background", color(main.getMyThemeColors().getLyricsBackgroundColor()));
            theme.put("--song-text", color(main.getMyThemeColors().getLyricsTextColor()));
            theme.put("--song-chord", color(main.getMyThemeColors().getLyricsChordsColor()));
            theme.put("--song-melody", color(main.getMyThemeColors().getLyricsCommentColor()));
            data.put("theme", theme);
            JSONObject labels = new JSONObject();
            labels.put("chords", context.getString(R.string.chords));
            labels.put("melody", context.getString(R.string.song_view_melody));
            labels.put("size", context.getString(R.string.song_view_size));
            labels.put("modes", new JSONArray(new String[]{context.getString(R.string.song_view_inline),
                    context.getString(R.string.song_view_hidden), context.getString(R.string.song_view_separate)}));
            data.put("labels", labels);
        } catch (org.json.JSONException error) { throw new IllegalStateException(error); }
        Dialog dialog = new Dialog(context);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        Button close = new Button(context);
        close.setText(R.string.close);
        close.setOnClickListener(view -> dialog.dismiss());
        layout.addView(close, new LinearLayout.LayoutParams(-1, -2));
        WebView web = new WebView(context);
        web.setBackgroundColor(main.getMyThemeColors().getLyricsBackgroundColor());
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setBlockNetworkLoads(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return true; }
            @Override public void onPageFinished(WebView view, String url) {
                if (dialog.isShowing() && url.equals("file:///android_asset/song-view/index.html"))
                    view.evaluateJavascript("SongView.load(" + data + ")", null);
            }
        });
        layout.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setContentView(layout);
        dialog.setOnDismissListener(ignored -> { layout.removeView(web); web.destroy(); });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        web.loadUrl("file:///android_asset/song-view/index.html");
        return dialog;
    }
}
