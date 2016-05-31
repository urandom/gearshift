package org.sugr.gearshift.ui.view

import android.view.View
import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ViewDepth(val value : Int)

interface ViewModelConsumer<in VM> {
    fun setViewModel(viewModel: VM)
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
