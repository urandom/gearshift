package org.sugr.gearshift.viewmodel.api

import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.Single
import org.sugr.gearshift.App
import org.sugr.gearshift.BuildConfig
import org.sugr.gearshift.model.Profile
import org.sugr.gearshift.model.ProfileType
import org.sugr.gearshift.model.Torrent
import org.sugr.gearshift.viewmodel.api.transmission.TransmissionApi

interface Api {
    fun version(): Single<String>
    fun torrents(): Observable<Torrent>
}

fun apiOf(profile: Profile, app: App = org.sugr.gearshift.app(), gson : Gson = Gson(), debug : Boolean = BuildConfig.DEBUG) : Api {
    if (profile.type == ProfileType.TRANSMISSION) {
        return TransmissionApi(profile, app, gson, debug)
    }

    throw IllegalArgumentException("unsupported profile type")
}