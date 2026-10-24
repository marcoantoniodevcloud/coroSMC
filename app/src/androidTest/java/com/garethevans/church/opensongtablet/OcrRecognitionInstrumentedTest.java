package com.garethevans.church.opensongtablet;

import android.graphics.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.garethevans.church.opensongtablet.pdf.OcrChordLayout;
import com.garethevans.church.opensongtablet.songmenu.SongReviewStatus;
import com.garethevans.church.opensongtablet.songprocessing.Song;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class OcrRecognitionInstrumentedTest {
    @Test public void detachedBootCallbacksAreIgnored() {
        com.garethevans.church.opensongtablet.appdata.BootUpFragment fragment =
                new com.garethevans.church.opensongtablet.appdata.BootUpFragment();
        fragment.startOrSetUp();
        fragment.startBootProcess(true,true);
    }
    @Test public void recognizesRealBitmapAndKeepsLyricsAndChordGeometry() throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(1000,400,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTextSize(48);
        canvas.drawText("Am",60,100,paint);canvas.drawText("G",360,100,paint);
        canvas.drawText("Mi alma canta",60,180,paint);
        TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        CountDownLatch done=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();
        AtomicReference<Exception> failure=new AtomicReference<>();
        recognizer.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener(text->{
            ArrayList<OcrChordLayout.Token> tokens=new ArrayList<>();
            for(Text.TextBlock block:text.getTextBlocks()) for(Text.Line line:block.getLines())
                for(Text.Element element:line.getElements()) {
                    Rect b=element.getBoundingBox();if(b!=null)tokens.add(new OcrChordLayout.Token(element.getText(),b.left,b.top,b.right,b.bottom));
                }
            result.set(OcrChordLayout.toChordPro(tokens,1000));done.countDown();
        }).addOnFailureListener(e->{failure.set(e);done.countDown();});
        try {
            assertTrue("ML Kit timed out",done.await(45,TimeUnit.SECONDS));
            assertNull(failure.get());assertNotNull(result.get());
            assertEquals("Mi alma canta",result.get().replaceAll("\\[[^]]*]", "").trim());
            assertTrue(result.get(),result.get().contains("[Am]"));assertTrue(result.get(),result.get().contains("[G]"));
        } finally {recognizer.close();bitmap.recycle();}
    }

    @Test public void reviewedStatePersistsAndOverridesAttention() {
        android.content.Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Song song=new Song();song.setFolder("_review_test_"+java.util.UUID.randomUUID());song.setFilename("Prueba.cho");song.setTitle("Prueba");
        assertFalse(SongReviewStatus.isReviewed(c,song));
        SongReviewStatus.setReviewed(c,song,true);assertTrue(SongReviewStatus.isReviewed(c,song));
        assertEquals("",SongReviewStatus.attentionNote(c,song));
        SongReviewStatus.toggle(c,song);assertFalse(SongReviewStatus.isReviewed(c,song));
    }

    @Test public void bundledAttentionNotesCanBeReadAndAcknowledged() throws Exception {
        android.content.Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        try(java.io.InputStream in=c.getAssets().open("DefaultSongs/revision-cantos.json")) {
            byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);
        }
        org.json.JSONArray entries=new org.json.JSONObject(bytes.toString("UTF-8")).getJSONArray("songs");
        org.json.JSONObject entry=null;
        for(int i=0;i<entries.length();i++)if(entries.getJSONObject(i).optBoolean("needsAttention")){entry=entries.getJSONObject(i);break;}
        assertNotNull(entry);String path=entry.getJSONArray("files").getString(0);int slash=path.lastIndexOf('/');
        Song song=new Song();song.setFolder(path.substring(0,slash));song.setFilename(path.substring(slash+1));song.setTitle("Test");
        boolean previous=SongReviewStatus.isReviewed(c,song);
        try {
            SongReviewStatus.setReviewed(c,song,false);
            assertEquals(entry.getString("notes"),SongReviewStatus.attentionNote(c,song));
            SongReviewStatus.setReviewed(c,song,true);assertEquals("",SongReviewStatus.attentionNote(c,song));
        } finally {SongReviewStatus.setReviewed(c,song,previous);}
    }
}
