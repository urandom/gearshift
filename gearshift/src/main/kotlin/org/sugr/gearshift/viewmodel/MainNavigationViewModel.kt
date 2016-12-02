package org.sugr.gearshift.viewmodel

import android.content.SharedPreferences
import android.support.design.widget.NavigationView
import android.view.MenuItem
import io.reactivex.Flowable
import io.reactivex.FlowableTransformer
import io.reactivex.functions.BiFunction
import io.reactivex.processors.PublishProcessor
import org.sugr.gearshift.App
import org.sugr.gearshift.logD
import org.sugr.gearshift.model.loadProfiles
import java.util.concurrent.TimeUnit

class MainNavigationViewModel(tag: String, private val app: App, private val prefs: SharedPreferences) :
        RetainedViewModel<MainNavigationViewModel.Consumer>(tag) {

    val activityLifecycle = PublishProcessor.create<ActivityLifecycle>()

    val navigationListener = object : NavigationView.OnNavigationItemSelectedListener {
        override fun onNavigationItemSelected(item: MenuItem): Boolean {
            logD("Navigation item ${item.title}")

            consumer?.closeDrawer()

            return true
        }

    }

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

fun <T> lifecyclePauseTransformer(
        lifecycleObserver: Flowable<ActivityLifecycle>,
        stop: ActivityLifecycle, start: ActivityLifecycle
) : FlowableTransformer<T, T> {
    val lifecycle = lifecycleObserver.filter { state -> state == stop || state == start }.share()

    return FlowableTransformer { o ->
        Flowable.combineLatest (
                o, lifecycle.throttleLast(1, TimeUnit.SECONDS), BiFunction {t1: T, t2: ActivityLifecycle ->  Pair(t1, t2) }
        ).map { pair ->
            if (pair.second == stop) {
                logD("Stopping observable due to lifecycle signal")

                throw LifecycleException()
            }

            pair.first
        }.retryWhen { attempts -> attempts.flatMap { err ->
            if (err is LifecycleException) {
                logD("Waiting for lifecycle signal to restart observable")

                lifecycle.filter { v -> v == start }.take(1)
            } else {
                Flowable.error(err)
            }
        } }
    }
}

private class LifecycleException : RuntimeException()

