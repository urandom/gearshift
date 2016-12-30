package org.sugr.gearshift.model

import android.content.SharedPreferences
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.google.gson.JsonParseException
import org.sugr.gearshift.C
import org.sugr.gearshift.Log
import org.sugr.gearshift.Logger
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
                   val proxyHost: String = "", val proxyPort: Int = 0,
                   val updateInterval: Long = 1,
                   val color: Int = 0, val sessionData: String = "",
                   val temporary: Boolean = false) {

    @Transient var loaded : Boolean = false
        private set

    val valid : Boolean
        get() = name != "" && host != "" && !host.endsWith("example.com") && port > 0 && port < 65536 && (
                proxyHost == "" || (!proxyHost.endsWith("example.com") && proxyPort > 0 && proxyPort < 65536)
                )

    val proxyEnabled : Boolean
        get() = valid && proxyHost != ""

    fun load(prefs: SharedPreferences, log : Logger = Log) : Profile {
        val p = prefs.getString(prefix + id, null)
        if (p != null) {
            try {
                val profile = Gson().fromJson<Profile>(p ?: "")
                profile.loaded = true

                return profile
            } catch (e : JsonParseException) {
                log.E("Cannot parse data for profile $id: '$p'", e)
                return this
            }
        } else {
            return this
        }
    }

    fun save(prefs : SharedPreferences) : Profile {
        if (temporary) {
            return this
        }

        prefs.set(prefix + id, Gson().toJson(this))

        val ids = prefs.getStringSet(C.PREF_PROFILES, mutableSetOf())

        if (!ids.contains(id)) {
            ids.add(id)
            prefs.set(C.PREF_PROFILES, ids)
        }

        return this
    }

    fun delete(prefs : SharedPreferences) : Profile {
        prefs.delete(prefix + id)

        val ids = prefs.getStringSet(C.PREF_PROFILES, emptySet())

        if (ids.contains(id)) {
            ids.remove(id)
            prefs.set(C.PREF_PROFILES, ids)
        }

        return this
    }

    companion object {
        val prefix = "profile_"
    }

}

enum class ProfileType {
    TRANSMISSION
}

fun profileOf(id: String, prefs: SharedPreferences) = Profile(id = id).load(prefs)

fun loadProfiles(prefs: SharedPreferences) : Array<Profile> {
    val ids = prefs.getStringSet(C.PREF_PROFILES, setOf()).toList()

    return Array(ids.size) { i ->
        Profile(id = ids[i]).load(prefs)
    }
}

fun transmissionProfile(): Profile {
    return Profile(type = ProfileType.TRANSMISSION, path = "/transmission/rpc", port = 9091)
}
