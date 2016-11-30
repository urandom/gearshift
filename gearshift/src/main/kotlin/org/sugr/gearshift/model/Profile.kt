package org.sugr.gearshift.model

import android.app.Activity
import android.content.SharedPreferences
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import org.sugr.gearshift.App
import org.sugr.gearshift.C
import org.sugr.gearshift.defaultPreferences
import org.sugr.gearshift.viewmodel.rxutil.delete
import org.sugr.gearshift.viewmodel.rxutil.set
import java.util.*

data class Profile(val id: String = UUID.randomUUID().toString(), val type: ProfileType = ProfileType.TRANSMISSION,
                   val name: String = "", val host: String = "example.com", val port: Int = 0,
                   val path: String = "", val username: String = "", val password: String = "",
                   val useSSL: Boolean = false, val timeout: Int = 40, val retries: Int = 3,
                   val lastDirectory: String = "", val moveData: Boolean = false,
                   val deleteLocal: Boolean = false, val startPaused: Boolean = false,
                   val directories: List<String> = listOf(),
                   val proxyHost: String = "", val proxyPort: Int = 8080,
                   val updateInterval: Int = 1, val fullUpdate: Int = 2,
                   val color: Int = 0, val sessionData: String = "",
                   val temporary: Boolean = false) {

    var loaded : Boolean = false
        private set

    val valid : Boolean
        get() = name != "" && host != "" && !host.endsWith("example.com") && port > 0 && port < 65535 && (
                proxyHost == "" || (!proxyHost.endsWith("example.com") && proxyPort > 0 && proxyPort < 65535)
                )

    fun proxyEnabled() = proxyHost != "" && proxyPort > 0

    fun updateActiveTorrentsOnly() = fullUpdate > 0

    fun load(prefs: SharedPreferences = profilePreferences()) : Profile {
        val p = prefs.getString(id, null)
        if (p != null) {
            loaded = true
            return Gson().fromJson<Profile>(p ?: "")
        } else {
            return this
        }
    }

    fun save(defaultPrefs : SharedPreferences = defaultPreferences(),
             prefs: SharedPreferences = profilePreferences()) : Profile {

        if (temporary) {
            return this
        }

        prefs.set(id, Gson().toJson(this))

        val ids = defaultPrefs.getStringSet(C.PREF_PROFILES, mutableSetOf())

        if (!ids.contains(id)) {
            ids.add(id)
            defaultPrefs.set(C.PREF_PROFILES, ids)
        }

        return this
    }

    fun delete(defaultPrefs : SharedPreferences = defaultPreferences(),
               prefs: SharedPreferences = profilePreferences()) : Profile {
        prefs.delete(id)

        val ids = defaultPrefs.getStringSet(C.PREF_PROFILES, emptySet())

        if (ids.contains(id)) {
            ids.remove(id)
            defaultPrefs.set(C.PREF_PROFILES, ids)
        }

        return this
    }

}

enum class ProfileType {
    TRANSMISSION
}

fun loadProfiles(prefs: SharedPreferences = defaultPreferences(),
                 profilePrefs : SharedPreferences = profilePreferences()) : Array<Profile> {
    val ids = prefs.getStringSet(C.PREF_PROFILES, setOf()).toList()

    return Array(ids.size) { i ->
        Profile(id = ids[i]).load(profilePrefs)
    }
}

fun transmissionProfile(): Profile {
    return Profile(type = ProfileType.TRANSMISSION, path = "/transmission/rpc", port = 9091)
}

fun profilePreferences(app: App = org.sugr.gearshift.app()) : SharedPreferences {
    return app.getSharedPreferences(C.PROFILES_PREF_NAME, Activity.MODE_PRIVATE)
}
