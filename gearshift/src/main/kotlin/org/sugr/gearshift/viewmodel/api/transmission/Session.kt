package org.sugr.gearshift.viewmodel.api.transmission

import com.google.gson.annotations.SerializedName
import org.sugr.gearshift.model.Session

data class TransmissionSession(
        @SerializedName("download-dir") val downloadDir: String = "",
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

