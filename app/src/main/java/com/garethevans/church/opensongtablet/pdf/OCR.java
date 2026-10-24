package com.garethevans.church.opensongtablet.pdf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.garethevans.church.opensongtablet.interfaces.MainActivityInterface;
import com.garethevans.church.opensongtablet.songprocessing.Song;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;

public class OCR {

    public interface BatchOCRListener {
        void onProgress(int completed, int total, String filename);
        void onComplete(int succeeded, int skipped, int failed, ArrayList<String> errors);
    }

    private ArrayList<String> pdfPages;
    private int pageCount;
    private String filename = null;
    private String folder = null;
    private final MainActivityInterface mainActivityInterface;
    private final String TAG = "OCR";
    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor pdfDescriptor;
    private int nextPdfPage;
    private boolean currentFileFailed;
    private boolean recognitionRunning;
    public boolean isBusy() { return recognitionRunning || batchOCRListener != null; }
    private BatchOCRListener batchOCRListener;
    private final ArrayList<String> batchFiles = new ArrayList<>();
    private final ArrayList<String> batchErrors = new ArrayList<>();
    private int batchFileIndex, batchSucceeded, batchSkipped, batchFailed;

    public OCR(Context c) {
        mainActivityInterface = (MainActivityInterface) c;
    }
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void getTextFromPDF(String folder, String filename, boolean useCropped) {
        if (recognitionRunning) return;
        resetRecognitionState();
        this.folder = folder;
        this.filename = filename;
        Uri uri = mainActivityInterface.getStorageAccess().getUriForItem("Songs",folder,filename);
        pdfDescriptor = mainActivityInterface.getProcessSong().getPDFParcelFileDescriptor(uri);
        pdfRenderer = mainActivityInterface.getProcessSong().getPDFRenderer(pdfDescriptor);
        pageCount = mainActivityInterface.getProcessSong().getPDFPageCount(pdfRenderer);
        nextPdfPage = 0;

        if (pdfDescriptor == null || pdfRenderer == null || pageCount <= 0) {
            currentFileFailed = true;
            closePDF();
            runCompleteTask();
        } else {
            processNextPDFPage(useCropped);
        }
    }

    public void getTextFromImageFile(String folder, String filename) {
        if (recognitionRunning) return;
        resetRecognitionState();
        this.folder = folder;
        this.filename = filename;
        pageCount = 1;
        Uri uri = mainActivityInterface.getStorageAccess().getUriForItem("Songs",folder,filename);
        Bitmap bitmap = mainActivityInterface.getProcessSong().getBitmapFromUri(uri,0,0);
        if (bitmap != null) {
            extractTextFromBitmap(bitmap, 0, this::runCompleteTask);
        } else {
            currentFileFailed = true;
            runCompleteTask();
        }
    }

    public void getTextFromImage(Bitmap bmp) {
        if (recognitionRunning) return;
        resetRecognitionState();
        pageCount = 1;
        filename = mainActivityInterface.getSong().getFilename();
        folder = mainActivityInterface.getSong().getFolder();
        if (bmp!=null) {
            extractTextFromBitmap(bmp, 0, this::runCompleteTask);
        } else {
            currentFileFailed = true;
            runCompleteTask();
        }
    }

    public void getTextFromCamera(Uri uri) {
        if (recognitionRunning) return;
        resetRecognitionState();
        filename = null;
        folder = null;
        pageCount = 1;
        Bitmap bmp = mainActivityInterface.getProcessSong().getBitmapFromUri(uri,0,0);
        if (bmp!=null) {
            extractTextFromBitmap(bmp, 0, this::runCompleteTask);
        } else {
            currentFileFailed = true;
            runCompleteTask();
        }
    }

    private void resetRecognitionState() {
        recognitionRunning = true;
        pdfPages = new ArrayList<>();
        currentFileFailed = false;
    }

