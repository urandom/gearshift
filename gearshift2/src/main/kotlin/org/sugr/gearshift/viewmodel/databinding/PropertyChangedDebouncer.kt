package org.sugr.gearshift.viewmodel.databinding

import android.databinding.Observable
import rx.Subscription
import rx.functions.Action1
import rx.lang.kotlin.PublishSubject
import java.util.concurrent.TimeUnit

class PropertyChangedDebouncer : Observable.OnPropertyChangedCallback() {
    private val subject = PublishSubject<Observable>()

    override fun onPropertyChanged(observable: Observable, i: Int) {
        subject.onNext(observable)
    }

    fun subscribe(onNext: Action1<Observable>): Subscription {
        return debouncer().subscribe(onNext)
    }

    fun subscribe(onNext: Action1<Observable>, onError: Action1<Throwable>): Subscription {
        return debouncer().subscribe(onNext, onError)
    }

    private fun debouncer(): rx.Observable<Observable> {
        return subject.debounce(300, TimeUnit.MILLISECONDS)
    }
}
