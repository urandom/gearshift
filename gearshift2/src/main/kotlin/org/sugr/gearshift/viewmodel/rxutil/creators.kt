package org.sugr.gearshift.viewmodel.rxutil

import android.content.SharedPreferences
import com.f2prateek.rx.preferences.RxSharedPreferences

fun sharedPreferences(prefs: SharedPreferences) = RxSharedPreferences.create(prefs)
