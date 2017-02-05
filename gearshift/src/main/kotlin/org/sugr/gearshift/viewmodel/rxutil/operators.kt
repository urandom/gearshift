package org.sugr.gearshift.viewmodel.rxutil

import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.disposables.Disposables
import io.reactivex.functions.BiFunction
import io.reactivex.functions.Function3
import org.funktionale.either.Either
import org.sugr.gearshift.viewmodel.ActivityLifecycle
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
	// delay the original before publishing, because the connection occurs before the real subscription might occur.
	val published = delay(1, TimeUnit.MILLISECONDS).publish();
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

fun Observable<ActivityLifecycle>.onStop() : Observable<Boolean> {
	return filter {
		it == ActivityLifecycle.START || it == ActivityLifecycle.STOP
	}.map {
		it == ActivityLifecycle.START
	}.startWith(true)
}

fun <T1, T2, R> Flowable<T1>.combineLatestWith(other: Flowable<T2>, mapper: (T1, T2) -> R) : Flowable<R> {
	return Flowable.combineLatest(this, other, BiFunction { t1, t2 -> mapper(t1, t2) })
}

fun <T, R> Observable<T>.switchToThrowableEither(body: (T) -> Observable<R>): Observable<Either<Throwable, R>> {
	return switchMap {
		body(it).map { r ->
			Either.right(r) as Either<Throwable, R>

		}.onErrorReturn { err ->
			Either.left(err)
		}
	}
}

fun <T, R> Observable<Either<Throwable, T>>.switchUsingThrowableEither(body: (T) -> Observable<R>): Observable<Either<Throwable, R>> {
	return switchMap {
		it.fold({ err ->
			Observable.just(Either.left(err))
		}) { t ->
			body(t).map { r ->
				Either.right(r) as Either<Throwable, R>
			}.onErrorReturn { err ->
				Either.left(err)
			}
		}
	}
}

fun <T> Observable<Either<Throwable, T>>.filterRight() : Observable<T> {
	return filter { it.isRight() }.map { it.right().get() }
}
fun <T> Observable<Either<Throwable, T>>.filterRightOr(t: T) : Observable<T> {
	return map { either -> either.fold({ err -> t}) { it } }
}
fun <T> Observable<Either<Throwable, T>>.filterRightOrThrow() : Observable<T> {
	return map { either -> either.fold({ err -> throw err }) { it } }
}
