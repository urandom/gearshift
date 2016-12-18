package org.sugr.gearshift.model

interface Session {
        val downloadSpeedLimit: Long
        val uploadSpeedLimit: Long
        val seedRatioLimitEnabled: Boolean
        val seedRatioLimit: Float
}

data class NoSession(
        override val downloadSpeedLimit: Long = -1,
        override val uploadSpeedLimit: Long = -1,
        override val seedRatioLimitEnabled: Boolean = false,
        override val seedRatioLimit: Float = -1f
) : Session