package org.sugr.gearshift.viewmodel.api.transmission

import android.content.Context
import android.content.SharedPreferences
import com.github.salomonbrys.kotson.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.sugr.gearshift.Logger
import org.sugr.gearshift.R
import org.sugr.gearshift.model.Profile
import org.sugr.gearshift.model.Session
import org.sugr.gearshift.model.Torrent
import org.sugr.gearshift.viewmodel.api.Api
import java.net.HttpURLConnection

class TransmissionApiTest {
    val server = MockWebServer()
    val baseProfile : Profile
    val sessionId = "session-id"
    val gson = Gson()
    val log = mock<Logger> {
        on { E(any<String>(), any()) }.then { println(it.arguments[0]) }
    }
    val ctx = mock<Context>{
        on { getString(R.string.status_format) } doReturn "%1\$s %2\$s"
        on { getString(R.string.status_state_downloading_metadata)} doReturn "d_meta"
        on { getString(R.string.status_state_downloading) } doReturn "d"
        on { getString(R.string.status_state_download_waiting) } doReturn "d_wait"
        on { getString(R.string.status_more_downloading_format) } doReturn "%1\$d %2\$d %3\$s"
        on { getString(R.string.status_more_downloading_speed_format) } doReturn "%1\$s %2\$s"
        on { getString(R.string.status_more_idle) } doReturn "m_i"
        on { getString(R.string.status_state_seeding) } doReturn "s"
        on { getString(R.string.status_state_seed_waiting) } doReturn "s_wait"
        on { getString(R.string.status_more_seeding_format) } doReturn "%1\$d %2\$d %3\$s"
        on { getString(R.string.status_more_seeding_speed_format) } doReturn "%1\$s %2\$s"
        on { getString(R.string.status_state_checking) } doReturn "c"
        on { getString(R.string.status_state_check_waiting) } doReturn "c_wait"
        on { getString(R.string.status_state_paused) } doReturn "p"
        on { getString(R.string.status_state_finished) } doReturn "f"
        on { getString(R.string.traffic_downloading_format) } doReturn "%1\$s %2\$s %3\$s %4\$s"
        on { getString(R.string.traffic_downloading_percentage_format) } doReturn "%1\$s"
        on { getString(R.string.traffic_remaining_time_unknown) } doReturn "t_u"
        on { getString(R.string.traffic_remaining_time_format) } doReturn "%1\$s"
        on { getString(R.string.traffic_seeding_format) } doReturn "%1\$s %2\$s %3\$s %4\$s"
        on { getString(R.string.traffic_seeding_ratio_format) } doReturn "%1\$s %2\$s"
        on { getString(R.string.traffic_seeding_ratio_goal_format) } doReturn "%1\$s"
    }

    init {
        server.start()
        baseProfile = Profile(name = "default", host = server.hostName, port = server.port, path = "/transmission/rpc")
    }

