package org.sugr.gearshift.viewmodel.rxutil

import android.content.SharedPreferences

fun SharedPreferences.set(key: String, value: String) {
    edit().apply {
        putString(key, value)
        apply()
    }
}

fun SharedPreferences.set(key: String, value: Set<String>) {
    edit().apply {
        putStringSet(key, value)
        apply()
    }
}

fun SharedPreferences.delete(key: String) {
    edit().apply {
        remove(key)
        apply()
    }
}

fun SharedPreferences.toFlowable() =
    latestFlowable<String> { e ->
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { pref, key ->
            e.onNext(key)
        }

        registerOnSharedPreferenceChangeListener(listener)
        e.setCancellable {
            unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.share()
