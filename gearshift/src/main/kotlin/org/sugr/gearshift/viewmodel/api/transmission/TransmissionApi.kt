package org.sugr.gearshift.viewmodel.api.transmission

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.github.salomonbrys.kotson.*
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import org.sugr.gearshift.BuildConfig
import org.sugr.gearshift.Log
import org.sugr.gearshift.Logger
import org.sugr.gearshift.R
import org.sugr.gearshift.model.Profile
import org.sugr.gearshift.model.Session
import org.sugr.gearshift.model.Torrent
import org.sugr.gearshift.model.TorrentFile
import org.sugr.gearshift.viewmodel.api.Api
import org.sugr.gearshift.viewmodel.api.AuthException
import org.sugr.gearshift.viewmodel.api.NetworkException
import org.sugr.gearshift.viewmodel.ext.readableFileSize
import org.sugr.gearshift.viewmodel.ext.readablePercent
import org.sugr.gearshift.viewmodel.ext.readableRemainingTime
import org.sugr.gearshift.viewmodel.rxutil.ResponseSingle
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class TransmissionApi(
        private var profile: Profile,
        private val ctx : Context,
        private val prefs: SharedPreferences,
        private val gson: Gson = Gson(),
        private val log: Logger = Log,
        private val mainThreadScheduler: Scheduler = AndroidSchedulers.mainThread(),
        debug: Boolean = BuildConfig.DEBUG
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
                val basic = "Basic " + String(Base64.encode(credentials.toByteArray(), Base64.NO_WRAP))

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
                        profile.save(prefs)

                        requestBuilder.header("X-Transmission-Session-Id", profile.sessionData)
                        response = chain.proceed(requestBuilder.build())
                    }
                }
            }

            when (response.code()) {
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN -> throw AuthException()
                HttpURLConnection.HTTP_OK -> response
                else -> throw NetworkException(response.code())
            }
        }

        builder.addInterceptor { chain ->
            var retries = 0
            var response : Response? = null
            val total = if (profile.retries < 0) 1 else profile.retries

            while (true) {
                try {
                    response = chain.proceed(chain.request())
                    break
                } catch (err: SocketTimeoutException) {
                    retries++

                    if (retries < total) {
                        log.D("Request timed out, retry count : ${retries}")

                        Thread.sleep(1000L + (retries - 1L) * 500L)
                    } else {
                        break
                    }
                }
            }

            response ?: throw TimeoutException()
        }

        if (debug) {
            val logger = HttpLoggingInterceptor { message ->
                log.D("http: $message")
            }
            logger.level = HttpLoggingInterceptor.Level.BODY
            builder.addInterceptor(logger)
        }

        if (profile.useSSL) {
            // Trust self-signed certificates
            val sc = SSLContext.getInstance("TLS")
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
            builder.hostnameVerifier { host, session -> true }
        }

        val timeout = if (profile.timeout > 0) profile.timeout else 10
        builder.readTimeout(timeout.toLong(), TimeUnit.SECONDS)
                .connectTimeout(timeout.toLong(), TimeUnit.SECONDS)

        if (profile.proxyEnabled) {
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

    override fun test(): Single<Boolean> {
        return request(requestBody("session-get")).map { json ->
            if ("test" in json) {
                json["test"].string.isNotEmpty()
            } else {
                false
            }
        }
    }

    override fun session(initial: Session): Observable<Session> {
        return request(requestBody("session-get"))
                .toObservable()
                .map { json -> gson.fromJson<TransmissionSession>(json) }
                .map { session -> session as Session }
                .repeatWhen { completed ->
                    completed.delay(profile.updateInterval, TimeUnit.SECONDS)
                }
    }

    override fun torrents(session: Observable<Session>, initial: Set<Torrent>) : Observable<Set<Torrent>> {
        val initialMap = HashMap(initial.associateBy { it.hash })

        return session.take(1).map { session ->
            (session as? TransmissionSession)?.rpcVersion ?: 0
        }.flatMap { rpcVersion ->
            getTorrents(TORRENT_META_FIELDS + TORRENT_STAT_FIELDS).toObservable().concatWith(
                Observable.rangeLong(1, Long.MAX_VALUE).concatMap { counter ->
                    val args = mutableListOf<Pair<String, Any?>>()

                    args.add("fields" to jsonArray(*TORRENT_STAT_FIELDS))
                    if ((counter % 10L) != 0L) {
                        args.add("ids" to "recently-active".toJson())
                    }

                    request(requestBody(
                            "torrent-get", jsonObject(*args.toTypedArray())
                    ))
                            .delay(profile.updateInterval, TimeUnit.SECONDS)
                            .toObservable()
                            .flatMap { json ->
                                val torrents = json["torrents"].array

                                var list = Observable.just(torrents)

                                val incomplete = torrents.filter { it.isJsonObject }.filter {
                                    (it.obj[FIELD_TOTAL_SIZE]?.nullInt ?: 0) == 0
                                }.map { it[FIELD_HASH].string }.toTypedArray()

                                val withoutFiles = torrents.filter { it.isJsonObject }
                                        .filter {
                                            !incomplete.contains(it.obj[FIELD_HASH].string)
                                        }
                                        .filter {
                                            (it.obj[FIELD_FILES]?.nullArray?.size() ?: 0) == 0
                                        }.map { it[FIELD_HASH].string }.toTypedArray()

                                json["removed"]?.nullArray?.map { jsonObject(FIELD_ID to it) }?.forEach { t ->
                                    torrents.add(t)
                                }

                                if (incomplete.isNotEmpty()) {
                                    list = list.concatWith(
                                            getTorrents(TORRENT_META_FIELDS, "ids" to jsonArray(*incomplete))
                                                    .toObservable()
                                    )
                                }

                                if (withoutFiles.isNotEmpty()) {
                                    list = list.concatWith(
                                            getTorrents(arrayOf(FIELD_HASH, FIELD_FILES), "ids" to jsonArray(*withoutFiles))
                                                    .toObservable()
                                    )
                                }

                                list
                            }
                }
            ).scan(initialMap) { accum, json ->
                val torrents = json.filter { it.isJsonObject }.map { it.asJsonObject }
                val removed = torrents.filter { !it.contains(FIELD_HASH) }.map { it[FIELD_ID].int }.toSet()

                torrents.filter { it.contains(FIELD_HASH) }.map { torrentFrom(it, ctx, rpcVersion, gson) }.forEach { torrent ->
                    accum[torrent.hash] = accum[torrent.hash]?.merge(torrent) ?: torrent
                }

                if (removed.isNotEmpty()) {
                    HashMap(accum.filter { !removed.contains(it.value.id) })
                } else {
                    accum
                }
            }.map { map -> map.values.toSet() }
        }
    }

    override fun freeSpace(dir: Observable<String>): Observable<Long> {
        return dir.switchMap { d ->
            request(requestBody("free-space", jsonObject("path" to d)))
                    .toObservable()
                    .map { json -> json["size-bytes"].long }
                    .repeatWhen { completed ->
                        completed.delay(profile.updateInterval, TimeUnit.SECONDS)
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

    private fun request(body: RequestBody): Single<JsonObject> {
        return ResponseSingle(httpClient, requestBuilder.post(body).build())
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .map { response ->
                    val json = gson.fromJson<JsonObject>(response.body().charStream())
                    when {
                        !("result" in json) -> throw TransmissionApiException("unknown response")
                        json["result"].string != "success" -> throw TransmissionApiException(json["result"].string)
                       "arguments" in json -> json["arguments"] as? JsonObject ?: throw TransmissionApiException("unexpected response arguments")
                        else -> throw TransmissionApiException("unknown response")
                    }
                }
                .observeOn(mainThreadScheduler)
    }


    private fun getTorrents(fields: Array<String>, vararg args: Pair<String, JsonElement>) : Single<JsonArray> {
        return request(requestBody(
                "torrent-get", jsonObject("fields" to jsonArray(*fields), *args)
        )).map { json -> json["torrents"].array }
    }

    companion object {
        val JSON = MediaType.parse("application/json; charset=utf-8");

        private val NEW_STATUS_RPC_VERSION = 14

        private val FIELD_ID = "id"
        private val FIELD_HASH = "hashString"
        private val FIELD_NAME = "name"
        private val FIELD_TOTAL_SIZE = "totalSize"
        private val FIELD_FILES = "files"
        private val FIELD_TRACKERS = "trackers"
        private val FIELD_FILE_STATS = "fileStats"

        private val TORRENT_META_FIELDS = arrayOf(FIELD_HASH, FIELD_NAME, "addedDate", FIELD_TOTAL_SIZE)

        private val TORRENT_STAT_FIELDS = arrayOf(
                FIELD_HASH, FIELD_ID, "error", "errorString", "eta",
                "isFinished", "isStalled", "leftUntilDone", "metadataPercentComplete",
                "peersConnected", "peersGettingFromUs", "peersSendingToUs", "percentDone",
                "queuePosition", "rateDownload", "rateUpload", "recheckProgress",
                "seedRatioMode", "seedRatioLimit", "sizeWhenDone", "status",
                "uploadedEver", "uploadRatio", "downloadDir"
        )

        private fun torrentFrom(json: JsonObject, ctx: Context, rpcVersion: Int, gson: Gson) : Torrent {
            val metaProgress = json["metadataPercentComplete"]?.nullFloat ?: 0f
            val downloadProgress = json["percentDone"]?.nullFloat ?: 0f
            val eta = json["eta"]?.nullLong ?: 0L
            val isStalled = json["isStalled"]?.nullBool ?: false
            val rateDownload = json["rateDownload"]?.nullLong ?: 0L
            val rateUpload = json["rateUpload"]?.nullLong ?: 0L
            val peersSendingToUs = json["peersSendingToUs"]?.nullInt ?: 0
            val peersGettingFromUs = json["peersGettingFromUs"]?.nullInt ?: 0
            val peersConnected = json["peersConnected"]?.nullInt ?: 0
            val recheckProgress = json["recheckProgress"]?.nullFloat ?: 0f
            val uploadRatio = json["uploadRatio"]?.nullFloat ?: 0f
            val seedLimit = json["seedRatioLimit"]?.nullFloat ?: 0f
            val seedMode = json["seedRatioMode"]?.nullInt ?: 0
            val sizeWhenDone = json["sizeWhenDone"]?.nullLong ?: 0
            val leftUntilDone = json["leftUntilDone"]?.nullLong ?: 0
            val uploadedEver = json["uploadedEver"]?.nullLong ?: 0L
            val status = if (rpcVersion < NEW_STATUS_RPC_VERSION) {
                LegacyStatusType.values().filter {
                    it.value == json["status"]?.nullInt ?: 0
                }.getOrElse(0) { LegacyStatusType.STOPPED }.type
            } else {
                StatusType.values().filter {
                    it.value == json["status"]?.nullInt ?: 0
                }.getOrElse(0) { StatusType.STOPPED }.type
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

            val jsonFiles = json[FIELD_FILES].nullArray ?: jsonArray()
            val jsonFileStats = json[FIELD_FILE_STATS].nullArray ?: jsonArray()

            val files = jsonFiles.mapIndexed { i, file ->
                val stats = if (jsonFileStats.size() > i) jsonFileStats[i].obj else jsonObject()

                TorrentFile(path = file["name"].string,
                        downloaded = file["bytesCompleted"].long,
                        total = file["length"].long,
                        priority = stats["priority"].nullInt ?: 0,
                        wanted = stats["wanted"].nullBool ?: false)
            }.toSet()

            return Torrent(
                    hash = json[FIELD_HASH]?.nullString ?: "", id = json[FIELD_ID]?.nullInt ?: 0,
                    name = json[FIELD_NAME]?.nullString ?: "", statusType = status,
                    metaProgress = metaProgress,
                    downloadProgress = downloadProgress,
                    uploadProgress = uploadRatio,
                    downloadRate = rateDownload,
                    uploadRate = rateUpload,
                    uploadRatio = uploadRatio,
                    isDirectory = (json[FIELD_FILES]?.nullArray?.size() ?: 0) > 1,
                    statusText = statusText, trafficText = trafficText,
                    error = json["errorString"]?.nullString ?: "",
                    errorType = ErrorType.values().filter { it.value == json["error"]?.nullInt ?: 0 }.first().type,
                    downloadDir = json["downloadDir"]?.nullString ?: "",
                    connectedPeers = peersConnected,
                    queuePosition = json["queuePosition"]?.nullInt ?: 0,
                    validSize = sizeWhenDone,
                    totalSize = json["totalSize"]?.nullLong ?: 0,
                    sizeLeft = leftUntilDone,
                    seedRatioLimit =  seedLimit,
                    addedTime = json["addedDate"]?.nullLong ?: 0,
                    seedRatioMode = SeedRatioMode.values().filter { it.value == seedMode }
                            .map { it.mode }.firstOrNull() ?: Torrent.SeedRatioMode.UNKNOWN,
                    files = files
            )
        }
    }
}

enum class ErrorType(val value: Int, val type: Torrent.ErrorType) {
    OK(0, Torrent.ErrorType.OK),
    TRACKER_WARNING(1, Torrent.ErrorType.TRACKER_WARNING),
    TRACKER_ERROR(2, Torrent.ErrorType.TRACKER_ERROR),
    LOCAL_ERROR(3, Torrent.ErrorType.LOCAL_ERROR)
}

enum class StatusType(val value: Int, val type: Torrent.StatusType) {
    STOPPED(0, Torrent.StatusType.STOPPED),
    CHECK_WAITING(1, Torrent.StatusType.CHECK_WAITING),
    CHECKING(2, Torrent.StatusType.CHECKING),
    DOWNLOAD_WAITING(3, Torrent.StatusType.DOWNLOAD_WAITING),
    DOWNLOADING(4, Torrent.StatusType.DOWNLOADING),
    SEED_WAITING(5, Torrent.StatusType.SEED_WAITING),
    SEEDING(6, Torrent.StatusType.SEEDING)
}

enum class LegacyStatusType(val value: Int, val type: Torrent.StatusType) {
    CHECK_WAITING(1, Torrent.StatusType.CHECK_WAITING),
    CHECKING(2, Torrent.StatusType.CHECKING),
    DOWNLOADING(4, Torrent.StatusType.DOWNLOADING),
    SEEDING(8, Torrent.StatusType.SEEDING),
    STOPPED(16, Torrent.StatusType.STOPPED)
}

enum class SeedRatioMode(val value: Int, val mode: Torrent.SeedRatioMode) {
    GLOBAL_LIMIT(0, Torrent.SeedRatioMode.GLOBAL_LIMIT),
    TORRENT_LIMIT(1, Torrent.SeedRatioMode.LIMIT),
    NO_LIMIT(2, Torrent.SeedRatioMode.NO_LIMIT)
}

class TransmissionApiException(message: String): RuntimeException("transmission api: " + message)
