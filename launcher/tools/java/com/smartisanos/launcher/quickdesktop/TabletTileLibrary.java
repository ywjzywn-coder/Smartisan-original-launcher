package com.smartisanos.launcher.quickdesktop;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
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
import com.smartisanos.launcher.theme.MaintainedLauncherSettingsHost;
import static com.smartisanos.launcher.theme.MaintainedLauncherSettingsHost.*;

/** Browse the same artwork as HOME, then add to a free slot or pick a placement. */
final class TabletTileLibrary {
    static final String[] NAMES={"日历","时钟","音乐","天气","电量","设备信息","照片"};
    private final Activity activity;
    private final Dialog dialog;
    private final SharedPreferences prefs;
    private final LinearLayout sheet;
    private final int original, count;
    private final LinearLayout[] cards=new LinearLayout[NAMES.length];
    private final ImageView[] checks=new ImageView[NAMES.length];
    private int selected, draft;
    private boolean placement, land;
    private FrameLayout grid;
    private TextView primary, secondary;
    private final View host;

    TabletTileLibrary(Activity a,int initialKind) {
        activity=a;
        prefs=a.getSharedPreferences(TabletSlots.PREF,0);
        draft=original=TabletSlots.positions(prefs);
        count=MaintainedLauncherSettingsHost.resolvePortLauncherMode(a,12);
        placement=initialKind>=0;
        selected=placement?initialKind:0;
        if(!placement) {
            for(int k=0;k<NAMES.length;k++) {
                if(TabletSlots.slot(original,k)<0 && freeSlot(k)>=0) {selected=k;break;}
            }
        }
        dialog=new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        sheet=new LinearLayout(a);
        prepareSmartisanDialogRoot(a,sheet);
        dialog.setContentView(sheet);
        host=a.getWindow().getDecorView();
    }

    private int freeSlot(int kind) {
        for(int i=0;i<6;i++) if(TabletSlots.fits(original,kind,i)) return i;
        return -1;
    }

