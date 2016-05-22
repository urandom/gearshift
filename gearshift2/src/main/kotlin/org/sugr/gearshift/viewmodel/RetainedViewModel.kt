package org.sugr.gearshift.viewmodel

import android.content.SharedPreferences

import com.f2prateek.rx.preferences.RxSharedPreferences

import rx.Observable
import rx.lang.kotlin.PublishSubject

open class RetainedViewModel<T>(prefs: SharedPreferences) {
    protected val prefs: RxSharedPreferences
    protected var consumer: T? = null

    private val unbindSubject = PublishSubject<Void>()
    private val destroySubject = PublishSubject<Void>()

    init {
        this.prefs = RxSharedPreferences.create(prefs)
    }

    fun bind(consumer: T) {
        this.consumer = consumer
    }

    fun unbind() {
        unbindSubject.onNext(null)

        consumer = null
    }

    fun onDestroy() {
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
