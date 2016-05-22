package org.sugr.gearshift

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.preference.PreferenceManager
import com.github.zafarkhaja.semver.Version
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
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

        app = this

        Thread.setDefaultUncaughtExceptionHandler(Thread.UncaughtExceptionHandler { thread, e -> this.handleUncaughtException(thread, e) })
    }

    fun checkForUpdates(): Observable<Update> {
        return observable<Update> { subscriber ->
            val request = Request.Builder().url(UPDATE_URL).build()
            val gson = Gson()

            try {
                val response = OkHttpClient().newCall(request).execute()

                val result = gson.fromJson<JSONArray>(response.body().charStream(), JSONArray::class.java).getJSONObject(0)

                val tag = result.getString("tag_name")

                val current = Version.valueOf(packageManager.getPackageInfo(packageName, 0).versionName)
                val remote = Version.valueOf(tag)

                var update: Update? = null

                if (remote.greaterThan(current)) {
                    update = Update(result.getString("name"),
                            result.getString("body"),
                            result.getString("html_url"),
                            result.getJSONArray("assets").getJSONObject(0).getString("browser_download_url"))
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
        val intent = Intent()
        intent.action = "org.sugr.gearshift.CRASH_REPORT"
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)

        System.exit(1)
    }

    companion object {
        lateinit private var app : App
        private val UPDATE_URL = "https://api.github.com/repos/urandom/gearshift/releases"

        fun get(): App {
            return app
        }

        fun defaultPreferences(): SharedPreferences {
            return PreferenceManager.getDefaultSharedPreferences(app)
        }
    }
}
