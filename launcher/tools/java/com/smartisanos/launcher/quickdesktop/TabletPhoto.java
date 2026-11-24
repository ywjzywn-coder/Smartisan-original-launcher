package com.smartisanos.launcher.quickdesktop;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.smartisanos.launcher.backup.TabletSlots;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Imports a single user-selected image into a bounded, private frame copy. */
public final class TabletPhoto {
    static final String KEY = "tablet_photo_frame_file";
    static final int REPLACE = 6;
    private static final int PICK = 54170, EDGE = 1600;
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final String FRAME_PREFIX = "tablet-frame-";
    private static final String INPUT_PREFIX = "frame-input-";
    private static boolean cleanupScheduled;
    private static String cachedName;
    private static Bitmap cached;

    private TabletPhoto() {}

    // Queue recovery before any new import, once per launcher process. A process death
    // can interrupt either an import or removal after its preference commit.
    static synchronized void recover(final Context context) {
        if (cleanupScheduled) return;
        cleanupScheduled = true;
        final Context app = context.getApplicationContext();
        worker.execute(new Runnable() {
            public void run() {
                String current = app.getSharedPreferences(TabletSlots.PREF, 0).getString(KEY, null);
                removeUnused(app.getNoBackupFilesDir(), FRAME_PREFIX, FRAME_PREFIX + current);
                removeUnused(app.getCacheDir(), INPUT_PREFIX, null);
            }
        });
    }

