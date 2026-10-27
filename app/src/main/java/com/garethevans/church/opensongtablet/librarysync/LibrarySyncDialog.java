package com.garethevans.church.opensongtablet.librarysync;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.View;
import android.widget.*;
import android.webkit.*;
import com.garethevans.church.opensongtablet.MainActivity;
import org.json.JSONObject;
import java.util.concurrent.*;
import java.util.regex.*;

/** One explicit workspace for editing and submitting raw ChordPro, including offline drafts. */
public final class LibrarySyncDialog {
    private final MainActivity activity;
    private LibrarySyncClient client;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private LinearLayout body,list;
    private TextView status;
    private CheckBox pending;
    private AlertDialog dialog;
    private boolean busy;
    public LibrarySyncDialog(MainActivity activity){this.activity=activity;}
    private int dp(int value){return (int)(value*activity.getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(activity);view.setOrientation(LinearLayout.VERTICAL);view.setPadding(dp(12),dp(8),dp(12),dp(8));return view;}
    private TextView text(String label){TextView v=new TextView(activity);v.setText(label);v.setPadding(0,dp(6),0,dp(6));return v;}
    private Button button(LinearLayout parent,String label,Runnable action){Button b=new Button(activity);b.setText(label);b.setOnClickListener(v->{if(!busy)action.run();});parent.addView(b);return b;}
    private interface Work{String run() throws Exception;}
    private void work(Work action){if(busy)return;busy=true;if(pending!=null)pending.setEnabled(false);if(dialog!=null)dialog.setCancelable(false);status.setText("Procesando…");io.execute(()->{String message;
        try{message=action.run();}catch(Exception e){message=e.getMessage()==null?e.toString():e.getMessage();}String result=message;
        activity.runOnUiThread(()->{busy=false;if(pending!=null)pending.setEnabled(true);if(activity.isFinishing()){io.shutdown();return;}if(dialog!=null)dialog.setCancelable(true);status.setText(result);render();if(dialog!=null&&!dialog.isShowing())io.shutdown();});});}
    private void progress(String text){activity.runOnUiThread(()->status.setText(text));}
    public void show(){show(null);}
    public void show(String qr){
        try{client=new LibrarySyncClient(activity);}catch(Exception e){new AlertDialog.Builder(activity).setMessage(e.getMessage()).setPositiveButton("Cerrar",null).show();return;}
        body=column();status=text(client.linked()?"Vinculado a "+client.address():"Vincula Cantoral Studio en tu computadora para descargar la biblioteca.");body.addView(status);
        button(body,"Escanear QR",()->{dialog.dismiss();activity.startLibraryQrScan();});
        button(body,"Pegar enlace de vinculación",()->{EditText value=new EditText(activity);value.setHint("opensong-sync://pair?…");new AlertDialog.Builder(activity).setTitle("Enlace mostrado junto al QR").setView(value).setNegativeButton("Cancelar",null).setPositiveButton("Vincular",(d,w)->work(()->{client.pair(value.getText().toString());return "Celular vinculado. Descarga la biblioteca.";})).show();});
        button(body,"Descargar cambios",()->work(()->client.download(false,this::progress)));
        button(body,"Descargar toda la biblioteca",()->new AlertDialog.Builder(activity).setMessage("Se descargarán letras, imágenes y PDF. Los borradores locales se conservan.").setNegativeButton("Cancelar",null).setPositiveButton("Descargar",(d,w)->work(()->client.download(true,this::progress))).show());
        button(body,"Enviar cambios para aprobación",()->work(()->client.send(this::progress)));
        button(body,"Consultar propuestas enviadas",()->work(()->{client.refreshStatus();return "Estado de propuestas actualizado.";}));
        pending=new CheckBox(activity);pending.setText("Solo pendientes y cambios locales");pending.setChecked(true);pending.setOnCheckedChangeListener((b,checked)->render());body.addView(pending);
        list=column();body.addView(list);ScrollView scroll=new ScrollView(activity);scroll.addView(body);
        dialog=new AlertDialog.Builder(activity).setTitle("Sincronizar biblioteca").setView(scroll).setNegativeButton("Cerrar",(d,w)->{}).create();
        // Do not abandon an in-progress transfer or an unflushed draft.
        dialog.setOnDismissListener(d->{if(!busy)io.shutdown();});dialog.show();dialog.getButton(-2).setOnClickListener(v->{if(!busy)dialog.dismiss();});render();
        if(qr!=null)work(()->{client.pair(qr);return "Celular vinculado. Descarga la biblioteca.";});
        else if(client.linked())work(()->{client.captureExternalEdits();return "Vinculado a "+client.address()+". Puedes editar sin conexión.";});
    }
    private void render(){if(list==null)return;list.removeAllViews();try{int count=0;
        for(JSONObject e:client.entries())if(e.optBoolean("editable")&&(!pending.isChecked()||!e.optBoolean("reviewed")||e.optBoolean("dirty"))){
            String state=e.optBoolean("conflict")?"Conflicto":e.optBoolean("dirty")?"Borrador local":e.optBoolean("reviewed")?"Revisado":"Pendiente";
            String proposal=e.optString("proposalStatus");if(!proposal.isEmpty())state+=" · "+(proposal.equals("pending")?"Propuesta enviada":proposal.equals("approved")?"Propuesta aprobada":"Propuesta rechazada");
            String label=e.optString("path")+"\n"+state;button(list,label,()->{if(e.optBoolean("conflict"))conflict(e);else editor(e);});count++;
        }if(count==0)list.addView(text("No hay cantos en este filtro. Descarga la biblioteca o desmarca el filtro."));
        }catch(Exception error){list.addView(text(error.getMessage()));}}
    private static String directive(String source,String name){Matcher m=Pattern.compile("(?im)^\\s*\\{"+name+":\\s*([^}\\r\\n]*)}").matcher(source);return m.find()?m.group(1).trim():"";}
    private static String setDirective(String source,String name,String value) throws Exception {
        if(value.matches("(?s).*[{}\\r\\n].*"))throw new Exception("La tonalidad y las etiquetas deben ocupar una sola línea y no contener llaves.");
        Pattern p=Pattern.compile("(?im)^[ \\t]*\\{"+name+":[^}\\r\\n]*}[^\\S\\r\\n]*(?:\\r?\\n|$)");Matcher m=p.matcher(source);
        if(m.find())return m.replaceAll(Matcher.quoteReplacement(value.isEmpty()?"":"{"+name+": "+value+"}\n"));
        return value.isEmpty()?source:"{"+name+": "+value+"}\n"+source;
    }
    private void editor(JSONObject entry){try{
        LinearLayout fields=column();fields.addView(text(entry.getString("path")));if(!entry.optString("notes").isEmpty())fields.addView(text(entry.getString("notes")));
        String initial=client.content(entry);EditText source=new EditText(activity);source.setTypeface(Typeface.MONOSPACE);source.setGravity(android.view.Gravity.TOP);source.setMinLines(10);source.setText(initial);source.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        fields.addView(text("ChordPro · letra, acordes y directivas"));fields.addView(source);
        CheckBox reviewed=new CheckBox(activity);reviewed.setText("Solicitar marcar esta versión como revisada al aprobar");reviewed.setChecked(entry.optBoolean("reviewed"));fields.addView(reviewed);
        // Clear the review request when lyrics change; the user may deliberately re-check it.
        source.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){reviewed.setChecked(false);}public void afterTextChanged(android.text.Editable e){}});
        button(fields,"Tonalidad y etiquetas…",()->{LinearLayout metadata=column();EditText key=new EditText(activity),tags=new EditText(activity);key.setSingleLine();tags.setSingleLine();key.setText(directive(source.getText().toString(),"key"));tags.setText(directive(source.getText().toString(),"tags"));metadata.addView(text("Tonalidad"));metadata.addView(key);metadata.addView(text("Etiquetas separadas por comas"));metadata.addView(tags);
            AlertDialog meta=new AlertDialog.Builder(activity).setTitle("Metadatos del canto").setView(metadata).setNegativeButton("Cancelar",null).setPositiveButton("Aplicar",null).create();meta.setOnShowListener(d->meta.getButton(-1).setOnClickListener(v->{try{String value=setDirective(source.getText().toString(),"key",key.getText().toString().trim());source.setText(setDirective(value,"tags",tags.getText().toString().trim()));meta.dismiss();}catch(Exception ex){Toast.makeText(activity,ex.getMessage(),Toast.LENGTH_LONG).show();}}));meta.show();});
        button(fields,"Vista previa",()->preview(source.getText().toString()));
        TextView error=text("");fields.addView(error);ScrollView scroll=new ScrollView(activity);scroll.addView(fields);
        AlertDialog edit=new AlertDialog.Builder(activity).setTitle("Corregir canto").setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("Guardar borrador",null).create();
        edit.setOnShowListener(d->edit.getButton(-1).setOnClickListener(v->{try{client.saveDraft(entry,source.getText().toString(),reviewed.isChecked());edit.dismiss();status.setText("Borrador guardado. Envíalo cuando tengas conexión.");render();}catch(Exception ex){error.setText(ex.getMessage());}}));
        edit.setCancelable(false);edit.show();edit.getWindow().setLayout(-1,-1);
    }catch(Exception e){status.setText(e.getMessage());}}
    private void conflict(JSONObject entry){try{
        LinearLayout comparison=column();comparison.addView(text("La biblioteca oficial cambió. Compara y conserva manualmente lo necesario antes de enviar una nueva propuesta."));
        comparison.addView(text("VERSIÓN DE ORIGEN\n"+client.base(entry)));comparison.addView(text("VERSIÓN OFICIAL NUEVA\n"+client.incoming(entry)));comparison.addView(text("TU BORRADOR\n"+client.content(entry)));
        ScrollView scroll=new ScrollView(activity);scroll.addView(comparison);
        new AlertDialog.Builder(activity).setTitle("Revisar conflicto").setView(scroll).setNegativeButton("Conservar sin enviar",null).setPositiveButton("Editar sobre versión nueva",(d,w)->new AlertDialog.Builder(activity).setMessage("Tu borrador se conserva completo. Revisa e incorpora los cambios oficiales en el editor antes de enviarlo; no se hará una combinación automática.").setNegativeButton("Cancelar",null).setPositiveButton("Continuar",(a,b)->{try{client.rebase(entry);editor(entry);render();}catch(Exception e){status.setText(e.getMessage());}}).show()).show();
    }catch(Exception e){status.setText(e.getMessage());}}
    @android.annotation.SuppressLint("SetJavaScriptEnabled") private void preview(String source){
        WebView web=new WebView(activity);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setBlockNetworkLoads(true);web.getSettings().setSupportZoom(true);web.getSettings().setBuiltInZoomControls(true);web.getSettings().setDisplayZoomControls(false);
        web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView w,String url){return true;}@Override public void onPageFinished(WebView w,String url){if(url.equals("file:///android_asset/song-view/index.html"))try{w.evaluateJavascript("SongView.load("+new JSONObject().put("content",source)+")",null);}catch(Exception ignored){}}});web.loadUrl("file:///android_asset/song-view/index.html");
        AlertDialog preview=new AlertDialog.Builder(activity).setTitle("Vista previa").setView(web).setPositiveButton("Listo",null).create();preview.setOnDismissListener(d->web.destroy());preview.show();preview.getWindow().setLayout(-1,-1);
    }
}
