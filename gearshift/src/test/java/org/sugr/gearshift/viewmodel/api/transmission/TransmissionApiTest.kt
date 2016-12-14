package org.sugr.gearshift.viewmodel.api.transmission

import android.content.Context
import android.content.SharedPreferences
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.nullObj
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import io.reactivex.schedulers.Schedulers
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.sugr.gearshift.Logger
import org.sugr.gearshift.model.Profile
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

        val ctx = mock<Context>{}
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

        val ctx = mock<Context>{}
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

    }

}