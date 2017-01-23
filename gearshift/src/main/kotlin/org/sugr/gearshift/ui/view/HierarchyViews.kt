package org.sugr.gearshift.ui.view

import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import io.reactivex.Flowable

interface ViewModelConsumer<in VM> {
    fun setViewModel(viewModel: VM)
}

interface ToolbarMenuItemClickListener {
    fun onToolbarMenuItemClick(item: MenuItem): Boolean
}

interface ToolbarConsumer {
    fun setToolbar(toolbar: Toolbar)
}

interface ContextMenuProvider {
    fun contextMenu(): Flowable<Int>
    fun closeContextMenu()
}

object Depth {
    val TOP_LEVEL = 0
}

