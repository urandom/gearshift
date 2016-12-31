package org.sugr.gearshift

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.preference.PreferenceManager
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.github.zafarkhaja.semver.Version
import com.google.gson.Gson
import com.google.gson.JsonArray
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.sugr.gearshift.compat.migrateTransmissionProfiles
import org.sugr.gearshift.viewmodel.rxutil.toMaybe
import java.io.IOException
import java.util.concurrent.Callable

class App : Application() {
    val component : AppComponent by lazy { AppComponentImpl(this) }

    class Update(val title: String, val description: String, val url: String, val downloadUrl: String)

    class UpdateException(val networkError: String?, val jsonError: String?) : RuntimeException("update exception")

    val updateChecker =
        Callable {
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

                update
            } catch (e: IOException) {
                throw UpdateException(e.toString(), null)
            } catch (e: JSONException) {
                throw UpdateException(null, e.toString())
            } catch (e: PackageManager.NameNotFoundException) {
                throw UpdateException(null, null)
            }

        }.toMaybe().subscribeOn(Schedulers.io())

    override fun onCreate() {
        super.onCreate()

        if (!BuildConfig.DEBUG) {
            Thread.setDefaultUncaughtExceptionHandler { thread, e -> this.handleUncaughtException(thread, e) }
        }

        migrateTransmissionProfiles(this, component.prefs)
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

interface AppComponent {
    val app : App
    val context : Context
    val prefs : SharedPreferences
    val log : Logger
    val ioScheduler : Scheduler
}

class AppComponentImpl(application : App, override val log: Logger = Log) : AppComponent {
    override val app : App = application
    override val context : Context = app
    override val prefs = PreferenceManager.getDefaultSharedPreferences(app)
    override val ioScheduler = Schedulers.io()
}
