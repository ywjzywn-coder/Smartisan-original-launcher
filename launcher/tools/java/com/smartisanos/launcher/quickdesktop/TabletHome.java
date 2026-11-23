package com.smartisanos.launcher.quickdesktop;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import com.smartisanos.home.settings.PreviewSettingItemView;
import com.smartisanos.launcher.backup.TabletSlots;
import com.smartisanos.launcher.data.TabletLayout;
import com.smartisanos.launcher.reload.LauncherColdReloadCoordinator;
import com.smartisanos.launcher.theme.MaintainedLauncherSettingsHost;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Composes live tiles and recent launch targets over the original workspace. */
public final class TabletHome extends FrameLayout {
    private static volatile WeakReference<TabletHome> active = new WeakReference<>(null);
    private static volatile int pinned = 2;
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final String HISTORY = "tablet_recent_launches";
    private final SharedPreferences prefs;
    private final TabletTile calendar, companion;
    private final LinearLayout recent;
    private final Runnable render = new Runnable() { public void run() { arrange(); } };
    private volatile Geometry state;
    private int recentGeneration;
    private float downX, downY;
    private View candidate;

    private static final class Geometry {
        final int page, occupied, cols, rows;
        final float cw,ch,top,dock;
        Geometry(int page,int occupied,int cols,int rows,float cw,float ch,float top,float dock) {
            this.page=page;this.occupied=occupied;this.cols=cols;this.rows=rows;
            this.cw=cw;this.ch=ch;this.top=top;this.dock=dock;
        }
        boolean matches(int p,int mask,int c,int r,float w,float h,float t,float d) {
            return page==p && occupied==mask && cols==c && rows==r && cw==w && ch==h && top==t && dock==d;
        }
    }

