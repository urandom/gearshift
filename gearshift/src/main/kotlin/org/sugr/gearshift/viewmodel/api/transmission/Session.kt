package org.sugr.gearshift.viewmodel.api.transmission

import com.google.gson.annotations.SerializedName
import org.sugr.gearshift.model.AltSpeedSession
import org.sugr.gearshift.model.Session

data class TransmissionSession(
		@SerializedName("rpc-version")
		val rpcVersion: Int = 0,

		@SerializedName("speed-limit-down")
		override val downloadSpeedLimit: Long = -1,
		@SerializedName("speed-limit-up")
		override val uploadSpeedLimit: Long = -1,
		@SerializedName("speed-limit-down-enabled")
		override val downloadSpeedLimitEnabled: Boolean = false,
		@SerializedName("speed-limit-up-enabled")
		override val uploadSpeedLimitEnabled: Boolean = false,
		@SerializedName("seedRatioLimited")
		override val seedRatioLimitEnabled: Boolean = false,
		@SerializedName("seedRatioLimit")
		override val seedRatioLimit: Float = -1f,
		@SerializedName("download-dir")
		override val downloadDir: String = "",

		@SerializedName("alt-speed-enabled")
		override val altSpeedLimitEnabled: Boolean = false,
		@SerializedName("alt-speed-down")
		override val altDownloadSpeedLimit: Long = -1,
		@SerializedName("alt-speed-up")
		override val altUploadSpeedLimit: Long = -1
) : Session, AltSpeedSession

