package org.sugr.gearshift.viewmodel.databinding

import android.databinding.Observable
import io.reactivex.ObservableOnSubscribe
import java.io.Serializable
import io.reactivex.Observable as RxObservable

class ObservableField<T>(t: T) : android.databinding.ObservableField<T>(), Serializable {
    var value : T

    init {
        value = t
    }

    override fun get() = value
    override fun set(t: T) {
        if (value != t) {
            value = t
            notifyChange()
        }
    }
}

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

fun <T : Observable> T.observe() =
        RxObservable.create(ObservableOnSubscribe<T> { e ->
            val cb = PropertyChangedCallback {
                e.onNext(this)
            }
            addOnPropertyChangedCallback(cb)
            e.setCancellable {
                removeOnPropertyChangedCallback(cb)
            }
        })

