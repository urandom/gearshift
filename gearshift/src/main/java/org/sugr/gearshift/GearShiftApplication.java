package org.sugr.gearshift;

import android.app.Application;

public class GearShiftApplication extends Application {
    private static boolean activityVisible;

    public static boolean isActivityVisible() {
        return activityVisible;
    }

    public static void setActivityVisible(boolean visible) {
        activityVisible = visible;
    }
}
