package org.sugr.gearshift.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.support.design.widget.NavigationView
import android.view.LayoutInflater
import android.view.MenuItem
import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.PublishSubject
import org.funktionale.option.firstOption
import org.funktionale.option.toOption
import org.sugr.gearshift.C
import org.sugr.gearshift.Logger
import org.sugr.gearshift.R
import org.sugr.gearshift.model.loadProfiles
import org.sugr.gearshift.model.profileOf
import org.sugr.gearshift.ui.path.Path
import org.sugr.gearshift.viewmodel.adapters.FilterAdapter
import org.sugr.gearshift.viewmodel.api.apiOf
import org.sugr.gearshift.viewmodel.rxutil.*
import io.reactivex.functions.Consumer as rxConsumer

class MainNavigationViewModel(tag: String, log: Logger,
                              private val ctx: Context,
                              private val prefs: SharedPreferences) :
        RetainedViewModel<MainNavigationViewModel.Consumer>(tag, log) {

	interface Consumer {
		fun restorePath()
		fun closeDrawer()
		fun createProfile()
	}

    val activityLifecycle = PublishSubject.create<ActivityLifecycle>()

    val gson = Gson()

    val refresher = PublishSubject.create<Any>()

    val navigationListener = object : NavigationView.OnNavigationItemSelectedListener {
        override fun onNavigationItemSelected(item: MenuItem): Boolean {
            log.D("Navigation item ${item.title}")

            consumer?.closeDrawer()

            return true
        }

    }

    val profileObservable = prefs.observe()
            .filter { key -> key == C.PREF_CURRENT_PROFILE }
            .startWith(C.PREF_CURRENT_PROFILE)
            .map { key -> prefs.getString(key, "") }
            .map { id ->
				if (id == "") prefs.getStringSet(C.PREF_PROFILES, setOf()).firstOption()
				else id.toOption()
			}
			.filter { opt -> opt.isDefined() }
			.map { opt -> opt.get() }
            .map { id -> profileOf(id, prefs) }
            .filter { profile -> profile.valid }
            .takeUntil(takeUntilDestroy())
            .replay(1).refCount()

    val apiObservable = profileObservable
            .map { profile -> apiOf(profile, ctx, prefs, gson, log) }
            .takeUntil(takeUntilDestroy())
            .replay(1).refCount()

    var firstTimeProfile = true

	val sessionObservable = apiObservable.refresh(refresher).switchToThrowableEither { api ->
		api.session()
	}.pauseOn(activityLifecycle.onStop()).takeUntil(takeUntilDestroy()).replay(1).refCount()

	val torrentsObservable = apiObservable.refresh(refresher).switchToThrowableEither { api ->
		api.torrents()
	}.pauseOn(activityLifecycle.onStop()).takeUntil(takeUntilDestroy()).replay(1).refCount()

	private val directories = torrentsObservable.filterRightOr(setOf()).map { set ->
		set.map { it.downloadDir }.toSet()
	}.replay(1).refCount()

	private val trackers = torrentsObservable.filterRightOr(setOf()).map { set ->
		set.map { it.trackers.map { it.host } }.flatten().toSet() + setOf("")
	}.replay(1).refCount()

    private val filterList = prefs.observe().map { key ->
		when (key) {
			C.PREF_LIST_FILTER_STATUS, C.PREF_LIST_FILTER_DIRECTORY, C.PREF_LIST_FILTER_TRACKER -> true
			else -> key.startsWith("PREF_FILTER_")
		}
    }.filter { it }.map { prefs }.startWith(prefs).flatMap { prefs ->
        val statusFilters = getStatusFilters(prefs)
		statusFilters.add(0, Filter.Header(
				ctx.getString(R.string.filter_header_status),
				forType = FilterHeaderType.STATUS)
		)

		val observables = mutableListOf(Observable.just(statusFilters))

        if (prefs.getBoolean(C.PREF_FILTER_DIRECTORIES, true)) {
			observables.add(
					directories.map { set ->
						set.sorted().map { dir -> Filter.Directory(dir) as Filter }.toMutableList().apply {
							add(0, Filter.Header(
									ctx.getString(R.string.filter_header_directories),
									forType = FilterHeaderType.DIRECTORIES
							))
						}
					}
			)
        }

		if (prefs.getBoolean(C.PREF_FILTER_TRACKERS, false)) {
			observables.add(
					trackers.map { set ->
						set.sorted().map { host -> Filter.Tracker(host) as Filter }.toMutableList().apply {
							add(0, Filter.Header(
									ctx.getString(R.string.filter_header_trackers),
									forType = FilterHeaderType.TRACKERS
							))
						}
					}
			)
		}

		Observable.merge(observables).filter {
			it.isNotEmpty()
		}.scan(mutableListOf<Filter>(
				// The initial list will preseve the order of the filter types
				Filter.Header(value = "", forType = FilterHeaderType.STATUS),
				Filter.Header(value = "", forType = FilterHeaderType.DIRECTORIES),
				Filter.Header(value = "", forType = FilterHeaderType.TRACKERS)
		)) { accum, list ->
			val header = list[0]
			var insertIndex = 0

			if (header is Filter.Header) {
				val iter = accum.listIterator()

				while (iter.hasNext()) {
					val index = iter.nextIndex()
					val filter = iter.next()
					if (filter is Filter.Header && filter.forType == header.forType) {
						insertIndex = index
						iter.remove()
					} else if (filter is Filter.Status && header.forType == FilterHeaderType.STATUS) {
						iter.remove()
					} else if (filter is Filter.Directory && header.forType == FilterHeaderType.DIRECTORIES) {
						iter.remove()
					} else if (filter is Filter.Tracker && header.forType == FilterHeaderType.TRACKERS) {
						iter.remove()
					}
				}
			}

			accum.addAll(insertIndex, list)

			accum
		}.map { list ->
			val selectedStatus = prefs.getString(C.PREF_LIST_FILTER_STATUS, "")
			val selectedDirectory = prefs.getString(C.PREF_LIST_FILTER_DIRECTORY, "")
			val selectedTracker = prefs.getString(C.PREF_LIST_FILTER_TRACKER, "")

			list.forEach { filter ->
				when (filter) {
					is Filter.Status -> filter.active = filter.value.name == selectedStatus
					is Filter.Directory -> filter.active = filter.value == selectedDirectory
					is Filter.Tracker -> filter.active = filter.value == selectedTracker
				}
			}
			list as List<Filter>
		}
    }
            .takeUntil(takeUntilUnbind())
            .observeOn(AndroidSchedulers.mainThread())
            .replay(1).refCount()

    override fun bind(consumer: Consumer) {
        super.bind(consumer)

		val profiles = loadProfiles(prefs)

		if (profiles.isEmpty() && firstTimeProfile) {
			firstTimeProfile = false
			consumer.createProfile()
		} else {
			consumer.restorePath()
		}
    }

	fun filtersAdapter(ctx: Context): FilterAdapter {
		return FilterAdapter(
				filterList, log, ctx,
				LayoutInflater.from(ctx),
				rxConsumer { filter ->
					when (filter) {
						is Filter.Status -> {
							val current = prefs.getString(C.PREF_LIST_FILTER_STATUS, "")

							val new = if (current == filter.value.name) {
								""
							} else {
								filter.value.name
							}
							prefs.set(C.PREF_LIST_FILTER_STATUS, new)
						}
						is Filter.Directory -> {
							val current = prefs.getString(C.PREF_LIST_FILTER_DIRECTORY, "")

							val new = if (current == filter.value) {
								""
							} else {
								filter.value
							}
							prefs.set(C.PREF_LIST_FILTER_DIRECTORY, new)
						}
						is Filter.Tracker -> {
							val current = prefs.getString(C.PREF_LIST_FILTER_TRACKER, "")

							val new = if (current == filter.value) {
								""
							} else {
								filter.value
							}
							prefs.set(C.PREF_LIST_FILTER_TRACKER, new)
						}
					}
					consumer?.closeDrawer()
				}
		)
	}

	fun  onSetcontent(newPath: Path<*>, oldPath: Path<*>) {
	}

}

