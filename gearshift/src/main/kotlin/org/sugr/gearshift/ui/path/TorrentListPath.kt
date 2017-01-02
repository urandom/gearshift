package org.sugr.gearshift.ui.path

import org.sugr.gearshift.R
import org.sugr.gearshift.ui.NavComponent
import org.sugr.gearshift.viewmodel.TorrentListViewModel
import org.sugr.gearshift.viewmodel.viewModelFrom

class TorrentListPath(navComponent: NavComponent,
                      val component: TorrentListComponent = TorrentListComponentImpl(navComponent)) :
        Path<TorrentListViewModel> {
    override val viewModel: TorrentListViewModel
        get() = component.viewModel

    override val layout = R.layout.torrent_list_content
    override val title = R.string.app_name
    override val extraLayouts = arrayOf(R.layout.torrent_list_bottom_sheet)
}

interface TorrentListComponent : NavComponent {
    val viewModel : TorrentListViewModel
}

class TorrentListComponentImpl(b : NavComponent) : TorrentListComponent, NavComponent by b {
    private val tag = TorrentListViewModel::class.java.toString()

    override val viewModel : TorrentListViewModel by lazy {
        viewModelFrom(fragmentManager, tag) {
            TorrentListViewModel(tag, log, context, prefs, apiObservable, sessionObservable, lifecycle)
        }
    }
}
