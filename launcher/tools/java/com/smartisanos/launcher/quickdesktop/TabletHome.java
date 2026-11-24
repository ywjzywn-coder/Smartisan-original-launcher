package com.smartisanos.launcher.quickdesktop;

import android.app.Activity;
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
    private final TabletTile[] tiles = new TabletTile[TabletSlots.KINDS];
    private int positions;
    private final LinearLayout recent;
    private final Runnable render = new Runnable() { public void run() { arrange(); } };
    private volatile Geometry state;
    private int recentGeneration;
    private MotionEvent touchDown;
    private boolean replaying, held;
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
        TabletPhoto.recover(context);
        positions=TabletSlots.positions(prefs);
        for(int k=0;k<tiles.length;k++){
            tiles[k]=new TabletTile(context,k);
            tiles[k].setOnLongClickListener(new OnLongClickListener(){public boolean onLongClick(View tile) {
                tileMenu((TabletTile)tile); return true;
            }});
            tiles[k].setVisibility(GONE);
            addView(tiles[k]);
        }
        recent=new LinearLayout(context);
        recent.setGravity(Gravity.CENTER_VERTICAL);
        addView(recent);
        setVisibility(INVISIBLE);
    }
    public static void setPinnedCount(int count) {
        if (pinned==count) return;
        pinned=count;
        TabletLayout.invalidateDock();
        TabletHome panel=active.get();
        if (panel!=null) panel.post(new Runnable() { public void run() { panel.arrange(); panel.loadRecent(); } });
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
            TabletLayout.invalidateDock();
            panel.post(panel.render);
        }
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
        super.onSizeChanged(w,h,oldW,oldH);
        post(render);
    }
    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        post(render);
        return super.onApplyWindowInsets(insets);
    }

    private void updateNavigation() {
        WindowInsets insets = getRootWindowInsets();
        if (insets == null) return;
        int left, right, bottom;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars());
            left = bars.left; right = bars.right; bottom = bars.bottom;
        } else {
            left = insets.getStableInsetLeft(); right = insets.getStableInsetRight();
            bottom = insets.getStableInsetBottom();
        }
        TabletLayout.navigation(getResources().getConfiguration().orientation, left, right, bottom);
    }
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) { loadRecent(); post(render); }
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); loadRecent(); }
    @Override protected void onDetachedFromWindow() {
        recentGeneration++;
        endTouch();
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
        updateNavigation();
        Geometry g=state;
        int width=getWidth(), height=getHeight();
        if (g==null || width<=0 || height<=0 || g.cols*g.rows!=12 && g.cols*g.rows!=20
                || (width>height)!=(g.cols>g.rows)) {setVisibility(INVISIBLE);return;}
        boolean entering = getVisibility() != VISIBLE;
        setVisibility(VISIBLE);
        if (entering) loadRecent();
        int[] origin=new int[2]; getLocationOnScreen(origin);
        positions=TabletSlots.positions(prefs);
        for(int i=0;i<tiles.length;i++) {
            View tile=tiles[i];
            int slot=TabletSlots.slot(positions,i);
            if(g.page!=0 || !TabletSlots.fits(positions,i,slot)){tile.setVisibility(GONE);continue;}
            Rect r=TabletSlots.rect(g.cols*g.rows,width>height,i,slot);
            int mask=TabletSlots.bits(r,g.cols);
            tile.setVisibility((mask & g.occupied)==0 ? VISIBLE : GONE);
            float pad=Math.max(1,dp(.65f));
            place(tile,(width-g.cols*g.cw)*.5f+r.left*g.cw+pad,g.top-origin[1]+r.top*g.ch+pad,
                    r.width()*g.cw-2*pad,r.height()*g.ch-2*pad);
        }
        int count=Math.min(TabletLayout.recentLimit(width),Math.max(0,recent.getChildCount()-1));
        TabletLayout.recentCount(count);
        float pitch=TabletLayout.dockPitch(width,pinned);
        float start=TabletLayout.dockLeft(width,pinned)+pinned*pitch;
        int gap=pinned>0 && count>0 ? Math.round(pitch*.25f) : 0;
        float bottom=TabletLayout.bottomInset();
        float content=g.dock-bottom;
        place(recent,start,height-g.dock,count*Math.round(pitch)+gap,content);
        if (recent.getChildCount()>0) {
            View divider=recent.getChildAt(0);
            LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)divider.getLayoutParams();
            int line=gap>0 ? dp(1):0;
            int inset=(gap-line)/2;
            if (lp.width!=line || lp.leftMargin!=inset || lp.rightMargin!=gap-line-inset) {
                lp.width=line; lp.leftMargin=inset; lp.rightMargin=gap-line-inset;
                divider.setLayoutParams(lp);
            }
        }
        for (int i=1;i<recent.getChildCount();i++) {
            View v=recent.getChildAt(i);
            v.setVisibility(i<=count ? VISIBLE:GONE);
            LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)v.getLayoutParams();
            if(lp.width!=Math.round(pitch)) {lp.width=Math.round(pitch);v.setLayoutParams(lp);}
        }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) { return false; }

    // RootView owns the GL scene. Delay its DOWN only for interactive overlay cells,
    // then replay it before the first swipe or second pointer to preserve native gestures.
    public static boolean handleTouch(ViewGroup root, MotionEvent event) {
        TabletHome panel=active.get();
        return panel!=null && panel.getParent()==root && !panel.replaying && panel.touch(root,event);
    }

    private boolean emptyHomeCell(float x,float y) {
        Geometry g=state;
        if (g==null || g.page!=0 || g.cw<=0 || g.ch<=0) return false;
        int[] origin=new int[2]; getLocationOnScreen(origin);
        float col=(x-origin[0]-(getWidth()-g.cols*g.cw)*.5f)/g.cw;
        float row=(y-g.top)/g.ch;
        if (col>=0 && row>=0 && col<g.cols && row<g.rows)
            return (g.occupied & (1 << ((int)row*g.cols+(int)col)))==0;
        float dockY=y-origin[1]-(getHeight()-g.dock);
        if (dockY<0 || dockY>=g.dock-TabletLayout.bottomInset()) return false;
        float left=TabletLayout.dockLeft(getWidth(),pinned);
        return x-origin[0]<left || x-origin[0]>getWidth()-left;
    }
    private void endTouch() {
        removeCallbacks(longPress);
        if(touchDown!=null){touchDown.recycle();touchDown=null;}
        candidate=null;
    }
    private final Runnable longPress=new Runnable(){public void run(){
        if(touchDown==null || !isShown() || !hasWindowFocus() || state==null) return;
        held=true;
        if(candidate==TabletHome.this) openLibrary(-1);
        else if(candidate!=null && candidate.isShown()) candidate.performLongClick();
    }};
    private boolean touch(ViewGroup root,MotionEvent event) {
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN) {
            endTouch(); held=false;
            if (!isShown() || !hasWindowFocus() || state==null) return false;
            float x=event.getRawX(), y=event.getRawY();
            candidate=at(x,y);
            if(candidate==null && emptyHomeCell(x,y)) candidate=this;
            if(candidate==null) return false;
            touchDown=MotionEvent.obtain(event);
            postDelayed(longPress,ViewConfiguration.getLongPressTimeout());
            return true;
        }
        if(touchDown==null) return false;
        int slop=ViewConfiguration.get(getContext()).getScaledTouchSlop();
        if(!held && (action==MotionEvent.ACTION_POINTER_DOWN || (action==MotionEvent.ACTION_MOVE &&
                (Math.abs(event.getX()-touchDown.getX())>slop || Math.abs(event.getY()-touchDown.getY())>slop)))) {
            replaying=true;
            try {root.dispatchTouchEvent(touchDown);} finally {replaying=false;endTouch();}
            return false;
        }
        if(action==MotionEvent.ACTION_CANCEL || action==MotionEvent.ACTION_UP) {
            View target=candidate;
            boolean click=action==MotionEvent.ACTION_UP && !held && target!=this && target.isShown()
                    && target==at(event.getRawX(),event.getRawY());
            endTouch();
            if(click) target.performClick();
        }
        return true;
    }

    private Activity activity() {
        Context context=getContext();
        while(context instanceof ContextWrapper && !(context instanceof Activity))
            context=((ContextWrapper)context).getBaseContext();
        return context instanceof Activity ? (Activity)context : null;
    }

    private void openLibrary(int kind) {
        Activity home=activity();
        if(home!=null) new TabletTileLibrary(home,kind).show();
    }
    private void tileMenu(final TabletTile tile) {
        final Activity home=activity();
        if(home==null) return;
        MaintainedLauncherSettingsHost.showSmartisanSingleChoiceDialog(home,TabletTileLibrary.NAMES[tile.kind],
                tile.kind==TabletTile.PHOTO?new String[]{"调整位置","移除组件","添加组件","更换照片"}
                        :new String[]{"调整位置","移除组件","添加组件"},-1,
                new MaintainedLauncherSettingsHost.SingleChoiceListener() {
                    public void onSelected(int index) {
                        if(index==1) {
                            int layout=TabletSlots.positions(prefs);
                            TabletTileLibrary.apply(home,prefs,layout,TabletSlots.put(layout,tile.kind,-1));
                        } else if(index==3) TabletPhoto.choose(home,TabletPhoto.REPLACE);
                        else openLibrary(index==0?tile.kind:-1);
                    }
                });
    }

    private View at(float x,float y) {
        if (state==null) return null;
        for (View tile:tiles) if(contains(tile,x,y)) return tile;
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
    private static synchronized void forgetRecent(Context context, RecentApp item) {
        SharedPreferences history = context.getSharedPreferences(HISTORY, 0);
        List<String> order = new ArrayList<>(Arrays.asList(history.getString("order","").split("\n")));
        order.remove(item.user + "|" + item.cn.flattenToString());
        history.edit().putString("order", TextUtils.join("\n", order)).apply();
    }

    private void recentMenu(final RecentApp item) {
        Activity home=activity();
        if(home==null) return;
        MaintainedLauncherSettingsHost.showSmartisanSingleChoiceDialog(home,item.label,
                new String[]{"固定到常驻栏","从最近使用中移除"},-1,
                new MaintainedLauncherSettingsHost.SingleChoiceListener() {
                    public void onSelected(int index) {
                        if(index==0) {
                            String message=TabletSlots.requestPin(getContext(),item.cn,item.user,pinned);
                            if(message!=null) Toast.makeText(getContext(),message,Toast.LENGTH_LONG).show();
                        } else {
                            forgetRecent(getContext(),item);
                            loadRecent();
                        }
                    }
                });
    }
    private void loadRecent() {
        final int generation=++recentGeneration;
        Context app=getContext().getApplicationContext();
        String history=app.getSharedPreferences(HISTORY,0).getString("order","");
        worker.execute(new Runnable() { public void run() {
            List<RecentApp> entries=new ArrayList<>();
            LauncherApps apps=(LauncherApps)app.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            List<UserHandle> profiles;
            try {
                profiles = MaintainedLauncherSettingsHost.launcherProfiles(app, apps);
            } catch (SecurityException restricted) {
                profiles = Collections.singletonList(android.os.Process.myUserHandle());
            }
            for(String token:history.split("\n")) {
                int split=token.indexOf('|');
                if(split<=0 || apps==null) continue;
                try {
                    int user=Integer.parseInt(token.substring(0,split));
                    ComponentName cn=ComponentName.unflattenFromString(token.substring(split+1));
                    if(cn==null || !TabletSlots.mayShowRecent(cn.getPackageName(),cn.getClassName(),user)) continue;
                    for(UserHandle profile:profiles) {
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
            LinearLayout.LayoutParams rule=new LinearLayout.LayoutParams(dp(1),dp(24));
            recent.addView(divider,rule);
        }
        for(RecentApp item:entries) {
            LinearLayout button=new LinearLayout(getContext());
            button.setOrientation(LinearLayout.VERTICAL);button.setGravity(Gravity.CENTER);
            button.setContentDescription("最近使用，"+item.label);
            ImageView image=new ImageView(getContext());image.setImageDrawable(item.icon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int size=dp(TabletLayout.DOCK_ICON_DP);
            button.addView(image,new LinearLayout.LayoutParams(size,size));
            recent.addView(button,new LinearLayout.LayoutParams(dp(80),-1));
            button.setOnLongClickListener(new OnLongClickListener() {
                public boolean onLongClick(View anchor) { recentMenu(item); return true; }
            });
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
        int layout=TabletSlots.positions(prefs);
        twelve.setPreviewBitmap(preview(activity,12,layout));
        twenty.setPreviewBitmap(preview(activity,20,layout));
        ViewGroup row=(ViewGroup)twelve.getParent();
        if(!(row.getParent() instanceof LinearLayout)) return;
        LinearLayout parent=(LinearLayout)row.getParent();
        if(parent.findViewWithTag(TabletSlots.SHOW)!=null) return;
        Context settings=twelve.getContext();
        android.content.res.Resources res=settings.getResources();
        float scale=res.getDisplayMetrics().density;
        FrameLayout control=new FrameLayout(settings);
        control.setTag(TabletSlots.SHOW);
        int background=res.getIdentifier("selector_setting_sub_item_bg_single","drawable","com.smartisanos.home");
        if(background!=0)control.setBackgroundResource(background);
        TextView title=new TextView(settings);
        title.setText("桌面组件");title.setTextSize(18);title.setTextColor(0xff454a5c);
        title.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        title.setPadding(Math.round(30*scale),0,0,0);
        control.addView(title,new FrameLayout.LayoutParams(-1,-1));
        ImageView arrow=new ImageView(settings);
        arrow.setImageResource(res.getIdentifier("setting_next","drawable","com.smartisanos.home"));
        control.addView(arrow,new FrameLayout.LayoutParams(-2,-2,Gravity.RIGHT|Gravity.CENTER_VERTICAL));
        parent.addView(control,parent.indexOfChild(row)+1,new LinearLayout.LayoutParams(-1,Math.round(64*scale)));
        control.setOnClickListener(new OnClickListener(){public void onClick(View v){
            new TabletTileLibrary(activity,-1).show();
        }});
    }
    private static Bitmap preview(Activity a,int count,int layout) {
        android.util.DisplayMetrics metrics=a.getResources().getDisplayMetrics();
        boolean land=metrics.widthPixels>metrics.heightPixels;
        int w=240, h=Math.max(140,Math.min(400,Math.round(w*metrics.heightPixels/(float)metrics.widthPixels)));
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawColor(0xfff1f2f3);
        int cols=count==12?(land?4:3):(land?5:4);
        int rows=count/cols;float cw=w/(float)cols,ch=h*.84f/rows;
        for(int i=0;i<count;i++) {
            int x=i%cols,y=i/cols;p.setColor(0xffe3e4e6);c.drawRect(x*cw+1,y*ch+1,(x+1)*cw,(y+1)*ch,p);
            float cx=(x+.5f)*cw,cy=(y+.5f)*ch,r=Math.min(cw,ch)*.15f;p.setColor(0xffc7cbd0);
            c.drawRoundRect(cx-r,cy-r,cx+r,cy+r,2,2,p);
        }
        for(int kind=0;kind<TabletSlots.KINDS;kind++){
            int slot=TabletSlots.slot(layout,kind);
            if(!TabletSlots.fits(layout,kind,slot)) continue;
            Rect r=TabletSlots.rect(count,land,kind,slot);
            p.setColor(kind==0?0xfffafafa:0xffc2c8cf);
            c.drawRect(r.left*cw+1,r.top*ch+1,r.right*cw,r.bottom*ch,p);
            if(kind==0){p.setColor(0xffdbdde0);c.drawRect(r.left*cw+1,r.top*ch+1,r.right*cw,(r.top+.35f)*ch,p);}
        }
        p.setColor(0xffa9adad);
        for(int i=0;i<5;i++) c.drawCircle(w*(.25f+i*.12f+(i>2?.035f:0)),h*.92f,Math.min(5,h*.045f),p);
        return b;
    }
}
