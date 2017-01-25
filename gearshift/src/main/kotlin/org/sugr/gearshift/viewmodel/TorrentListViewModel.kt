package org.sugr.gearshift.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.support.v4.widget.SwipeRefreshLayout
import android.view.LayoutInflater
import io.reactivex.BackpressureStrategy
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import org.funktionale.option.Option
import org.sugr.gearshift.C
import org.sugr.gearshift.Logger
import org.sugr.gearshift.R
import org.sugr.gearshift.model.AltSpeedSession
import org.sugr.gearshift.model.Session
import org.sugr.gearshift.model.Torrent
import org.sugr.gearshift.viewmodel.adapters.TorrentListAdapter
import org.sugr.gearshift.viewmodel.adapters.TorrentSelectorManager
import org.sugr.gearshift.viewmodel.adapters.TorrentViewModelManager
import org.sugr.gearshift.viewmodel.api.Api
import org.sugr.gearshift.viewmodel.api.CurrentSpeed
import org.sugr.gearshift.viewmodel.api.StatisticsApi
import org.sugr.gearshift.viewmodel.databinding.SelectionListener
import org.sugr.gearshift.viewmodel.databinding.observe
import org.sugr.gearshift.viewmodel.ext.readableFileSize
import org.sugr.gearshift.viewmodel.rxutil.*
import java.util.*
import java.util.concurrent.TimeUnit

