package org.sugr.gearshift.viewmodel.rxutil

import io.reactivex.*
import java.util.concurrent.Callable

fun <T> single(body: (e: SingleEmitter<in T>) -> Unit) = Single.create(body)
fun <T> singleOf(value: T) = Single.just(value)
fun <T> latestFlowable(body: (e: FlowableEmitter<in T>) -> Unit) =
        Flowable.create(body, BackpressureStrategy.LATEST)

fun <T> Callable<T>.toMaybe() = Maybe.fromCallable(this)