    private void processNextPDFPage(boolean useCropped) {
        if (nextPdfPage >= pageCount) {
            closePDF();
            runCompleteTask();
            return;
        }

        int page = nextPdfPage++;
        PdfRenderer.Page currentPage = mainActivityInterface.getProcessSong().getPDFPage(pdfRenderer, page);
        if (currentPage == null) {
            currentFileFailed = true;
            pdfPages.add("");
            processNextPDFPage(useCropped);
            return;
        }

        Bitmap bitmap = null;
        try {
            ArrayList<Integer> pdfSize = mainActivityInterface.getProcessSong()
                    .getPDFPageSize(currentPage, useCropped);
            ArrayList<Integer> bitmapSize = mainActivityInterface.getProcessSong()
                    .getBitmapScaledSize(pdfSize, 1200, 1600, "Y");
            bitmap = mainActivityInterface.getProcessSong()
                    .createBitmapFromPage(bitmapSize, currentPage, false, useCropped);
        } catch (Exception e) {
            Log.e(TAG, "Unable to render PDF page " + page, e);
            currentFileFailed = true;
        } finally {
            currentPage.close();
        }

        if (bitmap == null) {
            currentFileFailed = true;
            pdfPages.add("");
            processNextPDFPage(useCropped);
        } else {
            extractTextFromBitmap(bitmap, page, () -> processNextPDFPage(useCropped));
        }
    }

