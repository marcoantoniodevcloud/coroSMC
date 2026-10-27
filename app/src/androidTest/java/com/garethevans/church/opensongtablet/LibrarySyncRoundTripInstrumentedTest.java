package com.garethevans.church.opensongtablet;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.garethevans.church.opensongtablet.librarysync.LibrarySyncClient;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LibrarySyncRoundTripInstrumentedTest {
 @Rule public ActivityScenarioRule<MainActivity> rule=new ActivityScenarioRule<>(MainActivity.class);
 @Test public void pairsDownloadsKeepsDraftAndReceivesWebApproval() throws Exception {
  String qr=InstrumentationRegistry.getArguments().getString("syncQr");
  Assume.assumeNotNull(qr);
  AtomicReference<MainActivity> ref=new AtomicReference<>();rule.getScenario().onActivity(ref::set);MainActivity activity=ref.get();
  LibrarySyncClient client=new LibrarySyncClient(activity);client.pair(qr);assertTrue(client.linked());
  client.download(true,message->{});
  JSONObject song=null;int media=0;for(JSONObject entry:client.entries()){if(entry.optBoolean("editable"))song=entry;else media++;}
  assertNotNull(song);assertTrue(media>0);String original=client.content(song);
  assertTrue(original.contains("{custom: conservar}"));
  String changed=original+"\n{tags: comunion, prueba QR}\n{key: Em}\n[Em-Bm]Corregido desde Android\n";
  client.saveDraft(song,changed,true);assertTrue(song.getBoolean("dirty"));assertEquals(original,client.base(song));
  assertTrue(client.send(message->{}).startsWith("1 propuestas"));assertEquals("pending",song.getString("proposalStatus"));
  android.util.Log.i("LibrarySyncTest","PROPOSAL_READY");
  boolean approved=false;
  for(int i=0;i<120;i++){client.refreshStatus();if("approved".equals(song.optString("proposalStatus"))){approved=true;break;}Thread.sleep(500);}
  assertTrue("Approve the test proposal in the host fixture server",approved);
  client.download(false,message->{});assertFalse(song.optBoolean("dirty"));assertFalse(song.optBoolean("conflict"));
  assertTrue(song.optBoolean("reviewed"));assertEquals(changed,client.content(song));assertEquals(changed,client.base(song));
 }
}