    @Test
    fun sessionId() {
        server.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_CONFLICT)
                .setHeader("X-Transmission-Session-Id", sessionId)
        )

        server.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(jsonObject("result" to "success", "arguments" to jsonObject("version" to "v1")).toString())
        )

        val editor = mock<SharedPreferences.Editor> {}
        val prefs = mock<SharedPreferences>{
            on { edit() } doReturn editor
        }

        val api : Api = TransmissionApi(baseProfile, ctx, prefs, gson, log, Schedulers.trampoline())

        val version = api.version().blockingGet()
        assertThat("v1", `is`(version))

        var request = server.takeRequest()
        assertThat("/transmission/rpc", `is`(request.path))
        assertThat("POST", `is`(request.method))
        assertThat(null, `is`(request.headers["X-Transmission-Session-Id"]))

        request = server.takeRequest()
        assertThat("/transmission/rpc", `is`(request.path))
        assertThat("POST", `is`(request.method))
        assertThat(sessionId, `is`(request.headers["X-Transmission-Session-Id"]))
    }

    @Test
    fun version() {
        server.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(jsonObject("result" to "success", "arguments" to jsonObject("version" to "v1")).toString())
        )

        val prefs = mock<SharedPreferences>{}

        val api : Api = TransmissionApi(baseProfile, ctx, prefs, gson, log, Schedulers.trampoline())

        val version = api.version().blockingGet()
        assertThat("v1", `is`(version))

        val request = server.takeRequest()
        assertThat("/transmission/rpc", `is`(request.path))
        assertThat("POST", `is`(request.method))
        assertThat(null, `is`(request.headers["X-Transmission-Session-Id"]))

        val jp = JsonParser()
        val obj = jp.parse(request.body.readUtf8()).obj
        assertThat("session-get", `is`(obj["method"].string))
        assertThat(null, `is`(obj["arguments"].nullObj))
    }

    @Test
    fun torrents() {
        server.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(Torrents.data3)
        )

        val prefs = mock<SharedPreferences>{}

        val api : Api = TransmissionApi(baseProfile, ctx, prefs, gson, log, Schedulers.trampoline())

        val torrents = api.torrents(Observable.just(Session(rpcVersion = Torrents.new_status_rpc_version)), 1, setOf()).skip(1).blockingFirst()

        assertThat(3, `is`(torrents.size))

        torrents.forEachIndexed { i, torrent ->
            assertThat(Torrents.names[i], `is`(torrent.name))
            assertThat(Torrents.statuses[i], `is`(torrent.statusType))
            assertThat(Torrents.fileCount[i], `is`(torrent.files.size))
        }

        val request = server.takeRequest()
        assertThat("/transmission/rpc", `is`(request.path))
        assertThat("POST", `is`(request.method))
        assertThat(null, `is`(request.headers["X-Transmission-Session-Id"]))

        val jp = JsonParser()
        val obj = jp.parse(request.body.readUtf8()).obj
        assertThat("torrent-get", `is`(obj["method"].string))
        assertThat(null, `is`(obj["arguments"].obj["ids"].nullObj))
        assertThat(jsonArray(*(Torrents.meta_fields + Torrents.stat_fields)), `is`(obj["arguments"].obj["fields"].array))
    }

    @Test
    fun torrentsSecondRequest() {
        server.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(Torrents.data3)
        )
        server.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(Torrents.data3)
        )

        val prefs = mock<SharedPreferences>{}

        val api : Api = TransmissionApi(baseProfile, ctx, prefs, gson, log, Schedulers.trampoline())

        val torrents = api.torrents(Observable.just(Session(rpcVersion = Torrents.new_status_rpc_version)), 1, setOf()).skip(2).take(1).blockingFirst()
        assertThat(3, `is`(torrents.size))

        torrents.forEachIndexed { i, torrent ->
            assertThat(Torrents.names[i], `is`(torrent.name))
            assertThat(Torrents.statuses[i], `is`(torrent.statusType))
            assertThat(Torrents.fileCount[i], `is`(torrent.files.size))
        }

        var request = server.takeRequest()
        assertThat("/transmission/rpc", `is`(request.path))
        assertThat("POST", `is`(request.method))
        assertThat(null, `is`(request.headers["X-Transmission-Session-Id"]))

        val jp = JsonParser()
        var obj = jp.parse(request.body.readUtf8()).obj
        assertThat("torrent-get", `is`(obj["method"].string))
        assertThat(null, `is`(obj["arguments"].obj["ids"].nullObj))
        assertThat(jsonArray(*(Torrents.meta_fields + Torrents.stat_fields)), `is`(obj["arguments"].obj["fields"].array))

        request = server.takeRequest()
        assertThat("/transmission/rpc", `is`(request.path))
        assertThat("POST", `is`(request.method))
        assertThat(null, `is`(request.headers["X-Transmission-Session-Id"]))

        obj = jp.parse(request.body.readUtf8()).obj
        assertThat("torrent-get", `is`(obj["method"].string))
        assertThat("recently-active", `is`(obj["arguments"].obj["ids"].string))
        assertThat(jsonArray(*(Torrents.stat_fields)), `is`(obj["arguments"].obj["fields"].array))
    }

}

private object Torrents {
    val new_status_rpc_version = 14
    val field_hash_string = "hashString"
    val field_files = "files"

