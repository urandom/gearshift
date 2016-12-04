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

    fun merge(other: Torrent) = copy(
            hash = if (other.hash == "") hash else other.hash,
            id = if (other.id == 0) id else other.id,
            name = if (other.name == "") name else other.name,
            statusType = if (other.statusType == StatusType.UNKNOWN) statusType else other.statusType,
            metaProgress = if (other.metaProgress == 0f) metaProgress else other.metaProgress,
            downloadProgress = if (other.downloadProgress == 0f) downloadProgress else other.downloadProgress,
            uploadProgress = if (other.uploadProgress == 0f) uploadProgress else other.uploadProgress,
            isDirectory = if (!other.isDirectory) isDirectory else other.isDirectory,
            statusText = if (other.statusText == "") statusText else other.statusText,
            trafficText = if (other.trafficText == "") trafficText else other.trafficText,
            error = if (other.error == "") error else other.error,
            errorType = if (other.errorType == ErrorType.UNKNOWN) errorType else other.errorType,
            downloadDir = if (other.downloadDir == "") downloadDir else other.downloadDir,
            validSize = if (other.validSize == 0L) validSize else other.validSize,
            totalSize = if (other.totalSize == 0L) totalSize else other.totalSize,
            sizeLeft = if (other.sizeLeft == 0L) sizeLeft else other.sizeLeft,
            seedRatioLimit = if (other.seedRatioLimit == 0f) seedRatioLimit else other.seedRatioLimit,
            seedRatioMode = if (other.seedRatioMode == SeedRatioMode.UNKNOWN) seedRatioMode else other.seedRatioMode,
            downloaded = if (other.downloaded == 0L) downloaded else other.downloaded,
            uploaded = if (other.uploaded == 0L) uploaded else other.uploaded,
            startTime = if (other.startTime == 0L) startTime else other.startTime,
            activityTime = if (other.activityTime == 0L) activityTime else other.activityTime,
            addedTime = if (other.addedTime == 0L) addedTime else other.addedTime,
            remainingTime = if (other.remainingTime == 0L) remainingTime else other.remainingTime,
            createdTime = if (other.createdTime == 0L) createdTime else other.createdTime,
            pieceSize = if (other.pieceSize == 0L) pieceSize else other.pieceSize,
            pieceCount = if (other.pieceCount == 0) pieceCount else other.pieceCount,
            isPrivate = if (!other.isPrivate) isPrivate else other.isPrivate,
            creator = if (other.creator == "") creator else other.creator,
            comment = if (other.comment == "") comment else other.comment,
            files = if (other.files.isEmpty()) files else other.files,
            trackers = if (other.trackers.isEmpty()) trackers else other.trackers
    )


    fun hasError() = errorType != ErrorType.OK
    fun isActive() = when (statusType) {
        StatusType.CHECKING, StatusType.DOWNLOADING, StatusType.SEEDING -> true
        else -> false
    }

    fun seedRatioLimit(session: Session = Session()) : Float {
        if (seedRatioMode == SeedRatioMode.NO_LIMIT) {
            return 0f
        } else if (seedRatioMode == SeedRatioMode.GLOBAL_LIMIT) {
            return if (session.seedRatioLimitEnabled) session.seedRatioLimit else 0f
        } else {
            return seedRatioLimit
        }
    }

    enum class ErrorType(val value: Int) {
        OK(0),
        TRACKER_WARNING(1),
        TRACKER_ERROR(2),
        LOCAL_ERROR(3),
        UNKNOWN(-1)
    }

    enum class StatusType(val value: Int) {
        STOPPED(0),
        CHECK_WAITING(1),
        CHECKING(2),
        DOWNLOAD_WAITING(3),
        DOWNLOADING(4),
        SEED_WAITING(5),
        SEEDING(6),
        UNKNOWN(-1);
    }

    enum class SeedRatioMode {
        NO_LIMIT, LIMIT, GLOBAL_LIMIT, UNKNOWN
    }

    companion object {
        fun statusOf(v: Int) =
                StatusType.values().filter { it.value == v }.firstOrNull() ?: StatusType.UNKNOWN

        fun errorOf(v: Int) =
                ErrorType.values().filter { it.value == v }.firstOrNull() ?: ErrorType.UNKNOWN
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

