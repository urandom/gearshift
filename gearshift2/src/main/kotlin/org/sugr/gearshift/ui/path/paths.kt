package org.sugr.gearshift.ui.path

import android.support.v4.app.FragmentManager
import org.sugr.gearshift.R
import org.sugr.gearshift.viewmodel.ProfileEditorViewModel
import org.sugr.gearshift.viewmodel.TorrentListViewModel
import org.sugr.gearshift.viewmodel.viewModelFrom

class TorrentListPath: Path<TorrentListViewModel> {
    override fun getViewModel(fm: FragmentManager): TorrentListViewModel {
        return viewModelFrom(fm) {tag, prefs ->
            TorrentListViewModel(tag, prefs)
        }
    }

    override val layout = R.layout.torrent_list_content
    override val title = R.string.app_name
}

class FirstTimeProfileEditorPath: Path<ProfileEditorViewModel> {
    override fun getViewModel(fm: FragmentManager): ProfileEditorViewModel {
        return viewModelFrom(fm) {tag, prefs ->
            ProfileEditorViewModel(tag, prefs)
        }
    }

    override val layout = R.layout.first_time_profile_editor
    override val depth = 1
    override val title = R.string.new_profile
    override val menu = R.menu.first_time_profile_editor
}
