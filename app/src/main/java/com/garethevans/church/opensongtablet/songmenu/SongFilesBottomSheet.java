package com.garethevans.church.opensongtablet.songmenu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.garethevans.church.opensongtablet.R;
import com.garethevans.church.opensongtablet.customviews.BottomSheetCommon;
import com.garethevans.church.opensongtablet.databinding.BottomSheetSongFilesBinding;
import com.garethevans.church.opensongtablet.interfaces.MainActivityInterface;
import com.garethevans.church.opensongtablet.preferences.AreYouSureBottomSheet;

import java.util.ArrayList;
import java.util.Locale;

public class SongFilesBottomSheet extends BottomSheetCommon {

    private static final String ARG_FOLDER = "song_folder";
    private static final String ARG_FILENAME = "song_filename";
    private static final String TAG = "SongFilesBottomSheet";

    private MainActivityInterface mainActivityInterface;
    private BottomSheetSongFilesBinding binding;
    private ActivityResultLauncher<Intent> imagePicker;

    public SongFilesBottomSheet() {
    }

    public static SongFilesBottomSheet newInstance(String folder, String filename) {
        SongFilesBottomSheet sheet = new SongFilesBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_FOLDER, folder);
        args.putString(ARG_FILENAME, filename);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mainActivityInterface = (MainActivityInterface) context;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        imagePicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri sourceUri = result.getData().getData();
                if (sourceUri != null) {
                    attachImage(sourceUri);
                }
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetSongFilesBinding.inflate(inflater, container, false);
        binding.dialogHeading.setClose(this);
        String folder = getFolder();
        String filename = getFilename();
        binding.songFileIdentity.setText(folder + "/" + filename);
        binding.attachImage.setVisibility(isTextSong(filename) ? View.VISIBLE : View.GONE);
        binding.attachImage.setOnClickListener(v -> chooseImage());
        binding.deleteSongFile.setOnClickListener(v -> chooseFileToDelete());
        return binding.getRoot();
    }

    private String getFolder() {
        return getArguments() == null ? "" : getArguments().getString(ARG_FOLDER, "");
    }

    private String getFilename() {
        return getArguments() == null ? "" : getArguments().getString(ARG_FILENAME, "");
    }

    private void chooseFileToDelete() {
        ArrayList<String> relatedFiles = findRelatedFiles(getFolder(), getFilename());
        if (relatedFiles.isEmpty()) {
            mainActivityInterface.getShowToast().doIt(getString(R.string.no_song_files_found));
            return;
        }
        String[] choices = relatedFiles.toArray(new String[0]);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_song_file)
                .setItems(choices, (dialog, which) -> confirmDelete(relatedFiles.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDelete(String filename) {
        ArrayList<String> arguments = new ArrayList<>();
        arguments.add("Songs");
        arguments.add(getFolder());
        arguments.add(filename);
        AreYouSureBottomSheet confirm = new AreYouSureBottomSheet(
                "deleteItem",
                getString(R.string.delete_song_file_warning, filename),
                arguments,
                null,
                null,
                null
        );
        dismiss();
        confirm.show(mainActivityInterface.getMyFragmentManager(), "deleteSongFile");
    }

    private ArrayList<String> findRelatedFiles(String folder, String filename) {
        ArrayList<String> relatedFiles = new ArrayList<>();
        String requestedFolder = normalizedFolder(folder);
        String requestedName = SongReviewKey.create("", filename, "").substring(1);
        for (String relativePath : mainActivityInterface.getStorageAccess().listSongs(true)) {
            if (relativePath == null || relativePath.endsWith("/")) {
                continue;
            }
            int separator = relativePath.lastIndexOf('/');
            String candidateFolder = separator < 0 ? "" : relativePath.substring(0, separator);
            String candidateName = separator < 0 ? relativePath : relativePath.substring(separator + 1);
            if (normalizedFolder(candidateFolder).equals(requestedFolder)
                    && SongReviewKey.create("", candidateName, "").substring(1).equals(requestedName)
                    && isSongOrMediaFile(candidateName)) {
                relatedFiles.add(candidateName);
            }
        }
        return relatedFiles;
    }

    private String normalizedFolder(String folder) {
        String mainFolder = mainActivityInterface.getMainfoldername();
        if (folder == null || folder.isEmpty() || folder.equalsIgnoreCase("MAIN")
                || folder.equalsIgnoreCase(mainFolder)) {
            return SongReviewKey.create(mainFolder, "", "");
        }
        return SongReviewKey.create(folder, "", "");
    }

    private boolean isSongOrMediaFile(String filename) {
        return mainActivityInterface.getStorageAccess().filenameIsImage(filename)
                || filename.toLowerCase(Locale.ROOT).endsWith(".pdf")
                || filename.toLowerCase(Locale.ROOT).endsWith(".xml")
                || mainActivityInterface.getStorageAccess().isSpecificFileExtension("chordpro", filename)
                || !filename.contains(".");
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        imagePicker.launch(intent);
    }

    private void attachImage(Uri sourceUri) {
        String sourceName = mainActivityInterface.getStorageAccess().getFileNameFromUri(sourceUri);
        String extension = imageExtension(sourceName);
        String mimeType = requireContext().getContentResolver().getType(sourceUri);
        if (extension.isEmpty() && mimeType != null) {
            switch (mimeType.toLowerCase(Locale.ROOT)) {
                case "image/jpeg":
                    extension = ".jpg";
                    break;
                case "image/png":
                    extension = ".png";
                    break;
                case "image/gif":
                    extension = ".gif";
                    break;
                case "image/webp":
                    extension = ".webp";
                    break;
                case "image/bmp":
                    extension = ".bmp";
                    break;
                case "image/heic":
                    extension = ".heic";
                    break;
                case "image/heif":
                    extension = ".heif";
                    break;
            }
        }
        if (extension.isEmpty()) {
            mainActivityInterface.getShowToast().doIt(getString(R.string.image_attach_failed));
            return;
        }
        String baseName = SongReviewKey.fileBaseName(getFilename());
        String destinationName = baseName + extension;
        String folder = getFolder();
        Uri destinationUri = mainActivityInterface.getStorageAccess()
                .getUriForItem("Songs", folder, destinationName);
        if (mainActivityInterface.getStorageAccess().uriExists(destinationUri)) {
            mainActivityInterface.getShowToast().doIt(getString(R.string.image_already_attached));
            return;
        }

        if (mimeType == null) {
            mimeType = mimeTypeForExtension(extension);
        }
        mainActivityInterface.getStorageAccess().createFile(
                mimeType, "Songs", folder, destinationName);
        destinationUri = mainActivityInterface.getStorageAccess()
                .getUriForItem("Songs", folder, destinationName);
        boolean copied = mainActivityInterface.getStorageAccess().uriExists(destinationUri)
                && mainActivityInterface.getStorageAccess().copyUriToUri(sourceUri, destinationUri);
        if (copied) {
            mainActivityInterface.getStorageAccess().updateFileActivityLog(
                    TAG + " attached image to Songs/" + folder + "/" + destinationName);
            mainActivityInterface.getShowToast().doIt(getString(R.string.image_attached));
            mainActivityInterface.updateSongMenu(null, null, null);
            dismiss();
        } else {
            if (mainActivityInterface.getStorageAccess().uriExists(destinationUri)) {
                mainActivityInterface.getStorageAccess().deleteFile(destinationUri);
            }
            mainActivityInterface.getShowToast().doIt(getString(R.string.image_attach_failed));
        }
    }

    private String imageExtension(String filename) {
        if (filename == null) {
            return "";
        }

        String lowerName = filename.toLowerCase(Locale.ROOT);
        String[] imageExtensions = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".heic", ".heif"};
        for (String extension : imageExtensions) {
            if (lowerName.endsWith(extension)) {
                return extension;
            }
        }
        return "";
    }

    private String mimeTypeForExtension(String extension) {
        switch (extension) {
            case ".png":
                return "image/png";
            case ".gif":
                return "image/gif";
            case ".bmp":
                return "image/bmp";
            case ".webp":
                return "image/webp";
            case ".heic":
                return "image/heic";
            case ".heif":
                return "image/heif";
            default:
                return "image/jpeg";
        }
    }

    private boolean isTextSong(String filename) {
        String lowerName = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".xml")
                || mainActivityInterface.getStorageAccess().isSpecificFileExtension("chordpro", filename)
                || !lowerName.contains(".");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
