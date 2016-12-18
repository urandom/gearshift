package org.sugr.gearshift.viewmodel.api

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import org.sugr.gearshift.BuildConfig
import org.sugr.gearshift.Log
import org.sugr.gearshift.Logger
import org.sugr.gearshift.model.Profile
import org.sugr.gearshift.model.ProfileType
import org.sugr.gearshift.model.Session
import org.sugr.gearshift.model.Torrent
import org.sugr.gearshift.viewmodel.api.transmission.TransmissionApi

interface Api {
    fun test(): Single<Boolean>
    fun torrents(session: Observable<Session>, interval: Long, initial: Set<Torrent> = setOf()): Observable<Set<Torrent>>
}

typealias ApiFactory = (profile: Profile, ctx: Context,
                        prefs: SharedPreferences,
                        gson: Gson,
                        log: Logger,
                        debug: Boolean) -> Api

fun apiOf(profile: Profile, ctx: Context,
          prefs: SharedPreferences,
          gson : Gson = Gson(),
          log: Logger = Log,
          debug : Boolean = BuildConfig.DEBUG) : Api {
    if (profile.type == ProfileType.TRANSMISSION) {
        return TransmissionApi(profile, ctx, prefs, gson, log, AndroidSchedulers.mainThread(), debug)
    }

    return NoApi
}

object NoApi : Api {
    override fun test() = Single.just(false)
    override fun torrents(session: Observable<Session>, interval: Long, initial: Set<Torrent>) = Observable.empty<Set<Torrent>>()
}
