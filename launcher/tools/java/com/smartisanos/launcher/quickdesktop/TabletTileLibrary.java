package com.smartisanos.launcher.quickdesktop;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.*;
import com.smartisanos.launcher.backup.TabletSlots;
import com.smartisanos.launcher.reload.LauncherColdReloadCoordinator;
import com.smartisanos.launcher.theme.MaintainedLauncherSettingsHost;
import java.io.InputStream;
import java.io.IOException;

/** An edit session owns a draft until Done. Both screen orientations share each placement. */
final class TabletTileLibrary {
    static final String[] NAMES={"日历","时钟","音乐","天气"};
    private static final String[] ART={"quickdesktop_original/calendar.png","quickdesktop_original/clock.png",
            "quickdesktop_original/music_default.png","Textures/1080p/weather/cloudy.png"};
    private final Activity activity;
    private final Dialog dialog;
    private final SharedPreferences prefs;
    private final int original, count;
    private final boolean land;
    private final LinearLayout sheet, choices;
    private final FrameLayout grid;
    private final TextView remove, heading;
    private int draft, selected;

    TabletTileLibrary(Activity a) {
        activity=a;
        prefs=a.getSharedPreferences(TabletSlots.PREF,0);
        draft=original=TabletSlots.positions(prefs);
        count=MaintainedLauncherSettingsHost.resolvePortLauncherMode(a,12);
        land=a.getResources().getConfiguration().orientation==2;
        dialog=new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        sheet=new LinearLayout(a);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackground(surface(0xff484b4d,0xff2b2d30,0xff1c1e20));
        LinearLayout bar=new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(surface(0xff5a5d60,0xff383a3d,0xff242629));
        sheet.addView(bar,-1,dp(50));
        TextView cancel=label("取消",15); bar.addView(cancel,dp(68),-1);
        cancel.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.dismiss();}});
        TextView title=label("桌面组件",18); title.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        TextView done=label("完成",15); bar.addView(done,dp(68),-1);
        done.setOnClickListener(new View.OnClickListener(){public void onClick(View v){save();}});
        ScrollView scroll=new ScrollView(a);
        sheet.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout body=new LinearLayout(a);
        body.setPadding(dp(16),dp(12),dp(16),dp(12));body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        choices=new LinearLayout(a);
        body.addView(choices,-1,dp(68));
        for(int kind=0;kind<NAMES.length;kind++) {
            final int id=kind;
            LinearLayout choice=new LinearLayout(a);choice.setGravity(Gravity.CENTER);choice.setOrientation(LinearLayout.VERTICAL);
            ImageView image=new ImageView(a);
            try(InputStream in=a.getAssets().open(ART[kind])){image.setImageBitmap(BitmapFactory.decodeStream(in));}
            catch(IOException missing){android.util.Log.w("TabletTileLibrary",ART[kind],missing);}
            choice.addView(image,dp(31),dp(31));
            choice.addView(label(NAMES[kind]+(kind==0?" · 四格":" · 双格"),13),-1,dp(23));
            choices.addView(choice,new LinearLayout.LayoutParams(0,-1,1));
            choice.setOnClickListener(new View.OnClickListener(){public void onClick(View v){selected=id;render();}});
            choice.setContentDescription(NAMES[kind]);
        }
        LinearLayout tools=new LinearLayout(a);
        body.addView(tools,-1,dp(48));
        heading=label("",14);heading.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        tools.addView(heading,new LinearLayout.LayoutParams(0,-1,1));
        remove=label("移除",14);remove.setTextColor(0xffffb7ac);tools.addView(remove,dp(64),-1);
        remove.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
            draft=TabletSlots.put(draft,selected,-1);render();
        }});
        grid=new FrameLayout(a);
        body.addView(grid,-1,dp(land?168:230));
        grid.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){
            public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){
                if(r-l!=or-ol || b-t!=ob-ot) grid.post(new Runnable(){public void run(){render();}});
            }
        });
        TextView foot=label("新增位置上的应用会移到空格。",12);
        foot.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);body.addView(foot,-1,dp(28));
        dialog.setContentView(sheet);
    }
    void show(){
        dialog.show();
        Window w=dialog.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        int width=activity.getWindow().getDecorView().getWidth();
        w.setLayout(Math.min(dp(560),width-dp(24)),-2);
        render();
    }
    private int dp(float v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}
    private TextView label(String value,int size){
        TextView text=new TextView(activity);text.setGravity(Gravity.CENTER);
        text.setTextColor(0xfff6f2ea);text.setTextSize(size);text.setText(value);return text;
    }
    private GradientDrawable surface(int top,int bottom,int border){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{top,bottom});
        d.setStroke(dp(.7f),border);d.setCornerRadius(dp(3));return d;
    }
    private void render(){
        for(int k=0;k<choices.getChildCount();k++)
            choices.getChildAt(k).setBackground(k==selected?surface(0xff92564b,0xff643b34,0xffae776b):surface(0xff494d50,0xff383b3e,0xff26282a));
        boolean added=TabletSlots.slot(draft,selected)>=0;
        heading.setText(NAMES[selected]+(added?" · 选择新的位置":" · 选择添加位置"));
        remove.setVisibility(added?View.VISIBLE:View.GONE);
        grid.removeAllViews();
        int cols=TabletSlots.columns(count,land),rows=count/cols;
        for(int cell=0;cell<count;cell++){
            int x=cell%cols,y=cell/cols;
            box(new Rect(x,y,x+1,y+1),"",0xff292c2e,0xff222427,-1,cols,rows);
        }
        for(int slot=0;slot<6;slot++){
            if(!TabletSlots.fits(draft,selected,slot) || slot==TabletSlots.slot(draft,selected)) continue;
            box(TabletSlots.rect(count,land,selected,slot),"＋",0xff555a5d,0xff45494b,slot,cols,rows);
        }
        for(int k=0;k<NAMES.length;k++){
            int slot=TabletSlots.slot(draft,k);
            if(!TabletSlots.fits(draft,k,slot)) continue;
            box(TabletSlots.rect(count,land,k,slot),NAMES[k],k==selected?0xff93584d:0xff657079,
                    k==selected?0xff664138:0xff444a50,-1,cols,rows);
        }
    }
    private void box(Rect r,String name,int top,int bottom,final int target,int cols,int rows){
        int w=grid.getWidth(), h=grid.getHeight();
        if(w<=0 || h<=0)return;
        TextView v=label(name,target>=0?22:14);
        v.setBackground(surface(top,bottom,0xff202225));
        int left=w*r.left/cols, y=h*r.top/rows;
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(w*r.right/cols-left,h*r.bottom/rows-y);
        lp.gravity=Gravity.TOP|Gravity.LEFT;lp.leftMargin=left;lp.topMargin=y;grid.addView(v,lp);
        if(target>=0){
            v.setContentDescription(NAMES[selected]+"位置 "+(target+1));
            v.setOnClickListener(new View.OnClickListener(){public void onClick(View b){
                draft=TabletSlots.put(draft,selected,target);render();
            }});
        }
    }
    private void save(){
        if(draft==original){dialog.dismiss();return;}
        boolean had=prefs.contains(TabletSlots.POSITIONS);
        boolean enabled=prefs.getBoolean(TabletSlots.SHOW,false);
        if(!prefs.edit().putInt(TabletSlots.POSITIONS,draft).putBoolean(TabletSlots.SHOW,draft!=0).commit())return;
        if(LauncherColdReloadCoordinator.beginTabletTilesReload(activity)){dialog.dismiss();return;}
        SharedPreferences.Editor undo=prefs.edit().putBoolean(TabletSlots.SHOW,enabled);
        if(had) undo.putInt(TabletSlots.POSITIONS,original);else undo.remove(TabletSlots.POSITIONS);
        undo.commit();
        Toast.makeText(activity,"桌面正在更新，请稍后再试",Toast.LENGTH_SHORT).show();
    }
}
