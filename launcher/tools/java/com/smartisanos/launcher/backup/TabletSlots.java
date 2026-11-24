package com.smartisanos.launcher.backup;

import android.content.ContentValues;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import android.util.Log;
import com.smartisanos.launcher.data.TabletLayout;
import com.smartisanos.launcher.theme.MaintainedLauncherSettingsHost;
import java.util.ArrayList;
import java.util.HashSet;

/** Home tile placements use whole cells and share a database reservation across rotations. */
public final class TabletSlots {
    private TabletSlots() {}
    public static final String PREF = "launcher_settings";
    public static final String SHOW = "tablet_home_tiles";
    public static final String MUSIC = "tablet_music_tile";
    public static final String POSITIONS = "tablet_tile_positions_v4";
    private static final String LEGACY_POSITIONS = "tablet_tile_positions_v3";
    public static final int KINDS = 7;
    private static final String PIN_TARGET = "tablet_pending_pin";
    private static final String PIN_USER = "tablet_pending_pin_user";

    private static volatile int homeMask;
    private static android.util.LongSparseArray<int[]> pendingMoves;

    public static int move(int layout, int kind, int target) {
        int from=slot(layout,kind);
        if(from<0 || target<0 || target>=6) return layout;
        if(fits(layout,kind,target)) return put(layout,kind,target);
        if(kind==0) return layout;
        for(int other=1;other<KINDS;other++) {
            if(other==kind || slot(layout,other)!=target) continue;
            int cleared=put(put(layout,kind,-1),other,-1);
            if(fits(cleared,kind,target) && fits(cleared,other,from))
                return put(put(cleared,kind,target),other,from);
        }
        return layout;
    }

