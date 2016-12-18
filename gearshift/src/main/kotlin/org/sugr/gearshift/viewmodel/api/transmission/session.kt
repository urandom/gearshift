package org.sugr.gearshift.viewmodel.api.transmission

import org.sugr.gearshift.model.Session

data class TransmissionSession(
        val downloadDir: String = "",
        val downloadDirFreeSpace: Long = -1,
        val downloadSpeedLimitEnabled: Boolean = false,
        val uploadSpeedLimitEnabled: Boolean = false,
        val altSpeedLimitEnabled: Boolean = false,
        val altDownloadSpeedLimit: Long = -1,
        val altUploadSpeedLimit: Long = -1,
        val rpcVersion: Int = 0,
        override val downloadSpeedLimit: Long = -1,
        override val uploadSpeedLimit: Long = -1,
        override val seedRatioLimitEnabled: Boolean = false,
        override val seedRatioLimit: Float = -1f
) : Session

