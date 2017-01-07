package org.sugr.gearshift.viewmodel.rxutil

import io.reactivex.Observable
import io.reactivex.disposables.Disposables
import io.reactivex.functions.BiFunction
import io.reactivex.functions.Function3
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

fun <T> Observable<T>.debounce() = debounce(300, TimeUnit.MILLISECONDS)

fun <T1, T2, R> Observable<T1>.combineLatestWith(other: Observable<T2>, mapper: (T1, T2) -> R) : Observable<R> {
	return Observable.combineLatest(this, other, BiFunction { t1, t2 -> mapper(t1, t2) })
}

fun <T1, T2, T3, R> Observable<T1>.combineLatestWith(other: Observable<T2>, other2: Observable<T3>, mapper: (T1, T2, T3) -> R) : Observable<R> {
	return Observable.combineLatest(this, other, other2, Function3 { t1, t2, t3 -> mapper(t1, t2, t3) })
}

fun <T, U, R> Observable<T>.zipWith(other: Observable<U>, mapper: (T, U) -> R) : Observable<R> {
	return this.zipWith(other, BiFunction { t1, t2 -> mapper(t1, t2) })
}

fun <T> Observable<T>.pauseOn(pauseObservable: Observable<Boolean>) : Observable<T> {
	val published = publish();
	val subRef = AtomicReference(Disposables.disposed())

	return pauseObservable
			.throttleLast(1, TimeUnit.SECONDS)
			.distinctUntilChanged()
			.switchMap { active ->
				if (active) {
					subRef.set(published.connect())
				} else {
					subRef.get().dispose()
				}

				published
			}
}

fun <T> Observable<T>.refresh(refresher: Observable<Any>) : Observable<T> {
	return refresher.startWith(1).debounce(50, TimeUnit.MILLISECONDS).switchMap { this }
}

private class LifecycleException : RuntimeException()