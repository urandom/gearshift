package org.sugr.gearshift.model

interface Session {
	var downloadSpeedLimit: Long
	var downloadSpeedLimitEnabled: Boolean
	var uploadSpeedLimit: Long
	var uploadSpeedLimitEnabled: Boolean
	var seedRatioLimitEnabled: Boolean
	var seedRatioLimit: Float
	var downloadDir: String
}

interface AltSpeedSession {
	var altSpeedLimitEnabled: Boolean
	var altDownloadSpeedLimit: Long
	var altUploadSpeedLimit: Long
}

interface


data class NoSession(
		override var downloadSpeedLimit: Long = -1,
		override var uploadSpeedLimit: Long = -1,
		override var seedRatioLimitEnabled: Boolean = false,
		override var seedRatioLimit: Float = -1f,
		override var downloadSpeedLimitEnabled: Boolean = false,
		override var uploadSpeedLimitEnabled: Boolean = false,
		override var downloadDir: String = ""
) : Session