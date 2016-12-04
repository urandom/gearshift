package org.sugr.gearshift.viewmodel.rxutil

import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import org.sugr.gearshift.Log
import org.sugr.gearshift.Logger
import java.util.concurrent.TimeUnit

fun <T> Flowable<T>.debounce() = debounce(300, TimeUnit.MILLISECONDS)

fun <T, R> Observable<T>.latest(mapper: (T) -> R) =
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

private class LifecycleException : RuntimeException()