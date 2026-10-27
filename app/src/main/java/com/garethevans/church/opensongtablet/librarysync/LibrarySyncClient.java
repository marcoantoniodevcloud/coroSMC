package com.garethevans.church.opensongtablet.librarysync;

import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;
import com.garethevans.church.opensongtablet.MainActivity;
import com.garethevans.church.opensongtablet.songprocessing.Song;
import com.garethevans.church.opensongtablet.songmenu.SongReviewStatus;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;

/** Raw ChordPro working copies. Network operations run on the dialog's background executor. */
public final class LibrarySyncClient {
    private final MainActivity activity;
    private final android.content.SharedPreferences prefs;
    private File root;
    public JSONObject state;
    public LibrarySyncClient(MainActivity activity) throws Exception {
        this.activity=activity;prefs=activity.getSharedPreferences("library-sync",Context.MODE_PRIVATE);
        load();
    }
    public boolean linked(){return !prefs.getString("token","").isEmpty();}
    public String address(){return prefs.getString("url","");}
    private void load() throws Exception {
        String server=prefs.getString("serverId","");
        if(server.isEmpty()){state=new JSONObject().put("entries",new JSONObject());return;}
        UUID.fromString(server);root=new File(activity.getFilesDir(),"library-sync/"+server);root.mkdirs();
        AtomicFile index=new AtomicFile(new File(root,"index.json"));
        state=index.getBaseFile().exists()?new JSONObject(new String(index.readFully(),StandardCharsets.UTF_8)):new JSONObject().put("entries",new JSONObject());
    }
    private void persist() throws Exception {if(root!=null)write(new File(root,"index.json"),state.toString().getBytes(StandardCharsets.UTF_8));}
    public static String hash(byte[] data) throws Exception {StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(data))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private static void write(File file,byte[] bytes) throws Exception {
        file.getParentFile().mkdirs();AtomicFile atomic=new AtomicFile(file);FileOutputStream stream=atomic.startWrite();
        try{stream.write(bytes);atomic.finishWrite(stream);}catch(Exception e){atomic.failWrite(stream);throw e;}
    }
    private static byte[] read(File file) throws Exception {try(InputStream in=new FileInputStream(file)){return bytes(in,32*1024*1024);}}
    private static byte[] bytes(InputStream in,int max) throws Exception {try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
        byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>max)throw new IOException("El archivo supera 32 MB.");out.write(buffer,0,n);}return out.toByteArray();}}
    private byte[] request(String url,String method,JSONObject body,String token) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(10_000);c.setReadTimeout(60_000);c.setInstanceFollowRedirects(false);c.setRequestMethod(method);
        if(token!=null&&!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);
        try{if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int status=c.getResponseCode();if(status<200||status>=300){String message="Error "+status;
                InputStream error=c.getErrorStream();if(error!=null)try(InputStream in=error){JSONObject detail=new JSONObject(new String(bytes(in,65536),StandardCharsets.UTF_8));message=detail.optString("message",detail.optString("detail",message));}catch(Exception ignored){}
                throw new IOException(message);}
            try(InputStream in=c.getInputStream()){return bytes(in,32*1024*1024);}
        }finally{c.disconnect();}
    }
    private JSONObject api(String path,String method,JSONObject body) throws Exception {
        return new JSONObject(new String(request(address()+"/api/sync/"+path,method,body,prefs.getString("token","")),StandardCharsets.UTF_8));
    }
    public void pair(String qr) throws Exception {
        Uri u=Uri.parse(qr.trim());if(!"opensong-sync".equals(u.getScheme())||!"pair".equals(u.getHost()))throw new IOException("Este QR no es de Cantoral Studio.");
        String address=u.getQueryParameter("url"),expected=u.getQueryParameter("server"),code=u.getQueryParameter("code");
        if(address==null||code==null||expected==null)throw new IOException("QR incompleto.");
        UUID.fromString(expected);URI server=URI.create(address);InetAddress host=InetAddress.getByName(server.getHost());
        if(!"http".equals(server.getScheme())||server.getUserInfo()!=null||server.getQuery()!=null||server.getFragment()!=null||!(host.isSiteLocalAddress()||host.isLoopbackAddress())||!(server.getPath().isEmpty()||server.getPath().equals("/")))throw new IOException("Usa el QR de una computadora en tu red local.");
        JSONObject result=new JSONObject(new String(request(address+"/api/sync/pair","POST",new JSONObject().put("code",code).put("name",android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL),null),StandardCharsets.UTF_8));
        if(!expected.equals(result.getString("serverId")))throw new IOException("La computadora no coincide con el QR.");
        prefs.edit().putString("url",address).putString("token",result.getString("token")).putString("serverId",expected).apply();load();
    }
    private File local(String kind,String id) throws Exception {UUID.fromString(id);return new File(root,kind+"/"+id);}
    public List<JSONObject> entries() throws Exception {List<JSONObject> entries=new ArrayList<>();JSONObject all=state.getJSONObject("entries");Iterator<String> keys=all.keys();while(keys.hasNext())entries.add(all.getJSONObject(keys.next()));entries.sort(Comparator.comparing(e->e.optString("path")));return entries;}
    public String content(JSONObject entry) throws Exception {return new String(read(local("work",entry.getString("id"))),StandardCharsets.UTF_8);}
    public String base(JSONObject entry) throws Exception {return new String(read(local("base",entry.getString("id"))),StandardCharsets.UTF_8);}
    private String[] destination(JSONObject e) throws Exception {
        String path=e.getString("path");if(path.startsWith("/")||path.contains("\\")||path.contains(":"))throw new IOException("Ruta de biblioteca inválida.");
        for(String part:path.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))throw new IOException("Ruta de biblioteca inválida.");
        int slash=path.lastIndexOf('/');String folder="WebSync/"+prefs.getString("serverId","")+(slash<0?"":"/"+path.substring(0,slash));return new String[]{folder,path.substring(slash+1)};
    }
    private byte[] installed(JSONObject entry) throws Exception {String[] d=destination(entry);Uri uri=activity.getStorageAccess().getUriForItem("Songs",d[0],d[1]);
        if(!activity.getStorageAccess().uriExists(uri))return null;try(InputStream in=activity.getStorageAccess().getInputStream(uri)){if(in==null)return null;return bytes(in,32*1024*1024);}}
    public void captureExternalEdits() throws Exception {
        for(JSONObject e:entries())if(e.optBoolean("editable")&&e.has("installedHash")){
            byte[] current=installed(e);if(current!=null&&!hash(current).equals(e.getString("installedHash"))){
                if(new String(current,StandardCharsets.UTF_8).trim().startsWith("<"))throw new IOException("El archivo "+e.getString("path")+" fue cambiado a XML fuera del editor de sincronización. Se conserva sin sobrescribir.");
                write(local("work",e.getString("id")),current);e.put("dirty",true);e.put("installedHash",hash(current));}
        }persist();
    }
    private boolean storageReady() {
        String tree=activity.getPreferences().getMyPreferenceString("uriTree","");
        return !tree.isEmpty() && activity.getStorageAccess().uriTreeValid(Uri.parse(tree));
    }
    private void mirror(JSONObject e,byte[] data) throws Exception {
        // The private working copy remains usable before the user selects a Songs folder.
        if(!storageReady())return;
        String[] d=destination(e);byte[] current=installed(e);
        if(current!=null&&!hash(current).equals(e.optString("installedHash",hash(data)))&&!Arrays.equals(current,data))throw new IOException("Hay cambios locales sin registrar en "+e.getString("path")+". Se conservan ambos archivos.");
        if(current==null||!Arrays.equals(current,data)){
            Uri out=activity.getStorageAccess().makeSureFileIsRegistered("Songs",d[0],d[1],true);
            if(out==null)throw new IOException("Selecciona primero la carpeta de canciones en Android.");
            try(OutputStream stream=activity.getStorageAccess().getOutputStream(out)){if(stream==null)throw new IOException("No se pudo guardar "+e.getString("path"));stream.write(data);}
        }
        e.put("installedHash",hash(data));
        if(e.optBoolean("editable")){Song song=new Song();song.setFolder(d[0]);song.setFilename(d[1]);SongReviewStatus.setReviewed(activity,song,e.optBoolean("reviewed"));}
    }
    public String download(boolean all,Consumer<String> progress) throws Exception {
        if(!linked())throw new IOException("Primero vincula el celular.");captureExternalEdits();refreshStatus();
        JSONObject manifest=api("manifest","GET",null);if(!prefs.getString("serverId","").equals(manifest.getString("serverId")))throw new IOException("La biblioteca cambió de identidad. Vincúlala nuevamente.");
        JSONArray remote=manifest.getJSONArray("files");JSONObject entries=state.getJSONObject("entries");int downloaded=0,conflicts=0;
        for(int i=0;i<remote.length();i++){
            JSONObject incoming=remote.getJSONObject(i);String id=incoming.getString("id");progress.accept((i+1)+"/"+remote.length()+" · "+incoming.getString("path"));
            JSONObject e=entries.optJSONObject(id);if(e==null){e=new JSONObject(incoming.toString());e.put("dirty",false);entries.put(id,e);}
            String revision=incoming.getString("revision");boolean editable=incoming.getBoolean("editable");
            boolean changed=editable?!revision.equals(e.optString("baseRevision")):!revision.equals(e.optString("installedHash"));
            if(all||changed||!local(editable?"base":"media",id).exists()){
                byte[] data=request(address()+"/api/sync/file?path="+java.net.URLEncoder.encode(incoming.getString("path"),"UTF-8"),"GET",null,prefs.getString("token",""));
                if(!hash(data).equals(revision))throw new IOException("El canto cambió durante la descarga. Intenta descargar cambios nuevamente.");
                if(editable){boolean clean=!e.optBoolean("dirty")||(hash(read(local("work",id))).equals(revision)&&e.optBoolean("reviewed")==incoming.optBoolean("reviewed"));
                    if(clean){write(local("base",id),data);write(local("work",id),data);e.put("baseRevision",revision);e.put("dirty",false);e.put("conflict",false);e.put("baseReviewed",incoming.optBoolean("reviewed"));e.put("reviewed",incoming.optBoolean("reviewed"));}
                    else if(changed){write(local("incoming",id),data);e.put("conflict",true);e.put("incomingReviewed",incoming.optBoolean("reviewed"));conflicts++;}
                }else write(local("media",id),data);
                downloaded++;
            }
            if(editable&&!e.optBoolean("dirty")){e.put("reviewed",incoming.optBoolean("reviewed"));e.put("baseReviewed",incoming.optBoolean("reviewed"));}
            e.put("path",incoming.getString("path"));e.put("notes",incoming.optString("notes"));
            if(editable&&local("work",id).exists())mirror(e,read(local("work",id)));else if(!editable&&local("media",id).exists())mirror(e,read(local("media",id)));
            persist();
        }
        if(storageReady())activity.runOnUiThread(this::refreshMainLibrary);
        return downloaded+" archivos descargados. "+conflicts+" conflictos conservados para revisar."
                +(storageReady()?"":" Selecciona una carpeta de canciones para verlos también en la biblioteca principal.");
    }
    private void refreshMainLibrary() {
        if(activity.isFinishing() || activity.isDestroyed())return;
        if(activity.getSongListBuildIndex().getCurrentlyIndexing()) {
            activity.getMainHandler().postDelayed(this::refreshMainLibrary,1000);return;
        }
        activity.getThreadPoolExecutor().execute(()->{
            // Register new downloads before the metadata pass; indexSongs alone scans existing rows.
            activity.getSongListBuildIndex().buildBasicFromFiles();
            activity.indexSongs();
        });
    }
    public void saveDraft(JSONObject e,String text,boolean reviewed) throws Exception {
        byte[] data=text.getBytes(StandardCharsets.UTF_8);if(data.length>1_000_000)throw new IOException("El canto supera 1 MB.");
        write(local("work",e.getString("id")),data);e.put("reviewed",reviewed);e.put("dirty",!hash(data).equals(e.getString("baseRevision"))||reviewed!=e.optBoolean("baseReviewed"));persist();mirror(e,data);persist();
    }
    public void rebase(JSONObject e) throws Exception {File incoming=local("incoming",e.getString("id"));if(!incoming.exists())throw new IOException("Descarga cambios antes de resolver el conflicto.");byte[] data=read(incoming);write(local("base",e.getString("id")),data);e.put("baseRevision",hash(data));e.put("baseReviewed",e.optBoolean("incomingReviewed"));e.put("conflict",false);e.put("dirty",true);persist();}
    public String incoming(JSONObject e) throws Exception {return new String(read(local("incoming",e.getString("id"))),StandardCharsets.UTF_8);}
    public String send(Consumer<String> progress) throws Exception {
        captureExternalEdits();int count=0;for(JSONObject e:entries())if(e.optBoolean("editable")&&e.optBoolean("dirty")){
            if(e.optBoolean("conflict"))continue;progress.accept("Enviando "+e.getString("path"));
            JSONObject result=api("proposals","POST",new JSONObject().put("path",e.getString("path")).put("baseRevision",e.getString("baseRevision")).put("baseContent",base(e)).put("content",content(e)).put("reviewed",e.optBoolean("reviewed")));
            e.put("proposalId",result.getString("id"));e.put("proposalStatus",result.getString("status"));persist();count++;
        }return count+" propuestas enviadas. Los conflictos requieren revisión antes de enviarse.";
    }
    public void refreshStatus() throws Exception {JSONArray proposals=api("proposals","GET",null).getJSONArray("proposals");for(JSONObject e:entries())for(int i=0;i<proposals.length();i++){JSONObject p=proposals.getJSONObject(i);if(p.getString("id").equals(e.optString("proposalId")))e.put("proposalStatus",p.getString("status"));}persist();}
}
