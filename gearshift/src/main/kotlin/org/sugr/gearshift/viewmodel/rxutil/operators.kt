package org.sugr.gearshift.viewmodel.rxutil

import io.reactivex.Observable
import io.reactivex.disposables.Disposables
import io.reactivex.functions.BiFunction
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

fun <T> Observable<T>.debounce() = debounce(300, TimeUnit.MILLISECONDS)

fun <T, R> Observable<T>.latest(mapper: (T) -> Observable<R>) =
        Observable.switchOnNext<R> { this.map(mapper) }

fun <T1, T2, R> Observable<T1>.combineLatestWith(other: Observable<T2>, mapper: (T1, T2) -> R) : Observable<R> {
    return Observable.combineLatest(this, other, BiFunction { t1, t2 -> mapper(t1, t2) })
}

fun <T> Observable<T>.pauseOn(pauseObservable: Observable<Boolean>) : Observable<T> {
    val published = publish();
    val subRef = AtomicReference(Disposables.disposed())

    return pauseObservable
            .throttleLast(1, TimeUnit.SECONDS)
            .distinctUntilChanged()
            .latest { active ->
                if (active) {
                    subRef.set(published.connect())
                } else {
                    subRef.get().dispose()
                }

                published
            }
}

private class LifecycleException : RuntimeException()