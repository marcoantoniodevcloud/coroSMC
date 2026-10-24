package com.garethevans.church.opensongtablet.songprocessing;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.garethevans.church.opensongtablet.R;
import com.garethevans.church.opensongtablet.interfaces.MainActivityInterface;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** File-preserving batch action; the menu can gain more correction actions later. */
public final class SongCorrections {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private SongCorrections() { }

    public static void show(Context context, MainActivityInterface main) {
        new AlertDialog.Builder(context).setTitle(R.string.correct_all_songs)
                .setItems(new String[]{context.getString(R.string.correct_american_chords)}, (dialog, which) ->
                        new AlertDialog.Builder(context).setTitle(R.string.correct_american_chords)
                                .setMessage(R.string.correct_all_description)
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(R.string.correct_all_songs, (d, w) -> run(context, main)).show()).show();
    }

    private static String read(MainActivityInterface main, Uri uri) throws Exception {
        try (InputStream stream = main.getStorageAccess().getInputStream(uri)) {
            if (stream == null) throw new java.io.IOException("Cannot read file");
            InputStreamReader reader = new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8);
            StringBuilder text = new StringBuilder(); char[] buffer = new char[4096]; int n;
            while ((n = reader.read(buffer)) != -1) {
                text.append(buffer, 0, n);
                if (text.length() > 1_000_000) throw new java.io.IOException("File too large");
            }
            return text.toString();
        }
    }

    private static void run(Context context, MainActivityInterface main) {
        if (!RUNNING.compareAndSet(false, true)) return;
        AtomicBoolean stop = new AtomicBoolean(); Handler handler = new Handler(Looper.getMainLooper());
        ProgressDialog progress = new ProgressDialog(context);
        progress.setTitle(R.string.correct_all_songs); progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setCancelable(false);
        progress.setButton(ProgressDialog.BUTTON_NEGATIVE, context.getString(R.string.stop_correction), (d, w) -> stop.set(true));
        progress.setOnDismissListener(d -> stop.set(true)); progress.show();
        main.getThreadPoolExecutor().execute(() -> {
            int changed = 0, unchanged = 0, failed = 0;
            ArrayList<String> errors = new ArrayList<>();
            try {
                ArrayList<String> files = main.getStorageAccess().listSongs(false);
                handler.post(() -> progress.setMax(files.size()));
                for (int i = 0; i < files.size() && !stop.get(); i++) {
                    String id = files.get(i); if (id == null || id.endsWith("/")) continue;
                    int slash = id.lastIndexOf('/'); String folder = slash < 0 ? "" : id.substring(0, slash);
                    String name = id.substring(slash + 1), lower = name.toLowerCase(Locale.ROOT);
                    if (name.contains(".") && !lower.matches(".*\\.(xml|cho|pro|chopro|chordpro|crd)$")) continue;
                    int count = i + 1; handler.post(() -> { progress.setProgress(count); progress.setMessage(id); });
                    try {
                        Uri uri = main.getStorageAccess().getUriForItem("Songs", folder, name);
                        String before = read(main, uri);
                        String after = ChordNotationCorrector.correctFile(name, before);
                        if (before.equals(after)) { unchanged++; continue; }
                        if (!main.getStorageAccess().writeFileFromString("Backups", "Corrections",
                                UUID.randomUUID() + "." + name + ".bak", before, false)) {
                            throw new java.io.IOException(context.getString(R.string.correction_backup_failed));
                        }
                        if (!before.equals(read(main, uri))) throw new java.io.IOException(context.getString(R.string.correction_file_changed));
                        if (!main.getStorageAccess().writeFileFromString("Songs", folder, name, after, false)) {
                            // Best effort restore after a failed provider write; the backup also remains.
                            main.getStorageAccess().writeFileFromString("Songs", folder, name, before, false);
                            throw new java.io.IOException(context.getString(R.string.correction_write_failed));
                        }
                        Song song = main.getSQLiteHelper().getSpecificSong(folder, name);
                        song.setLyrics(ChordNotationCorrector.convert(song.getLyrics()));
                        song.setKey(ChordNotationCorrector.chord(song.getKey() == null ? "" : song.getKey()));
                        main.getSQLiteHelper().updateSong(song);
                        if (main.getSong() != null && folder.equals(main.getSong().getFolder()) && name.equals(main.getSong().getFilename())) {
                            main.getSong().setLyrics(song.getLyrics()); main.getSong().setKey(song.getKey());
                        }
                        changed++;
                    } catch (Exception e) { failed++; errors.add(id + ": " + e.getMessage()); }
                }
            } catch (Exception e) { failed++; errors.add(e.getMessage()); }
            finally {
                RUNNING.set(false);
                String summary = context.getString(R.string.correction_result, changed, unchanged, failed)
                        + (stop.get() ? "\n" + context.getString(R.string.correction_stopped) : "")
                        + (errors.isEmpty() ? "" : "\n\n" + android.text.TextUtils.join("\n", errors));
                handler.post(() -> {
                    progress.dismiss(); main.getStorageAccess().setDatabaseLastUpdate(0);
                    main.updateSongList();
                    if (main.getSongMenuFragment() != null) main.getSongMenuFragment().refreshSongListDisplay();
                    new AlertDialog.Builder(context).setTitle(R.string.correct_all_songs).setMessage(summary)
                            .setPositiveButton(android.R.string.ok, null).show();
                });
            }
        });
    }

    public static boolean backupExisting(MainActivityInterface main, String folder, String name) {
        Uri uri = main.getStorageAccess().getUriForItem("Songs", folder, name);
        if (!main.getStorageAccess().uriExists(uri)) return true;
        try {
            return main.getStorageAccess().writeFileFromString("Backups", "OCR",
                    UUID.randomUUID() + "." + name + ".bak", read(main, uri), false);
        } catch (Exception e) { return false; }
    }
}