    private final View.OnLayoutChangeListener resize=new View.OnLayoutChangeListener() {
        public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob) {
            if((r-l!=or-ol || b-t!=ob-ot) && dialog.isShowing()) rebuild();
        }
    };

    void show() {
        showDialogWithNavigation(dialog);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        host.addOnLayoutChangeListener(resize);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface ignored) { host.removeOnLayoutChangeListener(resize); }
        });
        rebuild();
    }

    private int dp(float v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}
    private TextView label(String value,int size){
        TextView text=new TextView(activity);text.setGravity(Gravity.CENTER);
        text.setTextColor(0xff4f5360);text.setTextSize(size);text.setText(value);return text;
    }
    private GradientDrawable surface(int fill,int border){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);
        d.setStroke(Math.max(1,dp(1)),border);d.setCornerRadius(dp(4));return d;
    }

    private void rebuild() {
        int width=host.getWidth(), height=host.getHeight();
        if(width<=0 || height<=0) return;
        land=width>height;
        int maxWidth=placement?580:land?886:600;
        int desiredHeight=placement? (land?420:535) : (land?540:688);
        dialog.getWindow().setLayout(Math.min(dp(maxWidth),width-dp(28)),Math.min(dp(desiredHeight),height-dp(64)));
        sheet.removeAllViews();
        sheet.addView(smartisanDialogTitle(activity,placement?NAMES[selected]+" · 选择位置":"添加桌面组件"),-1,dp(52));
        sheet.addView(smartisanDivider(activity),-1,1);
        if(placement) buildPlacement(); else buildGallery();
        sheet.addView(smartisanDivider(activity),-1,1);
        LinearLayout actions=new LinearLayout(activity);
        TextView cancel=smartisanDialogActionButton(activity,"取消",false,-1);
        cancel.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.dismiss();}});
        addAction(actions,cancel);
        secondary=smartisanDialogActionButton(activity,"",false,0);
        secondary.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
            if(placement){draft=original;placement=false;rebuild();}
            else if(TabletSlots.slot(original,selected)>=0){draft=TabletSlots.put(original,selected,-1);save();}
            else {placement=true;rebuild();}
        }});
        addAction(actions,secondary);
        primary=smartisanDialogActionButton(activity,"",true,1);
        primary.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
            if(!placement && TabletSlots.slot(original,selected)>=0){placement=true;rebuild();return;}
            if(!placement){
                int slot=freeSlot(selected);if(slot<0)return;
                draft=TabletSlots.put(original,selected,slot);
            }
            save();
        }});
        addAction(actions,primary);
        sheet.addView(actions,-1,dp(50));
        updateActions();
    }
    private void addAction(LinearLayout row,TextView action) {
        if(row.getChildCount()>0) row.addView(smartisanDivider(activity),1,-1);
        row.addView(action,new LinearLayout.LayoutParams(0,-1,1));
    }

    private void buildGallery() {
        ScrollView scroll=new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xffeeefef);
        sheet.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout list=new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);list.setPadding(dp(11),dp(9),dp(11),dp(9));
        scroll.addView(list);
        int columns=land?3:2;
        for(int start=0;start<NAMES.length;start+=columns) {
            LinearLayout row=new LinearLayout(activity);
            list.addView(row,new LinearLayout.LayoutParams(-1,dp(land?194:174),1));
            for(int k=start;k<start+columns;k++) {
                if(k<NAMES.length) buildCard(row,k);
                else {
                    LinearLayout.LayoutParams spacer=new LinearLayout.LayoutParams(0,1,1);
                    spacer.setMargins(dp(5),0,dp(5),0);
                    row.addView(new View(activity),spacer);
                }
            }
        }
    }

    private void buildCard(LinearLayout row,final int kind) {
        LinearLayout card=new LinearLayout(activity);
        cards[kind]=card;
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(4),dp(4),dp(4),dp(3));
        card.setElevation(dp(1));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
        lp.setMargins(dp(5),dp(5),dp(5),dp(5));
        row.addView(card,lp);
        FrameLayout stage=new FrameLayout(activity);
        GradientDrawable backdrop=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xffdbddde,0xffe9eaeb});
        backdrop.setCornerRadius(dp(3));stage.setBackground(backdrop);
        card.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        final TabletTile preview=new TabletTile(activity,kind);
        preview.setClickable(false);preview.setFocusable(false);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        preview.setElevation(dp(2));
        stage.addView(preview,new FrameLayout.LayoutParams(1,1,Gravity.CENTER));
        stage.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){
            public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){
                int availableWidth=r-l-dp(18),availableHeight=b-t-dp(16);
                if(availableWidth<1 || availableHeight<1)return;
                float aspect=kind==TabletTile.CALENDAR?1.45f:2.12f;
                int w=Math.min(availableWidth,Math.round(availableHeight*aspect));
                int h=Math.round(w/aspect);
                FrameLayout.LayoutParams frame=(FrameLayout.LayoutParams)preview.getLayoutParams();
                if(w!=frame.width || h!=frame.height){frame.width=w;frame.height=h;preview.setLayoutParams(frame);}
            }
        });
        LinearLayout caption=new LinearLayout(activity);
        caption.setPadding(dp(8),0,dp(7),0);caption.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(caption,-1,dp(36));
        TextView name=label(NAMES[kind],15);name.setTypeface(null,Typeface.BOLD);
        name.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        caption.addView(name,new LinearLayout.LayoutParams(0,-1,1));
        boolean added=TabletSlots.slot(original,kind)>=0;
        TextView meta=label((added?"已添加 · ":"")+(kind==0?"四格":"双格"),11);
        meta.setTextColor(0xff72767b);caption.addView(meta);
        Resources res=MaintainedLauncherSettingsHost.getMaintainedResources(activity);
        ImageView check=new ImageView(activity);checks[kind]=check;
        int mark=res.getIdentifier("preview_picture_selected","drawable","com.smartisanos.home");
        if(mark!=0) check.setImageDrawable(res.getDrawable(mark));
        LinearLayout.LayoutParams markSize=new LinearLayout.LayoutParams(dp(21),dp(21));
        markSize.leftMargin=dp(6);
        caption.addView(check,markSize);
        card.setContentDescription(NAMES[kind]+(added?"，已添加":"，可选"));
        card.setFocusable(true);
        card.setOnClickListener(new View.OnClickListener(){public void onClick(View view){
            selected=kind;updateActions();
        }});
    }

    private void updateActions() {
        boolean added=TabletSlots.slot(original,selected)>=0;
        if(!placement) for(int k=0;k<NAMES.length;k++){
            boolean picked=k==selected;
            GradientDrawable frame=surface(picked?0xfff2f6fb:0xfffafafa,picked?0xff7999b9:0xffc5c8c9);
            if(picked) frame.setStroke(dp(2),0xff7999b9);
            cards[k].setSelected(picked);cards[k].setBackground(frame);
            checks[k].setVisibility(picked?View.VISIBLE:View.INVISIBLE);
        }
        boolean hasRoom=added || freeSlot(selected)>=0;
        secondary.setText(placement?"返回组件库":added?"移除组件":"选择位置");
        secondary.setEnabled(placement || hasRoom);
        primary.setText(placement?"完成":added?"调整位置":hasRoom?(selected==TabletTile.PHOTO?"选照片并添加":"添加到桌面"):"位置已满");
        primary.setEnabled(placement?draft!=original:hasRoom);
        primary.setAlpha(primary.isEnabled()?1f:.5f);
        secondary.setAlpha(secondary.isEnabled()?1f:.5f);
    }

    private void buildPlacement() {
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16),dp(16),dp(16),dp(8));
        sheet.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        grid=new FrameLayout(activity);body.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        grid.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){
            public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){
                if(r-l!=or-ol || b-t!=ob-ot) v.post(new Runnable(){public void run(){
                    if(placement && grid==v && v.isAttachedToWindow()) renderGrid();
                }});
            }
        });
        body.addView(label("点选空位，原位置的应用会移到其他空格",13),-1,dp(37));
    }

    private void renderGrid(){
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
                draft=TabletSlots.put(draft,selected,target);renderGrid(); updateActions();
            }});
        }
    }
    private void save(){
        if(draft==original){dialog.dismiss();return;}
        int photoSlot=TabletSlots.slot(draft,TabletTile.PHOTO);
        if(photoSlot>=0 && TabletSlots.slot(original,TabletTile.PHOTO)<0){
            if(TabletPhoto.choose(activity,photoSlot)) dialog.dismiss();
            return;
        }
        if(apply(activity,prefs,original,draft)) dialog.dismiss();
    }

    static boolean apply(Activity activity,SharedPreferences prefs,int original,int draft){
        String image=TabletSlots.slot(draft,TabletTile.PHOTO)<0?null:prefs.getString(TabletPhoto.KEY,null);
        return apply(activity,prefs,original,draft,image);
    }
    static boolean apply(Activity activity,SharedPreferences prefs,int original,int draft,String image){
        if(TabletSlots.positions(prefs)!=original) {
            Toast.makeText(activity,"组件位置已改变，请重新选择",Toast.LENGTH_SHORT).show();
            return false;
        }
        boolean had=prefs.contains(TabletSlots.POSITIONS);
        boolean enabled=prefs.getBoolean(TabletSlots.SHOW,false);
        String oldImage=prefs.getString(TabletPhoto.KEY,null);
        SharedPreferences.Editor edit=prefs.edit().putString(TabletPhoto.KEY,image);
        if(!edit.putInt(TabletSlots.POSITIONS,draft).putBoolean(TabletSlots.SHOW,draft!=0).commit()) {
            Toast.makeText(activity,"未能保存组件",Toast.LENGTH_SHORT).show();
            return false;
        }
        if(original==draft || TabletSlots.updateLive(activity,draft)){
            TabletHome.refreshLayout();
            if(!android.text.TextUtils.equals(oldImage,image)) TabletPhoto.forget(activity,oldImage);
            return true;
        }
        SharedPreferences.Editor undo=prefs.edit().putBoolean(TabletSlots.SHOW,enabled);
        if(had) undo.putInt(TabletSlots.POSITIONS,original);else undo.remove(TabletSlots.POSITIONS);
        undo.putString(TabletPhoto.KEY,oldImage).commit();
        Toast.makeText(activity,"桌面正在更新，请稍后再试",Toast.LENGTH_SHORT).show();
        return false;
    }
}
