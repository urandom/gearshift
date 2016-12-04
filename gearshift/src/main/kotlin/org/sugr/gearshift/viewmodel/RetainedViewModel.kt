package org.sugr.gearshift.viewmodel

import io.reactivex.subjects.PublishSubject
import org.sugr.gearshift.Logger

open class RetainedViewModel<T>(val tag: String, val log: Logger) {
    protected var consumer: T? = null

    protected val lifecycle: PublishSubject<Lifecycle> =
            PublishSubject.create<Lifecycle>()

    enum class Lifecycle {
        BIND, UNBIND, DESTROY
    }

    init {
        log.D("Creating $this view model")
    }

    open fun bind(consumer: T) {
        log.D("Binding $this view model")
        this.consumer = consumer

        lifecycle.onNext(Lifecycle.BIND)
    }

    fun unbind() {
        log.D("Unbinding $this view model")
        lifecycle.onNext(Lifecycle.UNBIND)

        consumer = null
    }

    fun onDestroy() {
        log.D("Destroying $this view model")
        lifecycle.onNext(Lifecycle.DESTROY)
    }

    fun takeUntilUnbind() = lifecycle.filter { it -> it == Lifecycle.UNBIND }
    fun takeUntilDestroy() = lifecycle.filter { it -> it == Lifecycle.DESTROY }
}

interface LeaveBlocker {
    fun canLeave(): Boolean
}
