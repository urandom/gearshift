package org.sugr.gearshift.model

import java.io.File
import java.net.URI
import java.net.URISyntaxException
import kotlin.comparisons.compareValuesBy

data class Torrent(val hash: String, val id: Int, val name: String,
                   val statusType: StatusType = Torrent.StatusType.UNKNOWN,
                   val metaProgress: Float = 0f,
                   val downloadProgress: Float = 0f, val uploadProgress: Float = 0f,
                   val isDirectory: Boolean = false,
                   val statusText: String = "", val trafficText: String = "",
                   val error: String = "", val errorType: ErrorType = Torrent.ErrorType.UNKNOWN,
                   val downloadDir: String = "",
                   val validSize: Long = 0, val totalSize: Long = 0, val sizeLeft: Long = 0,
                   val seedRatioLimit: Float = 0f, val seedRatioMode: SeedRatioMode = Torrent.SeedRatioMode.UNKNOWN,
                   val downloaded: Long = 0, val uploaded: Long = 0,
                   val startTime: Long = 0, val activityTime: Long = 0, val addedTime: Long = 0,
                   val remainingTime: Long = 0, val createdTime: Long = 0,
                   val pieceSize: Long = 0, val pieceCount: Int = 0,
                   val isPrivate: Boolean = false,
                   val creator: String = "", val comment: String = "",
                   val files: List<TorrentFile> = emptyList(),
                   val trackers: List<TorrentTracker> = emptyList()) {

    fun merge(other: Torrent) : Torrent {
        val default = Torrent(hash = "", id = 0, name = "")

        return copy(
                hash = if (other.hash == default.hash) hash else other.hash,
                id = if (other.id == default.id) id else other.id,
                name = if (other.name == default.name) name else other.name,
                statusType = if (other.statusType == default.statusType) statusType else other.statusType,
                metaProgress = if (other.metaProgress == default.metaProgress) metaProgress else other.metaProgress,
                downloadProgress = if (other.downloadProgress == default.downloadProgress) downloadProgress else other.downloadProgress,
                uploadProgress = if (other.uploadProgress == default.uploadProgress) uploadProgress else other.uploadProgress,
                isDirectory = if (other.isDirectory == default.isDirectory) isDirectory else other.isDirectory,
                statusText = if (other.statusText == default.statusText) statusText else other.statusText,
                trafficText = if (other.trafficText == default.trafficText) trafficText else other.trafficText,
                error = if (other.error == default.error) error else other.error,
                errorType = if (other.errorType == default.errorType) errorType else other.errorType,
                downloadDir = if (other.downloadDir == default.downloadDir) downloadDir else other.downloadDir,
                validSize = if (other.validSize == default.validSize) validSize else other.validSize,
                totalSize = if (other.totalSize == default.totalSize) totalSize else other.totalSize,
                sizeLeft = if (other.sizeLeft == default.sizeLeft) sizeLeft else other.sizeLeft,
                seedRatioLimit = if (other.seedRatioLimit == default.seedRatioLimit) seedRatioLimit else other.seedRatioLimit,
                seedRatioMode = if (other.seedRatioMode == default.seedRatioMode) seedRatioMode else other.seedRatioMode,
                downloaded = if (other.downloaded == default.downloaded) downloaded else other.downloaded,
                uploaded = if (other.uploaded == default.uploaded) uploaded else other.uploaded,
                startTime = if (other.startTime == default.startTime) startTime else other.startTime,
                activityTime = if (other.activityTime == default.activityTime) activityTime else other.activityTime,
                addedTime = if (other.addedTime == default.addedTime) addedTime else other.addedTime,
                remainingTime = if (other.remainingTime == default.remainingTime) remainingTime else other.remainingTime,
                createdTime = if (other.createdTime == default.createdTime) createdTime else other.createdTime,
                pieceSize = if (other.pieceSize == default.pieceSize) pieceSize else other.pieceSize,
                pieceCount = if (other.pieceCount == default.pieceCount) pieceCount else other.pieceCount,
                isPrivate = if (other.isPrivate == default.isPrivate) isPrivate else other.isPrivate,
                creator = if (other.creator == default.creator) creator else other.creator,
                comment = if (other.comment == default.comment) comment else other.comment,
                files = if (other.files == default.files) files else other.files,
                trackers = if (other.trackers == default.trackers) trackers else other.trackers
        )
    }

    val hasError : Boolean
        get() = errorType != ErrorType.OK

    val isActive : Boolean
        get() = when (statusType) {
            StatusType.CHECKING, StatusType.DOWNLOADING, StatusType.SEEDING -> true
            else -> false
        }

    fun seedRatioLimit(session: Session = Session()) : Float {
        if (seedRatioMode == SeedRatioMode.NO_LIMIT || seedRatioMode == SeedRatioMode.UNKNOWN) {
            return 0f
        } else if (seedRatioMode == SeedRatioMode.GLOBAL_LIMIT) {
            return if (session.seedRatioLimitEnabled) session.seedRatioLimit else 0f
        } else {
            return seedRatioLimit
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

data class TorrentFile(val hash: String,
                       val path: String,
                       val bytes: Long, val total: Long,
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

data class TorrentTracker(val hash: String,
                          val id: Int,
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

