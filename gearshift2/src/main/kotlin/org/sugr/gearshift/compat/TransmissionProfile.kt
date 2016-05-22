package org.sugr.gearshift.compat

import android.app.Activity
import android.content.SharedPreferences
import com.f2prateek.rx.preferences.RxSharedPreferences
import org.sugr.gearshift.C
import org.sugr.gearshift.app
import org.sugr.gearshift.defaultPreferences
import org.sugr.gearshift.model.Profile
import org.sugr.gearshift.model.transmissionProfile

private class TransmissionProfileCompat(private val prefs: SharedPreferences) {
    fun migrate() :Array<Profile> {
        val ids = prefs.getStringSet(C.PREF_PROFILES, setOf()).toList()
        val prefs = getPreferences()
        val rxPrefs = RxSharedPreferences.create(prefs)

        val profiles = Array(ids.size) { i ->
            val id = ids[i]
            val profile = transmissionProfile().copy(id = id,
                    name = rxPrefs.getString(PREF_NAME + id).get() ?: "",
                    host = rxPrefs.getString(PREF_HOST + id).get() ?: "",
                    port = (rxPrefs.getString(PREF_PORT + id).get() ?: "9091").toInt(),
                    path = rxPrefs.getString(PREF_PATH + id).get() ?: "",
                    username = rxPrefs.getString(PREF_USER + id).get() ?: "",
                    password = rxPrefs.getString(PREF_PASS + id).get() ?: "",
                    useSSL = rxPrefs.getBoolean(PREF_SSL + id).get() ?: false,
                    timeout = (rxPrefs.getString(PREF_TIMEOUT + id).get() ?: "-1").toInt(),
                    retries = (rxPrefs.getString(PREF_RETRIES + id).get() ?: "-1").toInt(),
                    directories = (rxPrefs.getStringSet(PREF_DIRECTORIES + id).get() ?: emptySet<String>()).toList(),
                    lastDirectory = rxPrefs.getString(PREF_LAST_DIRECTORY + id).get() ?: "",
                    moveData = rxPrefs.getBoolean(PREF_MOVE_DATA + id).get() ?: false,
                    startPaused = rxPrefs.getBoolean(PREF_START_PAUSED + id).get() ?: false,
                    proxyHost = rxPrefs.getString(PREF_PROXY_HOST + id).get() ?: "",
                    proxyPort = (rxPrefs.getString(PREF_PROXY_PORT + id).get() ?: "8080").toInt(),
                    updateInterval = (rxPrefs.getString(PREF_UPDATE_INTERVAL + id).get() ?: "1").toInt(),
                    fullUpdate = (rxPrefs.getString(PREF_FULL_UPDATE + id).get() ?: "2").toInt(),
                    color = rxPrefs.getInteger(PREF_COLOR + id).get() ?: 0
            )

            profile
        }

        prefs.edit().clear().apply()

        return profiles.map { profile -> profile.save() }.toTypedArray()
    }

    fun getPreferences() : SharedPreferences {
        return app().getSharedPreferences(C.PROFILES_PREF_NAME, Activity.MODE_PRIVATE)
    }

    companion object {
        val PREF_NAME = "profile_name"
        val PREF_HOST = "profile_host"
        val PREF_PORT = "profile_port"
        val PREF_PATH = "profile_path"
        val PREF_USER = "profile_username"
        val PREF_PASS = "profile_password"
        val PREF_SSL = "profile_use_ssl"
        val PREF_TIMEOUT = "profile_timeout"
        val PREF_RETRIES = "profile_retries"
        val PREF_DIRECTORIES = "profile_directories"
        val PREF_LAST_DIRECTORY = "profile_last_directory"
        val PREF_MOVE_DATA = "profile_move_data"
        val PREF_DELETE_LOCAL = "profile_delete_local"
        val PREF_START_PAUSED = "profile_start_paused"
        val PREF_PROXY = "profile_use_proxy"
        val PREF_PROXY_HOST = "profile_proxy_host"
        val PREF_PROXY_PORT = "profile_proxy_port"
        val PREF_UPDATE_INTERVAL = "profile_update_interval"
        val PREF_FULL_UPDATE = "profile_full_update"
        val PREF_COLOR = "profile_color"
    }
}

fun migrateTransmissionProfiles(prefs: SharedPreferences = defaultPreferences()): Array<Profile> {
    return TransmissionProfileCompat(prefs).migrate()
}