    val meta_fields = arrayOf(field_hash_string, "name", "addedDate", "totalSize")
    val stat_fields = arrayOf(
            field_hash_string, "id", "error", "errorString", "eta",
            "isFinished", "isStalled", "leftUntilDone", "metadataPercentComplete",
            "peersConnected", "peersGettingFromUs", "peersSendingToUs", "percentDone",
            "queuePosition", "rateDownload", "rateUpload", "recheckProgress",
            "seedRatioMode", "seedRatioLimit", "sizeWhenDone", "status",
            "uploadedEver", "uploadRatio", "downloadDir"
    )

    val names = arrayOf("T1", "T2", "T3")
    val statuses = arrayOf(Torrent.StatusType.STOPPED, Torrent.StatusType.DOWNLOADING, Torrent.StatusType.CHECK_WAITING)
    val fileCount = arrayOf(2, 1, 0)
    val data3 = """{
  "arguments": {
    "torrents": [
      {
        "addedDate": 1462710476,
        "downloadDir": "/dir/1",
        "error": 0,
        "errorString": "",
        "eta": -1,
        "files": [
          {
            "bytesCompleted": 1111,
            "length": 1111,
            "name": "file1"
          },
          {
            "bytesCompleted": 11,
            "length": 11,
            "name": "file2"
          }
        ],
        "hashString": "1111111111111111111111111111111111111111",
        "id": 1,
        "isFinished": true,
        "isStalled": false,
        "leftUntilDone": 0,
        "metadataPercentComplete": 1,
        "name": "${names[0]}",
        "peersConnected": 0,
        "peersGettingFromUs": 0,
        "peersSendingToUs": 0,
        "percentDone": 1,
        "queuePosition": 0,
        "rateDownload": 0,
        "rateUpload": 0,
        "recheckProgress": 0,
        "seedRatioLimit": 2,
        "seedRatioMode": 0,
        "sizeWhenDone": 1111,
        "status": 0,
        "totalSize": 1111,
        "uploadRatio": 2,
        "uploadedEver": 1229490811
      },
      {
        "addedDate": 1449518691,
        "downloadDir": "/dir/2",
        "error": 3,
        "errorString": "No data found! Ensure your drives are connected or use \"Set Location\". To re-download, remove the torrent and re-add it.",
        "eta": -1,
        "files": [
          {
            "bytesCompleted": 2000,
            "length": 2222,
            "name": "file1"
          }
        ],
        "hashString": "2222222222222222222222222222222222222222",
        "id": 2,
        "isFinished": false,
        "isStalled": false,
        "leftUntilDone": 222,
        "metadataPercentComplete": 1,
        "name": "${names[1]}",
        "peersConnected": 0,
        "peersGettingFromUs": 0,
        "peersSendingToUs": 0,
        "percentDone": 0.9,
        "queuePosition": 1,
        "rateDownload": 0,
        "rateUpload": 0,
        "recheckProgress": 0,
        "seedRatioLimit": 2,
        "seedRatioMode": 0,
        "sizeWhenDone": 22222,
        "status": 4,
        "totalSize": 22222,
        "uploadRatio": 2.0002,
        "uploadedEver": 699589356
      },
      {
        "addedDate": 1478039739,
        "downloadDir": "/dir/2",
        "error": 3,
        "errorString": "No data found! Ensure your drives are connected or use \"Set Location\". To re-download, remove the torrent and re-add it.",
        "eta": -1,
        "hashString": "3333333333333333333333333333333333333333",
        "id": 3,
        "isFinished": true,
        "isStalled": false,
        "leftUntilDone": 0,
        "metadataPercentComplete": 1,
        "name": "${names[2]}",
        "peersConnected": 0,
        "peersGettingFromUs": 0,
        "peersSendingToUs": 0,
        "percentDone": 1,
        "queuePosition": 2,
        "rateDownload": 0,
        "rateUpload": 0,
        "recheckProgress": 0,
        "seedRatioLimit": 1.2,
        "seedRatioMode": 0,
        "sizeWhenDone": 33333,
        "status": 1,
        "totalSize": 0,
        "uploadRatio": 1.2003,
        "uploadedEver": 1167729396
      }
    ]
  },
  "result": "success"
}
"""
}