    public static void attach(ViewGroup root) {
        if (!TabletLayout.isTablet(root.getResources().getConfiguration())) return;
        TabletHome panel=new TabletHome(root.getContext());
        root.addView(panel,new ViewGroup.LayoutParams(-1,-1));
        active=new WeakReference<>(panel);
    }
    private TabletHome(Context context) {
        super(context);
        prefs=context.getSharedPreferences(TabletSlots.PREF,0);
        calendar=new TabletTile(context,TabletTile.CALENDAR);
        companion=new TabletTile(context,prefs.getBoolean(TabletSlots.MUSIC,false) ? TabletTile.MUSIC : TabletTile.CLOCK);
        recent=new LinearLayout(context);
        recent.setGravity(Gravity.CENTER_VERTICAL);
        addView(calendar);addView(companion);addView(recent);
        setVisibility(INVISIBLE);
    }
    public static void setPinnedCount(int count) {
        if (pinned==count) return;
        pinned=count;
        TabletHome panel=active.get();
        if (panel!=null) panel.post(panel.render);
    }
    public static void scene(int page,int occupied,int cols,int rows,float cw,float ch,float top,float dock) {
        TabletHome panel=active.get();
        if (panel==null) return;
        Geometry old=panel.state;
        if (old!=null && old.matches(page,occupied,cols,rows,cw,ch,top,dock)) return;
        panel.state=new Geometry(page,occupied,cols,rows,cw,ch,top,dock);
        panel.post(panel.render);
    }
    public static void hideScene() {
        TabletHome panel=active.get();
        if (panel!=null && panel.state!=null) {
            panel.state=null;
            panel.post(panel.render);
        }
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
        super.onSizeChanged(w,h,oldW,oldH);
        post(render);
    }
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) { loadRecent(); post(render); }
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); loadRecent(); }
    @Override protected void onDetachedFromWindow() {
        recentGeneration++;
        removeCallbacks(render);
        super.onDetachedFromWindow();
    }

    private int dp(float value) {return Math.round(value*getResources().getDisplayMetrics().density);}
    private static void place(View view,float left,float top,float width,float height) {
        LayoutParams p=(LayoutParams)view.getLayoutParams();
        int x=Math.round(left), y=Math.round(top), w=Math.max(1,Math.round(width)), h=Math.max(1,Math.round(height));
        if(p.leftMargin==x && p.topMargin==y && p.width==w && p.height==h) return;
        p.leftMargin=x;p.topMargin=y;p.width=w;p.height=h;p.gravity=Gravity.TOP|Gravity.LEFT;
        view.setLayoutParams(p);
    }

    private void arrange() {
        Geometry g=state;
        int width=getWidth(), height=getHeight();
        if (g==null || width<=0 || height<=0 || g.cols*g.rows!=12 && g.cols*g.rows!=20
                || (width>height)!=(g.cols>g.rows)) {setVisibility(INVISIBLE);return;}
        boolean entering = getVisibility() != VISIBLE;
        setVisibility(VISIBLE);
        if (entering) loadRecent();
        int[] origin=new int[2]; getLocationOnScreen(origin);
        Rect[] slots=TabletSlots.rectangles(g.cols*g.rows,width>height);
        boolean show=g.page==0 && prefs.getBoolean(TabletSlots.SHOW,false);
        for(int i=0;i<slots.length;i++) {
            View tile=i==0 ? calendar : companion;
            Rect r=slots[i];
            int mask=0;
            for(int row=r.top;row<r.bottom;row++)
                for(int col=r.left;col<r.right;col++) mask |= 1 << (row*g.cols+col);
            tile.setVisibility(show && (mask & g.occupied)==0 ? VISIBLE : GONE);
            float pad=Math.max(1,dp(.65f));
            place(tile,(width-g.cols*g.cw)*.5f+r.left*g.cw+pad,g.top-origin[1]+r.top*g.ch+pad,
                    r.width()*g.cw-2*pad,r.height()*g.ch-2*pad);
        }
        int count=width/density()>=820 ? 3 : 2;
        float pitch=TabletLayout.dockPitch(width,pinned);
        float start=TabletLayout.dockLeft(width,pinned)+(pinned+.25f)*pitch;
        float bottom=TabletLayout.bottomInset();
        float content=g.dock-bottom;
        place(recent,start,height-g.dock+dp(8),(count+.25f)*pitch,content);
        for (int i=1;i<recent.getChildCount();i++) {
            View v=recent.getChildAt(i);
            v.setVisibility(i<=count ? VISIBLE:GONE);
            LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)v.getLayoutParams();
            if(lp.width!=Math.round(pitch)) {lp.width=Math.round(pitch);v.setLayoutParams(lp);}
        }
    }
    private float density(){return getResources().getDisplayMetrics().density;}

    // Let RootView and its GL surface own every swipe, drag and long press.
    @Override public boolean dispatchTouchEvent(MotionEvent e) {return false;}
    public static void observeTouch(ViewGroup root, MotionEvent event) {
        TabletHome panel=active.get();
        if(panel==null || panel.getParent()!=root || !panel.isShown() || !panel.hasWindowFocus()) return;
        int action=event.getActionMasked();
        float x=event.getRawX(), y=event.getRawY();
        if(action==MotionEvent.ACTION_DOWN) {
            panel.downX=x;panel.downY=y;panel.candidate=panel.at(x,y);
        } else if(action==MotionEvent.ACTION_MOVE) {
            int slop=ViewConfiguration.get(root.getContext()).getScaledTouchSlop();
            if(Math.abs(x-panel.downX)>slop || Math.abs(y-panel.downY)>slop) panel.candidate=null;
        } else if(action==MotionEvent.ACTION_UP) {
            View target=panel.candidate;panel.candidate=null;
            if(target!=null && target==panel.at(x,y) &&
                    event.getEventTime()-event.getDownTime()<ViewConfiguration.getLongPressTimeout()) {
                event.setAction(MotionEvent.ACTION_CANCEL);
                target.post(new Runnable() { public void run() { target.performClick(); } });
            }
        } else {panel.candidate=null;}
    }
    private View at(float x,float y) {
        if (state==null) return null;
        if (contains(calendar,x,y)) return calendar;
        if (contains(companion,x,y)) return companion;
        for(int i=1;i<recent.getChildCount();i++) {
            View child=recent.getChildAt(i);
            if(contains(child,x,y)) return child;
        }
        return null;
    }
    private static boolean contains(View v,float x,float y) {
        if (!v.isShown()) return false;
        int[] origin = new int[2];
        v.getLocationOnScreen(origin);
        return x >= origin[0] && x < origin[0] + v.getWidth()
                && y >= origin[1] && y < origin[1] + v.getHeight();
    }

    public static synchronized void record(Context context, Intent intent,int user) {
        ComponentName cn=intent.getComponent();
        if(cn==null || cn.getPackageName().equals(context.getPackageName())
                || !TabletLayout.isTablet(context.getResources().getConfiguration())) return;
        String token=Math.max(0,user)+"|"+cn.flattenToString();
        SharedPreferences history=context.getSharedPreferences(HISTORY,0);
        ArrayList<String> list=new ArrayList<>(Arrays.asList(history.getString("order","").split("\n")));
        list.remove("");list.remove(token);list.add(0,token);
        history.edit().putString("order",TextUtils.join("\n",list.subList(0,Math.min(20,list.size())))).apply();
        TabletHome panel=active.get();
        if(panel!=null) panel.post(new Runnable() { public void run() { panel.loadRecent(); } });
    }
    private static final class RecentApp {
        ComponentName cn; int user; Drawable icon; String label;
    }
    private void loadRecent() {
        final int generation=++recentGeneration;
        Context app=getContext().getApplicationContext();
        String history=app.getSharedPreferences(HISTORY,0).getString("order","");
        worker.execute(new Runnable() { public void run() {
            List<RecentApp> entries=new ArrayList<>();
            LauncherApps apps=(LauncherApps)app.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            for(String token:history.split("\n")) {
                int split=token.indexOf('|');
                if(split<=0 || apps==null) continue;
                try {
                    int user=Integer.parseInt(token.substring(0,split));
                    ComponentName cn=ComponentName.unflattenFromString(token.substring(split+1));
                    if(cn==null || !TabletSlots.mayShowRecent(cn.getPackageName(),cn.getClassName(),user)) continue;
                    for(UserHandle profile:apps.getProfiles()) {
                        for(LauncherActivityInfo info:apps.getActivityList(cn.getPackageName(),profile)) {
                            if(!info.getComponentName().equals(cn) || info.getApplicationInfo().uid/100000!=user) continue;
                            RecentApp item=new RecentApp();
                            item.cn=cn;item.user=user;item.label=info.getLabel().toString();
                            item.icon=MaintainedLauncherSettingsHost.loadIconForDesktopItem(app,cn.getPackageName(),cn.getClassName(),item.label,user);
                            if(item.icon==null) item.icon=info.getBadgedIcon(0);
                            entries.add(item);
                        }
                    }
                } catch(RuntimeException ignored) { /* Removed or unavailable profile. */ }
                if(entries.size()>=3) break;
            }
            post(new Runnable() { public void run() { if(generation==recentGeneration && isAttachedToWindow()) bindRecents(entries); }});
        }});
    }

    private void bindRecents(List<RecentApp> entries) {
        recent.removeAllViews();
        if(!entries.isEmpty()) {
            View divider=new View(getContext()); divider.setBackgroundColor(0x66858889);
            LinearLayout.LayoutParams rule=new LinearLayout.LayoutParams(dp(1),dp(34));
            rule.rightMargin=dp(9); recent.addView(divider,rule);
        }
        for(RecentApp item:entries) {
            LinearLayout button=new LinearLayout(getContext());
            button.setOrientation(LinearLayout.VERTICAL);button.setGravity(Gravity.CENTER);
            button.setContentDescription("最近使用，"+item.label);
            ImageView image=new ImageView(getContext());image.setImageDrawable(item.icon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            button.addView(image,new LinearLayout.LayoutParams(dp(54),dp(54)));
            TextView label=new TextView(getContext());label.setText(item.label);label.setTextSize(14);
            label.setTextColor(0xffeeeeee);label.setGravity(Gravity.CENTER);
            label.setShadowLayer(2,0,1,0xff222222);label.setSingleLine();
            label.setEllipsize(TextUtils.TruncateAt.END);
            if(!prefs.getBoolean("launcher_hide_lable",false)) button.addView(label,new LinearLayout.LayoutParams(-1,-2));
            recent.addView(button,new LinearLayout.LayoutParams(dp(80),-1));
            button.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                if(!TabletSlots.mayShowRecent(item.cn.getPackageName(),item.cn.getClassName(),item.user)) {loadRecent();return;}
                Intent launch=Intent.makeMainActivity(item.cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    MaintainedLauncherSettingsHost.startActivityForUser(getContext(),launch,null,item.user);
                    record(getContext(),launch,item.user);
                } catch(Exception removed) { loadRecent(); }
            }});
        }
        arrange();
    }

    public static void bindSettings(Activity activity,PreviewSettingItemView twelve,PreviewSettingItemView twenty) {
        SharedPreferences prefs=activity.getSharedPreferences(TabletSlots.PREF,0);
        boolean enabled=prefs.getBoolean(TabletSlots.SHOW,false);
        twelve.setPreviewBitmap(preview(activity,12,enabled));
        twenty.setPreviewBitmap(preview(activity,20,enabled));
        ViewGroup row=(ViewGroup)twelve.getParent();
        if(!(row.getParent() instanceof LinearLayout)) return;
        LinearLayout parent=(LinearLayout)row.getParent();
        if(parent.findViewWithTag(TabletSlots.SHOW)!=null) return;
        TextView control=new TextView(activity);
        control.setTag(TabletSlots.SHOW);control.setTextColor(0xff4f5360);control.setTextSize(16);
        int pad=Math.round(16*activity.getResources().getDisplayMetrics().density);
        control.setPadding(pad,pad,pad,pad);control.setText("首页磁贴　"+(enabled ? (prefs.getBoolean(TabletSlots.MUSIC,false)?"日历和音乐":"日历和时钟") : "关闭")+"　›");
        parent.addView(control,parent.indexOfChild(row)+1);
        control.setOnClickListener(new OnClickListener() { public void onClick(View v) { new AlertDialog.Builder(activity).setTitle("首页磁贴")
                .setItems(new String[]{"关闭磁贴","四格日历 ＋ 双格时钟","四格日历 ＋ 双格音乐"}, new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog,int choice) {
                    boolean music=prefs.getBoolean(TabletSlots.MUSIC,false);
                    if((choice!=0)==enabled && (choice==0 || (choice==2)==music)) return;
                    Runnable apply=new Runnable() { public void run() {
                        if(!prefs.edit().putBoolean(TabletSlots.SHOW,choice!=0).putBoolean(TabletSlots.MUSIC,choice==2).commit()) return;
                        if(!LauncherColdReloadCoordinator.beginTabletTilesReload(activity)) {
                            prefs.edit().putBoolean(TabletSlots.SHOW,enabled).putBoolean(TabletSlots.MUSIC,music).commit();
                            Toast.makeText(activity,"桌面正在更新，请稍后再试",Toast.LENGTH_SHORT).show();
                        }
                    }};
                    if(!enabled && choice!=0) new AlertDialog.Builder(activity).setTitle("为磁贴留出六格")
                            .setMessage("首页这六格中的应用和文件夹会移到空位。当前页空间不足时，会顺延到其他页。")
                            .setNegativeButton("取消",null).setPositiveButton("启用",new DialogInterface.OnClickListener() { public void onClick(DialogInterface d,int i) { apply.run(); }}).show();
                    else apply.run();
                }}).show(); }});
    }
    private static Bitmap preview(Activity a,int count,boolean tiles) {
        android.util.DisplayMetrics metrics=a.getResources().getDisplayMetrics();
        boolean land=metrics.widthPixels>metrics.heightPixels;
        int w=240, h=Math.max(140,Math.min(400,Math.round(w*metrics.heightPixels/(float)metrics.widthPixels)));
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawColor(0xff35393c);
        int cols=count==12?(land?4:3):(land?5:4);
        int rows=count/cols;float cw=w/(float)cols,ch=h*.84f/rows;
        for(int i=0;i<count;i++) {
            int x=i%cols,y=i/cols;p.setColor(0xff44484c);c.drawRect(x*cw+1,y*ch+1,(x+1)*cw,(y+1)*ch,p);
            float cx=(x+.5f)*cw,cy=(y+.5f)*ch,r=Math.min(cw,ch)*.15f;p.setColor(0xffbec3c4);
            c.drawRoundRect(cx-r,cy-r,cx+r,cy+r,2,2,p);
        }
        if(tiles) {
            Rect[] units=TabletSlots.rectangles(count,land);
            int n=0;
            for(Rect r:units) {
                p.setColor(n++==0 ? 0xffd9d3c8 : 0xff727777);
                c.drawRect(r.left*cw+1,r.top*ch+1,r.right*cw,r.bottom*ch,p);
                if(n==1){p.setColor(0xff8c342e);c.drawRect(r.left*cw+1,r.top*ch+1,r.right*cw,(r.top+.35f)*ch,p);}
            }
        }
        p.setColor(0xffa9adad);
        for(int i=0;i<5;i++) c.drawCircle(w*(.25f+i*.12f+(i>2?.035f:0)),h*.92f,Math.min(5,h*.045f),p);
        return b;
    }
}