    private void closePDF() {
        try {
            if (pdfRenderer != null) {
                pdfRenderer.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to close PDF renderer", e);
        } finally {
            pdfRenderer = null;
        }
        try {
            if (pdfDescriptor != null) {
                pdfDescriptor.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to close PDF descriptor", e);
        } finally {
            pdfDescriptor = null;
        }
    }

    private void extractTextFromBitmap(Bitmap bmp, int page, Runnable onPageComplete) {
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        recognizer.process(InputImage.fromBitmap(bmp, 0)).addOnSuccessListener(text -> {
            try {
                ArrayList<OcrChordLayout.Token> tokens = new ArrayList<>();
                for (Text.TextBlock block : text.getTextBlocks()) {
                    for (Text.Line line : block.getLines()) {
                        for (Text.Element element : line.getElements()) {
                            Rect box = element.getBoundingBox();
                            if (box == null) continue;
                            String[] words = element.getText().trim().split("\\s+");
                            int total = Math.max(1, element.getText().length()), offset = 0;
                            for (String word : words) {
                                float left = box.left + (float) offset / total * box.width();
                                offset += word.length();
                                float right = box.left + (float) offset / total * box.width();
                                tokens.add(new OcrChordLayout.Token(word, left, box.top, right, box.bottom));
                                offset++;
                            }
                        }
                    }
                }
                String result = OcrChordLayout.toChordPro(tokens, bmp.getWidth());
                if (result.trim().isEmpty()) currentFileFailed = true;
                pdfPages.add(result);
            } catch (Exception e) {
                currentFileFailed = true;
                Log.e(TAG, "Unable to align OCR chords on page " + page, e);
            } finally {
                finishBitmapRecognition(bmp, recognizer, onPageComplete);
            }
        }).addOnFailureListener(e -> {
            currentFileFailed = true;
            Log.e(TAG, "OCR recognition failed on page " + page, e);
            finishBitmapRecognition(bmp, recognizer, onPageComplete);
        });
    }

    private void finishBitmapRecognition(Bitmap bitmap, TextRecognizer textRecognizer, Runnable onPageComplete) {
        try {
            textRecognizer.close();
        } catch (Exception e) {
            Log.w(TAG, "Unable to close text recognizer", e);
        }
        if (!bitmap.isRecycled()) {
            bitmap.recycle();
        }
        onPageComplete.run();
    }

    private void runCompleteTask() {
        recognitionRunning = false;
        if (filename==null) {
            filename = mainActivityInterface.getSong().getFilename();
        }
        if (batchOCRListener != null) {
            finishBatchFile();
            return;
        }
        if (currentFileFailed) {
            mainActivityInterface.getShowToast().doIt(((Context) mainActivityInterface).getString(
                    com.garethevans.church.opensongtablet.R.string.ocr_no_text));
            filename = null;
            return;
        }
        PDFExtractBottomSheet pdfExtractBottomSheet = new PDFExtractBottomSheet(pdfPages,filename);
        pdfExtractBottomSheet.show(mainActivityInterface.getMyFragmentManager(),"PDFExtractBottomSheet");
        filename = null;
    }

    public void startBatchOCR(List<String> songIds, BatchOCRListener listener) {
        if (isBusy()) {
            throw new IllegalStateException("A batch OCR operation is already running");
        }
        batchOCRListener = listener;
        batchFiles.clear();
        batchErrors.clear();
        batchFileIndex = 0;
        batchSucceeded = 0;
        batchSkipped = 0;
        batchFailed = 0;

        for (String songId : songIds) {
            if (songId == null || songId.endsWith("/")) {
                continue;
            }
            int separator = songId.lastIndexOf('/');
            String itemFilename = separator < 0 ? songId : songId.substring(separator + 1);
            if (!mainActivityInterface.getStorageAccess()
                    .isSpecificFileExtension("imageorpdf", itemFilename)) {
                continue;
            }
            String itemFolder = separator < 0 ? "" : songId.substring(0, separator);
            if (hasLyricsCompanion(itemFolder, itemFilename)) {
                batchSkipped++;
            } else {
                batchFiles.add(songId);
            }
        }
        processNextBatchFile();
    }

    private void processNextBatchFile() {
        if (batchFileIndex >= batchFiles.size()) {
            BatchOCRListener listener = batchOCRListener;
            batchOCRListener = null;
            if (listener != null) {
                listener.onComplete(batchSucceeded, batchSkipped, batchFailed, new ArrayList<>(batchErrors));
            }
            return;
        }

        String songId = batchFiles.get(batchFileIndex);
        int separator = songId.lastIndexOf('/');
        folder = separator < 0 ? "" : songId.substring(0, separator);
        filename = separator < 0 ? songId : songId.substring(separator + 1);
        batchOCRListener.onProgress(batchFileIndex + 1, batchFiles.size(), filename);

        if (mainActivityInterface.getStorageAccess().isSpecificFileExtension("pdf", filename)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                failCurrentBatchFile("PDF OCR requires Android 5.0 or later");
            } else {
                getTextFromPDF(folder, filename, false);
            }
        } else {
            getTextFromImageFile(folder, filename);
        }
    }

    private void finishBatchFile() {
        StringBuilder recognizedText = new StringBuilder();
        if (pdfPages != null) {
            for (String page : pdfPages) {
                if (page != null && !page.trim().isEmpty()) {
                    recognizedText.append(page).append("\n\n");
                }
            }
        }

        if (currentFileFailed || recognizedText.toString().trim().isEmpty()) {
            failCurrentBatchFile(currentFileFailed
                    ? "OCR failed on one or more pages" : "No text was recognized");
            return;
        }

        String baseName = filename.contains(".")
                ? filename.substring(0, filename.lastIndexOf('.')) : filename;
        Song recognizedSong = new Song();
        recognizedSong.setFolder(folder);
        recognizedSong.setFilename(baseName);
        recognizedSong.setTitle(baseName);
        recognizedSong.setFiletype("XML");
        recognizedSong = mainActivityInterface.getConvertChoPro()
                .convertChordProLyricsToOpenSong(recognizedSong, recognizedText.toString());
        String xml = mainActivityInterface.getProcessSong().getXML(recognizedSong);
        if (xml == null || xml.trim().isEmpty() ||
                !mainActivityInterface.getStorageAccess().writeFileFromString(
                        "Songs", folder, baseName + ".xml", xml, false)) {
            failCurrentBatchFile("Unable to save the OCR result");
            return;
        }

        Song mediaSong = mainActivityInterface.getNonOpenSongSQLiteHelper()
                .getSpecificSong(folder, filename);
        mediaSong.setTitle(baseName);
        mediaSong.setLyrics(recognizedSong.getLyrics());
        mediaSong.setFiletype(mainActivityInterface.getStorageAccess()
                .isSpecificFileExtension("pdf", filename) ? "PDF" : "IMG");
        mainActivityInterface.getSQLiteHelper().updateSong(mediaSong);
        mainActivityInterface.getNonOpenSongSQLiteHelper().updateSong(mediaSong);

        com.garethevans.church.opensongtablet.songmenu.SongReviewStatus.setReviewed(
                (Context) mainActivityInterface, mediaSong, false);
        batchSucceeded++;
        advanceBatch();
    }

    private void failCurrentBatchFile(String error) {
        batchFailed++;
        batchErrors.add(filename + ": " + error);
        advanceBatch();
    }

    private void advanceBatch() {
        batchFileIndex++;
        processNextBatchFile();
    }

    private boolean hasLyricsCompanion(String folder, String mediaFilename) {
        String baseName = mediaFilename.contains(".")
                ? mediaFilename.substring(0, mediaFilename.lastIndexOf('.')) : mediaFilename;
        String[] extensions = {".xml", ".cho", ".crd", ".chopro", ".chordpro", ".pro"};
        for (String extension : extensions) {
            Uri companion = mainActivityInterface.getStorageAccess()
                    .getUriForItem("Songs", folder, baseName + extension);
            if (mainActivityInterface.getStorageAccess().uriExists(companion)) {
                return true;
            }
        }
        return false;
    }
}