class TorrentListViewModel(tag: String, log: Logger, ctx: Context, prefs: SharedPreferences,
						   private val apiObservable: Observable<Api>,
						   private val sessionObservable: Observable<Session>,
						   private val activityLifecycle: Observable<ActivityLifecycle>):
		RetainedViewModel<TorrentListViewModel.Consumer>(tag, log),
		TorrentViewModelManager by TorrentViewModelManagerImpl(log, ctx, prefs),
		TorrentSelectorManager {

	interface Consumer {
		fun selectedTorrentStatus(paused: Boolean, running: Boolean, empty: Boolean)

	}

	val statusText = ObservableField<String>(ctx.getString(R.string.torrent_list_status_loading))
	val hasSpeedLimitSwitch = ObservableBoolean(false)
	val speedLimit = ObservableBoolean()
	val sortListener = SelectionListener()
	val sortEntries = SortBy.values().map { it.stringRes }.map { ctx.getString(it) }
	val sortDescending = ObservableBoolean(false)
	val refreshing = ObservableBoolean(false)
	val refreshListener = SwipeRefreshLayout.OnRefreshListener { refresher.onNext(1) }

	private val refresher = PublishSubject.create<Any>()
	private val selectedTorrents = mutableMapOf<String, Torrent>()

	private val contextMenuProcessor = BehaviorProcessor.create<Int>()

	val sorting = prefs.observe().filter {
		it == C.PREF_LIST_SORT_BY || it == C.PREF_LIST_SORT_DIRECTION
	}.map { Sorting(
			SortBy.valueOf(prefs.getString(C.PREF_LIST_SORT_BY, SortBy.AGE.name)),
			SortDirection.valueOf(prefs.getString(C.PREF_LIST_SORT_DIRECTION, SortDirection.DESCENDING.name))
	) }
			.startWith(Sorting(SortBy.AGE, SortDirection.DESCENDING))
			.takeUntil(takeUntilDestroy()).replay(1).refCount()

	val torrents = apiObservable.refresh(refresher).switchMap { api ->
		api.torrents(sessionObservable).combineLatestWith(sorting) { set, sorting ->
			Pair(set, sorting)
		}.flatMap { pair ->
			val limitObservable = if (pair.second.by == SortBy.STATUS || pair.second.baseBy == SortBy.STATUS) {
				sessionObservable.take(1).map { session -> session.seedRatioLimit }
			} else {
				Observable.just(0f)
			}

			limitObservable.observeOn(Schedulers.computation()).map { limit ->
				val now = Date().time

				val sorted = pair.first.sortedWith(Comparator { t1, t2 ->
					val ret = compareWith(t1, t2, pair.second.by, pair.second.direction, limit)

					if (ret == 0) {
						compareWith(t1, t2, pair.second.baseBy, pair.second.baseDirection, limit)
					} else {
						ret
					}
				})

				log.D("Time to sort ${pair.first.size} torrents: ${Date().time - now}")

				sorted.filter { t -> !t.downloadDir.contains("other") }
			}
		}
	}
			.pauseOn(activityLifecycle.onStop())
			.takeUntil(takeUntilDestroy()).replay(1).refCount()
			.observeOn(AndroidSchedulers.mainThread())

	private val speedLimitUpdateSignal = BehaviorSubject.createDefault(true)

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

		sortDescending.observe().debounce(250, TimeUnit.MILLISECONDS).map { o ->
			o.get()
		}.subscribe { descending ->
			prefs.set(C.PREF_LIST_SORT_DIRECTION,
					if (descending) SortDirection.DESCENDING.name
					else SortDirection.ASCENDING.name)
		}

		sortListener.position.observe().debounce(250, TimeUnit.MILLISECONDS).map { o ->
			o.get()
		}.map { index ->
			SortBy.values()[index].name
		}.subscribe { value ->
			prefs.set(C.PREF_LIST_SORT_BY, value)
		}

		val downloadDirObservable = sessionObservable.map { session -> session.downloadDir }

		apiObservable.switchMap { api ->
			val speedObservable = if (api is StatisticsApi) {
				api.currentSpeed()
			} else {
				Observable.just(CurrentSpeed(-1, -1))
			}

			val spaceObservable = if (api is StatisticsApi) {
				api.freeSpace(downloadDirObservable)
			} else {
				Observable.just(-1L)
			}

			speedObservable.combineLatestWith(spaceObservable) { t1, t2 -> Pair(t1, t2) }
		}.combineLatestWith(sessionObservable) { speedPair, session ->
			val speed = speedPair.first
			val space = speedPair.second

			var downLimit = -1L
			var upLimit = -1L

			if (session is AltSpeedSession) {
				downLimit = if (session.altSpeedLimitEnabled) {
					session.altDownloadSpeedLimit * 1024
				} else if (session.downloadSpeedLimitEnabled) {
					session.downloadSpeedLimit * 1024
				} else {
					0
				}

				upLimit = if (session.altSpeedLimitEnabled) {
					session.altUploadSpeedLimit * 1024
				} else if (session.uploadSpeedLimitEnabled) {
					session.uploadSpeedLimit * 1024
				} else {
					0
				}

			} else {
				downLimit = session.downloadSpeedLimit * 1024
				upLimit = session.uploadSpeedLimit * 1024
			}

			Status(speed.download, speed.upload, downLimit, upLimit, space)
		}
				.pauseOn(activityLifecycle.onStop())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe { status ->
					val download = if (status.download == -1L) {
						ctx.getString(R.string.status_bar_download,
								(status.downloadLimit.readableFileSize() + "/s"),
								""
						)
					} else {
						ctx.getString(R.string.status_bar_download,
								(status.download.readableFileSize() + "/s"),
								" (" + (status.downloadLimit.readableFileSize() + "/s") + ")"
						)
					}

					val upload = if (status.upload == -1L) {
						ctx.getString(R.string.status_bar_upload,
								(status.uploadLimit.readableFileSize() + "/s"),
								""
						)
					} else {
						ctx.getString(R.string.status_bar_upload,
								(status.upload.readableFileSize() + "/s"),
								" (" + (status.uploadLimit.readableFileSize() + "/s") + ")"
						)
					}

					val space = if (status.freeSpace == -1L) {
						""
					} else {
						ctx.getString(R.string.status_bar_free_space,
								status.freeSpace.readableFileSize())
					}

					statusText.set(arrayOf(download, upload, space).joinToString(", "))
				}

		sessionObservable.pauseOn(speedLimitUpdateSignal).filter { session ->
			session is AltSpeedSession
		}.map { session ->
			session as AltSpeedSession
		}.subscribe { session ->
			hasSpeedLimitSwitch.set(true)
			speedLimit.set(session.altSpeedLimitEnabled)
		}
	}

	override fun onDestroy() {
		super.onDestroy()

		clearSelection()
		removeAllViewModels()
	}

	override fun toggleSelection(torrent: Torrent) {
		val vm = getViewModel(torrent.hash)
		val isChecked = !vm.isChecked.get()

		vm.isChecked.set(isChecked)

		if (isChecked) {
			selectedTorrents[torrent.hash] = torrent
		} else {
			selectedTorrents.remove(torrent.hash)
		}

		toggleContextMenuItems()
		toggleContextMenu()
	}

	override fun clearSelection(torrent: Option<Torrent>) {
		if (torrent.isDefined()) {
			val t = torrent.get()
			getViewModel(t.hash).isChecked.set(false)
			selectedTorrents.remove(t.hash)
		} else {
			selectedTorrents.keys.forEach { hash ->
				getViewModel(hash).isChecked.set(false)
			}
			selectedTorrents.clear()
		}

		toggleContextMenuItems()
		toggleContextMenu()
	}


	fun onSelectAllTorrents() {
		torrents.take(1).observeOn(AndroidSchedulers.mainThread()).subscribe { torrents ->
			torrents.map { it.hash }.map {
				getViewModel(it)
			}.forEachIndexed { i, viewModel ->
				if (!viewModel.isChecked.get()) {
					val torrent = torrents[i]

					viewModel.isChecked.set(true)
					selectedTorrents[torrent.hash] = torrent
				}
			}


			toggleContextMenuItems()
			toggleContextMenu()
		}
	}

	override fun hasSelection(): Boolean {
		return selectedTorrents.isNotEmpty()
	}

	fun contextToolbarFlowable() = contextMenuProcessor
			.takeUntil(takeUntilUnbind().toFlowable(BackpressureStrategy.LATEST))


	fun onSpeedLimitChecked(checked: Boolean) {
		speedLimitUpdateSignal.onNext(false)

		sessionObservable.take(1).flatMapCompletable { session ->
			if (session is AltSpeedSession) {
				session.altSpeedLimitEnabled = checked

				apiObservable.take(1).flatMapCompletable { api ->
					api.updateSession(session)
				}
			} else {
				Completable.complete()
			}
		}.observeOn(AndroidSchedulers.mainThread())
				.subscribe({
					speedLimitUpdateSignal.onNext(true)
				}, { err ->
					log.E("torrent list set alt speed limit", err)
				})
	}

	fun onTorrentClick(torrent: Torrent) {}

	fun adapter(ctx: Context) : TorrentListAdapter =
		TorrentListAdapter(torrents,
				log,
				this, this, LayoutInflater.from(ctx), Consumer { onTorrentClick(it) })

	fun onResumeSelected() {
		val selected = HashSet(selectedTorrents.keys)

		apiObservable.combineLatestWith(
				torrents.map { torrents ->
					torrents.filter { selected.contains(it.hash) }
				}.map { torrents ->
					torrents.filter { !it.isActive }
				}.map { torrents ->
					val stopped = torrents.filter { it.statusType == Torrent.StatusType.STOPPED }.toTypedArray()
					val queued = torrents.filter { it.statusType != Torrent.StatusType.STOPPED }.toTypedArray()

					Pair(stopped, queued)
				},
				{ api, hashes -> Triple(api, hashes.first, hashes.second) }
		).take(1).flatMapCompletable { triple ->
			triple.first.startTorrents(triple.second, triple.third)
		}.subscribe({}) { err -> log.E("resuming selected torrents", err) }

		clearSelection()
	}

	fun onPauseSelected() {
		val selected = HashSet(selectedTorrents.keys)

		apiObservable.combineLatestWith(
				torrents.map { torrents ->
					torrents.filter { selected.contains(it.hash) }
				}.map { torrents ->
					torrents.filter { it.isActive }
				}.map { torrents -> torrents.toTypedArray() },
				{ api, torrents -> Pair(api, torrents) }
		).take(1).flatMapCompletable { pair ->
			pair.first.stopTorrents(pair.second)
		}.subscribe({}) { err -> log.E("pausing selected torrents", err) }

		clearSelection()
	}

	fun onSearchToggle() {
	}

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

	private fun toggleContextMenu() {
		val menu = if (hasSelection()) R.menu.torrent_list_context else 0

		if (contextMenuProcessor.value != menu) {
			contextMenuProcessor.onNext(menu)
		}
	}

	private fun toggleContextMenuItems() {
		torrents.map { torrents ->
			torrents.filter { torrent -> selectedTorrents.contains(torrent.hash) }
		}.map {  torrents ->
			var paused = false
			var running = false

			for (torrent in torrents) {
				when {
					torrent.isActive -> running = true
					else -> paused = true
				}

				if (paused && running) {
					break
				}
			}

			Triple(paused, running, torrents.isEmpty())
		}.distinctUntilChanged().observeOn(AndroidSchedulers.mainThread()).subscribe { tripple ->
			consumer?.selectedTorrentStatus(tripple.first, tripple.second, tripple.third)
		}
	}
}

private data class Status(val download: Long, val upload: Long,
						  val downloadLimit: Long, val uploadLimit: Long,
						  val freeSpace: Long)

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
