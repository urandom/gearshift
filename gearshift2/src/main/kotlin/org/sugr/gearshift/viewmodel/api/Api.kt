package org.sugr.gearshift.viewmodel.api

import org.sugr.gearshift.model.Profile
import org.sugr.gearshift.model.ProfileType
import rx.Observable

interface Api {
    fun version(): Observable<String>
}

fun apiOf(profile: Profile) : Api {
    if (profile.type == ProfileType.TRANSMISSION) {
        return TransmissionApi(profile)
    }

    throw IllegalArgumentException("unsupported profile type")
}