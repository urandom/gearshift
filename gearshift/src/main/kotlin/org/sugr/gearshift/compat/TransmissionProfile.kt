package org.sugr.gearshift.compat

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import org.sugr.gearshift.C
import org.sugr.gearshift.Log
import org.sugr.gearshift.Logger
import org.sugr.gearshift.model.transmissionProfile
import org.sugr.gearshift.viewmodel.rxutil.set

fun migrateTransmissionProfiles(ctx : Context, prefs: SharedPreferences, log : Logger = Log) {
    log.D("Migrating any old profiles")

    if (prefs.contains(C.PREF_MIGRATION_V1)) {
        return
    }

    val compatPrefs = profilePreferences(ctx)
    val ids = prefs.getStringSet(C.PREF_PROFILES, setOf()).toList()

    val profiles = Array(ids.size) { i -> ids[i] }
            .filter { id -> compatPrefs.contains(keys.PREF_NAME + id) }
            .map { id ->
                log.D("Copy data for profile $id")

                transmissionProfile().copy(id = id,
                        name = compatPrefs.getString(keys.PREF_NAME + id, ""),
                        host = compatPrefs.getString(keys.PREF_HOST + id, ""),
                        port = compatPrefs.getString(keys.PREF_PORT + id, "9091").toInt(),
                        path = compatPrefs.getString(keys.PREF_PATH + id, ""),
                        username = compatPrefs.getString(keys.PREF_USER + id, ""),
                        password = compatPrefs.getString(keys.PREF_PASS + id, ""),
                        useSSL = compatPrefs.getBoolean(keys.PREF_SSL + id, false),
                        timeout = compatPrefs.getString(keys.PREF_TIMEOUT + id, "-1").toInt(),
                        retries = compatPrefs.getString(keys.PREF_RETRIES + id, "-1").toInt(),
                        directories = compatPrefs.getStringSet(keys.PREF_DIRECTORIES + id, emptySet()).toList(),
                        lastDirectory = compatPrefs.getString(keys.PREF_LAST_DIRECTORY + id, ""),
                        moveData = compatPrefs.getBoolean(keys.PREF_MOVE_DATA + id, false),
                        startPaused = compatPrefs.getBoolean(keys.PREF_START_PAUSED + id, false),
                        proxyHost = compatPrefs.getString(keys.PREF_PROXY_HOST + id, ""),
                        proxyPort = compatPrefs.getString(keys.PREF_PROXY_PORT + id, "8080").toInt(),
                        updateInterval = compatPrefs.getString(keys.PREF_UPDATE_INTERVAL + id, "-1").toInt(),
                        fullUpdate = compatPrefs.getString(keys.PREF_FULL_UPDATE + id, "2").toInt(),
                        color = compatPrefs.getInt(keys.PREF_COLOR + id, 0)
                )
            }

    compatPrefs.edit().clear().apply()

    profiles.map {
        profile -> profile.save(prefs)
    }

    prefs.set(C.PREF_MIGRATION_V1, true)
}

private fun profilePreferences(ctx: Context) : SharedPreferences {
    return ctx.getSharedPreferences("profiles", Activity.MODE_PRIVATE)
}

private object keys {
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

