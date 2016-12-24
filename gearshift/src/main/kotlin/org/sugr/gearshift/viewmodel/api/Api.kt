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
import org.sugr.gearshift.model.*
import org.sugr.gearshift.viewmodel.api.transmission.TransmissionApi

interface Api {
    fun test(): Single<Boolean>
    fun session(interval: Long, initial: Session = NoSession()) : Observable<Session>
    fun torrents(session: Observable<Session>, interval: Long, initial: Set<Torrent> = setOf()): Observable<Set<Torrent>>
}

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
    override fun session(interval: Long, initial: Session) = Observable.empty<Session>()
    override fun torrents(session: Observable<Session>, interval: Long, initial: Set<Torrent>) = Observable.empty<Set<Torrent>>()
}
