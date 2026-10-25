package com.garethevans.church.opensongtablet;

import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.garethevans.church.opensongtablet.songprocessing.Song;
import com.garethevans.church.opensongtablet.songprocessing.UnifiedSongView;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UnifiedSongViewInstrumentedTest {
    @Rule public ActivityScenarioRule<MainActivity> rule = new ActivityScenarioRule<>(MainActivity.class);
    private WebView web;
    private Dialog dialog;
    private WebView find(View view) {
        if (view instanceof WebView) return (WebView)view;
        if (view instanceof ViewGroup) for (int i=0; i<((ViewGroup)view).getChildCount(); i++) {
            WebView result=find(((ViewGroup)view).getChildAt(i)); if(result!=null) return result;
        }
        return null;
    }
    private String js(String script) throws Exception {
        CountDownLatch done=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(script, value -> {result.set(value);done.countDown();}));
        assertTrue(done.await(10,TimeUnit.SECONDS));return result.get();
    }
    @Test public void preservesMelodyThroughNativeConversionAndRendersAllThreeModes() throws Exception {
        rule.getScenario().onActivity(activity -> {
            Song original=activity.getSong();
            Song sample=new Song();sample.setTitle("Prueba");sample.setFolder("MAIN");sample.setFilename("Prueba");
            String source="[G]Mi [D7]alma\n{comment: melodia: <si>Mi <do>alma}";
            activity.getConvertChoPro().convertChordProLyricsToOpenSong(sample,source);
            String roundTrip=activity.getConvertChoPro().fromOpenSongToChordPro(sample.getLyrics());
            assertTrue(roundTrip,roundTrip.contains("<si>Mi <do>alma"));
            activity.setSong(sample);
            try {
                dialog=UnifiedSongView.show(activity,activity);
                web=find(dialog.getWindow().getDecorView());assertNotNull(web);
            } finally { activity.setSong(original); }
        });
        try {
            boolean ready=false;
            for(int i=0;i<30;i++) {
                if("2".equals(js("document.querySelectorAll('.melody-note').length"))) {ready=true;break;}
                Thread.sleep(300);
            }
            assertTrue("Shared assets or converted melody failed to render: "+js("document.body.innerText"),ready);
            assertEquals("true",js("document.querySelector('#mode').value='off'; updateView(); document.querySelector('#sheet').classList.contains('hide-melody')"));
            assertEquals("true",js("document.querySelector('#mode').value='separate'; updateView(); !document.querySelector('#separate').hidden && document.querySelector('#separate').innerText.includes('Mi alma')"));
            assertEquals("true",js("document.querySelector('#mode').value='inline'; document.querySelector('#chords').checked=false; updateView(); document.querySelector('#sheet').classList.contains('hide-chords')"));
            js("document.querySelector('#chords').checked=true; updateView()");
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> dialog.dismiss()); }
    }
}
