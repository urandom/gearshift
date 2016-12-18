package org.sugr.gearshift.model

open class Session(
        val downloadSpeedLimit: Long = -1,
        val uploadSpeedLimit: Long = -1,
        val seedRatioLimitEnabled: Boolean = false,
        val seedRatioLimit: Float = -1f
)
