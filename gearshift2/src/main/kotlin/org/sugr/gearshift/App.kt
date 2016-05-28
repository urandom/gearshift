package org.sugr.gearshift

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.preference.PreferenceManager
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.github.zafarkhaja.semver.Version
import com.google.gson.Gson
import com.google.gson.JsonArray
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import rx.Observable
import rx.lang.kotlin.observable
import rx.schedulers.Schedulers
import java.io.IOException

class App : Application() {

    class Update(val title: String, val description: String, val url: String, val downloadUrl: String)

    class UpdateException(val networkError: String?, val jsonError: String?) : RuntimeException("update exception")

    override fun onCreate() {
        super.onCreate()

        appDependencies.app = this

        Thread.setDefaultUncaughtExceptionHandler { thread, e -> this.handleUncaughtException(thread, e) }
    }

    fun checkForUpdates(): Observable<Update> {
        return observable<Update> { subscriber ->
            val request = Request.Builder().url(UPDATE_URL).build()
            val gson = Gson()

            try {
                val response = OkHttpClient().newCall(request).execute()

                val result = gson.fromJson<JsonArray>(response.body().charStream())[0];

                val tag = result.get("tag_name").string

                val current = Version.valueOf(packageManager.getPackageInfo(packageName, 0).versionName)
                val remote = Version.valueOf(tag)

                var update: Update? = null

                if (remote.greaterThan(current)) {
                    update = Update(result.get("name").string,
                            result.get("body").string,
                            result.get("html_url").string,
                            result.get("assets").asJsonArray[0].get("browser_download_url").string)
                }

                subscriber.onNext(update)
            } catch (e: IOException) {
                subscriber.onError(UpdateException(e.toString(), null))
            } catch (e: JSONException) {
                subscriber.onError(UpdateException(null, e.toString()))
            } catch (e: PackageManager.NameNotFoundException) {
                subscriber.onError(UpdateException(null, null))
            }
            subscriber.onCompleted();
        }.subscribeOn(Schedulers.io()).cacheWithInitialCapacity(1)
    }

    private fun handleUncaughtException(thread: Thread, e: Throwable) {
        e.printStackTrace()
        val intent = Intent()
        intent.action = "org.sugr.gearshift.CRASH_REPORT"
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)

        System.exit(1)
    }

    companion object {
        private val UPDATE_URL = "https://api.github.com/repos/urandom/gearshift/releases"
    }
}

private object appDependencies {
    lateinit var app: App
}

fun app() = appDependencies.app

fun defaultPreferences() = PreferenceManager.getDefaultSharedPreferences(app())
