package org.sugr.gearshift.viewmodel.rxutil

import okhttp3.Call
import okhttp3.Response
import rx.Observable
import rx.Producer
import rx.Subscriber
import rx.Subscription
import rx.exceptions.Exceptions
import java.util.concurrent.atomic.AtomicBoolean

class CallOnSubscribe(private val call: Call): Observable.OnSubscribe<Response> {
    override fun call(subscriber: Subscriber<in Response>) {
        val arbiter = RequestArbiter(call, subscriber)

        subscriber.add(arbiter)
        subscriber.setProducer(arbiter)
    }
}

class RequestArbiter(private val call: Call, private val subscriber: Subscriber<in Response>):
        AtomicBoolean(), Subscription, Producer {

    override fun isUnsubscribed(): Boolean {
        return call.isCanceled
    }

    override fun unsubscribe() {
        call.cancel()
    }

    override fun request(n: Long) {
        when {
            n < 0 -> throw IllegalArgumentException("n < 0: " + n)
            n == 0L -> return
            !compareAndSet(false, true) -> return // request already triggered
        }

        try {
            val response = call.execute()
            if (!subscriber.isUnsubscribed) {
                subscriber.onNext(response)
            }
        } catch (e: Throwable) {
            Exceptions.throwIfFatal(e)
            if (!subscriber.isUnsubscribed) {
                subscriber.onError(e)
            }

            return
        }

        if (!subscriber.isUnsubscribed) {
            subscriber.onCompleted()
        }
    }

}