    /** Keep native ItemInfo rows in step with database moves before rebuilding the GL pages. */
    public static synchronized boolean updateLive(Context context,int draft) {
        int count=MaintainedLauncherSettingsHost.resolvePortLauncherMode(context,12);
        int reserved=mask(count,draft);
        if ((reserved & ~homeMask)==0) { homeMask=reserved; return true; }
        android.util.LongSparseArray<int[]> changed=new android.util.LongSparseArray<>();
        try {
            SQLiteDatabase db=LayoutSnapshotExporter.database(true);
            db.beginTransaction();
            try {
                android.util.LongSparseArray<int[]> before=new android.util.LongSparseArray<>();
                try(Cursor rows=db.rawQuery("SELECT _id,pageIndex,cellIndex FROM table_iteminfos WHERE pageIndex=0",null)) {
                    while(rows.moveToNext()) before.put(rows.getLong(0),new int[]{0,rows.getInt(2)});
                }
                if(!arrange(db,count,reserved)) return false;
                if(before.size()>0) try(Cursor rows=db.rawQuery("SELECT _id,pageIndex,cellIndex FROM table_iteminfos WHERE pageIndex>=0",null)) {
                    while(rows.moveToNext()) {
                        long id=rows.getLong(0);
                        int[] previous=before.get(id);
                        if(previous!=null && (rows.getInt(1)!=0 || rows.getInt(2)!=previous[1]))
                            changed.put(id,new int[]{rows.getInt(1),rows.getInt(2)});
                    }
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        } catch(Exception error) {
            Log.w("TabletSlots","Could not place home tiles",error);return false;
        }
        homeMask=reserved;
        if(changed.size()>0) {
            if(pendingMoves==null) pendingMoves=changed;
            else for(int i=0;i<changed.size();i++) pendingMoves.put(changed.keyAt(i),changed.valueAt(i));
        }
        return true;
    }

    public static synchronized android.util.LongSparseArray<int[]> takeMoves() {
        android.util.LongSparseArray<int[]> result=pendingMoves;
        pendingMoves=null;
        return result;
    }

    public static int reservedHomeMask() { return homeMask; }

    public static boolean keepCellIndices(int page) { return page==0 && homeMask!=0; }

    public static boolean reserved(int page,int index) {
        return page==0 && index>=0 && index<20 && (homeMask & (1<<index))!=0;
    }

    /** A tile page may contain gaps. Each application still owns exactly one real slot. */
    public static boolean validSortedSlots(int[] slots, int capacity) {
        int previous = -1;
        for (int slot : slots) {
            if (slot <= previous || slot >= capacity) return false;
            previous = slot;
        }
        return true;
    }

    public static int positions(android.content.SharedPreferences prefs) {
        if (prefs.contains(POSITIONS)) return prefs.getInt(POSITIONS, 0);
        // Kind 5 formerly held storage. Preserve every other saved placement, including photos.
        if (prefs.contains(LEGACY_POSITIONS)) return put(prefs.getInt(LEGACY_POSITIONS,0),5,-1);
        if (!prefs.getBoolean(SHOW,false)) return 0;
        return put(put(0,0,0), prefs.getBoolean(MUSIC,false) ? 2 : 1, 2);
    }

    public static int slot(int positions,int kind) {
        return ((positions >>> (kind*3)) & 7)-1;
    }

    public static int put(int positions,int kind,int slot) {
        return (positions & ~(7 << (kind*3))) | ((slot+1) << (kind*3));
    }

    public static int columns(int count,boolean land) {
        return count==12 ? (land?4:3) : (land?5:4);
    }

    public static boolean fits(int positions,int kind,int target) {
        if (target<0 || target>=6 || (kind==0 && target%3!=0)) return false;
        int wanted=(kind==0 ? 3 : 1)<<target;
        for (int other=0;other<KINDS;other++) {
            int used=slot(positions,other);
            if (other!=kind && used>=0 && (wanted & ((other==0?3:1)<<used))!=0) return false;
        }
        return true;
    }

    // Pair the two orientations so rotation can keep the original database addresses.
    public static Rect rect(int count,boolean land,int kind,int slot) {
        int cols=columns(count,land), rows=count/cols;
        int x=count==12 ? 0 : land ? 1 : 2;
        int y=count==20 && !land ? 1 : 0;
        Rect r=new Rect(x,y,x+2,y+2);
        if (slot%3==2) {
            if (count==12) r=land?new Rect(2,0,4,1):new Rect(2,0,3,2);
            else r=land?new Rect(0,2,2,3):new Rect(1,0,3,1);
        } else if (kind!=0) {
            r.top+=slot%3;
            r.bottom=r.top+1;
        }
        if (slot>=3) r.set(cols-r.right,rows-r.bottom,cols-r.left,rows-r.top);
        return r;
    }

    public static int bits(Rect r,int cols) {
        int mask=0;
        for(int y=r.top;y<r.bottom;y++) for(int x=r.left;x<r.right;x++) mask |= 1<<(y*cols+x);
        return mask;
    }

    private static int mask(int count,int positions) {
        int result=0;
        for(int kind=0;kind<KINDS;kind++) {
            int place=slot(positions,kind);
            if (!fits(positions,kind,place)) continue;
            result |= bits(rect(count,true,kind,place),columns(count,true));
            result |= bits(rect(count,false,kind,place),columns(count,false));
        }
        return result;
    }

    /** Called on the original database worker before the in-memory model is populated. */
    public static void prepare(Context context, boolean fresh) {
        homeMask = 0;
        if (!TabletLayout.isTablet(context.getResources().getConfiguration())) return;
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREF,0);
        if (!prefs.contains(SHOW)) prefs.edit().putBoolean(SHOW, fresh).apply();
        int count = MaintainedLauncherSettingsHost.resolvePortLauncherMode(context,12);
        int reserved = mask(count, positions(prefs));
        applyPendingPin(context, prefs, reserved);
        if (reserved==0) return;
        try {
            SQLiteDatabase db = LayoutSnapshotExporter.database(true);
            boolean ready;
            db.beginTransaction();
            try {
                ready = arrange(db,count,reserved);
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
            if (ready) homeMask = reserved;
        } catch (Exception error) {
            Log.w("TabletSlots","Could not reserve home tiles",error);
        }
    }

    public static String requestPin(Context context, ComponentName target, int user, int pinned) {
        if (pinned >= TabletLayout.dockCapacity(context.getResources().getConfiguration()))
            return "常驻栏已满，请先把一个常驻应用拖回桌面";
        SharedPreferences prefs = context.getSharedPreferences(PREF, 0);
        if (prefs.contains(PIN_TARGET)) return "常驻栏正在更新";
        if (!prefs.edit().putString(PIN_TARGET, target.flattenToString()).putInt(PIN_USER, user).commit())
            return "未能保存固定请求";
        if (com.smartisanos.launcher.reload.LauncherColdReloadCoordinator.beginTabletTilesReload(context))
            return null;
        prefs.edit().remove(PIN_TARGET).remove(PIN_USER).commit();
        return "桌面正在更新，请稍后再试";
    }

    // Move the original row before the native model loads, so a stale scene cannot overwrite it.
    private static void applyPendingPin(Context context, SharedPreferences prefs, int reserved) {
        String token = prefs.getString(PIN_TARGET, null);
        if (token == null) return;
        ComponentName target = ComponentName.unflattenFromString(token);
        String message = null;
        try {
            SQLiteDatabase db = LayoutSnapshotExporter.database(true);
            db.beginTransaction();
            try {
                int capacity = TabletLayout.dockCapacity(context.getResources().getConfiguration());
                message = moveToDock(db, target, prefs.getInt(PIN_USER, 0), capacity, reserved);
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        } catch (Exception error) {
            Log.w("TabletSlots", "Could not pin recent app", error);
            message = "未能固定应用，请重试";
        }
        prefs.edit().remove(PIN_TARGET).remove(PIN_USER).commit();
        if (message != null) {
            final String feedback = message;
            new android.os.Handler(context.getMainLooper()).post(new Runnable() { public void run() {
                android.widget.Toast.makeText(context, feedback, android.widget.Toast.LENGTH_LONG).show();
            }});
        }
    }

    private static String moveToDock(SQLiteDatabase db, ComponentName target, int user,
            int capacity, int reserved) {
        if (target == null) return "应用已不可用";
        String[] args = {target.getPackageName(), target.getClassName(), String.valueOf(user)};
        long id, folder;
        int page, cell;
        try (Cursor c = db.rawQuery("SELECT i._id,i.pageIndex,i.cellIndex,i.folderIndex FROM table_iteminfos i "
                + "LEFT JOIN table_pageinfos p ON i.pageIndex=p.pageIndex WHERE i.packageName=? "
                + "AND i.componentName=? AND i.user=? AND i.itemType=0 AND "
                + "(i.pageIndex=-2 OR (i.pageIndex>=0 AND p.status=0)) ORDER BY i.pageIndex LIMIT 1", args)) {
            if (!c.moveToFirst()) return "原位置已变更，请重新选择应用";
            id=c.getLong(0); page=c.getInt(1); cell=c.getInt(2); folder=c.getLong(3);
        }
        if (page == -2) return null; // Recover an already committed move.
        if (!mayShowRecent(target.getPackageName(), target.getClassName(), user))
            return "应用当前不可固定";
        long next = android.database.DatabaseUtils.longForQuery(db,
                "SELECT coalesce(max(cellIndex),-1)+1 FROM table_iteminfos WHERE pageIndex=-2 AND folderIndex<=0",null);
        if (next >= capacity) return "常驻栏已满";
        if (folder > 0 && android.database.DatabaseUtils.longForQuery(db,
                "SELECT count(*) FROM table_iteminfos WHERE folderIndex=?", new String[]{String.valueOf(folder)}) == 1)
            return "请先把此应用拖出文件夹，再固定到常驻栏";
        db.execSQL("UPDATE table_iteminfos SET pageIndex=-2,cellIndex=?,folderIndex=-1 WHERE _id=?",
                new Object[]{next,id});
        if (folder > 0) {
            db.execSQL("UPDATE table_iteminfos SET cellIndex=cellIndex-1 WHERE folderIndex=? AND cellIndex>?",
                    new Object[]{folder,cell});
        } else if (page != 0 || reserved == 0) {
            db.execSQL("UPDATE table_iteminfos SET cellIndex=cellIndex-1 WHERE pageIndex=? AND folderIndex<=0 AND cellIndex>?",
                    new Object[]{page,cell});
        }
        return null;
    }

    /** An app is eligible only while it is on an ordinary unlocked page. */
    public static boolean mayShowRecent(String pkg, String cls, int user) {
        try (Cursor c = LayoutSnapshotExporter.database(false).rawQuery(
                "SELECT i.pageIndex,p.status,i.componentName FROM table_iteminfos i LEFT JOIN table_pageinfos p ON i.pageIndex=p.pageIndex WHERE i.packageName=? AND i.user=? AND i.itemType=0",
                new String[]{pkg,String.valueOf(user)})) {
            boolean found = false;
            while (c.moveToNext()) {
                if (c.getInt(0)<0 || c.isNull(1) || c.getInt(1)!=0) return false;
                if (cls.equals(c.getString(2))) found = true;
            }
            return found;
        } catch (Exception error) { return false; }
    }

    private static boolean arrange(SQLiteDatabase db,int count,int reserved) {
        HashSet<Integer> used = new HashSet<>();
        ArrayList<Integer> displaced = new ArrayList<>();
        HashSet<Integer> editable = new HashSet<>();
        int last = 0;
        try (Cursor c=db.rawQuery("SELECT pageIndex,status FROM table_pageinfos WHERE pageIndex>=0",null)) {
            while (c.moveToNext()) {
                last=Math.max(last,c.getInt(0));
                if (c.getInt(1)==0) editable.add(c.getInt(0));
            }
        }
        if (!editable.contains(0)) return false;
        try (Cursor c=db.rawQuery("SELECT DISTINCT pageIndex,cellIndex FROM table_iteminfos WHERE folderIndex<=0 AND pageIndex>=0 AND cellIndex>=0 ORDER BY pageIndex,cellIndex",null)) {
            while (c.moveToNext()) {
                int page=c.getInt(0), cell=c.getInt(1);
                if (cell>=count) continue;
                used.add(page*count+cell);
                if (page==0 && (reserved & (1<<cell))!=0) displaced.add(cell);
            }
        }
        int next=0;
        for (int cell:displaced) {
            while (used.contains(next) || (next<count && (reserved & (1<<next))!=0)
                    || (next/count<=last && !editable.contains(next/count))) next++;
            int page=next/count;
            if (page>last) {
                // Claim an empty page using the same reserved-row convention as the importer.
                long id;
                try (Cursor c=db.rawQuery("SELECT _id FROM table_pageinfos WHERE pageIndex=-1 AND status=0 AND coalesce(pageTitle,'')='' AND coalesce(containment,0)=0 AND data1 IS NULL AND data2 IS NULL AND data3 IS NULL ORDER BY _id LIMIT 1",null)) {
                    if (!c.moveToFirst()) throw new IllegalStateException("No free page");
                    id=c.getLong(0);
                }
                ContentValues values=new ContentValues(); values.put("pageIndex",page);
                db.update("table_pageinfos",values,"_id=?",new String[]{String.valueOf(id)});
                editable.add(page); last=page;
            }
            ContentValues values=new ContentValues();
            values.put("pageIndex",page); values.put("cellIndex",next%count);
            // A child stores its own shelf position and refers to its folder by row id.
            if (page!=0) db.execSQL("UPDATE table_iteminfos SET pageIndex=? WHERE folderIndex IN "
                    + "(SELECT _id FROM table_iteminfos WHERE pageIndex=0 AND cellIndex=? AND itemType=2)",
                    new Object[]{page,cell});
            db.update("table_iteminfos",values,"folderIndex<=0 AND pageIndex=0 AND cellIndex=?",new String[]{String.valueOf(cell)});
            used.add(next++);
        }
        return true;
    }
}
