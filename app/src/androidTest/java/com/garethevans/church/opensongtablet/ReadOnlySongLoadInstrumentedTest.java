package com.garethevans.church.opensongtablet;

import android.net.Uri;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.garethevans.church.opensongtablet.filemanagement.DefaultSongsInstaller;
import com.garethevans.church.opensongtablet.filemanagement.StorageAccess;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import com.garethevans.church.opensongtablet.songprocessing.Song;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReadOnlySongLoadInstrumentedTest {
    @Rule public ActivityScenarioRule<MainActivity> rule = new ActivityScenarioRule<>(MainActivity.class);

    /** Real private fixture files, isolated from SAF permissions and the user's library. */
    private static class FixtureStorage extends StorageAccess {
        final File root;
        final ArrayList<String> files = new ArrayList<>();
        int sourceWrites;
        FixtureStorage(MainActivity activity, File root) { super(activity); this.root=root; }
        @Override public Uri getUriForItem(String folder,String subfolder,String filename) {
            File dir = new File(root,folder == null ? "" : folder);
            if (subfolder != null && !subfolder.isEmpty()) dir = new File(dir,subfolder);
            return Uri.fromFile(filename == null || filename.isEmpty() ? dir : new File(dir,filename));
        }
        @Override public boolean uriExists(Uri uri) { return uri != null && new File(uri.getPath()).exists(); }
        @Override public boolean uriIsFile(Uri uri) { return uriExists(uri); }
        @Override public InputStream getInputStream(Uri uri) {
            try { return new FileInputStream(new File(uri.getPath())); }
            catch (Exception e) { return null; }
        }
        @Override public OutputStream getOutputStream(Uri uri) {
            if (uri.getPath().contains("/Songs/")) sourceWrites++;
            try { return new FileOutputStream(new File(uri.getPath())); }
            catch (Exception e) { return null; }
        }
        @Override public boolean writeFileFromString(String folder,String subfolder,String filename,String text,boolean append) {
            if ("Songs".equals(folder)) sourceWrites++;
            return super.writeFileFromString(folder,subfolder,filename,text,append);
        }
        @Override public ArrayList<String> listSongs(boolean all) { return new ArrayList<>(files); }
        @Override public boolean deleteFile(Uri uri) {
            if (uri != null && uri.getPath().contains("/Songs/")) sourceWrites++;
            return uri != null && new File(uri.getPath()).delete();
        }
        @Override public boolean deleteFolder(String folder,String subfolder) {
            if ("Songs".equals(folder)) sourceWrites++;
            return new File(getUriForItem(folder,subfolder,"").getPath()).delete();
        }
    }

    private byte[] read(MainActivity activity, Uri uri) throws Exception {
        try (InputStream in = activity.getStorageAccess().getInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024]; int size;
            while ((size = in.read(buffer)) != -1) out.write(buffer,0,size);
            return out.toByteArray();
        }
    }

    @Test public void loadingAndIndexingPreserveSourcesAndLibraryUpdatesDoNotOverwrite() throws Exception {
        AtomicReference<MainActivity> reference = new AtomicReference<>();
        rule.getScenario().onActivity(reference::set);
        MainActivity activity = reference.get();
        String folder = "__read_only_test_" + UUID.randomUUID();
        File root = new File(activity.getCacheDir(),folder);
        File sourceDir = new File(new File(root,"Songs"),folder);
        assertTrue(sourceDir.mkdirs());
        new File(root,"Settings").mkdirs();
        FixtureStorage storage = new FixtureStorage(activity,root);
        java.lang.reflect.Field storageField = MainActivity.class.getDeclaredField("storageAccess");
        storageField.setAccessible(true);
        StorageAccess originalStorage = activity.getStorageAccess();
        storageField.set(activity,storage);
        Map<String,String> sources = new LinkedHashMap<>();
        sources.put("Fuente.cho","{title: Fuente}\r\n[Rem-Lam]Mi letra  exacta\r\n{comment: melodia: <do>Mi letra  exacta}\r\n");
        sources.put("Fuente.onsong","Title: Fuente\nArtist: Autor\n[C]Mi letra\n");
        sources.put("Fuente.txt","Mi letra  exacta\r\nSegunda línea\r\n");
        sources.put("Fuente.xml","<song><title>Fuente</title><author>Autor & otro</author><lyrics> Mi letra</lyrics></song>");
        int installed = activity.getPreferences().getMyPreferenceInt("defaultSongsVersionCode",0);
        try {
            for (Map.Entry<String,String> source : sources.entrySet()) {
                try (FileOutputStream out = new FileOutputStream(new File(sourceDir,source.getKey()))) {
                    out.write(source.getValue().getBytes(StandardCharsets.UTF_8));
                }
                storage.files.add(folder+"/"+source.getKey());
                Uri uri = activity.getStorageAccess().getUriForItem("Songs",folder,source.getKey());
                byte[] before = read(activity,uri);
                for (int pass=0;pass<2;pass++) {
                    Song song = new Song();song.setFolder(folder);song.setFilename(source.getKey());
                    Song loaded = activity.getLoadSong().doLoadSongFile(song,true);
                    assertEquals(source.getKey(),loaded.getFilename());
                    assertEquals(folder,loaded.getFolder());
                    assertTrue(loaded.shouldShowLyrics());
                    assertArrayEquals(source.getKey(),before,read(activity,uri));
                }
                assertArrayEquals(source.getValue().getBytes(StandardCharsets.UTF_8),read(activity,uri));
            }
            // Exercise the actual index pipeline, including UUID/date enrichment.
            for (String filename : sources.keySet()) activity.getSQLiteHelper().createSong(folder,filename);
            activity.getSongListBuildIndex().setFullIndexRequired(true);
            activity.getSongListBuildIndex().setCheckForUUIDLastMod(true);
            activity.getSongListBuildIndex().fullIndex(null,folder);
            for (Map.Entry<String,String> source : sources.entrySet())
                assertArrayEquals(source.getKey(), source.getValue().getBytes(StandardCharsets.UTF_8),
                        read(activity,activity.getStorageAccess().getUriForItem("Songs",folder,source.getKey())));
            assertTrue(activity.getStorageAccess().badFileExtension("referencia.html"));
            assertTrue(activity.getStorageAccess().badFileExtension("referencia.web"));
            assertFalse(activity.getStorageAccess().badFileExtension(folder+"/Fuente.txt"));
            assertTrue(activity.getStorageAccess().isSpecificFileExtension("image","imagen.webp"));
            assertFalse(activity.getAlertChecks().showUpdateInfo());
            activity.getPreferences().setMyPreferenceInt("defaultSongsVersionCode",0);
            DefaultSongsInstaller installer = new DefaultSongsInstaller(activity,activity);
            assertNull(installer.installIfNeeded());
            assertFalse(installer.wasInstalled());
            for (Map.Entry<String,String> source : sources.entrySet())
                assertArrayEquals(source.getValue().getBytes(StandardCharsets.UTF_8),read(activity,
                        activity.getStorageAccess().getUriForItem("Songs",folder,source.getKey())));
            assertEquals("Read/index/install attempted a source write",0,storage.sourceWrites);
        } finally {
            activity.getPreferences().setMyPreferenceInt("defaultSongsVersionCode",installed);
            storageField.set(activity,originalStorage);
            for (String filename : sources.keySet()) {
                new File(sourceDir,filename).delete();
                activity.getSQLiteHelper().deleteSong(folder,filename);
            }
            sourceDir.delete();
            new File(root,"Songs").delete();
            File settings = new File(root,"Settings");
            File[] logs = settings.listFiles();
            if (logs != null) for (File log : logs) log.delete();
            settings.delete(); root.delete();
        }
    }
}
