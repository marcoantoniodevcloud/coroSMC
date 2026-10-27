package com.garethevans.church.opensongtablet.librarysync;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.util.*;
import java.util.concurrent.*;

/** Uses the bundled ZXing decoder: no external scanner, account or Play Services required. */
@SuppressWarnings("deprecation")
public final class LibraryQrScannerActivity extends Activity implements SurfaceHolder.Callback {
    private SurfaceView preview;private Camera camera;private boolean decoding;private final ExecutorService io=Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state){super.onCreate(state);LinearLayout body=new LinearLayout(this);body.setOrientation(1);
        TextView hint=new TextView(this);hint.setText("Apunta al QR de Sincronizar biblioteca en Cantoral Studio.");hint.setPadding(16,16,16,16);body.addView(hint);
        preview=new SurfaceView(this);body.addView(preview,new LinearLayout.LayoutParams(-1,0,1));Button cancel=new Button(this);cancel.setText("Cancelar");cancel.setOnClickListener(v->finish());body.addView(cancel);setContentView(body);preview.getHolder().addCallback(this);
        if(android.os.Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},41);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==41){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)start();else{Toast.makeText(this,"Puedes pegar el enlace de vinculación sin usar la cámara.",Toast.LENGTH_LONG).show();finish();}}}
    @Override protected void onResume(){super.onResume();if(preview!=null&&preview.getHolder().getSurface().isValid())start();}
    private void start(){if(camera!=null||isFinishing())return;if(android.os.Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;
        try{camera=Camera.open();Camera.Parameters p=camera.getParameters();if(p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);camera.setParameters(p);
            Camera.CameraInfo info=new Camera.CameraInfo();Camera.getCameraInfo(0,info);int rotation=getWindowManager().getDefaultDisplay().getRotation()*90;camera.setDisplayOrientation((info.orientation-rotation+360)%360);
            camera.setPreviewDisplay(preview.getHolder());camera.setPreviewCallback((data,c)->{if(decoding||isFinishing())return;decoding=true;byte[] copy=data.clone();Camera.Size size=c.getParameters().getPreviewSize();
                io.execute(()->{String result=null;try{MultiFormatReader reader=new MultiFormatReader();reader.setHints(Map.of(DecodeHintType.POSSIBLE_FORMATS,List.of(BarcodeFormat.QR_CODE),DecodeHintType.TRY_HARDER,Boolean.TRUE));
                    result=reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(new PlanarYUVLuminanceSource(copy,size.width,size.height,0,0,size.width,size.height,false)))).getText();}catch(Exception ignored){}
                    String value=result;runOnUiThread(()->{decoding=false;if(value!=null&&value.startsWith("opensong-sync://pair?")&&!isFinishing()){setResult(RESULT_OK,new Intent().putExtra("qr",value));finish();}});});});camera.startPreview();
        }catch(Exception e){stop();Toast.makeText(this,"No se pudo abrir la cámara. Usa Pegar enlace de vinculación.",Toast.LENGTH_LONG).show();finish();}}
    private void stop(){if(camera!=null){camera.setPreviewCallback(null);camera.stopPreview();camera.release();camera=null;}}
    @Override protected void onPause(){stop();super.onPause();}
    @Override protected void onDestroy(){io.shutdown();super.onDestroy();}
    public void surfaceCreated(SurfaceHolder h){start();}public void surfaceChanged(SurfaceHolder h,int f,int w,int height){}public void surfaceDestroyed(SurfaceHolder h){stop();}
}
