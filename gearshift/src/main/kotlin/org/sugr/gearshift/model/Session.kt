package org.sugr.gearshift.model

interface Session {
	val downloadSpeedLimit: Long
	val downloadSpeedLimitEnabled: Boolean
	val uploadSpeedLimit: Long
	val uploadSpeedLimitEnabled: Boolean
	val seedRatioLimitEnabled: Boolean
	val seedRatioLimit: Float
	val downloadDir: String
}

interface AltSpeedSession {
	val altSpeedLimitEnabled: Boolean
	val altDownloadSpeedLimit: Long
	val altUploadSpeedLimit: Long
}

interface


data class NoSession(
		override val downloadSpeedLimit: Long = -1,
		override val uploadSpeedLimit: Long = -1,
		override val seedRatioLimitEnabled: Boolean = false,
		override val seedRatioLimit: Float = -1f,
		override val downloadSpeedLimitEnabled: Boolean = false,
		override val uploadSpeedLimitEnabled: Boolean = false,
		override val downloadDir: String = ""
) : Session