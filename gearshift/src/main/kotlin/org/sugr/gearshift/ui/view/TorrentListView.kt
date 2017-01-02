package org.sugr.gearshift.ui.view

import android.content.Context
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.GridLayoutManager
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuItem
import android.widget.FrameLayout
import org.sugr.gearshift.R
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

        binding.list.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        binding.list.layoutManager = GridLayoutManager(context, context.resources.getInteger(R.integer.torrent_list_columns))
        binding.list.adapter = viewModel.adapter(context)

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

