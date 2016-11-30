package org.sugr.gearshift.ui.view

import android.view.Menu
import android.view.MenuItem

interface ViewModelConsumer<in VM> {
    fun setViewModel(viewModel: VM)
}

interface ToolbarMenuItemClickListener {
    fun onToolbarMenuItemClick(menu: Menu, item: MenuItem): Boolean
}

object Depth {
    val TOP_LEVEL = 0
}

