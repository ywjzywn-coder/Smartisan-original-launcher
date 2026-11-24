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
    static final int CALENDAR = 0, CLOCK = 1, MUSIC = 2, WEATHER = 3, BATTERY = 4, STORAGE = 5, PHOTO = 6;
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
    private Bitmap photo;
    private String photoName;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener photoChanges =
            new android.content.SharedPreferences.OnSharedPreferenceChangeListener(){
        public void onSharedPreferenceChanged(android.content.SharedPreferences prefs,String key) {
            if(TabletPhoto.KEY.equals(key)) refreshPhoto();
        }
    };
    private Bundle forecast;
    private Bitmap sky;
    private String skyArt;
    private Bitmap face, hourHand, minuteHand, hub, musicCover, musicIcon, paperPad, bindings, dateArt;
    private int displayedDay;
    private final TabletInstrumentArt instruments = new TabletInstrumentArt();
    private boolean charging;
    private int weatherCode = -1;
    private String weatherCity = "天气", condition = "", updated = "", feelsLike = "";
    private final android.content.BroadcastReceiver batteryListener = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) { readBattery(intent); invalidate(); }
    };
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
            if (kind == PHOTO) refreshPhoto();
            if (kind == MUSIC) refreshMedia();
            if (kind == BATTERY) refreshBattery();
            if (kind == STORAGE) refreshStorage();
            if (kind == WEATHER) refreshWeather();
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
            if (kind==PHOTO) {
                Context host=context;
                while(host instanceof android.content.ContextWrapper && !(host instanceof android.app.Activity))
                    host=((android.content.ContextWrapper)host).getBaseContext();
                if(host instanceof android.app.Activity) TabletPhoto.choose((android.app.Activity)host,TabletPhoto.REPLACE);
            }
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
        readBattery(battery);
    }

    private void readBattery(Intent battery) {
        int level = battery == null ? -1 : battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL,-1);
        int scale = battery == null ? 0 : battery.getIntExtra(android.os.BatteryManager.EXTRA_SCALE,0);
        fraction = level < 0 || scale <= 0 ? 0f : Math.min(1f,level/(float)scale);
        reading = level < 0 || scale <= 0 ? "—" : Math.round(fraction*100)+"%";
        int status = battery == null ? 0 : battery.getIntExtra(android.os.BatteryManager.EXTRA_STATUS,0);
        charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING && fraction<1f && level>=0;
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
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if(kind==PHOTO) photoPrefs().registerOnSharedPreferenceChangeListener(photoChanges);
        if(kind==BATTERY) getContext().registerReceiver(batteryListener,new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        post(refresh);
    }
    @Override protected void onDetachedFromWindow() {
        if(kind==PHOTO) {photoPrefs().unregisterOnSharedPreferenceChangeListener(photoChanges);photo=null;photoName=null;}
        if(kind==BATTERY) getContext().unregisterReceiver(batteryListener);
        removeCallbacks(refresh);
        instruments.release();
        super.onDetachedFromWindow();
    }

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
        if (paper) calendar(c,w,h,Calendar.getInstance());
        else if (kind==CLOCK) clock(c,w,h,Calendar.getInstance());
        else if (kind==MUSIC) music(c,w,h);
        else if (kind==WEATHER) weather(c,w,h);
        else if (kind==PHOTO) drawFrame(c,w,h);
        else if(kind==BATTERY) battery(c,w,h);
        else drive(c,w,h);
    }

    private android.content.SharedPreferences photoPrefs() {
        return getContext().getSharedPreferences(com.smartisanos.launcher.backup.TabletSlots.PREF,0);
    }
    private void refreshPhoto() {
        String name=photoPrefs().getString(TabletPhoto.KEY,null);
        if(android.text.TextUtils.equals(photoName,name)) return;
        photoName=name;photo=null;
        if(name!=null) TabletPhoto.load(this,name);
        else setContentDescription("照片");
        invalidate();
    }
    void photoLoaded(String name,Bitmap bitmap) {
        if(!name.equals(photoName) || !isAttachedToWindow()) return;
        photo=bitmap;
        setContentDescription(bitmap==null?"照片，点按重新选择":"照片，自定义相框");
        invalidate();
    }
    private void drawFrame(Canvas c,float w,float h) {
        float edge=Math.min(w,h),rail=Math.max(2,edge*.032f),mat=Math.max(2,edge*.034f);
        c.drawColor(0xff544a40);
        paint.setColor(0xffa39785);c.drawRect(0,0,w,rail*.58f,paint);
        paint.setColor(0xff342e2a);c.drawRect(0,h-rail*.58f,w,h,paint);
        paint.setColor(0xffe9e5d8);c.drawRect(rail,rail,w-rail,h-rail,paint);
        float inset=rail+mat;
        RectF window=new RectF(inset,inset,w-inset,h-inset);
        if(window.width()<1 || window.height()<1) return;
        if(photo!=null) {
            float scale=Math.max(window.width()/photo.getWidth(),window.height()/photo.getHeight());
            float sw=window.width()/scale,sh=window.height()/scale;
            int left=Math.max(0,(int)((photo.getWidth()-sw)/2)),top=Math.max(0,(int)((photo.getHeight()-sh)/2));
            Rect src=new Rect(left,top,Math.min(photo.getWidth(),left+Math.max(1,Math.round(sw))),
                    Math.min(photo.getHeight(),top+Math.max(1,Math.round(sh))));
            paint.setColor(-1);paint.setFilterBitmap(true);
            c.drawBitmap(photo,src,window,paint);
        } else {
            // A landscape motif makes the empty frame recognizable in the gallery.
            c.save();c.clipRect(window);
            paint.setColor(0xffbccdd0);c.drawRect(window,paint);
            paint.setColor(0xfff2deb1);c.drawCircle(w*.70f,h*.31f,edge*.094f,paint);
            Path ridge=new Path();ridge.moveTo(0,h*.84f);ridge.lineTo(w*.32f,h*.34f);
            ridge.lineTo(w*.65f,h*.76f);ridge.lineTo(w*.84f,h*.47f);ridge.lineTo(w,h*.63f);
            ridge.lineTo(w,h);ridge.lineTo(0,h);ridge.close();
            paint.setColor(0xff708c8a);c.drawPath(ridge,paint);
            ridge.reset();ridge.moveTo(0,h*.72f);ridge.quadTo(w*.28f,h*.57f,w*.52f,h*.78f);
            ridge.quadTo(w*.72f,h*.86f,w,h*.64f);ridge.lineTo(w,h);ridge.lineTo(0,h);ridge.close();
            paint.setColor(0xff4c6869);c.drawPath(ridge,paint);c.restore();
        }
        paint.setColor(0x4d282321);c.drawRect(window.left,window.top,window.right,window.top+Math.max(1,edge*.008f),paint);
    }

    private void battery(Canvas c,float w,float h) {
        float u=Math.min(w,h);
        instruments.draw(c,getWidth(),getHeight(),kind,Math.round(fraction*100));
        if(charging && hasWindowFocus() && isShown() && (android.os.Build.VERSION.SDK_INT<26
                || android.animation.ValueAnimator.areAnimatorsEnabled())) {
            instruments.chargingGlint(c,w,h,fraction,android.os.SystemClock.uptimeMillis());
            postInvalidateDelayed(100);
        }
        float value=Math.min(40*density,u*.23f), caption=Math.min(14*density,u*.085f);
        paint.setShadowLayer(Math.max(1,density*1.5f),0,density,0xd8000000);
        text(c,reading,w*.50f,h*.51f,value,0xfffafbf5,true,Paint.Align.CENTER);
        text(c,detail,w*.50f,h*.51f+caption*1.7f,caption,0xffe4e8e0,false,Paint.Align.CENTER);
        paint.clearShadowLayer();
    }

    private void drive(Canvas c,float w,float h) {
        instruments.draw(c,getWidth(),getHeight(),kind,0);
        boolean wide=w>h;
        float u=Math.min(w,h), left=(wide?w*.465f:u*.086f)+u*.046f;
        float right=w-u*.128f,room=right-left;
        float top=wide?h*.113f:h*.54f,bottom=h-u*.108f,panel=bottom-top;
        float small=Math.min(15*density,Math.min(room*.13f,panel*.135f));
        text(c,"可用空间",left,top+panel*.22f,small,0xff535952,true,Paint.Align.LEFT);
        float size=Math.min(47*density,Math.min(room*.245f,panel*.35f));
        text(c,fit(reading,room,size,true),left,top+panel*.60f,size,0xff252b29,true,Paint.Align.LEFT);
        String capacity=detail.replace("可用空间 · ","").replace("数据分区可用 · ","数据分区");
        text(c,fit(capacity,room,small,false),left,top+panel*.81f,small,0xff555b54,false,Paint.Align.LEFT);
        float pitch=room/20f;
        int used=Math.round(fraction*20);
        for(int i=0;i<20;i++) {
            paint.setColor(i<used?0xff596b6c:0xffb9bcb2);
            c.drawRect(left+i*pitch,top+panel*.895f,left+(i+.83f)*pitch,top+panel*.93f,paint);
        }
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

    private void refreshWeather() {
        forecast=WeatherBridge.getTabletForecast(getContext());
        String code=forecast==null?"99":forecast.getString("weatherCode","99");
        try {weatherCode=Integer.parseInt(code);} catch(NumberFormatException invalid) {weatherCode=99;}
        String art=weatherArt(code);
        if(!art.equals(skyArt)) {sky=asset(TEXTURES+"weather/"+art+".png");skyArt=art;}
        weatherCity=forecast==null?"天气":forecast.getString("city","");
        if(weatherCity.isEmpty()) weatherCity="天气";
        condition=forecast==null?"选择城市或更新天气":QuickDesktopContentView.weatherDescription(code);
        String feels=forecast==null?"":forecast.getString("feelsLike","");
        feelsLike=feels.isEmpty()?"":"体感 "+feels+"°";
        long stamp=forecast==null?0:forecast.getLong("updatedAt",0);
        updated="";
        if(stamp>0){
            Context ctx=getContext();
            int flags=android.text.format.DateUtils.FORMAT_SHOW_TIME;
            if(!android.text.format.DateUtils.isToday(stamp)) flags|=android.text.format.DateUtils.FORMAT_SHOW_DATE;
            updated=android.text.format.DateUtils.formatDateTime(ctx,stamp,flags)+" 更新";
        }
        String temperature=forecast==null?"":forecast.getString("temp","")+"°";
        setContentDescription("天气，"+weatherCity+"，"+condition+"，"+temperature+" "+feelsLike);
    }

    private void weather(Canvas c,float w,float h){
        instruments.draw(c,getWidth(),getHeight(),kind,weatherCode);
        float u=Math.min(w,h),pad=Math.min(23*density,u*.11f);
        float label=Math.min(17*density,u*.095f),small=Math.min(13*density,u*.075f);
        float size=Math.min(62*density,Math.min(h*.32f,w*.29f));
        float icon=Math.min(56*density,u*.27f);
        float cityWidth=w-2*pad;
        String city=fit(weatherCity,cityWidth,label,true);
        paint.setShadowLayer(Math.max(1,density),0,density*.6f,0x66314969);
        text(c,city,pad,pad+label*.83f,label,Color.WHITE,true,Paint.Align.LEFT);
        text(c,fit(updated,cityWidth,small,false),pad,pad+label*.83f+small*1.48f,small,0xffedf5ff,false,Paint.Align.LEFT);
        String temp=forecast==null?"—":forecast.getString("temp","—")+"°";
        text(c,temp,pad,h*.69f,size,Color.WHITE,true,Paint.Align.LEFT);
        String summary=feelsLike.isEmpty()?condition:condition+"  ·  "+feelsLike;
        text(c,fit(summary,w-2*pad,label,false),pad,h-pad*.94f,label,Color.WHITE,false,Paint.Align.LEFT);
        paint.clearShadowLayer();
        if(forecast!=null) artwork(c,sky,w-pad-icon,h*.55f-icon*.48f,icon,icon);
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
