package org.sugr.gearshift.ui.path

import android.support.v4.app.FragmentManager
import org.sugr.gearshift.viewmodel.RetainedViewModel
import org.sugr.gearshift.viewmodel.destroyViewModel
import java.io.Serializable

interface Path<VM: RetainedViewModel<*>>: Serializable {
    val layout : Int

    val extraLayouts: Array<Int>
        get() = arrayOf()
    val title : Int
        get() = 0
    val menu : Int
        get() = 0
    val depth : Int
        get() = 0

    fun getViewModel(fm: FragmentManager): VM

    fun destroyViewModel(fm: FragmentManager) {
        destroyViewModel(fm, getViewModel(fm))
    }

    fun isTopLevel() : Boolean {
        return depth == TOP_LEVEL
    }

    companion object {
        val TOP_LEVEL = 0
    }
}
