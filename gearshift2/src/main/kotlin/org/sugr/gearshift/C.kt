package org.sugr.gearshift

import android.util.Log

object C {
    val PREF_PROFILES = "profiles"
    val PROFILES_PREF_NAME = "profiles"
}

private object c {
    val TAG = "GearShift"
}

fun logD(message: String) {
    Log.d(c.TAG, message)
}
