package org.sugr.gearshift.model

import android.app.Activity
import com.f2prateek.rx.preferences.RxSharedPreferences
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import org.sugr.gearshift.C
import org.sugr.gearshift.app
import java.util.*

data class Profile(var id: String = UUID.randomUUID().toString(), var type: ProfileType = ProfileType.TRANSMISSION,
                   var name: String = "", var host: String = "example.com", var port: Int = 0,
                   var path: String = "", var username: String = "", var password: String = "",
                   var useSSL: Boolean = false, var timeout: Int = 40, var retries: Int = 3,
                   var lastDirectory: String = "", var moveData: Boolean = false,
                   var deleteLocal: Boolean = false, var startPaused: Boolean = false,
                   var directories: List<String> = listOf(),
                   var proxyHost: String = "", var proxyPort: Int = 8080,
                   var updateInterval: Int = 1, var fullUpdate: Int = 2,
                   var color: Int = 0) {

    fun proxyEnabled() = proxyHost != "" && proxyPort > 0

    fun updateActiveTorrentsOnly() = fullUpdate > 0

    fun isValid() : Boolean {
        return name != "" && host != "" && host != "example.com" && port > 0 && port < 65535 && (
                proxyHost == "" || (proxyHost != "example.com" && proxyPort > 0 && proxyPort < 65535)
                )
    }

    fun load(prefs: RxSharedPreferences = preferences()) : Profile {
        val p = prefs.getString(id)
        if (p.isSet) {
            return Gson().fromJson<Profile>(prefs.getString(id).get() ?: "")
        } else {
            return this
        }
    }

    fun save(prefs: RxSharedPreferences = preferences()) : Profile {
        prefs.getString(id).set(Gson().toJson(this))

        return this
    }

    fun delete(prefs: RxSharedPreferences = preferences()) : Profile {
        prefs.getString(id).delete()

        return this
    }

}

enum class ProfileType {
    TRANSMISSION
}

fun transmissionProfile(): Profile {
    return Profile(type = ProfileType.TRANSMISSION, path = "/transmission/rpc", port = 9091)
}

private fun preferences() : RxSharedPreferences {
    return RxSharedPreferences.create(app().getSharedPreferences(C.PROFILES_PREF_NAME, Activity.MODE_PRIVATE))
}

