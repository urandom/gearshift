package org.sugr.gearshift.model

import org.sugr.gearshift.R
import org.sugr.gearshift.app
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import kotlin.comparisons.compareValuesBy

data class Torrent(val id: Int, val name: String,
                   val status: String = "", val statusType: StatusType = Torrent.StatusType.STOPPED,
                   val metaProcess: Float = 0f,
                   val downloadProgress: Float = 0f, val uploadProgress: Float = 0f,
                   val isDirectory: Boolean = false,
                   val trafficText: String = "",
                   val error: String = "", val errorType: ErrorType = ErrorType.OK) {
    fun hasError() = errorType == ErrorType.OK
    fun isActive() = when (statusType) {
        StatusType.CHECKING, StatusType.DOWNLOADING, StatusType.SEEDING -> true
        else -> false
    }

    enum class ErrorType(val type: Int) {
        OK(0),
        TRACKER_WARNING(1),
        TRACKER_ERROR(2),
        LOCAL_ERROR(3)
    }

    enum class StatusType(val type: Int) {
        STOPPED(0),
        CHECK_WAITING(1),
        CHECKING(2),
        DOWNLOAD_WAITING(3),
        DOWNLOADING(4),
        SEED_WAITING(5),
        SEEDING(6)
    }
}

data class TorrentDetails(val validSize: Long, val totalSize: Long, val sizeLeft: Long,
                          val downloaded: Long, val uploaded: Long,
                          val startTime: Long, val activityTime: Long, val addedTime: Long,
                          val remainingTime: Long, val createdTime: Long,
                          val pieceSize: Long, val pieceCount: Int,
                          val hash: String, val isPrivate: Boolean,
                          val creator: String, val comment: String,
                          val downloadDir: String)

data class TorrentFile(val path: String,
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
        try {
            val uri = URI(announce)
            host = uri.host
        } catch (e: URISyntaxException) {
            host = app().getString(R.string.tracker_unknown_host)
        }
    }

    override fun compareTo(other: TorrentTracker) = compareValuesBy(this, other, { it.tier }, { it.host })

}

