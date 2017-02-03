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
import org.sugr.gearshift.C
import org.sugr.gearshift.Logger
import org.sugr.gearshift.R
import org.sugr.gearshift.model.loadProfiles
import org.sugr.gearshift.model.profileOf
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
            .map { id -> if (id == "") prefs.getStringSet(C.PREF_PROFILES, setOf()).first() else id }
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
	}.pauseOn(activityLifecycle.onStop()).flatMap { either ->
		either.fold({
			Observable.just(either)
		}) {
			Observable.just(either).replay(1).refCount()
		}
	}.takeUntil(takeUntilDestroy())

	val torrentsObservable = apiObservable.refresh(refresher).switchToThrowableEither { api ->
		api.torrents()
	}.pauseOn(activityLifecycle.onStop()).flatMap { either ->
		either.fold({
			Observable.just(either)
		}) {
			Observable.just(either).replay(1).refCount()
		}
	}.takeUntil(takeUntilDestroy())

	private val directories = torrentsObservable.filterRightOr(setOf()).map { set ->
		set.map { it.downloadDir }.toSet()
	}.replay(1).refCount()

    private val filterList = prefs.observe().map {
        it.startsWith("PREF_FILTER_")
    }.filter { it }.map { prefs }.startWith(prefs).flatMap { prefs ->
        val statusFilters = getStatusFilters(prefs)
        if (statusFilters.isNotEmpty()) {
			statusFilters.add(0, Filter.Header(ctx.getString(R.string.filter_header_status)))
        }

        var o = Observable.just(statusFilters)

        if (prefs.getBoolean(C.PREF_FILTER_DIRECTORIES, false)) {
            o = o.concatWith {
                directories.map { set ->
                    set.sorted().map { dir -> Filter.Directory(dir) }
                }
            }
        }

        o.map { list -> list as List<Filter> }
    }
            .takeUntil(takeUntilUnbind())
            .observeOn(AndroidSchedulers.mainThread())
            .replay(1).refCount()

    init {
        lifecycle.filter { it == Lifecycle.BIND }.take(1).subscribe {
            val profiles = loadProfiles(prefs)

            if (profiles.isEmpty() && firstTimeProfile) {
                firstTimeProfile = false
                consumer?.createProfile()
            } else {
                consumer?.restorePath()
            }
        }
    }

    override fun bind(consumer: Consumer) {
        super.bind(consumer)

    }

	fun filtersAdapter(ctx: Context): FilterAdapter {
		return FilterAdapter(
				filterList,
				log, LayoutInflater.from(ctx), rxConsumer { filter -> }
		)
	}

}

enum class ActivityLifecycle {
    CREATE, START, RESUME, PAUSE, STOP, DESTROY
}


private fun getStatusFilters(prefs: SharedPreferences): MutableList<Filter> {
    val filters = mutableListOf<Filter>()

    if (prefs.getBoolean(C.PREF_FILTER_ALL, false)) {
        filters.add(FilterStatus.ALL.asFilter())
    }

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
