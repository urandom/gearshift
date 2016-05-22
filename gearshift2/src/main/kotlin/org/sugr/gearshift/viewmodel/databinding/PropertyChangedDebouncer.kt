package org.sugr.gearshift.viewmodel.databinding

import android.databinding.Observable
import rx.functions.Action1
import rx.internal.util.InternalObservableUtils
import rx.lang.kotlin.PublishSubject
import java.util.concurrent.TimeUnit

class PropertyChangedDebouncer(
        private val transformer: rx.Observable.Transformer<Observable, Observable> =
            rx.Observable.Transformer {o -> o}
) : Observable.OnPropertyChangedCallback() {
    private val subject = PublishSubject<Observable>()

    override fun onPropertyChanged(observable: Observable, i: Int) = subject.onNext(observable)

    fun subscribe(
            onNext: Action1<Observable>,
            onError: Action1<Throwable> = InternalObservableUtils.ERROR_NOT_IMPLEMENTED
    ) = debouncer().subscribe(onNext, onError)

    fun subscribeChain(
            onNext: Action1<Observable>,
            onError: Action1<Throwable> = InternalObservableUtils.ERROR_NOT_IMPLEMENTED
    ) : PropertyChangedDebouncer{
        debouncer().subscribe(onNext, onError)
        return this;
    }

    private fun debouncer() = subject.debounce(300, TimeUnit.MILLISECONDS).compose(transformer)
}
