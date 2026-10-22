package com.garethevans.church.opensongtablet.filemanagement;

import android.content.res.AssetManager;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.garethevans.church.opensongtablet.BuildConfig;
import com.garethevans.church.opensongtablet.interfaces.MainActivityInterface;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DefaultSongsInstaller {

    private static final String TAG = "DefaultSongsInstaller";
    private static final String ASSET_ROOT = "DefaultSongs";
    private final Context context;
    private final MainActivityInterface mainActivityInterface;
    private boolean installed;

    public DefaultSongsInstaller(Context context, MainActivityInterface mainActivityInterface) {
        this.context = context.getApplicationContext();
        this.mainActivityInterface = mainActivityInterface;
    }

    public String installIfNeeded() {
        int installedVersion = mainActivityInterface.getPreferences()
                .getMyPreferenceInt("defaultSongsVersionCode", 0);
        if (installedVersion >= BuildConfig.VERSION_CODE) {
            return null;
        }

        try {
            copyDirectory(ASSET_ROOT, "");
            Uri oldBenditoLyrics = mainActivityInterface.getStorageAccess()
                    .getUriForItem("Songs", "adoracion", "Bendito bendito.xml");
            if (mainActivityInterface.getStorageAccess().uriExists(oldBenditoLyrics) &&
                    !mainActivityInterface.getStorageAccess().deleteFile(oldBenditoLyrics)) {
                throw new IOException("Unable to replace the old Bendito bendito XML companion");
            }
            if (!mainActivityInterface.getStorageAccess().deleteFolder("Songs", "OpenSongLibrary")) {
                throw new IOException("Unable to remove old Songs/OpenSongLibrary folder");
            }
            String currentFolder = mainActivityInterface.getSong().getFolder();
            if (currentFolder != null && (currentFolder.equals("OpenSongLibrary") ||
                    currentFolder.startsWith("OpenSongLibrary/"))) {
                mainActivityInterface.getPreferences().setMyPreferenceBoolean("songLoadSuccess", false);
            }
            mainActivityInterface.getPreferences().setMyPreferenceInt(
                    "defaultSongsVersionCode", BuildConfig.VERSION_CODE);
            installed = true;
            return null;
        } catch (IOException e) {
            Log.e(TAG, "Unable to install bundled songs", e);
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    public boolean wasInstalled() {
        return installed;
    }

    private void copyDirectory(String assetPath, String destinationPath) throws IOException {
        AssetManager assets = context.getAssets();
        String[] children = assets.list(assetPath);
        if (children == null || children.length == 0) {
            copyFile(assetPath, destinationPath);
            return;
        }

        for (String child : children) {
            String childAssetPath = assetPath + "/" + child;
            String childDestinationPath = destinationPath.isEmpty()
                    ? child : destinationPath + "/" + child;
            copyDirectory(childAssetPath, childDestinationPath);
        }
    }

    private void copyFile(String assetPath, String destinationPath) throws IOException {
        int separator = destinationPath.lastIndexOf('/');
        String folder = separator < 0 ? "" : destinationPath.substring(0, separator);
        String filename = separator < 0 ? destinationPath : destinationPath.substring(separator + 1);
        Uri destination = mainActivityInterface.getStorageAccess()
                .makeSureFileIsRegistered("Songs", folder, filename, true);
        if (destination == null) {
            throw new IOException("Unable to create Songs/" + destinationPath);
        }

        try (InputStream input = context.getAssets().open(assetPath);
             OutputStream output = mainActivityInterface.getStorageAccess().getOutputStream(destination)) {
            if (output == null) {
                throw new IOException("Unable to open Songs/" + destinationPath + " for writing");
            }
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
            output.flush();
        }
    }
}
