package org.sugr.gearshift.ui.view

import android.support.v7.widget.Toolbar
import android.view.MenuItem
import io.reactivex.Flowable
import org.sugr.gearshift.ui.path.PathNavigator

interface ViewModelConsumer<in VM> {
    fun setViewModel(viewModel: VM)
}

interface PathNavigatorConsumer {
	fun setPathNavigator(navigator: PathNavigator)
}

interface ToolbarMenuItemClickListener {
    fun onToolbarMenuItemClick(item: MenuItem): Boolean
}

interface ToolbarConsumer {
    fun setToolbar(toolbar: Toolbar)
    fun onToolbarMenuChanged()
}

interface ContextMenuProvider {
    fun contextMenu(): Flowable<Int>
    fun closeContextMenu()
}

object Depth {
    val TOP_LEVEL = 0
}

