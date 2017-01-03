package org.sugr.gearshift.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.support.v4.widget.SwipeRefreshLayout
import android.view.LayoutInflater
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import org.sugr.gearshift.C
import org.sugr.gearshift.Logger
import org.sugr.gearshift.R
import org.sugr.gearshift.model.Session
import org.sugr.gearshift.model.Torrent
import org.sugr.gearshift.viewmodel.adapters.TorrentListAdapter
import org.sugr.gearshift.viewmodel.adapters.TorrentViewModelManager
import org.sugr.gearshift.viewmodel.api.Api
import org.sugr.gearshift.viewmodel.databinding.SelectionListener
import org.sugr.gearshift.viewmodel.rxutil.observe
import org.sugr.gearshift.viewmodel.rxutil.refresh
import java.util.*
import java.util.concurrent.TimeUnit

class TorrentListViewModel(tag: String, log: Logger, ctx: Context, prefs: SharedPreferences,
                           private val apiObservable: Observable<Api>,
                           private val sessionObservable: Observable<Session>,
                           private val activityLifecycle: Observable<ActivityLifecycle>):
        RetainedViewModel<TorrentListViewModel.Consumer>(tag, log),
        TorrentViewModelManager by TorrentViewModelManagerImpl(log, ctx, prefs) {

    interface Consumer {

    }

    val statusText = ObservableField<String>(ctx.getString(R.string.torrent_list_status_loading))
    val hasSpeedLimitSwitch = ObservableBoolean(false)
    val speedLimit = ObservableBoolean(false)
    val sortListener = SelectionListener()
    val sortEntries = SortBy.values().map { it.stringRes }.map { ctx.getString(it) }
    val sortDescending = ObservableBoolean(false)
    val refreshing = ObservableBoolean(false)
    val refreshListener = SwipeRefreshLayout.OnRefreshListener { refresher.onNext(1) }

    private val refresher = PublishSubject.create<Any>()

    val sorting = prefs.observe().filter {
        it == C.PREF_LIST_SORT_BY || it == C.PREF_LIST_SORT_DIRECTION
    }.map { Sorting(
            SortBy.valueOf(prefs.getString(C.PREF_LIST_SORT_BY, SortBy.AGE.name)),
            SortDirection.valueOf(prefs.getString(C.PREF_LIST_SORT_DIRECTION, SortDirection.DESCENDING.name))
    ) }
            .startWith(Sorting(SortBy.AGE, SortDirection.DESCENDING))
            .takeUntil(takeUntilDestroy()).replay(1).refCount()

    val torrents = apiObservable.refresh(refresher).switchMap { api ->
        api.torrents(sessionObservable)
                .flatMap { torrentSet ->
                    sorting.take(1).flatMap { sorting ->
                        if (sorting.by == SortBy.STATUS || sorting.baseBy == SortBy.STATUS) {
                            sessionObservable.take(1).map { session ->
                                Pair(sorting, session.seedRatioLimit)
                            }
                        } else {
                            Observable.just(Pair(sorting, 0f))
                        }
                    }.observeOn(Schedulers.computation()).map { pair ->
                        val now = Date().time

                        val sorted = torrentSet.sortedWith(Comparator { t1, t2 ->
                            val ret = compareWith(t1, t2, pair.first.by, pair.first.direction, pair.second)

                            if (ret == 0) {
                                compareWith(t1, t2, pair.first.baseBy, pair.first.baseDirection, pair.second)
                            } else {
                                ret
                            }
                        })

                        log.D("Time to sort ${torrentSet.size} torrents: ${Date().time - now}")

                        sorted
                    }
                }
    }
            .takeUntil(takeUntilDestroy()).replay(1).refCount()
            .observeOn(AndroidSchedulers.mainThread())

    init {

        sorting.subscribe({ sorting ->
            sortListener.position.set(sorting.by.ordinal)
            sortDescending.set(sorting.direction == SortDirection.DESCENDING)
        }, { err -> log.D("Sorting error: ${err}") })

        apiObservable.observeOn(AndroidSchedulers.mainThread()).subscribe { refreshing.set(true) }

        Observable.timer(1, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { refreshing.set(true) }

        torrents.subscribe {
            refreshing.set(true)
            refreshing.set(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        removeAllViewModels()
    }

    fun onTorrentClick(torrent: Torrent) {}

    fun adapter(ctx: Context) : TorrentListAdapter =
        TorrentListAdapter(torrents,
                sessionObservable.observeOn(AndroidSchedulers.mainThread()),
                log, this, LayoutInflater.from(ctx), Consumer { onTorrentClick(it) })

    private fun compareWith(t1: Torrent, t2: Torrent, by: SortBy, direction: SortDirection, globalLimit: Float) : Int {
        val ret = when (by) {
            SortBy.NAME -> t1.name.compareTo(t2.name, true)
            SortBy.SIZE -> t1.totalSize.compareTo(t2.totalSize)
            SortBy.STATUS -> t1.statusSortWeight(globalLimit).compareTo(t2.statusSortWeight(globalLimit))
            SortBy.RATE_DOWNLOAD -> t2.downloadRate.compareTo(t1.downloadRate)
            SortBy.RATE_UPLOAD -> t2.uploadRate.compareTo(t1.uploadRate)
            SortBy.AGE -> t1.addedTime.compareTo(t2.addedTime)
            SortBy.PROGRESS -> t1.downloadProgress.compareTo(t2.downloadProgress)
            SortBy.RATIO -> t1.uploadRatio.compareTo(t2.uploadRatio)
            SortBy.ACTIVITY -> (t2.downloadRate + t2.uploadRate).compareTo(t1.downloadRate + t1.uploadRate)
            SortBy.LOCATION -> t1.downloadDir.compareTo(t2.downloadDir, true)
            SortBy.PEERS -> t1.connectedPeers.compareTo(t2.connectedPeers)
            SortBy.QUEUE -> t1.queuePosition.compareTo(t2.queuePosition)
        }

        return when (direction) {
            SortDirection.DESCENDING -> -ret
            SortDirection.ASCENDING -> ret

        }
    }
}

data class Sorting(val by: SortBy,
                   val direction: SortDirection,
                   val baseBy: SortBy = SortBy.AGE,
                   val baseDirection: SortDirection = SortDirection.DESCENDING)

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

private class TorrentViewModelManagerImpl(private val log: Logger,
                                          private val ctx: Context,
                                          private val prefs: SharedPreferences) : TorrentViewModelManager {
    private val viewModelMap = mutableMapOf<String, TorrentViewModel>()

    override fun getViewModel(hash: String): TorrentViewModel {
        var vm = viewModelMap[hash]
        if (vm == null) {
            vm = TorrentViewModel(log, ctx, prefs)

            viewModelMap[hash] = vm
        }

        return vm
    }

    override fun removeViewModel(hash: String) {
        viewModelMap.remove(hash)?.destroy()
    }

    override fun removeAllViewModels() {
        viewModelMap.keys.toList().forEach { hash -> removeViewModel(hash) }
    }
}