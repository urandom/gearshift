package org.sugr.gearshift.core;

import android.database.Cursor;

import org.sugr.gearshift.datasource.Constants;

public class Torrent {
    private Torrent() {}

    public static final class SetterFields {
        public static final String DOWNLOAD_LIMIT = "downloadLimit";
        public static final String DOWNLOAD_LIMITED = "downloadLimited";
        public static final String PEER_LIMIT = "peer-limit";
        public static final String QUEUE_POSITION = "queuePosition";
        public static final String SEED_RATIO_LIMIT = "seedRatioLimit";
        public static final String SEED_RATIO_MODE = "seedRatioMode";
        public static final String SESSION_LIMITS = "honorsSessionLimits";
        public static final String TORRENT_PRIORITY = "bandwidthPriority";
        public static final String UPLOAD_LIMIT = "uploadLimit";
        public static final String UPLOAD_LIMITED = "uploadLimited";

        public static final String FILES_WANTED = "files-wanted";
        public static final String FILES_UNWANTED = "files-unwanted";
        public static final String FILES_HIGH = "priority-high";
        public static final String FILES_NORMAL = "priority-normal";
        public static final String FILES_LOW = "priority-low";

        public static final String TRACKER_ADD = "trackerAdd";
        public static final String TRACKER_REMOVE = "trackerRemove";
        public static final String TRACKER_REPLACE = "trackerReplace";

    }

    public static final class AddFields {
        public static final String URI = "filename";
        public static final String META = "metainfo";
        public static final String LOCATION = "download-dir";
        public static final String PAUSED = "paused";
    }

    // https://github.com/killemov/Shift/blob/master/shift.js#L864
    public static class Status {
        public final static int ALL = -1;
        public final static int STOPPED = 0;
        public final static int CHECK_WAITING = 1;
        public final static int CHECKING = 2;
        public final static int DOWNLOAD_WAITING = 3;
        public final static int DOWNLOADING = 4;
        public final static int SEED_WAITING = 5;
        public final static int SEEDING = 6;
    }

    public static class OldStatus {
        public final static int CHECK_WAITING = 1;
        public final static int CHECKING = 2;
        public final static int DOWNLOADING = 4;
        public final static int SEEDING = 8;
        public final static int STOPPED = 16;
    }

    // http://packages.python.org/transmissionrpc/reference/transmissionrpc.html
    public static class SeedRatioMode {
        public final static int GLOBAL_LIMIT = 0;
        public final static int TORRENT_LIMIT = 1;
        public final static int NO_LIMIT = 2;
    }

    public static class Error {
        public static final int OK = 0;
        public static final int TRACKER_WARNING = 1;
        public static final int TRACKER_ERROR = 2;
        public static final int LOCAL_ERROR = 3;
    }

    public static class Priority {
        public static final int LOW = -1;
        public static final int NORMAL = 0;
        public static final int HIGH = 1;
    }

    public static class Fields {
        public static final String hashString = "hashString";

        /*
         * commonly used fields which only need to be loaded once, either on
         * startup or when a magnet finishes downloading its metadata
         * */
        public static final String[] METADATA = { "addedDate", "name", "totalSize", };

        // commonly used fields which need to be periodically refreshed
        public static final String[] STATS = {
            hashString, "id", "error", "errorString", "eta", "isFinished", "isStalled",
            "leftUntilDone", "metadataPercentComplete", "peersConnected",
            "peersGettingFromUs", "peersSendingToUs", "percentDone",
            SetterFields.QUEUE_POSITION, "rateDownload", "rateUpload",
            "recheckProgress", SetterFields.SEED_RATIO_MODE, SetterFields.SEED_RATIO_LIMIT,
            "sizeWhenDone", "status", "trackers", "uploadedEver",
            "uploadRatio", "downloadDir"
        };

        // fields used by the inspector which only need to be loaded once
        public static final String[] INFO_EXTRA = {
            "comment", "creator", "dateCreated", "files",
            "isPrivate", "pieceCount", "pieceSize"
        };

