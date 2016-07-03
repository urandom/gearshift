package org.sugr.gearshift.ui.view

import android.view.Menu
import android.view.MenuItem
import android.view.View
import kotlin.reflect.KClass

interface ViewModelConsumer<in VM> {
    fun setViewModel(viewModel: VM)
}

interface ToolbarMenuItemClickListener {
    fun onToolbarMenuItemClick(menu: Menu, item: MenuItem): Boolean
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
