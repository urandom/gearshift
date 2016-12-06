package org.sugr.gearshift.model

import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class TorrentTest {
    @Test
    fun merge() {

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

    }

    @Test
    fun seedRatioLimit() {

    }

}