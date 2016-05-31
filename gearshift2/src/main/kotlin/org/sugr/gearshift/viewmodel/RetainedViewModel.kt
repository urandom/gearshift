package org.sugr.gearshift.viewmodel

import android.content.SharedPreferences

import com.f2prateek.rx.preferences.RxSharedPreferences
import org.sugr.gearshift.logD
import org.sugr.gearshift.viewmodel.rxutil.sharedPreferences

import rx.Observable
import rx.lang.kotlin.PublishSubject

open class RetainedViewModel<T>(val tag: String, prefs: SharedPreferences) {
    protected val prefs: RxSharedPreferences
    protected var consumer: T? = null

    private val unbindSubject = PublishSubject<Void>()
    private val destroySubject = PublishSubject<Void>()

    init {
        logD("Creating $this view model")
        this.prefs = sharedPreferences(prefs)
    }

    open fun bind(consumer: T) {
        logD("Binding $this view model")
        this.consumer = consumer
    }

    fun unbind() {
        logD("Unbinding $this view model")
        unbindSubject.onNext(null)

        consumer = null
    }

    fun onDestroy() {
        logD("Destroying $this view model")
        destroySubject.onNext(null)
    }

    // Useful for stopping observable emission when an unbind happens
    fun <O> takeUntilUnbind(): Observable.Transformer<O, O> {
        return Observable.Transformer { o -> o.takeUntil(unbindSubject) }
    }

    // Similar to takeUntilUnbind, but for onDestroy
    fun <O> takeUntilDestroy(): Observable.Transformer<O, O> {
        return Observable.Transformer { o -> o.takeUntil(destroySubject) }
    }
}

interface LeaveBlocker {
    fun canLeave(): Boolean
}