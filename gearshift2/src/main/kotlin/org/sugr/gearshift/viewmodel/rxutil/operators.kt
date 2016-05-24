package org.sugr.gearshift.viewmodel.rxutil

import java.util.concurrent.TimeUnit

fun <T> rx.Observable<T>.debounce() : rx.Observable<T> {
    return debounce(300, TimeUnit.MILLISECONDS)
}

