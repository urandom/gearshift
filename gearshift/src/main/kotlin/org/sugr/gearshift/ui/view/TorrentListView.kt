package org.sugr.gearshift.ui.view

import android.content.Context
import android.graphics.Rect
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import io.reactivex.Flowable
import org.sugr.gearshift.R
import org.sugr.gearshift.databinding.TorrentListContentBinding
import org.sugr.gearshift.viewmodel.TorrentListViewModel

class TorrentListView(context: Context?, attrs: AttributeSet?) :
        FrameLayout(context, attrs),
        TorrentListViewModel.Consumer,
        ViewModelConsumer<TorrentListViewModel>,
        ToolbarMenuItemClickListener,
		ContextMenuProvider {

	lateinit private var viewModel : TorrentListViewModel

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (!inLayout()) {
            return
        }

        val binding = TorrentListContentBinding.bind(this)
        binding.viewModel = viewModel

        val res = context.resources

        binding.list.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        binding.list.addItemDecoration(SpacerDecoration(
                res.getDimension(R.dimen.torrent_list_top_space),
                res.getDimension(R.dimen.torrent_list_bottom_space)
        ))
        binding.list.layoutManager = GridLayoutManager(context, res.getInteger(R.integer.torrent_list_columns))
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

	override fun contextMenu(): Flowable<Int> {
		return viewModel.contextToolbarFlowable()
	}
}

class SpacerDecoration(val first: Float = 0f, val last: Float = 0f): RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State?) {
        if (parent.getChildAdapterPosition(view) == 0) {
            outRect.top = first.toInt()
        }

        if (parent.getChildAdapterPosition(view) == parent.adapter.itemCount - 1) {
            outRect.bottom = last.toInt()
        }
    }
}