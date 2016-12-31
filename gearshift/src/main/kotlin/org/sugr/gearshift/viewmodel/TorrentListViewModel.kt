package org.sugr.gearshift.viewmodel

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import io.reactivex.Observable
import org.sugr.gearshift.Logger
import org.sugr.gearshift.R
import org.sugr.gearshift.viewmodel.api.Api
import org.sugr.gearshift.viewmodel.databinding.SelectionListener

class TorrentListViewModel(tag: String, log: Logger, ctx: Context,
                           private val apiObservable: Observable<Api>,
                           private val activityLifecycle: Observable<ActivityLifecycle>):
        RetainedViewModel<TorrentListViewModel.Consumer>(tag, log) {

    interface Consumer {

    }

    val statusText = ObservableField<String>(ctx.getString(R.string.torrent_list_status_loading))
    val hasSpeedLimitSwitch = ObservableBoolean(false)
    val speedLimit = ObservableBoolean(false)
    val sortListener = SelectionListener()
    val sortEntries = ctx.resources.getStringArray(R.array.torrent_list_sort_entries)
    val sortDescending = ObservableBoolean(false)
}

