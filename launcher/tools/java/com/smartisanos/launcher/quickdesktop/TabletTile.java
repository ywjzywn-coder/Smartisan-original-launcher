package com.smartisanos.launcher.quickdesktop;

import android.app.AlarmManager;
import android.content.Context;
import android.graphics.*;
import android.content.Intent;
import android.os.Bundle;
import com.smartisanos.launcher.theme.WeatherBridge;

import android.view.View;
import java.io.InputStream;
import java.io.IOException;
import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Live artwork sized to whole Smartisan cells. */
final class TabletTile extends View {
    static final int CALENDAR = 0, CLOCK = 1, MUSIC = 2, WEATHER = 3, BATTERY = 4, STORAGE = 5;
    final int kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final float density;
    private QuickDesktopMediaBridge.Snapshot track;
    private static final java.util.concurrent.ExecutorService tileWorker =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean readingMedia;
    private boolean readingStorage;
    private String nextAlarm;
    private String reading = "—", detail = "";
    private float fraction;
    private Bundle forecast;
    private Bitmap sky;
    private String skyArt;
    private Bitmap face, hourHand, minuteHand, hub, musicCover, musicIcon, paperPad, bindings, dateArt;
    private int displayedDay;
    private static final String TEXTURES = "Textures/1080p/";

    private Bitmap asset(String path) {
        try (InputStream stream=getResources().getAssets().open(path)) {
            return BitmapFactory.decodeStream(stream);
        } catch (IOException missing) {
            android.util.Log.w("TabletTile","Original artwork unavailable: "+path, missing);
            return null;
        }
    }
    private void artwork(Canvas canvas, Bitmap image,float x,float y,float w,float h) {
        if(image==null) return;
        paint.setColor(-1); paint.setFilterBitmap(true);
        canvas.drawBitmap(image,null,new RectF(x,y,x+w,y+h),paint);
    }
    private final Runnable refresh = new Runnable() {
        public void run() {
            removeCallbacks(this);
            if (!isShown() || !hasWindowFocus()) return;
            if (kind == MUSIC) refreshMedia();
            if (kind == BATTERY) refreshBattery();
            if (kind == STORAGE) refreshStorage();
            if (kind == WEATHER) {
                forecast=WeatherBridge.getTabletForecast(getContext());
                String code=forecast==null?"99":forecast.getString("weatherCode","99");
                String art=forecast==null?"cloudy":weatherArt(code);
                if(!art.equals(skyArt)){sky=asset(TEXTURES+"weather/"+art+".png");skyArt=art;}
            }
            if (kind == CALENDAR) {
                int day=Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
                if (day!=displayedDay) {
                    dateArt=asset(TEXTURES+"calendar/"+(day-1)+".png");
                    displayedDay=day;
                }
            }
            if (kind == CLOCK) {
                AlarmManager alarm = (AlarmManager)getContext().getSystemService(Context.ALARM_SERVICE);
                AlarmManager.AlarmClockInfo next = alarm == null ? null : alarm.getNextAlarmClock();
                nextAlarm = next == null ? null : android.text.format.DateFormat.getTimeFormat(getContext())
                        .format(new Date(next.getTriggerTime()));
            }
            invalidate();
            postDelayed(this, kind == MUSIC ? 5000 : 60010 - System.currentTimeMillis()%60000);
        }
    };

    private void refreshMedia() {
        if (readingMedia) return;
        readingMedia=true;
        final Context app=getContext().getApplicationContext();
        tileWorker.execute(new Runnable(){public void run(){
            QuickDesktopMediaBridge.Snapshot result=null;
            try { result=QuickDesktopMediaBridge.read(app,false); }
            catch(RuntimeException unavailable){android.util.Log.w("TabletTile","Media session unavailable",unavailable);}
            final QuickDesktopMediaBridge.Snapshot snapshot=result;
            post(new Runnable(){public void run(){
                readingMedia=false; track=snapshot;
                setContentDescription(snapshot!=null && snapshot.sessionAvailable ?
                        "音乐，"+snapshot.title+"，"+snapshot.artist : "音乐");
                invalidate();
            }});
        }});
    }

