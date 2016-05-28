package org.sugr.gearshift.ui.view

import android.view.View
import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ViewDepth(val value : Int)

interface ViewDestructor {
    // Called when a view is replaced with one with the same or lesser depth
    // Any retained view model should be retained
    fun onDestroy()
}

interface DetachBlocker {
    fun canDetach() : Boolean
}

object Depth {
    val TOP_LEVEL = 0
}

inline fun <reified V : View> viewDepth(cls : KClass<V> = V::class) : Int {
    for (a in cls.annotations) {
        if (a is ViewDepth) {
            return a.value
        }
    }
    return Depth.TOP_LEVEL
}

fun viewDepth(v : View) : Int {
    return viewDepth(v.javaClass.kotlin)
}
