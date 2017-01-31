package org.sugr.gearshift.model

import android.text.Spannable
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import kotlin.comparisons.compareValuesBy

data class Torrent(val hash: String, val id: Int, val name: Spannable,
				   val statusType: StatusType = Torrent.StatusType.UNKNOWN,
				   val isStalled: Boolean = false, val isFinished: Boolean = false,
				   val metaProgress: Float = 0f,
				   val downloadProgress: Float = 0f, val uploadProgress: Float = 0f,
				   val downloadRate : Long = 0, val uploadRate: Long = 0, val uploadRatio : Float = 0f,
				   val isDirectory: Boolean = false,
				   val statusText: String = "", val trafficText: String = "",
				   val error: String = "", val errorType: ErrorType = Torrent.ErrorType.UNKNOWN,
				   val downloadDir: String = "", val connectedPeers : Int = 0,
				   val queuePosition : Int = 0,
				   val validSize: Long = 0, val totalSize: Long = 0, val sizeLeft: Long = 0,
				   val seedRatioLimit: Float = 0f, val seedRatioMode: SeedRatioMode = Torrent.SeedRatioMode.UNKNOWN,
				   val downloaded: Long = 0, val uploaded: Long = 0,
				   val startTime: Long = 0, val activityTime: Long = 0, val addedTime: Long = 0,
				   val remainingTime: Long = 0, val createdTime: Long = 0,
				   val pieceSize: Long = 0, val pieceCount: Int = 0,
				   val isPrivate: Boolean = false,
				   val creator: String = "", val comment: String = "",
				   val files: Set<TorrentFile> = emptySet(),
				   val trackers: Set<TorrentTracker> = emptySet()) {

	val hasError : Boolean
		get() = errorType != ErrorType.OK

	val isActive : Boolean
		get() = !isStalled && !isFinished && when (statusType) {
			StatusType.CHECKING, StatusType.DOWNLOADING, StatusType.SEEDING -> true
			else -> false
		}

	fun seedRatioLimit(globalLimit: Float = 0f, isGlobalLimitEnabled : Boolean = false) : Float {
		if (seedRatioMode == SeedRatioMode.NO_LIMIT || seedRatioMode == SeedRatioMode.UNKNOWN) {
			return 0f
		} else if (seedRatioMode == SeedRatioMode.GLOBAL_LIMIT) {
			return if (isGlobalLimitEnabled) globalLimit else 0f
		} else {
			return seedRatioLimit
		}
	}

	fun statusSortWeight(globalLimit : Float = 0f) : Int {
		return when (statusType) {
			Torrent.StatusType.STOPPED -> {
				if (seedRatioMode == SeedRatioMode.NO_LIMIT || (
						seedRatioMode == SeedRatioMode.GLOBAL_LIMIT && uploadProgress < globalLimit) ||
						uploadProgress < seedRatioLimit) {
					40
				} else {
					50
				}
			}
			Torrent.StatusType.CHECK_WAITING -> 100
			Torrent.StatusType.CHECKING -> 1
			Torrent.StatusType.DOWNLOAD_WAITING -> 10
			Torrent.StatusType.DOWNLOADING -> 2
			Torrent.StatusType.SEED_WAITING -> 20
			Torrent.StatusType.SEEDING -> 3
			Torrent.StatusType.UNKNOWN -> -1
		}
	}

	enum class ErrorType {
		OK, TRACKER_WARNING, TRACKER_ERROR, LOCAL_ERROR, UNKNOWN
	}

	enum class StatusType {
		STOPPED, CHECK_WAITING, CHECKING, DOWNLOAD_WAITING, DOWNLOADING,
		SEED_WAITING, SEEDING, UNKNOWN
	}

	enum class SeedRatioMode {
		NO_LIMIT, LIMIT, GLOBAL_LIMIT, UNKNOWN
	}
}

data class TorrentFile(val path: String,
					   val downloaded: Long, val total: Long,
					   val priority: Int, val wanted: Boolean) : Comparable<TorrentFile> {

	val name: String
	val directory: String
	init {
		val f = File(path)
		val dir = f.parent ?: ""

		name = f.name
		directory = dir
	}

	override fun compareTo(other: TorrentFile) = compareValuesBy(this, other, { it.directory }, { it.name })
}

data class TorrentTracker(val id: Int,
						  val announce: String, val scrape: String, val tier: Int,
						  val seederCount: Int, val leecherCount: Int,
						  val hasAnnounced: Boolean, val lastAnnounceTime: Long,
						  val hasLastAnnounceSucceeded: Boolean, val lastAnnouncePeerCount: Int,
						  val lastAnnounceResult: String,
						  val hasScraped: Boolean, val lastScrapeTime: Long,
						  val hasLastScrapeSucceeded: Boolean, val lastScrapeResult: String) : Comparable<TorrentTracker> {
	val host : String
	init {
		host = try {
			URI(announce).host
		} catch (e: URISyntaxException) {
			""
		}
	}

	override fun compareTo(other: TorrentTracker) = compareValuesBy(this, other, { it.tier }, { it.host })

}

