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
    private static int bottomInset;
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
        int recent = width / density() >= 820 ? 3 : 2;
        return Math.min(96 * density(), width / (Math.max(1, pinned) + recent + 1f));
    }

    public static float dockLeft(float width, int pinned) {
        int recent = width / density() >= 820 ? 3 : 2;
        return (width - (pinned + recent + 0.5f) * dockPitch(width, pinned)) / 2f;
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
        if (context == null || orientation == sceneOrientation
                || orientation != context.getResources().getConfiguration().orientation) return null;
        return context;
    }

    public static void onConfigurationChanged(Activity activity) {
        applyOrientation(activity);
    }
}
