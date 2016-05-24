package org.sugr.gearshift.viewmodel.databinding

import android.databinding.Observable
import rx.lang.kotlin.add
import rx.lang.kotlin.observable
import java.util.concurrent.TimeUnit

class PropertyChangedCallback(private val cb: (o: Observable) -> Unit) : Observable.OnPropertyChangedCallback() {
    override fun onPropertyChanged(o: Observable?, i: Int) = if (o != null) cb(o) else Unit
    fun addTo(vararg observables: Observable) {
        for (o in observables) {
            o.addOnPropertyChangedCallback(this)
        }
    }
}

fun <T: Observable> T.observe(cb: (o: Observable) -> Unit) : T {
    this.addOnPropertyChangedCallback(PropertyChangedCallback(cb))
    return this;
}

fun <T: Observable> T.observe(cb: Observable.OnPropertyChangedCallback) : T {
    this.addOnPropertyChangedCallback(cb)
    return this;
}

fun <T : Observable> T.observe(): rx.Observable<T> {
    return observable { subscriber ->
        val cb = PropertyChangedCallback {
            subscriber.onNext(this)
        }
        addOnPropertyChangedCallback(cb)
        subscriber.add {
            removeOnPropertyChangedCallback(cb)
        }
    }
}
