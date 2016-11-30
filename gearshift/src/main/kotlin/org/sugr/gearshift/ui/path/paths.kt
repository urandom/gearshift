package org.sugr.gearshift.ui.path

import android.support.v4.app.FragmentManager
import io.reactivex.Flowable
import org.sugr.gearshift.R
import org.sugr.gearshift.viewmodel.ActivityLifecycle
import org.sugr.gearshift.viewmodel.ProfileEditorViewModel
import org.sugr.gearshift.viewmodel.TorrentListViewModel
import org.sugr.gearshift.viewmodel.viewModelFrom

class TorrentListPath: Path<TorrentListViewModel> {
    override fun getViewModel(fm: FragmentManager, lifecycle: Flowable<ActivityLifecycle>): TorrentListViewModel {
        return viewModelFrom(fm = fm, lifecycle = lifecycle, factory = ::TorrentListViewModel)
    }

    override val layout = R.layout.torrent_list_content
    override val title = R.string.app_name
}

class FirstTimeProfileEditorPath: Path<ProfileEditorViewModel> {
    override fun getViewModel(fm: FragmentManager, lifecycle: Flowable<ActivityLifecycle>): ProfileEditorViewModel {
        return viewModelFrom(fm, lifecycle = lifecycle) {tag, app, lifecycle ->
            ProfileEditorViewModel(tag, app)
        }
    }

    override val layout = R.layout.first_time_profile_editor
    override val depth = 1
    override val title = R.string.new_profile
    override val menu = R.menu.first_time_profile_editor
}
