package org.sugr.gearshift.viewmodel.util;

import android.support.annotation.ArrayRes;

import org.sugr.gearshift.App;

public class ResourceUtils {
    public static int[] stringArrayAsInt(@ArrayRes int stringArrayRes) {
        String[] res = App.get().getResources().getStringArray(stringArrayRes);
        int[] ret = new int[res.length];

        for (int i = 0; i < res.length; i++) {
            ret[i] = Integer.parseInt(res[i]);
        }

        return ret;
    }
}
