package com.smartisanos.launcher.quickdesktop;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.content.Context;
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
import static com.smartisanos.launcher.theme.MaintainedLauncherSettingsHost.*;

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

    TabletTileLibrary(Activity a, Context settings) {
        activity=a;
        prefs=a.getSharedPreferences(TabletSlots.PREF,0);
        draft=original=TabletSlots.positions(prefs);
        count=MaintainedLauncherSettingsHost.resolvePortLauncherMode(a,12);
        land=a.getResources().getConfiguration().orientation==2;
        dialog=new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        sheet=new LinearLayout(a);
        prepareSmartisanDialogRoot(a,sheet);
        sheet.addView(smartisanDialogTitle(a,"桌面组件"),-1,dp(53));
        sheet.addView(smartisanDivider(a),-1,1);
        ScrollView scroll=new ScrollView(a);
        sheet.addView(scroll,new LinearLayout.LayoutParams(-1,-2,1));
        LinearLayout body=new LinearLayout(a);
        body.setPadding(dp(16),dp(12),dp(16),dp(12));body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        choices=new LinearLayout(a);
        body.addView(choices,-1,dp(68));
        for(int kind=0;kind<NAMES.length;kind++) {
            final int id=kind;
            LinearLayout choice=new LinearLayout(a);choice.setGravity(Gravity.CENTER);choice.setOrientation(LinearLayout.VERTICAL);
            FrameLayout icon=new FrameLayout(a);
            ImageView image=new ImageView(a);
            try(InputStream in=a.getAssets().open(ART[kind])){image.setImageBitmap(BitmapFactory.decodeStream(in));}
            catch(IOException missing){android.util.Log.w("TabletTileLibrary",ART[kind],missing);}
            // Shortcut pictograms are white; keep the same dark canvas they have in Quick Desktop.
            image.setBackground(surface(0xff55595d,0xff55595d));
            image.setPadding(dp(5),dp(5),dp(5),dp(5));
            icon.addView(image,new FrameLayout.LayoutParams(dp(32),dp(32),Gravity.CENTER));
            ImageView check=new ImageView(settings);
            int res=settings.getResources().getIdentifier("preview_picture_selected","drawable","com.smartisanos.home");
            if(res!=0) check.setImageResource(res);
            icon.addView(check,new FrameLayout.LayoutParams(dp(16),dp(16),Gravity.RIGHT|Gravity.TOP));
            choice.setTag(check);
            choice.addView(icon,dp(44),dp(36));
            choice.addView(label(NAMES[kind]+(kind==0?" · 四格":" · 双格"),13),-1,dp(23));
            choices.addView(choice,new LinearLayout.LayoutParams(0,-1,1));
            choice.setOnClickListener(new View.OnClickListener(){public void onClick(View v){selected=id;render();}});
            choice.setContentDescription(NAMES[kind]);
        }
        LinearLayout tools=new LinearLayout(a);
        body.addView(tools,-1,dp(48));
        heading=label("",14);heading.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        tools.addView(heading,new LinearLayout.LayoutParams(0,-1,1));
        remove=label("移除",14);remove.setTextColor(0xffa44540);tools.addView(remove,dp(64),-1);
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
        TextView foot=label("原位置的应用会移至空格",12);
        foot.setTextColor(0xff777b82);
        foot.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);body.addView(foot,-1,dp(28));
        sheet.addView(smartisanDivider(a),-1,1);
        LinearLayout actions=new LinearLayout(a);
        TextView cancel=smartisanDialogActionButton(a,"取消",false,-1);
        TextView done=smartisanDialogActionButton(a,"完成",true,1);
        cancel.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.dismiss();}});
        done.setOnClickListener(new View.OnClickListener(){public void onClick(View v){save();}});
        actions.addView(cancel,new LinearLayout.LayoutParams(0,-1,1));
        actions.addView(smartisanDivider(a),1,-1);
        actions.addView(done,new LinearLayout.LayoutParams(0,-1,1));
        sheet.addView(actions,-1,dp(47));
        dialog.setContentView(sheet);
    }
    void show(){
        showDialogWithNavigation(dialog);
        Window w=dialog.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        int width=activity.getWindow().getDecorView().getWidth();
        w.setLayout(Math.min(dp(560),width-dp(24)),-2);
        render();
    }
    private int dp(float v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}
    private TextView label(String value,int size){
        TextView text=new TextView(activity);text.setGravity(Gravity.CENTER);
        text.setTextColor(0xff4f5360);text.setTextSize(size);text.setText(value);return text;
    }
    private GradientDrawable surface(int fill,int border){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);
        d.setStroke(dp(.75f),border);d.setCornerRadius(dp(3));return d;
    }
    private void render(){
        for(int k=0;k<choices.getChildCount();k++) {
            View choice=choices.getChildAt(k);
            ((View)choice.getTag()).setVisibility(k==selected?View.VISIBLE:View.GONE);
            choice.setSelected(k==selected);
        }
        boolean added=TabletSlots.slot(draft,selected)>=0;
        heading.setText(NAMES[selected]+(added?" · 选择新的位置":" · 选择添加位置"));
        remove.setVisibility(added?View.VISIBLE:View.GONE);
        grid.removeAllViews();
        int cols=TabletSlots.columns(count,land),rows=count/cols;
        for(int cell=0;cell<count;cell++){
            int x=cell%cols,y=cell/cols;
            box(new Rect(x,y,x+1,y+1),"",0xfff1f2f3,0xffe3e4e6,-1,cols,rows);
        }
        for(int slot=0;slot<6;slot++){
            if(!TabletSlots.fits(draft,selected,slot) || slot==TabletSlots.slot(draft,selected)) continue;
            box(TabletSlots.rect(count,land,selected,slot),"＋",0xfffafbfc,0xffc4cbd4,slot,cols,rows);
        }
        for(int k=0;k<NAMES.length;k++){
            int slot=TabletSlots.slot(draft,k);
            if(!TabletSlots.fits(draft,k,slot)) continue;
            box(TabletSlots.rect(count,land,k,slot),NAMES[k],k==selected?0xffe6edf8:0xffdcdee1,
                    k==selected?0xff8caada:0xffd5d7db,-1,cols,rows);
        }
    }
    private void box(Rect r,String name,int top,int bottom,final int target,int cols,int rows){
        int w=grid.getWidth(), h=grid.getHeight();
        if(w<=0 || h<=0)return;
        TextView v=label(name,target>=0?22:14);
        v.setBackground(surface(top,bottom));
        if(target>=0) v.setTextColor(0xff6487b9);
        int left=w*r.left/cols, y=h*r.top/rows;
        int gap=dp(2);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(Math.max(1,w*r.right/cols-left-2*gap),Math.max(1,h*r.bottom/rows-y-2*gap));
        lp.gravity=Gravity.TOP|Gravity.LEFT;lp.leftMargin=left+gap;lp.topMargin=y+gap;grid.addView(v,lp);
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