enum class ActivityLifecycle {
    CREATE, START, RESUME, PAUSE, STOP, DESTROY
}


private fun getStatusFilters(prefs: SharedPreferences): MutableList<Filter> {
    val filters = mutableListOf<Filter>()

    if (prefs.getBoolean(C.PREF_FILTER_DOWNLOADING, false)) {
        filters.add(FilterStatus.DOWNLOADING.asFilter())
    }

    if (prefs.getBoolean(C.PREF_FILTER_SEEDING, false)) {
        filters.add(FilterStatus.SEEDING.asFilter())
    }

    if (prefs.getBoolean(C.PREF_FILTER_PAUSED, false)) {
        filters.add(FilterStatus.PAUSED.asFilter())
    }

    if (prefs.getBoolean(C.PREF_FILTER_COMPLETE, false)) {
        filters.add(FilterStatus.COMPLETE.asFilter())
    }

    if (prefs.getBoolean(C.PREF_FILTER_INCOMPLETE, false)) {
        filters.add(FilterStatus.INCOMPLETE.asFilter())
    }

    if (prefs.getBoolean(C.PREF_FILTER_ACTIVE, false)) {
        filters.add(FilterStatus.ACTIVE.asFilter())
    }

    if (prefs.getBoolean(C.PREF_FILTER_CHECKING, false)) {
        filters.add(FilterStatus.CHECKING.asFilter())
    }

    if (prefs.getBoolean(C.PREF_FILTER_ERRORS, false)) {
        filters.add(FilterStatus.ERRORS.asFilter())
    }

    return filters
}
