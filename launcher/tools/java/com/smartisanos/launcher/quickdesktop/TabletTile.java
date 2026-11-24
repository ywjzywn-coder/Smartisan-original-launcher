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
    static final int CALENDAR = 0, CLOCK = 1, MUSIC = 2, WEATHER = 3;
    final int kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final float density;
    private QuickDesktopMediaBridge.Snapshot track;
    private static final java.util.concurrent.ExecutorService mediaWorker =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean readingMedia;
    private String nextAlarm;
    private Bundle forecast;
    private Bitmap sky;
    private String skyArt;
    private Bitmap face, hourHand, minuteHand, hub, musicCover, paperPad, bindings, dateArt;
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
        mediaWorker.execute(new Runnable(){public void run(){
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
        int top=paper ? 0xfff5f1e7 : 0xff40474c, bottom=paper ? 0xffd9d4c9 : 0xff25292e;
        paint.setColor(-1);
        paint.setShader(new LinearGradient(0,0,0,h,top,bottom,Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,paint);
        paint.setShader(null);
        Calendar now=Calendar.getInstance();
        if (paper) calendar(c,w,h,now);
        else if (kind==CLOCK) clock(c,w,h,now);
        else if (kind==MUSIC) music(c,w,h);
        else weather(c,w,h);
        paint.setColor(0x44ffffff);
        c.drawRect(1,1,w-1,2,paint);
        paint.setColor(0x66000000);
        c.drawRect(0,h-2,w,h,paint);
    }

    private void calendar(Canvas c,float w,float h,Calendar now) {
        float header=Math.min(h*.26f,60*density), col=w/7;
        paint.setColor(-1);
        paint.setShader(new LinearGradient(0,0,0,header,0xff904b43,0xff632d29,Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,header,paint);
        paint.setShader(null);
        paint.setColor(0xffb4786c); c.drawRect(0,1,w,2,paint);
        // Leave a narrow edge of the sheets visible under the printed month.
        float edge = Math.max(1f, density);
        for (int sheet = 1; sheet <= 3; sheet++) {
            float y = h - sheet * 2 * edge;
            paint.setColor(0xffbab4a7); c.drawRect(0,y,w,y+edge,paint);
            paint.setColor(0xffebe7dc); c.drawRect(0,y+edge,w,y+2*edge,paint);
        }
        float side=header*.92f, left=w*.045f, top=(header-side)/2;
        artwork(c,paperPad,left,top,side,side);
        artwork(c,dateArt,left+side*.22f,top+side*.34f,side*.56f,side*.43f);
        artwork(c,bindings,left,top,side,side);
        String title=android.text.format.DateFormat.format(
                android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(),"yyyyMMMM"),now).toString();
        text(c,title,left+side+w*.035f,header*.60f,Math.min(header*.37f,w*.087f),0xfffaf4e9,true,Paint.Align.LEFT);
        paint.setColor(0x66615652); c.drawRect(0,header,w,header+1,paint);
        int today=now.get(Calendar.DAY_OF_MONTH), first=now.getFirstDayOfWeek();
        Calendar month=(Calendar)now.clone(); month.set(Calendar.DAY_OF_MONTH,1);
        int shift=(month.get(Calendar.DAY_OF_WEEK)-first+7)%7;
        String[] days=new DateFormatSymbols().getShortWeekdays();
        int weeks=(shift+month.getActualMaximum(Calendar.DAY_OF_MONTH)+6)/7;
        float step=(h-header-6*edge)/(weeks+1f);
        for (int i=0;i<7;i++) {
            int weekday=(first-1+i)%7+1;
            text(c,days[weekday],(i+.5f)*col,header+.65f*step,
                    Math.min(12*density,step*.56f),0xff796f66,false,Paint.Align.CENTER);
        }
        for (int day=1;day<=month.getActualMaximum(Calendar.DAY_OF_MONTH);day++) {
            int index=shift+day-1;
            float x=(index%7+.5f)*col, y=header+(1.5f+index/7)*step;
            if (day==today) {
                paint.setColor(0xff933d36); c.drawCircle(x,y,Math.min(col,step)*.43f,paint);
            }
            float size=Math.min(17*density,step*.68f);
            text(c,String.valueOf(day),x,y+size*.34f,size,day==today ? -1 : 0xff393633,
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
        boolean wide=w>=h;
        float side=Math.min(Math.min(w,h)*.64f,108*density);
        float x=wide?w*.25f:w*.5f,y=wide?h*.5f:h*.34f;
        artwork(c,sky,x-side/2,y-side/2,side,side);
        float tx=wide?w*.68f:w*.5f,ty=wide?h*.50f:h*.72f;
        String temp=forecast==null?"天气":forecast.getString("temp","—")+"°";
        String caption=forecast==null?"选择城市或更新天气":forecast.getString("city","")+"  "+
                QuickDesktopContentView.weatherDescription(forecast.getString("weatherCode","99"));
        text(c,temp,tx,ty,Math.min(32*density,w*.15f),0xfff8f3e9,false,Paint.Align.CENTER);
        float size=Math.min(14*density,w*.072f);
        paint.setTextSize(size);
        int end=paint.breakText(caption,true,w*(wide?.55f:.94f),null);
        text(c,caption.substring(0,end),tx,ty+size*1.45f,size,0xffd7d7cf,false,Paint.Align.CENTER);
    }

    private void music(Canvas c,float w,float h) {
        boolean wide=w>=h;
        float r=Math.min(w,h)*.29f, x=wide?w*.24f:w*.5f, y=wide?h*.5f:h*.34f;
        Bitmap cover=track!=null && track.artwork!=null ? track.artwork : musicCover;
        if(cover!=null){
            int side=Math.min(cover.getWidth(),cover.getHeight());
            int left=(cover.getWidth()-side)/2,top=(cover.getHeight()-side)/2;
            paint.setColor(-1);paint.setFilterBitmap(true);
            c.drawBitmap(cover,new Rect(left,top,left+side,top+side),new RectF(x-r,y-r,x+r,y+r),paint);
        }
        float tx=wide?w*.68f:w*.50f, ty=wide?h*.48f:h*.72f;
        String title=track!=null && track.sessionAvailable ? track.title : "音乐";
        float size=Math.min(19*density,w*.09f);
        paint.setTextSize(size); paint.setTypeface(Typeface.DEFAULT_BOLD);
        int n=paint.breakText(title,true,w*(wide?.54f:.90f),null);
        text(c,title.substring(0,n),tx,ty,size,0xfff6f2ec,true,Paint.Align.CENTER);
        String sub=track!=null && track.sessionAvailable ? (track.playing?"Ⅱ  ":"▶  ")+track.artist
                : track!=null && !track.notificationAccess?"授权显示正在播放":"打开音乐";
        paint.setTextSize(size*.7f);
        n=paint.breakText(sub,true,w*(wide?.53f:.9f),null);
        text(c,sub.substring(0,n),tx,ty+size*1.15f,size*.7f,0xffc3c4c1,false,Paint.Align.CENTER);
    }
}