    private static void removeUnused(File directory, String prefix, String keep) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File candidate : files) {
            String name = candidate.getName();
            if (name.startsWith(prefix) && !name.equals(keep) && candidate.isFile()) {
                candidate.delete();
            }
        }
    }

    static boolean choose(Activity activity,int slot) {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        for (String action : new String[]{Intent.ACTION_OPEN_DOCUMENT,Intent.ACTION_GET_CONTENT}) {
            picker.setAction(action);
            try { activity.startActivityForResult(picker,PICK+slot); return true; }
            catch (ActivityNotFoundException | SecurityException unavailable) { /* Try the system content picker. */ }
        }
        message(activity,"系统没有可用的图片选择器");
        return false;
    }

    public static boolean result(final Activity activity,int request,int status,Intent data) {
        final int slot=request-PICK;
        if(slot<0 || slot>REPLACE) return false;
        if(status!=Activity.RESULT_OK) return true;
        final Uri uri=data==null?null:data.getData();
        if(uri==null || !"content".equals(uri.getScheme())) {
            message(activity,"无法读取所选照片"); return true;
        }
        final SharedPreferences prefs=activity.getSharedPreferences(TabletSlots.PREF,0);
        final int original=TabletSlots.positions(prefs);
        final String before=prefs.getString(KEY,null);
        int current=TabletSlots.slot(original,TabletTile.PHOTO);
        if(slot==REPLACE ? current<0 : current>=0 || !TabletSlots.fits(original,TabletTile.PHOTO,slot)) {
            message(activity,"组件位置已改变，请重新选择");return true;
        }
        final Context context=activity.getApplicationContext();
        recover(context);
        worker.execute(new Runnable(){public void run(){
            final String name=UUID.randomUUID().toString();
            File destination=file(context,name);
            try {
                Bitmap bitmap=decode(context,uri);
                try(FileOutputStream output=new FileOutputStream(destination)) {
                    Bitmap.CompressFormat format=bitmap.hasAlpha()?Bitmap.CompressFormat.PNG:Bitmap.CompressFormat.JPEG;
                    if(!bitmap.compress(format,90,output)) throw new IOException("encode");
                    output.getFD().sync();
                } finally { bitmap.recycle(); }
            } catch(IOException | RuntimeException | OutOfMemoryError invalid) {
                destination.delete();
                main.post(new Runnable(){public void run(){message(context,"无法读取这张照片，请换一张");}});
                return;
            }
            main.post(new Runnable(){public void run(){
                if(activity.isFinishing() || activity.isDestroyed() || original!=TabletSlots.positions(prefs)
                        || !android.text.TextUtils.equals(before,prefs.getString(KEY,null))) {
                    destination.delete();message(context,"桌面已改变，请重新选图");return;
                }
                int draft=slot==REPLACE?original:TabletSlots.put(original,TabletTile.PHOTO,slot);
                if(!TabletTileLibrary.apply(activity,prefs,original,draft,name)) destination.delete();
            }});
        }});
        return true;
    }

    private static Bitmap decode(Context context,Uri uri) throws IOException {
        if(Build.VERSION.SDK_INT>=28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.getContentResolver(),uri),
                    new ImageDecoder.OnHeaderDecodedListener(){
                public void onHeaderDecoded(ImageDecoder decoder,ImageDecoder.ImageInfo info,ImageDecoder.Source source){
                    int w=info.getSize().getWidth(),h=info.getSize().getHeight();
                    float ratio=Math.min(1f,EDGE/(float)Math.max(w,h));
                    decoder.setTargetSize(Math.max(1,Math.round(w*ratio)),Math.max(1,Math.round(h*ratio)));
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                }
            });
        }
        File source=File.createTempFile(INPUT_PREFIX,".tmp",context.getCacheDir());
        try {
            try(InputStream in=context.getContentResolver().openInputStream(uri);
                OutputStream out=new FileOutputStream(source)) {
                if(in==null) throw new IOException("missing input");
                byte[] buffer=new byte[32768];long total=0;int n;
                while((n=in.read(buffer))!=-1) {
                    total+=n;
                    if(total>64L*1024*1024) throw new IOException("input too large");
                    out.write(buffer,0,n);
                }
            }
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(source.getPath(),options);
            if(options.outWidth<=0 || options.outHeight<=0) throw new IOException("unsupported image");
            options.inSampleSize=1;
            while(Math.max(options.outWidth,options.outHeight)/(long)options.inSampleSize>EDGE)
                options.inSampleSize*=2;
            options.inJustDecodeBounds=false;
            Bitmap input=BitmapFactory.decodeFile(source.getPath(),options);
            if(input==null) throw new IOException("decode");
            int orientation=1;
            try {orientation=new ExifInterface(source.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}
            catch(IOException noExif) { /* Some older decoders only expose JPEG metadata. */ }
            Matrix matrix=new Matrix();
            switch(orientation) {
                case 2: matrix.setScale(-1,1);break;
                case 3: matrix.setRotate(180);break;
                case 4: matrix.setScale(1,-1);break;
                case 5: matrix.setRotate(90);matrix.postScale(-1,1);break;
                case 6: matrix.setRotate(90);break;
                case 7: matrix.setRotate(-90);matrix.postScale(-1,1);break;
                case 8: matrix.setRotate(-90);break;
                default:return input;
            }
            Bitmap rotated=Bitmap.createBitmap(input,0,0,input.getWidth(),input.getHeight(),matrix,true);
            if(rotated!=input) input.recycle();
            return rotated;
        } finally {source.delete();}
    }

    private static File file(Context context,String name) {
        return new File(context.getNoBackupFilesDir(),FRAME_PREFIX+UUID.fromString(name).toString());
    }
    static void forget(Context context,String name) {
        if(name==null)return;
        try {file(context,name).delete();} catch(IllegalArgumentException ignored) {}
        synchronized(TabletPhoto.class) {
            if(name.equals(cachedName)){cachedName=null;cached=null;}
        }
    }
    static void load(final TabletTile view,final String name) {
        final Context context=view.getContext().getApplicationContext();
        worker.execute(new Runnable(){public void run(){
            final Bitmap result;
            synchronized(TabletPhoto.class) {
                if(!name.equals(cachedName) || cached==null){
                    try {cached=BitmapFactory.decodeFile(file(context,name).getPath());}
                    catch(IllegalArgumentException | OutOfMemoryError invalid) {cached=null;}
                    cachedName=name;
                }
                result=cached;
            }
            main.post(new Runnable(){public void run(){view.photoLoaded(name,result);}});
        }});
    }
    private static void message(Context context,String value) {Toast.makeText(context,value,Toast.LENGTH_SHORT).show();}
}
