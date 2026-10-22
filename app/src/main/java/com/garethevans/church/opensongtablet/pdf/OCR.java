package com.garethevans.church.opensongtablet.pdf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import androidx.annotation.RequiresApi;

import com.garethevans.church.opensongtablet.interfaces.MainActivityInterface;
import com.garethevans.church.opensongtablet.songprocessing.Song;
import com.google.android.gms.tasks.Task;
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
    private final String TAG = "OCR", blockSplitStart = "__BLOCKSTART_", blockSplitEnd = "_BLOCKEND_",
            blockSplitStartFinal = "_BLOCKSTARTFINAL__", blockSplitEndFinal = "_BLOCKENDFINAL__";
    private int pageHeightRunning = 0, maxTop = 0, totalLineHeight = 0, totalLineCount = 0;
    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor pdfDescriptor;
    private int nextPdfPage;
    private boolean currentFileFailed;
    private BatchOCRListener batchOCRListener;
    private final ArrayList<String> batchFiles = new ArrayList<>();
    private final ArrayList<String> batchErrors = new ArrayList<>();
    private int batchFileIndex, batchSucceeded, batchSkipped, batchFailed;

    public OCR(Context c) {
        mainActivityInterface = (MainActivityInterface) c;
    }
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void getTextFromPDF(String folder, String filename, boolean useCropped) {
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
        pdfPages = new ArrayList<>();
        pageHeightRunning = 0;
        maxTop = 0;
        totalLineHeight = 0;
        totalLineCount = 0;
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
        int pageHeightToAdd = (page) * bmp.getHeight();
        maxTop = pageHeightToAdd; // 0 for first page
        final int currpage = page;
        InputImage image = InputImage.fromBitmap(bmp, 0);
        TextRecognizer textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        Task<Text> task = textRecognizer.process(image);
        task.addOnSuccessListener(text -> {
            // Use an array where the index is the top position
            SparseArray<String> stringSparseArray = new SparseArray<>();

            for (Text.TextBlock block : text.getTextBlocks()) {
                for (Text.Line line : block.getLines()) {
                    String lineText = line.getText();
                    Rect lineFrame = line.getBoundingBox();
                    int top = pageHeightToAdd;
                    int left = 0;
                    int right = 0;
                    if (lineFrame != null) {
                        int currentTop = lineFrame.top + pageHeightToAdd;
                        left = lineFrame.left;
                        right = lineFrame.right;

                        // 1. Determine if THIS block is a chord or lyric before merging
                        boolean currentIsChord = mainActivityInterface.getConvertTextSong().convertText(lineText).startsWith(".");

                        int targetKey = currentTop;
                        int fudge = 10;

                        for (int i = 0; i < stringSparseArray.size(); i++) {
                            int existingTop = stringSparseArray.keyAt(i);
                            if (Math.abs(existingTop - currentTop) < fudge) {

                                // 2. CHECK: Only merge if the existing line is the same type
                                // We use the first character of the existing line to check ('.' for chords, ' ' for lyrics)
                                String existingText = stringSparseArray.get(existingTop);
                                boolean existingIsChord = existingText.contains(blockSplitEndFinal + ".");

                                if (currentIsChord == existingIsChord) {
                                    targetKey = existingTop;
                                    break;
                                }
                            }
                        }
                        top = targetKey;
                    }

                    String currLine = stringSparseArray.get(top, "");
                    String newLine = (currLine+blockSplitStart+left+blockSplitStartFinal+blockSplitEnd+right+blockSplitEndFinal+lineText);
                    Log.d(TAG,"top:"+top+"  newLine:"+newLine);
                    stringSparseArray.put(top,newLine);
                    maxTop = Math.max(maxTop,top);
                }
            }

            // Now we have the lines in the correct order (by top position)
            // Try to merge lines that have similar tops (within fudge)
            int lastTop = 0;
            // Work out the fudge factor based on average line height
            int fudge = 10;
            if (totalLineCount!=0) {
                float avHeight = (float)totalLineHeight/(float)totalLineCount;
                fudge = Math.round((0.5f*avHeight));
            }


            SparseArray<String> tidiedLines = new SparseArray<>();
            for (int x=0; x<maxTop+1; x++) {
                if (stringSparseArray.get(x,null)!=null) {
                    if (x<lastTop+fudge) {
                        // Merge to lastTop
                        String prevVal = tidiedLines.get(lastTop,"");
                        tidiedLines.put(lastTop,(prevVal+" "+stringSparseArray.get(x)).trim());
                    } else {
                        // New line
                        tidiedLines.put(x,stringSparseArray.get(x));
                        lastTop = x;
                    }
                }
            }

            StringBuilder textFromLinesArray = new StringBuilder();
            int minHPos = 1000;

            for (int x=0; x<maxTop+1; x++) {
                StringBuilder thisLine = new StringBuilder();
                try {
                    if (tidiedLines.get(x, null) != null) {
                        // This line has each section with the left position inside
                        // We need to do this to add them back in the correct order
                        String[] blocks = tidiedLines.get(x).split(blockSplitStart);
                        SparseArray<String> horizontalArray = new SparseArray<>();
                        SparseIntArray horizontalEndPosArray = new SparseIntArray();
                        int maxHPos = 0;
                        for (String block : blocks) {
                            // Get the horizontal pos
                            if (block.contains(blockSplitEnd)) {
                                String startHPos_String = block.substring(0, block.indexOf(blockSplitStartFinal)).replace(blockSplitStart, "").replace(blockSplitStartFinal, "").replaceAll("\\D", "");
                                String endHPos_String = block.substring(block.indexOf(blockSplitEnd), block.indexOf(blockSplitEndFinal)).replace(blockSplitEnd, "").replace(blockSplitEndFinal, "").replaceAll("\\D", "");
                                int hposStart = 0;
                                int hposEnd = 0;
                                if (!startHPos_String.isEmpty()) {
                                    hposStart = Integer.parseInt(startHPos_String);
                                    if (hposStart > -1 && hposStart < minHPos) {
                                        minHPos = hposStart;
                                    }
                                }
                                if (!endHPos_String.isEmpty()) {
                                    hposEnd = Integer.parseInt(endHPos_String);
                                }

                                String blockText = block.substring(block.indexOf(blockSplitEndFinal) + blockSplitEndFinal.length());

                                horizontalArray.put(hposStart, blockText);
                                horizontalEndPosArray.put(hposStart, hposEnd);
                                maxHPos = Math.max(hposStart, maxHPos);

                            }
                        }

                        int lastHPosEnd = 0;

                        for (int z = 0; z < maxHPos + 1; z++) {
                            if (horizontalArray.get(z, null) != null) {
                                String textBlock = horizontalArray.get(z);
                                int endPos = horizontalEndPosArray.get(z);

                                if (z < minHPos) {
                                    minHPos = z;
                                }

                                int numChars = textBlock.length();

                                float spacePerChar;
                                if (endPos != 0) {
                                    spacePerChar = (endPos - z) / (float) numChars;
                                    // How far away from the last position are we starting?
                                    // This tries to guess extra spaces needed
                                    if (z > lastHPosEnd && lastHPosEnd >= minHPos) {
                                        float extraSpace = z - lastHPosEnd;
                                        int numberOfSpaces = Math.round(extraSpace / spacePerChar);
                                        StringBuilder stringBuilder = new StringBuilder();
                                        for (int y = 0; y < numberOfSpaces; y++) {
                                            stringBuilder.append(" ");
                                        }
                                        textBlock = stringBuilder + textBlock;
                                    }
                                }

                                lastHPosEnd = endPos;

                                thisLine.append(textBlock).append(" ");
                            }
                        }
                        Log.d(TAG, "thisLine:" + thisLine);
                        textFromLinesArray.append(thisLine).append("\n");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            try {
                pdfPages.add(currpage, textFromLinesArray.toString());
            } catch (Exception e) {
                Log.e(TAG, "Unable to store OCR output for page " + currpage, e);
                currentFileFailed = true;
            }
            pageHeightRunning = maxTop;
            finishBitmapRecognition(bmp, textRecognizer, onPageComplete);
        });
        task.addOnFailureListener(e -> {
            pdfPages.add(currpage,"");
            currentFileFailed = true;
            Log.e(TAG, "OCR failed on page " + currpage, e);
            finishBitmapRecognition(bmp, textRecognizer, onPageComplete);
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
        if (filename==null) {
            filename = mainActivityInterface.getSong().getFilename();
        }
        if (batchOCRListener != null) {
            finishBatchFile();
            return;
        }
        PDFExtractBottomSheet pdfExtractBottomSheet = new PDFExtractBottomSheet(pdfPages,filename);
        pdfExtractBottomSheet.show(mainActivityInterface.getMyFragmentManager(),"PDFExtractBottomSheet");
        filename = null;
    }

    public void startBatchOCR(List<String> songIds, BatchOCRListener listener) {
        if (batchOCRListener != null) {
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
        recognizedSong.setLyrics(mainActivityInterface.getConvertTextSong()
                .convertText(recognizedText.toString()));
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
