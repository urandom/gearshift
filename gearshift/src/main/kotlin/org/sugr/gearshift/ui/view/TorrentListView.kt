package org.sugr.gearshift.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuItem
import android.widget.FrameLayout
import org.sugr.gearshift.databinding.TorrentListContentBinding
import org.sugr.gearshift.viewmodel.TorrentListViewModel

class TorrentListView(context: Context?, attrs: AttributeSet?) :
        FrameLayout(context, attrs),
        TorrentListViewModel.Consumer,
        ViewModelConsumer<TorrentListViewModel>,
        ToolbarMenuItemClickListener {
    lateinit private var viewModel : TorrentListViewModel

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (!inLayout()) {
            return
        }

        val binding = TorrentListContentBinding.bind(this)
        binding.viewModel = viewModel

        viewModel.bind(this)

    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        viewModel.unbind()
    }

    override fun setViewModel(viewModel: TorrentListViewModel) {
        this.viewModel = viewModel
    }

    override fun onToolbarMenuItemClick(menu: Menu, item: MenuItem): Boolean {
        return false
    }

}

