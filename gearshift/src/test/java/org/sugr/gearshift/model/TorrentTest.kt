package org.sugr.gearshift.model

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class TorrentTest {
    @Test
    fun merge() {
        val original = Torrent(hash = "hash", id = 1, name = "name", totalSize = 123)
        val merging = Torrent(hash = "hash", id = 1, name = "name", files = listOf(TorrentFile(hash = "hash", path = "path", bytes = 1, total = 3, priority = 1, wanted = true)))

        val merged = original.merge(merging)

        assertThat(original.hash, `is`(merged.hash))
        assertThat(original.id, `is`(merged.id))
        assertThat(original.name, `is`(merged.name))
        assertThat(original.totalSize, `is`(merged.totalSize))
        assertThat(merging.files, `is`(merged.files))
    }

    @Test
    fun hasError() {
        assertThat("OK is not an error", !Torrent(hash = "hash", id = 1, name = "name",
                errorType = Torrent.ErrorType.OK).hasError)
        assertThat("!OK is an error", Torrent(hash = "hash", id = 1, name = "name",
                errorType = Torrent.ErrorType.LOCAL_ERROR).hasError)
        assertThat("!OK is an error", Torrent(hash = "hash", id = 1, name = "name",
                errorType = Torrent.ErrorType.TRACKER_ERROR).hasError)
        assertThat("!OK is an error", Torrent(hash = "hash", id = 1, name = "name",
                errorType = Torrent.ErrorType.TRACKER_WARNING).hasError)
    }

    @Test
    fun isActive() {
        assertThat("STOPPED is not active", !Torrent(hash = "hash", id = 1, name = "name",
                statusType = Torrent.StatusType.STOPPED).isActive)
        assertThat("CHECK_WAITING is not active", !Torrent(hash = "hash", id = 1, name = "name",
                statusType = Torrent.StatusType.CHECK_WAITING).isActive)
        assertThat("CHECKING is active", Torrent(hash = "hash", id = 1, name = "name",
                statusType = Torrent.StatusType.CHECKING).isActive)
        assertThat("DOWNLOAD_WAITING is not active", !Torrent(hash = "hash", id = 1, name = "name",
                statusType = Torrent.StatusType.DOWNLOAD_WAITING).isActive)
        assertThat("DOWNLOADING is active", Torrent(hash = "hash", id = 1, name = "name",
                statusType = Torrent.StatusType.DOWNLOADING).isActive)
        assertThat("SEED_WAITING is not active", !Torrent(hash = "hash", id = 1, name = "name",
                statusType = Torrent.StatusType.SEED_WAITING).isActive)
        assertThat("SEEDING is active", Torrent(hash = "hash", id = 1, name = "name",
                statusType = Torrent.StatusType.SEEDING).isActive)
        assertThat("UNKNOWN is not active", !Torrent(hash = "hash", id = 1, name = "name",
                statusType = Torrent.StatusType.UNKNOWN).isActive)
    }

    @Test
    fun seedRatioLimit() {
        assertThat(0f, `is`(Torrent(hash = "hash", id = 1, name = "name", seedRatioLimit = 1.2f)
                .seedRatioLimit(Session())))
        assertThat(0f, `is`(Torrent(hash = "hash", id = 1, name = "name",
                seedRatioLimit = 1.2f, seedRatioMode = Torrent.SeedRatioMode.NO_LIMIT)
                .seedRatioLimit(Session())))
        assertThat(1.2f, `is`(Torrent(hash = "hash", id = 1, name = "name",
                seedRatioLimit = 1.2f, seedRatioMode = Torrent.SeedRatioMode.LIMIT)
                .seedRatioLimit(Session())))
        assertThat(0f, `is`(Torrent(hash = "hash", id = 1, name = "name",
                seedRatioLimit = 1.2f, seedRatioMode = Torrent.SeedRatioMode.GLOBAL_LIMIT)
                .seedRatioLimit(Session())))
        assertThat(0.5f, `is`(Torrent(hash = "hash", id = 1, name = "name",
                seedRatioLimit = 1.2f, seedRatioMode = Torrent.SeedRatioMode.GLOBAL_LIMIT)
                .seedRatioLimit(Session(seedRatioLimitEnabled = true, seedRatioLimit = 0.5f))))
    }

}