package com.smartisanos.launcher.data;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.smartisanos.launcher.reload.LauncherColdReloadCoordinator;

/** Selects the layout for the HOME activity. Workspace slots remain in the same database. */
public final class TabletLayout {
    private static final int TABLET_WIDTH_DP = 600;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Activity resumedHome;
    private static volatile boolean tabletScene;
    private static int bottomInset;
    private static int sceneOrientation;
    private static boolean reloadStarted;

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
        if (isTablet(config)) {
            Log.i("TabletLayout", "Scene orientation=" + sceneOrientation
                    + " smallestWidthDp=" + config.smallestScreenWidthDp);
        }
    }

    static int bottomInset() {
        return bottomInset;
    }

    public static boolean supportsLandscapeSurface() {
        return tabletScene;
    }

    private static final Runnable CHECK_SCENE = new Runnable() {
        @Override public void run() {
            Activity home = resumedHome;
            if (home == null || home.isFinishing() || home.isDestroyed() || reloadStarted) return;
            Configuration config = home.getResources().getConfiguration();
            if (!isTablet(config) || sceneOrientation == Configuration.ORIENTATION_UNDEFINED
                    || sceneOrientation == config.orientation) return;
            // The original engine caches both GL nodes and hit targets for the entire process.
            reloadStarted = LauncherColdReloadCoordinator.beginTabletRotation(home);
            if (!reloadStarted) MAIN.postDelayed(this, 500);
        }
    };

    public static void onHomeResumed(Activity activity) {
        resumedHome = activity;
        onConfigurationChanged(activity);
    }

    public static void onConfigurationChanged(Activity activity) {
        applyOrientation(activity);
        if (activity == resumedHome) {
            MAIN.removeCallbacks(CHECK_SCENE);
            MAIN.postDelayed(CHECK_SCENE, 250);
        }
    }

    public static void onHomePaused(Activity activity) {
        if (activity == resumedHome) {
            resumedHome = null;
            MAIN.removeCallbacks(CHECK_SCENE);
        }
    }
}
