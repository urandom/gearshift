package org.sugr.gearshift.model

import android.app.Activity
import com.f2prateek.rx.preferences.RxSharedPreferences
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import org.sugr.gearshift.C
import org.sugr.gearshift.app
import org.sugr.gearshift.defaultPreferences
import org.sugr.gearshift.viewmodel.rxutil.sharedPreferences
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

    fun load(prefs: RxSharedPreferences = preferences()) : Profile {
        val p = prefs.getString(id)
        if (p.isSet) {
            loaded = true
            return Gson().fromJson<Profile>(prefs.getString(id).get() ?: "")
        } else {
            return this
        }
    }

    fun save(defaultPrefs : RxSharedPreferences = sharedPreferences(defaultPreferences()),
             prefs: RxSharedPreferences = preferences()) : Profile {

        if (temporary) {
            return this
        }

        prefs.getString(id).set(Gson().toJson(this))

        val idsPref = defaultPrefs.getStringSet(C.PREF_PROFILES, mutableSetOf())
        val ids = idsPref.get() ?: mutableSetOf()

        if (!ids.contains(id)) {
            ids.add(id)
            idsPref.set(ids)
        }

        return this
    }

    fun delete(defaultPrefs : RxSharedPreferences = sharedPreferences(defaultPreferences()),
               prefs: RxSharedPreferences = preferences()) : Profile {
        prefs.getString(id).delete()

        val idsPref = defaultPrefs.getStringSet(C.PREF_PROFILES, emptySet())
        val ids = idsPref.get() ?: emptySet()

        if (ids.contains(id)) {
            ids.remove(id)
            idsPref.set(ids)
        }

        return this
    }

}

enum class ProfileType {
    TRANSMISSION
}

fun loadProfiles(prefs: RxSharedPreferences = sharedPreferences(defaultPreferences()),
                 profilePrefs : RxSharedPreferences = preferences()) : Array<Profile> {
    val ids = prefs.getStringSet(C.PREF_PROFILES, setOf()).get()?.toList() ?: return emptyArray()

    return Array(ids.size) { i ->
        Profile(id = ids[i]).load(profilePrefs)
    }
}

fun transmissionProfile(): Profile {
    return Profile(type = ProfileType.TRANSMISSION, path = "/transmission/rpc", port = 9091)
}

private fun preferences() : RxSharedPreferences {
    return sharedPreferences(app().getSharedPreferences(C.PROFILES_PREF_NAME, Activity.MODE_PRIVATE))
}

