package org.sugr.gearshift.viewmodel.api.transmission

import android.app.Application
import com.github.salomonbrys.kotson.*
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import org.apache.commons.codec.binary.Base64
import org.sugr.gearshift.*
import org.sugr.gearshift.model.Profile
import org.sugr.gearshift.model.Session
import org.sugr.gearshift.model.Torrent
import org.sugr.gearshift.model.profilePreferences
import org.sugr.gearshift.viewmodel.api.Api
import org.sugr.gearshift.viewmodel.ext.readableFileSize
import org.sugr.gearshift.viewmodel.ext.readablePercent
import org.sugr.gearshift.viewmodel.ext.readableRemainingTime
import org.sugr.gearshift.viewmodel.rxutil.ResponseSingle
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
        private val ctx : App = org.sugr.gearshift.app(),
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
                        profile.save(defaultPreferences(ctx), profilePreferences(ctx))

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

    override fun version(): Single<String> {
        return request<JsonObject>(requestBody("session-get")).map { json ->
            if ("version" in json) {
                json["version"].string
            } else {
                null
            }
        }
    }

    override fun torrents(): Observable<Torrent> {
        return request<JsonObject>(requestBody("torrent-get", jsonObject(
                "fields" to jsonArray( TORRENT_STAT_FIELDS)
        ))).map { json ->
            json.getAsJsonArray("torrents")
        }.flatMap { torrentsJson ->
            val partials = torrentsJson.filter {
                it.isJsonObject && (it.obj.get(FIELD_NAME)?.string?.isEmpty() ?: true ||
                        it.obj.get(FIELD_FILES)?.array?.size() == 0)
            }.map { it.get(FIELD_HASH).string }

            if (partials.isNotEmpty()) {
                // If any of these fields are missing for a given torrent,
                // fetch them and update the original json object
                val fields = TORRENT_META_FIELDS + arrayOf(FIELD_FILES)
                request<JsonObject>(requestBody("torrent-get", jsonObject(
                        "ids" to jsonArray(partials),
                        "fields" to jsonArray(arrayOf(FIELD_HASH) + fields)
                ))).map { json ->
                    json.getAsJsonArray("torrents")
                }.map { json ->
                    json.associate { it -> Pair(it.get(FIELD_HASH).string, it) }
                }.map { jsonMap ->
                    torrentsJson.apply {
                        forEach {
                            val torrent = it.obj
                            fields.forEach { field ->
                                torrent[field] = jsonMap[torrent.get(FIELD_HASH).string]?.get(field)
                            }
                        }
                    }
                }
            } else {
                Single.just(torrentsJson)
            }
        }.flatMapObservable { json ->
            Observable.fromIterable(json)
        }.map(JsonElement::obj).map { torrentFrom(it, ctx) }
    }


    private fun requestBody(method: String, arguments: JsonObject? = null): RequestBody {
        val obj = jsonObject("method" to method)
        if (arguments != null) {
            obj.put("arguments" to arguments)
        }

        return RequestBody.create(JSON, obj.toString())
    }

    inline private fun <reified T: Any> request(body: RequestBody): Single<T> {
        return ResponseSingle(httpClient, requestBuilder.post(body).build())
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .map { response ->
                    val json = gson.fromJson<JsonObject>(response.body().charStream())
                    when {
                        !("result" in json) -> throw TransmissionApiException("unknown response")
                        json["result"].string != "success" -> throw TransmissionApiException(json["result"].string)
                       "arguments" in json -> {
                            val args = json["arguments"]
                            if (T::class == JsonObject::class && args.isJsonObject) {
                                args.obj as T
                            } else {
                                gson.fromJson<T>(args)
                            }
                        }
                        else -> throw TransmissionApiException("unknown response")
                    }
                }
                .observeOn(AndroidSchedulers.mainThread())
    }

    companion object {
        val JSON = MediaType.parse("application/json; charset=utf-8");

        private val FIELD_HASH = "hashString"
        private val FIELD_NAME = "name"
        private val FIELD_FILES = "files"

        private val TORRENT_META_FIELDS = arrayOf(FIELD_NAME, "addedDate", "totalSize")

        private val TORRENT_STAT_FIELDS = arrayOf(
                FIELD_HASH, "id", "error", "errorString", "eta",
                "isFinished", "isStalled", "leftUntilDone", "metadataPercentComplete",
                "peersConnected", "peersGettingFromUs", "peersSendingToUs", "percentDone",
                "queuePosition", "rateDownload", "rateUpload", "recheckProgress",
                "seedRatioMode", "seedRatioLimit", "sizeWhenDone", "status",
                "trackers", "uploadedEver", "uploadRatio", "downloadDir"
        )

        private fun torrentFrom(json: JsonObject, ctx: Application = app(), session : Session = Session()) : Torrent {
            val metaProgress = json["metadataPercentComplete"].float
            val downloadProgress = json["percentDone"].float
            val eta = json["eta"].long
            val status = Torrent.statusOf(json["status"].int)
            val isStalled = json["isStalled"].bool
            val rateDownload = json["rateDownload"].long
            val rateUpload = json["rateUpload"].long
            val peersSendingToUs = json["peersSendingToUs"].int
            val peersGettingFromUs = json["peersGettingFromUs"].int
            val peersConnected = json["peersConnected"].int
            val recheckProgress = json["recheckProgress"].float
            val uploadRatio = json["uploadRatio"].float
            val seedMode = json["seedRatioMode"].int
            val sizeWhenDone = json["sizeWhenDone"]?.nullLong ?: 0
            val leftUntilDone = json["leftUntilDone"]?.nullLong ?: 0
            val uploadedEver = json["uploadedEver"].long

            val seedLimit = if (seedMode == SeedRatioMode.NO_LIMIT.value) {
                0f
            } else if (seedMode == SeedRatioMode.GLOBAL_LIMIT.value) {
                if (session.seedRatioLimitEnabled) session.seedRatioLimit else 0f
            } else {
                json["seedRatioLimit"].float
            }

            val statusFormat = ctx.getString(R.string.status_format);
            val statusText = when (status) {
                Torrent.StatusType.DOWNLOADING, Torrent.StatusType.DOWNLOAD_WAITING -> {
                    val type = ctx.getString(if (status == Torrent.StatusType.DOWNLOADING) {
                        if (metaProgress < 1) R.string.status_state_downloading_metadata
                        else R.string.status_state_downloading
                    } else R.string.status_state_download_waiting)
                    val moreFormat = ctx.getString(R.string.status_more_downloading_format);
                    val speedFormat = ctx.getString(R.string.status_more_downloading_speed_format);
                    val speed = if (isStalled)
                        ctx.getString(R.string.status_more_idle)
                    else
                        String.format(speedFormat, rateDownload.readableFileSize(), rateUpload.readableFileSize())

                    String.format(statusFormat, type,
                            String.format(moreFormat, peersSendingToUs, peersConnected, speed))
                }
                Torrent.StatusType.SEEDING, Torrent.StatusType.SEED_WAITING -> {
                    val type = ctx.getString(
                            if (status == Torrent.StatusType.SEEDING) R.string.status_state_seeding
                            else R.string.status_state_seed_waiting)
                    val moreFormat = ctx.getString(R.string.status_more_seeding_format);
                    val speedFormat = ctx.getString(R.string.status_more_seeding_speed_format);
                    val speed = if (isStalled)
                        ctx.getString(R.string.status_more_idle)
                    else
                        String.format(speedFormat, rateUpload.readableFileSize())

                    String.format(statusFormat, type,
                            String.format(moreFormat, peersGettingFromUs, peersConnected, speed))
                }
                Torrent.StatusType.CHECK_WAITING -> {
                    val type = ctx.getString(R.string.status_state_check_waiting)

                    String.format(statusFormat,
                            type, "-" + ctx.getString(R.string.status_more_idle))
                }
                Torrent.StatusType.CHECKING -> String.format(
                        ctx.getString(R.string.status_state_checking),
                        (recheckProgress * 100).readablePercent())
                Torrent.StatusType.STOPPED -> ctx.getString(
                        if (uploadRatio < seedLimit) R.string.status_state_paused
                        else R.string.status_state_finished
                )
                else -> ""
            }

            val trafficText = when(status) {
                Torrent.StatusType.DOWNLOADING, Torrent.StatusType.DOWNLOAD_WAITING ->
                    String.format(
                            ctx.getString(R.string.traffic_downloading_format),
                            (sizeWhenDone - leftUntilDone).readableFileSize(),
                            sizeWhenDone.readableFileSize(),
                            String.format(ctx.getString(R.string.traffic_downloading_percentage_format),
                                    (downloadProgress * 100).readablePercent()),
                            if (eta < 0) ctx.getString(R.string.traffic_remaining_time_unknown)
                            else String.format(ctx.getString(R.string.traffic_remaining_time_format),
                                    eta.readableRemainingTime(ctx))
                    )
                Torrent.StatusType.SEEDING, Torrent.StatusType.SEED_WAITING ->
                    String.format(
                            ctx.getString(R.string.traffic_seeding_format),
                            sizeWhenDone.readableFileSize(),
                            uploadedEver.readableFileSize(),
                            String.format(ctx.getString(R.string.traffic_seeding_ratio_format),
                                    uploadRatio.readablePercent(),
                                    if (seedLimit <= 0) "" else String.format(
                                            ctx.getString(R.string.traffic_seeding_ratio_goal_format),
                                            seedLimit.readablePercent())
                            ),
                            if (seedLimit <= 0) ""
                            else if (eta < 0) ctx.getString(R.string.traffic_remaining_time_unknown)
                            else String.format(ctx.getString(R.string.traffic_remaining_time_format),
                                    eta.readableRemainingTime(ctx))
                    )
                Torrent.StatusType.STOPPED -> {
                    if (downloadProgress < 1) {
                        String.format(
                                ctx.getString(R.string.traffic_downloading_format),
                                (sizeWhenDone - leftUntilDone).readableFileSize(),
                                sizeWhenDone.readableFileSize(),
                                String.format(ctx.getString(R.string.traffic_downloading_percentage_format),
                                        (downloadProgress * 100).readablePercent()),
                                "<br/>" + String.format(
                                        ctx.getString(R.string.traffic_seeding_format),
                                        sizeWhenDone.readableFileSize(),
                                        uploadedEver.readableFileSize(),
                                        String.format(ctx.getString(R.string.traffic_seeding_ratio_format),
                                                if (uploadRatio < 0) 0 else uploadRatio.readablePercent(),
                                                if (seedLimit <= 0) "" else String.format(
                                                        ctx.getString(R.string.traffic_seeding_ratio_goal_format),
                                                        seedLimit.readablePercent())
                                        ),
                                        ""
                                )
                        )
                    } else {
                        String.format(
                                ctx.getString(R.string.traffic_seeding_format),
                                sizeWhenDone.readableFileSize(),
                                uploadedEver.readableFileSize(),
                                String.format(ctx.getString(R.string.traffic_seeding_ratio_format),
                                        uploadRatio.readablePercent(),
                                        if (seedLimit <= 0) "" else String.format(
                                                ctx.getString(R.string.traffic_seeding_ratio_goal_format),
                                                seedLimit.readablePercent())
                                ),
                                ""
                        )
                    }

                }
                else -> ""
            }


            return Torrent(
                    hash = json[FIELD_HASH].string, id = json["id"].int,
                    name = json[FIELD_NAME].string, statusType = status,
                    metaProgress = metaProgress,
                    downloadProgress = downloadProgress,
                    uploadProgress = uploadRatio,
                    isDirectory = (json[FIELD_FILES]?.array?.size() ?: 0) > 1,
                    statusText = statusText, trafficText = trafficText,
                    error = json["errorString"].string, errorType = Torrent.errorOf(json["error"].int),
                    downloadDir = json["downloadDir"].string,
                    validSize = sizeWhenDone, totalSize = json["totalSize"]?.nullLong ?: 0,
                    sizeLeft = leftUntilDone
            )
        }
    }
}

enum class SeedRatioMode(val value: Int) {
    GLOBAL_LIMIT(0), TORRENT_LIMIT(1), NO_LIMIT(2)
}

class TransmissionApiException(message: String): RuntimeException("transmission api: " + message)
