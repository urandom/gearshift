package org.sugr.gearshift.model

import org.sugr.gearshift.R
import org.sugr.gearshift.app
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import kotlin.comparisons.compareValuesBy

data class Torrent(val hash: String, val id: Int, val name: String,
                   val statusType: StatusType = Torrent.StatusType.STOPPED,
                   val metaProgress: Float = 0f,
                   val downloadProgress: Float = 0f, val uploadProgress: Float = 0f,
                   val isDirectory: Boolean = false,
                   val statusText: String = "", val trafficText: String = "",
                   val error: String = "", val errorType: ErrorType = ErrorType.OK,
                   val downloadDir: String = "",
                   val validSize: Long = 0, val totalSize: Long = 0, val sizeLeft: Long = 0,
                   val downloaded: Long = 0, val uploaded: Long = 0,
                   val startTime: Long = 0, val activityTime: Long = 0, val addedTime: Long = 0,
                   val remainingTime: Long = 0, val createdTime: Long = 0,
                   val pieceSize: Long = 0, val pieceCount: Int = 0,
                   val isPrivate: Boolean = false,
                   val creator: String = "", val comment: String = "",
                   val files: List<TorrentFile> = emptyList(),
                   val trackers: List<TorrentTracker> = emptyList()) {

    fun hasError() = errorType != ErrorType.OK
    fun isActive() = when (statusType) {
        StatusType.CHECKING, StatusType.DOWNLOADING, StatusType.SEEDING -> true
        else -> false
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
            app().getString(R.string.tracker_unknown_host)
        }
    }

    override fun compareTo(other: TorrentTracker) = compareValuesBy(this, other, { it.tier }, { it.host })

}

