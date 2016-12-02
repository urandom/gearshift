package org.sugr.gearshift.ui.path

import android.support.v4.app.FragmentManager
import org.sugr.gearshift.viewmodel.RetainedViewModel
import org.sugr.gearshift.viewmodel.destroyViewModel

interface Path<VM: RetainedViewModel<*>> {
    val layout : Int

    val extraLayouts: Array<Int>
        get() = arrayOf()
    val title : Int
        get() = 0
    val menu : Int
        get() = 0
    val depth : Int
        get() = 0

    val viewModel : VM

    fun destroyViewModel(fm: FragmentManager) {
        destroyViewModel(fm, viewModel)
    }

    fun isTopLevel() : Boolean {
        return depth == TOP_LEVEL
    }

    companion object {
        val TOP_LEVEL = 0
    }
}
