package org.sugr.gearshift.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.support.design.widget.NavigationView
import android.view.MenuItem
import com.google.gson.Gson
import io.reactivex.subjects.PublishSubject
import org.sugr.gearshift.C
import org.sugr.gearshift.Logger
import org.sugr.gearshift.model.loadProfiles
import org.sugr.gearshift.model.profileOf
import org.sugr.gearshift.viewmodel.api.apiOf
import org.sugr.gearshift.viewmodel.rxutil.*

class MainNavigationViewModel(tag: String, log: Logger,
                              private val ctx: Context,
                              private val prefs: SharedPreferences) :
        RetainedViewModel<MainNavigationViewModel.Consumer>(tag, log) {

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

    interface Consumer {
        fun restorePath()
        fun closeDrawer()
        fun createProfile()
    }

    val sessionObservable = apiObservable.refresh(refresher).switchToThrowableEither { api ->
        api.session()
    }.pauseOn(activityLifecycle.onStop()).replay(1).refCount()

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
}

enum class ActivityLifecycle {
    CREATE, START, RESUME, PAUSE, STOP, DESTROY
}

