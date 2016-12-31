package org.sugr.gearshift.viewmodel.rxutil

import android.content.SharedPreferences

fun SharedPreferences.set(key: String, value: String) {
    edit().apply {
        putString(key, value)
        apply()
    }
}

fun SharedPreferences.set(key: String, value: Boolean) {
    edit().apply {
        putBoolean(key, value)
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

fun SharedPreferences.observe() =
        observable<String> { e ->
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                e.onNext(key)
            }

            registerOnSharedPreferenceChangeListener(listener)
            e.setCancellable {
                unregisterOnSharedPreferenceChangeListener(listener)
            }
        }


fun SharedPreferences.observeKey(key: String) =
        observe().filter { it == key }.startWith { key }