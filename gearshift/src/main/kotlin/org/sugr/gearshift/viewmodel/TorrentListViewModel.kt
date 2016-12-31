package org.sugr.gearshift.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import io.reactivex.Observable
import org.sugr.gearshift.C
import org.sugr.gearshift.Logger
import org.sugr.gearshift.R
import org.sugr.gearshift.viewmodel.api.Api
import org.sugr.gearshift.viewmodel.databinding.SelectionListener
import org.sugr.gearshift.viewmodel.rxutil.observe
import org.sugr.gearshift.viewmodel.rxutil.observeKey

class TorrentListViewModel(tag: String, log: Logger, ctx: Context, prefs: SharedPreferences,
                           private val apiObservable: Observable<Api>,
                           private val activityLifecycle: Observable<ActivityLifecycle>):
        RetainedViewModel<TorrentListViewModel.Consumer>(tag, log) {

    interface Consumer {

    }

    val statusText = ObservableField<String>(ctx.getString(R.string.torrent_list_status_loading))
    val hasSpeedLimitSwitch = ObservableBoolean(false)
    val speedLimit = ObservableBoolean(false)
    val sortListener = SelectionListener()
    val sortEntries = SortBy.values().map { it.stringRes }.map { ctx.getString(it) }
    val sortDescending = ObservableBoolean(false)

    init {
        val prefsObservable = prefs.observe().share()

        prefsObservable.startWith { C.PREF_LIST_SORT_BY }.filter {
            key -> key == C.PREF_LIST_SORT_BY
        }.map { key ->
            SortBy.valueOf(prefs.getString(key, SortBy.AGE.name)).ordinal
        }.subscribe { index ->
            sortListener.position.set(index)
        }

        prefs.observeKey(C.PREF_LIST_SORT_DIRECTION).map { key ->
            SortDirection.valueOf(prefs.getString(key, SortDirection.DESCENDING.name))
        }.subscribe { direction ->
            sortDescending.set(direction == SortDirection.DESCENDING)
        }
    }

}

enum class SortBy(val stringRes: Int) {
    NAME(R.string.torrent_list_sort_by_name),
    SIZE(R.string.torrent_list_sort_by_size),
    STATUS(R.string.torrent_list_sort_by_status),
    RATE_DOWNLOAD(R.string.torrent_list_sort_by_rate_download),
    RATE_UPLOAD(R.string.torrent_list_sort_by_rate_upload),
    AGE(R.string.torrent_list_sort_by_age),
    PROGRESS(R.string.torrent_list_sort_by_progress),
    RATIO(R.string.torrent_list_sort_by_ratio),
    ACTIVITY(R.string.torrent_list_sort_by_activity),
    LOCATION(R.string.torrent_list_sort_by_location),
    PEERS(R.string.torrent_list_sort_by_peers),
    QUEUE(R.string.torrent_list_sort_by_queue)
}

enum class SortDirection {
    ASCENDING, DESCENDING
}
