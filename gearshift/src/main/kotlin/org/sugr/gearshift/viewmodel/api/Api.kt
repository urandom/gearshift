package org.sugr.gearshift.viewmodel.api

import android.content.Context
import android.content.SharedPreferences
import android.text.Spannable
import com.google.gson.Gson
import io.reactivex.Completable
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
	fun session(initial: Session = NoSession()) : Observable<Session>
	fun torrents(initial: Set<Torrent> = setOf()): Observable<Set<Torrent>>
	fun startTorrents(stopped: Array<Torrent>, queued: Array<Torrent>): Completable
	fun stopTorrents(running: Array<Torrent>): Completable
	fun removeTorrents(torrents: Array<Torrent>): Completable
	fun deleteTorrents(torrents: Array<Torrent>): Completable

	fun updateSession(session: Session): Completable
}

interface StatisticsApi {
	fun freeSpace(dir: Observable<String>) : Observable<Long>
	fun currentSpeed(): Observable<CurrentSpeed>
}

typealias SpannableFactory = (String) -> Spannable

fun apiOf(profile: Profile, ctx: Context,
		  prefs: SharedPreferences,
		  gson : Gson = Gson(),
		  log: Logger = Log,
		  debug : Boolean = BuildConfig.DEBUG) : Api {

	if (profile.type == ProfileType.TRANSMISSION) {
		return TransmissionApi(profile, ctx, prefs, gson, log, debug)
	}

	return NoApi
}

object NoApi : Api {
	override fun test() = Single.just(false)
	override fun session(initial: Session) = Observable.empty<Session>()
	override fun torrents(initial: Set<Torrent>) = Observable.empty<Set<Torrent>>()
	override fun updateSession(session: Session) = Completable.complete()
	override fun startTorrents(stopped: Array<Torrent>, queued: Array<Torrent>) = Completable.complete()
	override fun stopTorrents(running: Array<Torrent>) = Completable.complete()
	override fun removeTorrents(torrents: Array<Torrent>) = Completable.complete()
	override fun deleteTorrents(torrents: Array<Torrent>) = Completable.complete()
}

class NetworkException(val code: Int): RuntimeException("Network error")
class AuthException: RuntimeException("Auth error")

data class CurrentSpeed(val download: Long = 0L, val upload: Long = 0L)