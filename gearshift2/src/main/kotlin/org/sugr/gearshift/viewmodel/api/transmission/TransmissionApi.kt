package org.sugr.gearshift.viewmodel.api.transmission

import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.put
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.repacked.apache.commons.codec.binary.Base64
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import org.sugr.gearshift.BuildConfig
import org.sugr.gearshift.logD
import org.sugr.gearshift.model.Profile
import org.sugr.gearshift.viewmodel.api.Api
import org.sugr.gearshift.viewmodel.rxutil.CallOnSubscribe
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class TransmissionApi(
        private var profile: Profile,
        private val gson: Gson = Gson(),
        private val debug: Boolean = BuildConfig.DEBUG
) : Api {
    private val httpClient: OkHttpClient
    private val requestBuilder: Request.Builder

    init {
        val builder = OkHttpClient.Builder()

        builder.addInterceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .cacheControl(CacheControl.FORCE_NETWORK)

            if (profile.username.isNotBlank() && profile.password.isNotBlank()) {
                val credentials = profile.username + ":" + profile.password
                val basic = "Basic " + Base64.encodeBase64(credentials.toByteArray())

                requestBuilder.header("Authorization", basic)
            }

            if (profile.sessionData.isNotBlank()) {
                requestBuilder.header("X-Transmission-Session-Id", profile.sessionData)
            }

            var response = chain.proceed(requestBuilder.build())

            var retries = 0
            while (response.code() == HttpURLConnection.HTTP_CONFLICT && retries < 3) {
                retries++
                val sessionId = response.header("X-Transmission-Session-Id")
                if (sessionId.isEmpty()) {
                    break
                } else {
                    profile = profile.copy(sessionData = sessionId)
                    if (profile.valid) {
                        profile.save()

                        requestBuilder.header("X-Transmission-Session-Id", profile.sessionData)
                        response = chain.proceed(requestBuilder.build())
                    }
                }
            }

            response
        }

        if (debug) {
            val logger = HttpLoggingInterceptor { message ->
                logD("http: $message")
            }
            builder.addInterceptor(logger)
        }

        if (profile.useSSL) {
            // Trust self-signed certificates
            val sc = SSLContext.getInstance("TLS");
            val manager = object: X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                }

                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                }

                override fun getAcceptedIssuers(): Array<out X509Certificate>? {
                    return emptyArray()
                }

            }
            sc.init(null, arrayOf<TrustManager>(manager), SecureRandom())
            builder.sslSocketFactory(sc.socketFactory, manager)
            builder.hostnameVerifier { hostname, sslSession -> true }
        }

        val timeout = if (profile.timeout > 0) profile.timeout else 10
        builder.readTimeout(timeout.toLong(), TimeUnit.SECONDS)
                .connectTimeout(timeout.toLong(), TimeUnit.SECONDS)

        if (profile.proxyEnabled()) {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(profile.proxyHost, profile.proxyPort)))
        }

        httpClient = builder.build()

        val url = HttpUrl.Builder()
                .scheme(if (profile.useSSL) "https" else "http")
                .host(profile.host).port(profile.port)
                .encodedPath(profile.path)
                .build()

        requestBuilder = Request.Builder().url(url)
    }

    override fun version(): Observable<String> {
        return request<JsonObject>(requestBody("session-get")).map { json ->
            if (json.has("version")) {
                json.get("version").asString
            } else {
                null
            }
        }
    }

    private fun requestBody(method: String, arguments: JsonObject? = null): RequestBody {
        val obj = jsonObject("method" to method)
        if (arguments != null) {
            obj.put("arguments" to arguments)
        }

        return RequestBody.create(JSON, obj.toString())
    }

    inline private fun <reified T: Any> request(body: RequestBody): Observable<T> {
        val call = httpClient.newCall(requestBuilder.post(body).build())
        return Observable.create(CallOnSubscribe(call))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .map { response ->
                    val json = gson.fromJson<JsonObject>(response.body().charStream())
                    when {
                        !json.has("result") -> throw TransmissionApiException("unknown response")
                        json.get("result").asString != "success" -> throw TransmissionApiException(json.get("result").asString)
                        json.has("arguments") -> gson.fromJson(json.get("arguments"), T::class.java)
                        else -> throw TransmissionApiException("unknown response")
                    }
                }
                .observeOn(AndroidSchedulers.mainThread())
    }

    companion object {
        val JSON = MediaType.parse("application/json; charset=utf-8");
    }
}

class TransmissionApiException(message: String): RuntimeException("transmission api: " + message)
