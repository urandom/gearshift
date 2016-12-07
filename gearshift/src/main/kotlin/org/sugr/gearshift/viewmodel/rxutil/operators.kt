package org.sugr.gearshift.viewmodel.rxutil

import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.disposables.Disposables
import io.reactivex.functions.BiFunction
import org.sugr.gearshift.Log
import org.sugr.gearshift.Logger
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

fun <T> Flowable<T>.debounce() = debounce(300, TimeUnit.MILLISECONDS)

fun <T, R> Observable<T>.latest(mapper: (T) -> Observable<R>) =
        Observable.switchOnNext<R> { this.map(mapper) }

fun <T, L> Observable<T>.pauseOn(pauseObservable: Observable<L>, stopEvent: L, startEvent: L, log: Logger = Log) : Observable<T> {
    val pauser = pauseObservable.filter { state -> state == stopEvent || state == startEvent }.share()

    return Observable.combineLatest(
            this,
            pauser.throttleLast(1, TimeUnit.SECONDS).startWith(startEvent),
            BiFunction { t: T, l: L -> Pair(t, l) }
    ).map { pair ->
        if (pair.second == stopEvent) {
            log.D("Stopping observable due to pause event")

            throw LifecycleException()
        }

        pair.first
    }.retryWhen { attempts -> attempts.flatMap { err ->
        if (err is LifecycleException) {
            log.D("Waiting for start event to restore observable")

            pauser.filter { state -> state == startEvent }.take(1)
        } else {
            Observable.error(err)
        }
    } }
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