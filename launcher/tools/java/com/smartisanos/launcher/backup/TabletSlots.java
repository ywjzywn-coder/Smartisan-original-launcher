package com.smartisanos.launcher.backup;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import android.util.Log;
import com.smartisanos.launcher.data.TabletLayout;
import com.smartisanos.launcher.theme.MaintainedLauncherSettingsHost;
import java.util.ArrayList;
import java.util.HashSet;

/** Both orientations reserve the same six logical slots. Rectangle units are cells. */
public final class TabletSlots {
    private TabletSlots() {}
    public static final String PREF = "launcher_settings";
    public static final String SHOW = "tablet_home_tiles";
    public static final String MUSIC = "tablet_music_tile";

    private static volatile int homeMask;

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

    public static int mask(int count) {
        return count == 12 ? 63 : (1<<1) | (1<<2) | (1<<6) | (1<<7) | (1<<10) | (1<<11);
    }

    public static Rect[] rectangles(int count, boolean landscape) {
        if (count == 12) return new Rect[]{new Rect(0,0,2,2),
                landscape ? new Rect(2,0,4,1) : new Rect(2,0,3,2)};
        return landscape ? new Rect[]{new Rect(1,0,3,2), new Rect(0,2,2,3)}
                : new Rect[]{new Rect(2,1,4,3), new Rect(1,0,3,1)};
    }

    /** Called on the original database worker before the in-memory model is populated. */
    public static void prepare(Context context, boolean fresh) {
        homeMask = 0;
        if (!TabletLayout.isTablet(context.getResources().getConfiguration())) return;
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREF,0);
        if (!prefs.contains(SHOW)) prefs.edit().putBoolean(SHOW, fresh).apply();
        if (!prefs.getBoolean(SHOW,false)) return;
        int count = MaintainedLauncherSettingsHost.resolvePortLauncherMode(context,12);
        try {
            SQLiteDatabase db = LayoutSnapshotExporter.database(true);
            boolean ready;
            db.beginTransaction();
            try {
                ready = arrange(db,count);
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
            if (ready) homeMask = mask(count);
        } catch (Exception error) {
            Log.w("TabletSlots","Could not reserve home tiles",error);
        }
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

    private static boolean arrange(SQLiteDatabase db,int count) {
        int reserved = mask(count);
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
