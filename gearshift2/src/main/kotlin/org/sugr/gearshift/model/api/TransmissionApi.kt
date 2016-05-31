package org.sugr.gearshift.model.api

import com.f2prateek.rx.preferences.RxSharedPreferences
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.put
import com.google.gson.JsonObject
import com.google.repacked.apache.commons.codec.binary.Base64
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import org.sugr.gearshift.defaultPreferences
import org.sugr.gearshift.model.Profile
import org.sugr.gearshift.viewmodel.rxutil.sharedPreferences
import rx.Observable
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class TransmissionApi(
        private val profile: Profile,
        private val prefs: RxSharedPreferences = sharedPreferences(defaultPreferences())
) : Api {
    private val httpClient: OkHttpClient

    init {
        val builder = OkHttpClient.Builder()

        builder.addInterceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .cacheControl(CacheControl.FORCE_NETWORK)

            if (profile.username.isNotBlank() && profile.password.isNotBlank()) {
                val credentials = profile.username + ":" + profile.password
                val basic = "Basic " + Base64.encodeBase64(credentials.toByteArray())

                requestBuilder.header("Authorization", basic)
            }

            if (profile.sessionData.isNotBlank()) {
                requestBuilder.header("X-Transmission-Session-Id", profile.sessionData)
            }

            chain.proceed(requestBuilder.build())
        }

        if (profile.useSSL) {
            // Trust self-signed certificates
            val sc = SSLContext.getInstance("TLS");
            sc.init(null, arrayOf<TrustManager>(
                    object: X509TrustManager {
                        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                        }

                        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                        }

                        override fun getAcceptedIssuers(): Array<out X509Certificate>? {
                            return null
                        }

                    }
            ), java.security.SecureRandom())
            builder.sslSocketFactory(sc.socketFactory)
            builder.hostnameVerifier { hostname, sslSession -> true }
        }

        val timeout = if (profile.timeout > 0) profile.timeout else 10
        builder.readTimeout(timeout.toLong(), TimeUnit.SECONDS)
                .connectTimeout(timeout.toLong(), TimeUnit.SECONDS)

        httpClient = builder.build()
    }

    override fun test(): Observable<Boolean> {
        return Observable.create { subscriber ->
            val body = requestBody("session-get")
        }
    }

    private fun requestBody(method: String, arguments: JsonObject? = null): JsonObject {
        val obj = jsonObject("method" to method)
        if (arguments != null) {
            obj.put("arguments" to arguments)
        }

        return obj
    }
}