        // fields used in the inspector which need to be periodically refreshed
        public static final String[] STATS_EXTRA = {
            "activityDate", SetterFields.TORRENT_PRIORITY, "corruptEver",
            "desiredAvailable", "downloadedEver", SetterFields.DOWNLOAD_LIMIT,
            SetterFields.DOWNLOAD_LIMITED, "fileStats", "haveUnchecked",
            "haveValid", SetterFields.SESSION_LIMITS, SetterFields.PEER_LIMIT, "peers",
            "startDate", "trackerStats", SetterFields.UPLOAD_LIMIT,
            SetterFields.UPLOAD_LIMITED, "webseedsSendingToUs"
        };
    }

    public static class Tracker {
        public static int getId(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_TRACKER_ID));
        }

        public static String getAnnounce(Cursor cursor) {
            return cursor.getString(cursor.getColumnIndex(Constants.C_ANNOUNCE));
        }

        public static String getScrape(Cursor cursor) {
            return cursor.getString(cursor.getColumnIndex(Constants.C_SCRAPE));
        }

        public static int getTier(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_TIER));
        }

        public static boolean hasAnnounced(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_HAS_ANNOUNCED)) > 0;
        }
        public static long getLastAnnounceTime(Cursor cursor) {
            return cursor.getLong(cursor.getColumnIndex(Constants.C_LAST_ANNOUNCE_TIME));
        }

        public static boolean hasLastAnnounceSucceeded(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_LAST_ANNOUNCE_SUCCEEDED)) > 0;
        }

        public static int getLastAnnouncePeerCount(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_LAST_ANNOUNCE_PEER_COUNT));
        }

        public static String getLastAnnounceResult(Cursor cursor) {
            return cursor.getString(cursor.getColumnIndex(Constants.C_LAST_ANNOUNCE_RESULT));
        }

        public static boolean hasScraped(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_HAS_SCRAPED)) > 0;
        }

        public static long getLastScrapeTime(Cursor cursor) {
            return cursor.getLong(cursor.getColumnIndex(Constants.C_LAST_SCRAPE_TIME));
        }

        public static boolean hasLastScrapeSucceeded(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_LAST_SCRAPE_SUCCEEDED)) > 0;
        }

        public static String getLastScrapeResult(Cursor cursor) {
            return cursor.getString(cursor.getColumnIndex(Constants.C_LAST_SCRAPE_RESULT));
        }

        public static int getSeederCount(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_SEEDER_COUNT));
        }

        public static int getLeecherCount(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_LEECHER_COUNT));
        }
    }

    public static class File {
        public static long getBytesCompleted(Cursor cursor) {
            return cursor.getLong(cursor.getColumnIndex(Constants.C_BYTES_COMPLETED));
        }

        public static long getLength(Cursor cursor) {
            return cursor.getLong(cursor.getColumnIndex(Constants.C_LENGTH));
        }

        public static String getName(Cursor cursor) {
            return cursor.getString(cursor.getColumnIndex(Constants.C_NAME));
        }

        public static int getPriority(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_PRIORITY));
        }

        public static boolean isWanted(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_WANTED)) > 0;
        }

        public static int getIndex(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_FILE_INDEX));
        }
    }

    /* CURRENTLY UNUSED
    public static class Peer {
        public static String getAddress(Cursor cursor) {
            return cursor.getString(cursor.getColumnIndex(Constants.C_ADDRESS));
        }
        public static String getClientName(Cursor cursor) {
            return cursor.getString(cursor.getColumnIndex(Constants.C_CLIENT_NAME));
        }
        public static boolean isClientChoked(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_CLIENT_IS_CHOKED)) > 0;
        }
        public static boolean isClientInterested(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_CLIENT_IS_INTERESTED)) > 0;
        }
        public static boolean isDownloadingFrom(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_IS_DOWNLOADING_FROM)) > 0;
        }
        public static boolean isEncrypted(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_IS_ENCRYPTED)) > 0;
        }
        public static boolean isIncoming(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_IS_INCOMING)) > 0;
        }
        public static boolean isUploadingTo(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_IS_UPLOADING_TO)) > 0;
        }
        public static boolean isPeerChoked(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_PEER_IS_CHOKED)) > 0;
        }
        public static boolean isPeerInterested(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_PEER_IS_INTERESTED)) > 0;
        }
        public static int getPort(Cursor cursor) {
            return cursor.getInt(cursor.getColumnIndex(Constants.C_PORT));
        }
        public static float getProgress(Cursor cursor) {
            return cursor.getFloat(cursor.getColumnIndex(Constants.C_PROGRESS));
        }
        public static long getRateToClient(Cursor cursor) {
            return cursor.getLong(cursor.getColumnIndex(Constants.C_RATE_TO_CLIENT));
        }
        public static long getRateToPeer(Cursor cursor) {
            return cursor.getLong(cursor.getColumnIndex(Constants.C_RATE_TO_PEER));
        }
    }
    */

    /* This is the rowid in the database */
    public static int getId(Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndex(Constants.C_ID));
    }

    public static String getHashString(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(Constants.C_HASH_STRING));
    }

    public static String getName(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(Constants.C_NAME));
    }

    public static int getError(Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndex(Constants.C_ERROR));
    }

    public static String getErrorString(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(Constants.C_ERROR_STRING));
    }

    public static int getStatus(Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndex(Constants.C_STATUS));
    }

    public static float getMetadataPercentDone(Cursor cursor) {
        return cursor.getFloat(cursor.getColumnIndex(Constants.C_METADATA_PERCENT_COMPLETE));
    }

    public static float getPercentDone(Cursor cursor) {
        return cursor.getFloat(cursor.getColumnIndex(Constants.C_PERCENT_DONE));
    }

    public static float getSeedRatioLimit(Cursor cursor) {
        return cursor.getFloat(cursor.getColumnIndex(Constants.C_SEED_RATIO_LIMIT));
    }

    public static float getUploadRatio(Cursor cursor) {
        return cursor.getFloat(cursor.getColumnIndex(Constants.C_UPLOAD_RATIO));
    }

    public static String getTrafficText(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(Constants.C_TRAFFIC_TEXT));
    }

    public static String getStatusText(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(Constants.C_STATUS_TEXT));
    }

    public static long getHaveValid(Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndex(Constants.C_HAVE_VALID));
    }

    public static long getSizeWhenDone(Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndex(Constants.C_SIZE_WHEN_DONE));
    }

    public static long getLeftUntilDone(Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndex(Constants.C_LEFT_UNTIL_DONE));
    }

    public static long getDownloadedEver(Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndex(Constants.C_DOWNLOADED_EVER));
    }

    public static long getUploadedEver(Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndex(Constants.C_UPLOADED_EVER));
    }

    public static long getStartDate(Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndex(Constants.C_START_DATE));
    }

    public static long getActivityDate(Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndex(Constants.C_ACTIVITY_DATE));
    }

    public static long getAddedDate(Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndex(Constants.C_ADDED_DATE));
    }

    public static long getEta(Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndex(Constants.C_ETA));
    }

    public static long getTotalSize(Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndex(Constants.C_TOTAL_SIZE));
    }

    public static long getPieceSize(Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndex(Constants.C_PIECE_SIZE));
    }

    public static int getPieceCount(Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndex(Constants.C_PIECE_COUNT));
    }

    public static boolean isPrivate(Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndex(Constants.C_IS_PRIVATE)) > 0;
    }

    public static long getDateCreated(Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndex(Constants.C_DATE_CREATED));
    }

    public static String getCreator(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(Constants.C_CREATOR));
    }

    public static String getComment(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(Constants.C_COMMENT));
    }

    public static int getQueuePosition(Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndex(Constants.C_QUEUE_POSITION));
    }

    public static String getDownloadDir(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(Constants.C_DOWNLOAD_DIR));
    }

    public static boolean areSessionLimitsHonored(Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndex(Constants.C_HONORS_SESSION_LIMITS)) > 0;
    }

    public static int getTorrentPriority(Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndex(Constants.C_TORRENT_PRIORITY));
    }

    public static boolean isDownloadLimited(Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndex(Constants.C_DOWNLOAD_LIMITED)) > 0;
    }

    public static boolean isUploadLimited(Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndex(Constants.C_UPLOAD_LIMITED)) > 0;
    }

    public static long getDownloadLimit(Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndex(Constants.C_DOWNLOAD_LIMIT));
    }

    public static long getUploadLimit(Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndex(Constants.C_UPLOAD_LIMIT));
    }

    public static int getSeedRatioMode(Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndex(Constants.C_SEED_RATIO_MODE));
    }

    public static int getPeerLimit(Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndex(Constants.C_PEER_LIMIT));
    }

    public static boolean isActive(int status) {
        switch(status) {
            case Status.CHECKING:
            case Status.DOWNLOADING:
            case Status.SEEDING:
                return true;
            default:
                return false;
        }
    }
}
