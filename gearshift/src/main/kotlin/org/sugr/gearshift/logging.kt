package org.sugr.gearshift

import android.util.Log

interface Logger {
    fun D(message: String)
    fun E(message: String, e: Throwable)
}

object Log : Logger {
    override fun D(message: String) { Log.d(c.TAG, message) }
    override fun E(message: String, e: Throwable) { Log.e(c.TAG, message, e) }

}

private object c {
    val TAG = "GearShift"
}
