package org.sugr.gearshift.compat

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import org.sugr.gearshift.C
import org.sugr.gearshift.logD
import org.sugr.gearshift.model.Profile
import org.sugr.gearshift.model.transmissionProfile

private class TransmissionProfileCompat(private val ctx: Context, private val prefs: SharedPreferences) {
    fun migrate() :Array<Profile> {
        logD("Migrating any old profiles")

        val prefs = profilePreferences(ctx)
        val ids = prefs.getStringSet(C.PREF_PROFILES, setOf()).toList()

        val profiles = Array(ids.size) { i ->
            val id = ids[i]
            val profile = transmissionProfile().copy(id = id,
                    name = prefs.getString(PREF_NAME + id, ""),
                    host = prefs.getString(PREF_HOST + id, ""),
                    port = prefs.getString(PREF_PORT + id, "9091").toInt(),
                    path = prefs.getString(PREF_PATH + id, ""),
                    username = prefs.getString(PREF_USER + id, ""),
                    password = prefs.getString(PREF_PASS + id, ""),
                    useSSL = prefs.getBoolean(PREF_SSL + id, false),
                    timeout = prefs.getString(PREF_TIMEOUT + id, "-1").toInt(),
                    retries = prefs.getString(PREF_RETRIES + id, "-1").toInt(),
                    directories = prefs.getStringSet(PREF_DIRECTORIES + id, emptySet()).toList(),
                    lastDirectory = prefs.getString(PREF_LAST_DIRECTORY + id, ""),
                    moveData = prefs.getBoolean(PREF_MOVE_DATA + id, false),
                    startPaused = prefs.getBoolean(PREF_START_PAUSED + id, false),
                    proxyHost = prefs.getString(PREF_PROXY_HOST + id, ""),
                    proxyPort = prefs.getString(PREF_PROXY_PORT + id, "8080").toInt(),
                    updateInterval = prefs.getString(PREF_UPDATE_INTERVAL + id, "-1").toInt(),
                    fullUpdate = prefs.getString(PREF_FULL_UPDATE + id, "2").toInt(),
                    color = prefs.getInt(PREF_COLOR + id, 0)
            )

            profile
        }

        prefs.edit().clear().apply()

        return profiles.map {
            profile -> profile.save(this.prefs)
        }.toTypedArray()
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

fun migrateTransmissionProfiles(ctx : Context, prefs: SharedPreferences): Array<Profile> {
    return TransmissionProfileCompat(ctx, prefs).migrate()
}

fun profilePreferences(ctx: Context) : SharedPreferences {
    return ctx.getSharedPreferences("profiles", Activity.MODE_PRIVATE)
}
