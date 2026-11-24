package com.smartisanos.launcher.data;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.util.Log;

/** Selects the layout for the HOME activity. Workspace slots remain in the same database. */
public final class TabletLayout {
    private static final int TABLET_WIDTH_DP = 600;
    private static volatile Context sceneContext;
    private static volatile boolean tabletScene;
    private static volatile int bottomInset;
    private static volatile int leftInset, rightInset;
    private static volatile int recentCount, observedOrientation, observedBottom;
    private static volatile boolean dockDirty;
    private static int sceneOrientation;

    private TabletLayout() {}

    public static boolean isTablet(Configuration config) {
        return config.smallestScreenWidthDp >= TABLET_WIDTH_DP;
    }

    public static void applyOrientation(Activity activity) {
        int orientation = isTablet(activity.getResources().getConfiguration())
                ? ActivityInfo.SCREEN_ORIENTATION_USER : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        if (activity.getRequestedOrientation() != orientation) {
            activity.setRequestedOrientation(orientation);
        }
    }

    public static void recordScene(Context context) {
        Configuration config = context.getResources().getConfiguration();
        sceneContext = context.getApplicationContext();
        tabletScene = isTablet(config);
        sceneOrientation = config.orientation;
        bottomInset = 0;
        if (tabletScene) {
            android.view.WindowManager wm = (android.view.WindowManager)
                    context.getSystemService(Context.WINDOW_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                bottomInset = wm.getCurrentWindowMetrics().getWindowInsets()
                        .getInsetsIgnoringVisibility(android.view.WindowInsets.Type.navigationBars()).bottom;
            } else {
                android.graphics.Point real = new android.graphics.Point();
                android.graphics.Point app = new android.graphics.Point();
                wm.getDefaultDisplay().getRealSize(real);
                wm.getDefaultDisplay().getSize(app);
                bottomInset = Math.max(0, real.y - app.y);
            }
        }
        if (observedOrientation==sceneOrientation) bottomInset=observedBottom;
        if (isTablet(config)) {
            Log.i("TabletLayout", "Scene orientation=" + sceneOrientation
                    + " smallestWidthDp=" + config.smallestScreenWidthDp);
        }
    }

    public static float density() {
        Context context = sceneContext;
        return context == null ? 1f : context.getResources().getDisplayMetrics().density;
    }

    public static float dockPitch(float width, int pinned) {
        float usable=Math.max(1,width-leftInset-rightInset);
        int recent=Math.min(recentCount,usable/density()>=820?3:2);
        return Math.min(92*density(),usable/(Math.max(1,pinned+recent)+.75f));
    }

    public static float dockLeft(float width, int pinned) {
        float usable=Math.max(1,width-leftInset-rightInset);
        int recent=Math.min(recentCount,usable/density()>=820?3:2);
        float gap=recent>0 && pinned>0 ? .25f : 0f;
        return leftInset+(usable-(pinned+recent+gap)*dockPitch(width,pinned))/2f;
    }

    public static int recentLimit(float width){return (width-leftInset-rightInset)/density()>=820?3:2;}
    public static synchronized void recentCount(int count){
        if(recentCount!=count){recentCount=count;dockDirty=true;}
    }
    public static synchronized boolean takeDockChange(){
        boolean change=dockDirty;dockDirty=false;return change;
    }
    public static synchronized void navigation(int orientation,int left,int right,int bottom){
        if(leftInset!=left || rightInset!=right){leftInset=left;rightInset=right;dockDirty=true;}
        observedBottom=bottom;observedOrientation=orientation;
    }
    public static boolean pendingNavigationResize(){
        return tabletScene && observedOrientation==sceneOrientation && bottomInset!=observedBottom;
    }
    public static Context navigationResizeContext(){
        Context context=sceneContext;
        if (context==null || !pendingNavigationResize()
                || context.getResources().getConfiguration().orientation!=sceneOrientation) return null;
        return context;
    }

    public static int bottomInset() {
        return bottomInset;
    }

    public static boolean supportsLandscapeSurface() {
        return tabletScene;
    }

    public static Context resizedSceneContext(int width, int height) {
        if (!tabletScene || width <= 0 || height <= 0) return null;
        int orientation = width > height ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
        Context context = sceneContext;
        if (context == null || (orientation == sceneOrientation && !pendingNavigationResize())
                || orientation != context.getResources().getConfiguration().orientation) return null;
        return context;
    }

    public static void onConfigurationChanged(Activity activity) {
        applyOrientation(activity);
    }
}
