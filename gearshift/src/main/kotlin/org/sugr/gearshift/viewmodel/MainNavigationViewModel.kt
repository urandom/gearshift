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
import org.sugr.gearshift.viewmodel.rxutil.latest
import org.sugr.gearshift.viewmodel.rxutil.toObservable

class MainNavigationViewModel(tag: String, log: Logger,
                              private val ctx: Context,
                              private val prefs: SharedPreferences) :
        RetainedViewModel<MainNavigationViewModel.Consumer>(tag, log) {

    val activityLifecycle = PublishSubject.create<ActivityLifecycle>()

    val gson = Gson()

    val navigationListener = object : NavigationView.OnNavigationItemSelectedListener {
        override fun onNavigationItemSelected(item: MenuItem): Boolean {
            log.D("Navigation item ${item.title}")

            consumer?.closeDrawer()

            return true
        }

    }

    val profileObservable = prefs.toObservable()
            .filter { key -> key == C.PREF_CURRENT_PROFILE }
            .startWith { C.PREF_CURRENT_PROFILE }
            .map { key -> prefs.getString(key, "") }
            .latest { id ->
                profileOf(id, prefs)
            }
            .replay(1).refCount()
            .takeUntil(takeUntilDestroy())

    val apiObservable = profileObservable
            .latest { profile -> apiOf(profile, ctx, prefs, gson, log) }
            .replay(1).refCount()
            .takeUntil(takeUntilDestroy())

    var firstTimeProfile = true

    interface Consumer {
        fun closeDrawer()
        fun createProfile()
    }

    override fun bind(consumer: Consumer) {
        super.bind(consumer)

        val profiles = loadProfiles(prefs)

        if (profiles.isEmpty() && firstTimeProfile) {
            firstTimeProfile = false
            consumer.createProfile()
        }
    }
}

enum class ActivityLifecycle {
    CREATE, START, RESUME, PAUSE, STOP, DESTROY
}

