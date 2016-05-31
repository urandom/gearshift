package org.sugr.gearshift.model.api

import org.sugr.gearshift.model.Profile
import org.sugr.gearshift.model.ProfileType
import rx.Observable

interface Api {
    fun test(): Observable<Boolean>

    companion object {
        fun create(profile: Profile): Api {
            if (profile.type == ProfileType.TRANSMISSION) {
                return TransmissionApi(profile)
            }

            throw IllegalArgumentException("unsupported profile type")
        }
    }
}