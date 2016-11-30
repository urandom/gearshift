package org.sugr.gearshift.viewmodel.rxutil

import io.reactivex.Flowable
import java.util.concurrent.TimeUnit

fun <T> Flowable<T>.debounce() = debounce(300, TimeUnit.MILLISECONDS)
