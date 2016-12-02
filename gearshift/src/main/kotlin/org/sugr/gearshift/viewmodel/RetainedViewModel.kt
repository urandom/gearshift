package org.sugr.gearshift.viewmodel

import io.reactivex.subjects.PublishSubject
import org.sugr.gearshift.logD

open class RetainedViewModel<T>(val tag: String) {
    protected var consumer: T? = null

    protected val lifecycle: PublishSubject<Lifecycle> =
            PublishSubject.create<Lifecycle>()

    enum class Lifecycle {
        BIND, UNBIND, DESTROY
    }

    init {
        logD("Creating $this view model")
    }

    open fun bind(consumer: T) {
        logD("Binding $this view model")
        this.consumer = consumer

        lifecycle.onNext(Lifecycle.BIND)
    }

    fun unbind() {
        logD("Unbinding $this view model")
        lifecycle.onNext(Lifecycle.UNBIND)

        consumer = null
    }

    fun onDestroy() {
        logD("Destroying $this view model")
        lifecycle.onNext(Lifecycle.DESTROY)
    }

    fun takeUntilUnbind() = lifecycle.filter { it -> it == Lifecycle.UNBIND }
    fun takeUntilDestroy() = lifecycle.filter { it -> it == Lifecycle.DESTROY }
}

interface LeaveBlocker {
    fun canLeave(): Boolean
}
