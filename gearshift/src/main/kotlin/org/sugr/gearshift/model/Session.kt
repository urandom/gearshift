package org.sugr.gearshift.model

data class Session(
        val downloadDir: String = "",
        val downloadDirFreeSpace: Long = -1,
        val downloadSpeedLimitEnabled: Boolean = false,
        val downloadSpeedLimit: Long = -1,
        val uploadSpeedLimitEnabled: Boolean = false,
        val uploadSpeedLimit: Long = -1,
        val altSpeedLimitEnabled: Boolean = false,
        val altDownloadSpeedLimit: Long = -1,
        val altUploadSpeedLimit: Long = -1,
        val seedRatioLimitEnabled: Boolean = false,
        val seedRatioLimit: Float = -1f
)
