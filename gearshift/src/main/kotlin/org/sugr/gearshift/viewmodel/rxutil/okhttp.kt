package org.sugr.gearshift.viewmodel.rxutil

import io.reactivex.Single
import io.reactivex.SingleObserver
import io.reactivex.disposables.Disposable
import io.reactivex.exceptions.CompositeException
import io.reactivex.exceptions.Exceptions
import io.reactivex.plugins.RxJavaPlugins
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class ResponseSingle(val client : OkHttpClient, val request : Request) : Single<Response>() {
    override fun subscribeActual(observer: SingleObserver<in Response>) {
        val call = client.newCall(request)

        observer.onSubscribe(CallDisposable(call))

        var terminated = false

        try {
            val response = call.execute()
            if (!call.isCanceled) {
                observer.onSuccess(response)
                terminated = true
            }
        } catch (e : Throwable) {
            Exceptions.throwIfFatal(e)
            if (terminated) {
                RxJavaPlugins.onError(e)
            } else if (!call.isCanceled) {
                try {
                    observer.onError(e)
                } catch (inner : Throwable) {
                    Exceptions.throwIfFatal(inner);
                    RxJavaPlugins.onError(CompositeException(e, inner));
                }
            }
        }
    }
}

class CallDisposable(val call : Call) : Disposable {
    override fun dispose() {
        call.cancel()
    }

    override fun isDisposed(): Boolean {
        return call.isCanceled;
    }

}