    TabletTile(Context context,int kind) {
        super(context);
        this.kind = kind;
        density = getResources().getDisplayMetrics().density;
        if (kind==CLOCK) {
            face=asset(TEXTURES+"clock/clock_bg.png");
            hourHand=asset(TEXTURES+"clock/clock_hour.png");
            minuteHand=asset(TEXTURES+"clock/clock_minute.png");
            hub=asset(TEXTURES+"clock/clock_dot.png");
        } else if (kind==CALENDAR) {
            paperPad=asset(TEXTURES+"calendar/bg.png");
            bindings=asset(TEXTURES+"calendar/ring.png");
        } else if (kind==MUSIC) {
            musicCover=asset("quickdesktop_original/music_default.png");
            musicIcon=asset("quickdesktop_original/music_def_app_icon.png");
        }
        setContentDescription(TabletTileLibrary.NAMES[kind]);
        setOnClickListener(new OnClickListener() { public void onClick(View v) {
            if (kind == CALENDAR && !QuickDesktopActions.openCalendar(context)) QuickDesktopActions.openShortcut(context,1);
            if (kind == CLOCK) QuickDesktopActions.openShortcut(context,5);
            if (kind == MUSIC) {
                if(track!=null && track.sessionAvailable) {
                    try {QuickDesktopMediaBridge.togglePlayback(context,false);}
                    catch(RuntimeException unavailable){android.util.Log.w("TabletTile","Playback unavailable",unavailable);}
                    postDelayed(refresh,250);
                } else if (track!=null && !track.notificationAccess) {
                    try {context.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
                    catch(android.content.ActivityNotFoundException missing){ QuickDesktopActions.openMusic(context); }
                } else QuickDesktopActions.openMusic(context);
            }
            if (kind==WEATHER) context.startActivity(new Intent("com.smartisanos.launcher.WEATHER_SETTINGS")
                    .setClassName(context.getPackageName(),"com.smartisanos.launcher.theme.ThemeChooserActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            if (kind == BATTERY || kind == STORAGE) {
                String action = kind == BATTERY ? Intent.ACTION_POWER_USAGE_SUMMARY
                        : android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS;
                try { context.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
                    android.widget.Toast.makeText(context,"系统未提供此设置页",android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        }});
    }

    private void refreshBattery() {
        Context context = getContext();
        Intent battery = context.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = battery == null ? -1 : battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL,-1);
        int scale = battery == null ? 0 : battery.getIntExtra(android.os.BatteryManager.EXTRA_SCALE,0);
        fraction = level < 0 || scale <= 0 ? 0f : Math.min(1f,level/(float)scale);
        reading = level < 0 || scale <= 0 ? "—" : Math.round(fraction*100)+"%";
        int status = battery == null ? 0 : battery.getIntExtra(android.os.BatteryManager.EXTRA_STATUS,0);
        detail = status == android.os.BatteryManager.BATTERY_STATUS_FULL ? "已充满"
                : status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ? "正在充电" : "剩余电量";
        setContentDescription(TabletTileLibrary.NAMES[kind] + "，" + reading + "，" + detail);
    }

    private void refreshStorage() {
        if (readingStorage) return;
        readingStorage = true;
        final Context context = getContext().getApplicationContext();
        tileWorker.execute(new Runnable() { public void run() {
            android.os.StatFs data = new android.os.StatFs(android.os.Environment.getDataDirectory().getPath());
            long total = data.getTotalBytes(), free = data.getAvailableBytes();
            String caption = "数据分区可用 · 共 ";
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                android.app.usage.StorageStatsManager stats = context.getSystemService(android.app.usage.StorageStatsManager.class);
                if (stats != null) try {
                    java.util.UUID volume = android.os.storage.StorageManager.UUID_DEFAULT;
                    long volumeTotal = stats.getTotalBytes(volume);
                    long volumeFree = stats.getFreeBytes(volume);
                    if (volumeTotal > 0 && volumeFree >= 0 && volumeFree <= volumeTotal) {
                        total = volumeTotal; free = volumeFree; caption = "可用空间 · 共 ";
                    }
                } catch (IOException | SecurityException unavailable) { /* Use the data partition. */ }
            }
            final String formatted = android.text.format.Formatter.formatShortFileSize(context, free);
            final String description = caption + android.text.format.Formatter.formatShortFileSize(context, total);
            final float used = total > 0 ? 1f - free/(float)total : 0f;
            post(new Runnable() { public void run() {
                readingStorage = false;
                reading = formatted; detail = description; fraction = used;
                setContentDescription("存储空间，" + reading + "，" + detail);
                invalidate();
            }});
        }});
    }

    @Override public void onWindowFocusChanged(boolean focus) { super.onWindowFocusChanged(focus); refresh.run(); }
    @Override protected void onVisibilityChanged(View changed, int visible) {
        super.onVisibilityChanged(changed,visible);
        if (refresh != null) refresh.run();
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); post(refresh); }
    @Override protected void onDetachedFromWindow() { removeCallbacks(refresh); super.onDetachedFromWindow(); }

    private void text(Canvas c,String value,float x,float baseline,float size,int color,boolean bold,Paint.Align align) {
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(align); paint.setTextSize(size); paint.setColor(color);
        c.drawText(value,x,baseline,paint);
    }

    @Override protected void onDraw(Canvas c) {
        float w=getWidth(), h=getHeight();
        if (w<=0 || h<=0) return;
        boolean paper=kind==CALENDAR;
        c.drawColor(paper ? 0xfffafafa : 0xff393e43);
        Calendar now=Calendar.getInstance();
        if (paper) calendar(c,w,h,now);
        else if (kind==CLOCK) clock(c,w,h,now);
        else if (kind==MUSIC) music(c,w,h);
        else if (kind==WEATHER) weather(c,w,h);
        else meter(c,w,h);
    }

    private void meter(Canvas c,float w,float h) {
        float pad=Math.min(Math.min(w,h)*.12f,20*density);
        float labelSize=Math.min(14*density,Math.min(w*.09f,h*.12f));
        float valueSize=Math.min(44*density,Math.min(w*.20f,h*.34f));
        text(c,TabletTileLibrary.NAMES[kind],pad,pad+labelSize,labelSize,0xffd4d7d9,false,Paint.Align.LEFT);
        text(c,fit(reading,w-2*pad,valueSize,true),pad,h*.58f,valueSize,0xfff5f3ef,true,Paint.Align.LEFT);
        text(c,fit(detail,w-2*pad,labelSize,false),pad,h*.77f,labelSize,0xffd4d7d9,false,Paint.Align.LEFT);
        float y=h-pad, thickness=Math.max(2*density,h*.022f);
        paint.setColor(0xff25292c); c.drawRoundRect(pad,y,w-pad,y+thickness,thickness,thickness,paint);
        paint.setColor(kind==BATTERY?0xff91b995:0xff8faac3);
        c.drawRoundRect(pad,y,pad+(w-2*pad)*fraction,y+thickness,thickness,thickness,paint);
    }

    private void calendar(Canvas c,float w,float h,Calendar now) {
        float header=Math.min(h*.26f,60*density), col=w/7;
        paint.setColor(0xfff2f2f2);
        c.drawRect(0,0,w,header,paint);
        float edge = Math.max(1f, density);
        float side=header*.92f, left=w*.045f, top=(header-side)/2;
        artwork(c,paperPad,left,top,side,side);
        artwork(c,dateArt,left+side*.22f,top+side*.34f,side*.56f,side*.43f);
        artwork(c,bindings,left,top,side,side);
        String title=android.text.format.DateFormat.format(
                android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(),"yyyyMMMM"),now).toString();
        text(c,title,left+side+w*.035f,header*.60f,Math.min(header*.37f,w*.087f),0xff4f5360,true,Paint.Align.LEFT);
        paint.setColor(0xffdedfe1); c.drawRect(0,header,w,header+1,paint);
        int today=now.get(Calendar.DAY_OF_MONTH), first=now.getFirstDayOfWeek();
        Calendar month=(Calendar)now.clone(); month.set(Calendar.DAY_OF_MONTH,1);
        int shift=(month.get(Calendar.DAY_OF_WEEK)-first+7)%7;
        String[] days=new DateFormatSymbols().getShortWeekdays();
        int weeks=(shift+month.getActualMaximum(Calendar.DAY_OF_MONTH)+6)/7;
        float step=(h-header-6*edge)/(weeks+1f);
        for (int i=0;i<7;i++) {
            int weekday=(first-1+i)%7+1;
            text(c,days[weekday],(i+.5f)*col,header+.65f*step,
                    Math.min(12*density,step*.56f),0xff777b82,false,Paint.Align.CENTER);
        }
        for (int day=1;day<=month.getActualMaximum(Calendar.DAY_OF_MONTH);day++) {
            int index=shift+day-1;
            float x=(index%7+.5f)*col, y=header+(1.5f+index/7)*step;
            if (day==today) {
                paint.setColor(0xffc26356); c.drawCircle(x,y,Math.min(col,step)*.43f,paint);
            }
            float size=Math.min(17*density,step*.68f);
            text(c,String.valueOf(day),x,y+size*.34f,size,day==today ? -1 : 0xff444850,
                    day==today,Paint.Align.CENTER);
        }
    }

    private void clock(Canvas c,float w,float h,Calendar now) {
        boolean wide=w>h;
        float radius=Math.min(Math.min(w,h)*.33f,54*density);
        float x=wide?w*.26f:w*.5f, y=wide?h*.50f:h*.35f;
        artwork(c,face,x-radius,y-radius,radius*2,radius*2);
        c.save(); c.translate(x,y);
        int minute=now.get(Calendar.MINUTE);
        drawHand(c,hourHand,(now.get(Calendar.HOUR)+minute/60f)*30,radius);
        drawHand(c,minuteHand,minute*6,radius);
        artwork(c,hub,-radius*.13f,-radius*.15f,radius*.26f,radius*.29f);
        c.restore();
        String time=android.text.format.DateFormat.getTimeFormat(getContext()).format(now.getTime());
        float tx=wide?w*.68f:w*.5f, ty=wide?h*.52f:h*.75f;
        text(c,time,tx,ty,Math.min(wide?w*.096f:w*.16f,32*density),0xfff4f1eb,false,Paint.Align.CENTER);
        text(c,nextAlarm==null ? "时钟" : "闹钟  "+nextAlarm,tx,ty+Math.min(h*.16f,21*density),
                Math.min(13*density,w*.075f),0xffc3c4c1,false,Paint.Align.CENTER);
    }

    private void drawHand(Canvas c, Bitmap hand, float angle,float radius) {
        if (hand==null) return;
        float scale=radius/96f;
        float w=hand.getWidth()*scale, h=hand.getHeight()*scale;
        c.save(); c.rotate(angle);
        artwork(c,hand,-w/2,-h/2,w,h); c.restore();
    }

    private String weatherArt(String code){
        switch(code){
            case "0":return "sunny";case "1":return "cloudy";case "2":return "overcast";
            case "4":return "thundershower";case "5":return "thundershowerhail";case "6":return "icerain";
            case "7":return "lightrain";case "8":return "moderaterain";case "9":return "heavyrain";
            case "13":return "snow";case "14":return "lightsnow";case "15":return "moderatesnow";
            case "16":return "heavysnow";case "18":return "foggy";default:return "unknown";
        }
    }

    private void weather(Canvas c,float w,float h){
        float pad=Math.min(22*density,Math.min(w,h)*.12f);
        float base= Math.min(52*density, Math.min(w*.27f,h*.40f));
        float cy=h*.53f;
        String temp=forecast==null?"—":forecast.getString("temp","—");
        text(c,temp,pad,cy,base,Color.WHITE,false,Paint.Align.LEFT);
        float unitX=pad+paint.measureText(temp)+3*density;
        text(c,"°C",unitX,cy-base*.45f,base*.30f,0xffe8e9eb,false,Paint.Align.LEFT);
        String caption=forecast==null?"选择城市或更新天气":QuickDesktopContentView.weatherDescription(
                forecast.getString("weatherCode","99"))+"  |  "+forecast.getString("city","");
        float size=Math.min(15*density,w*.08f);
        text(c,fit(caption,w-2*pad,size,false),pad,cy+size*1.65f,size,0xffe0e1e2,false,Paint.Align.LEFT);
        float side=Math.min(62*density,Math.min(w*.22f,h*.29f));
        artwork(c,sky,w-pad-side,Math.min(pad,h*.08f),side,side);
    }

    private String fit(String value,float width,float size,boolean bold) {
        paint.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
        paint.setTextSize(size);
        if(paint.measureText(value)<=width)return value;
        int n=paint.breakText(value,true,Math.max(0,width-paint.measureText("…")),null);
        return value.substring(0,n)+"…";
    }

    private void music(Canvas c,float w,float h) {
        boolean active=track!=null && track.sessionAvailable;
        Bitmap cover=active && track.artwork!=null ? track.artwork : musicCover;
        if(cover!=null){
            float scale=Math.max(w/cover.getWidth(),h/cover.getHeight());
            float sw=w/scale,sh=h/scale;
            int left=Math.round((cover.getWidth()-sw)/2),top=Math.round((cover.getHeight()-sh)/2);
            Rect source=new Rect(left,top,cover.getWidth()-left,cover.getHeight()-top);
            paint.setColor(-1);paint.setFilterBitmap(true);
            c.drawBitmap(cover,source,new RectF(0,0,w,h),paint);
        }
        if(active && track.artwork!=null) c.drawColor(0x66000000);
        float pad=Math.min(17*density,Math.min(w,h)*.09f);
        float icon=Math.min(35*density,Math.min(w,h)*.20f);
        artwork(c,musicIcon,pad*.55f,pad*.55f,icon,icon);
        float radius=Math.min(22*density,Math.min(w*.105f,h*.15f));
        float x=w-pad-radius,y=h-pad-radius;
        float titleSize=Math.min(21*density,Math.min(w*.11f,h*.14f));
        float subSize=titleSize*.70f;
        float room=Math.max(1,x-radius-2*pad);
        String title=active ? track.title : "Smartisan 音乐";
        String sub=active ? track.artist : track!=null && !track.notificationAccess ? "授权显示正在播放" : "打开音乐";
        text(c,fit(title,room,titleSize,true),pad,h-pad-subSize*1.50f,titleSize,-1,true,Paint.Align.LEFT);
        text(c,fit(sub,room,subSize,false),pad,h-pad,subSize,0xffd4d4d5,false,Paint.Align.LEFT);
        c.save();
        c.translate(x,y);
        c.scale(radius/25f,radius/25f);
        QuickDesktopContentView.drawPlayButton(c,0,0,25,active && track.playing,paint,outline);
        c.restore();
    }

    private final Paint outline=new Paint(Paint.ANTI_ALIAS_FLAG);
}
