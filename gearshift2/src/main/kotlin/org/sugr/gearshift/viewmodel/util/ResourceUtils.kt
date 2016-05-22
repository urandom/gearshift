package org.sugr.gearshift.viewmodel.util

import android.support.annotation.ArrayRes
import org.sugr.gearshift.app

object ResourceUtils {
    fun stringArrayAsInt(@ArrayRes stringArrayRes: Int): IntArray {
        val res = app().resources.getStringArray(stringArrayRes)
        val ret = IntArray(res.size)

        for (i in res.indices) {
            ret[i] = Integer.parseInt(res[i])
        }

        return ret
    }
}
