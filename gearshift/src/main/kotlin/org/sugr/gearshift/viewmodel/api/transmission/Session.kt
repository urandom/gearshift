package org.sugr.gearshift.viewmodel.api.transmission

import com.google.gson.annotations.SerializedName
import org.sugr.gearshift.model.AltSpeedSession
import org.sugr.gearshift.model.Session

data class TransmissionSession(
		@SerializedName("rpc-version")
		val rpcVersion: Int = 0,

		@SerializedName("speed-limit-down")
		override var downloadSpeedLimit: Long = -1,
		@SerializedName("speed-limit-up")
		override var uploadSpeedLimit: Long = -1,
		@SerializedName("speed-limit-down-enabled")
		override var downloadSpeedLimitEnabled: Boolean = false,
		@SerializedName("speed-limit-up-enabled")
		override var uploadSpeedLimitEnabled: Boolean = false,
		@SerializedName("seedRatioLimited")
		override var seedRatioLimitEnabled: Boolean = false,
		@SerializedName("seedRatioLimit")
		override var seedRatioLimit: Float = -1f,
		@SerializedName("download-dir")
		override var downloadDir: String = "",

		@SerializedName("alt-speed-enabled")
		override var altSpeedLimitEnabled: Boolean = false,
		@SerializedName("alt-speed-down")
		override var altDownloadSpeedLimit: Long = -1,
		@SerializedName("alt-speed-up")
		override var altUploadSpeedLimit: Long = -1
) : Session, AltSpeedSession

