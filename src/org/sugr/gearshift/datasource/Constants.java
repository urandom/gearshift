package org.sugr.gearshift.datasource;

public final class Constants {
    public static final String T_TORRENTS = "torrents";
    public static final String T_TRACKERS = "trackers";
    public static final String T_FILES = "files";
    public static final String T_PEERS = "peers";

    public static final String COLUMN_PROFILE = "profile";
    public static final String COLUMN_TORRENT_ID = "torrent_id";
    public static final String COLUMN_STATUS = "status";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_ERROR = "error";
    public static final String COLUMN_ERROR_STRING = "error_string";
    public static final String COLUMN_METADATA_PERCENT_COMPLETE = "metadata_percent_complete";
    public static final String COLUMN_PERCENT_DONE = "percent_done";
    public static final String COLUMN_ETA = "eta";
    public static final String COLUMN_IS_FINISHED = "is_finished";
    public static final String COLUMN_IS_STALLED = "is_stalled";
    public static final String COLUMN_PEERS_CONNECTED = "peers_connected";
    public static final String COLUMN_PEERS_GETTING_FROM_US = "peers_getting_from_us";
    public static final String COLUMN_PEERS_SENDING_TO_US = "peers_sending_to_us";
    public static final String COLUMN_LEFT_UNTIL_DONE = "left_until_done";
    public static final String COLUMN_DESIRED_AVAILABLE = "desired_available";
    public static final String COLUMN_TOTAL_SIZE = "total_size";
    public static final String COLUMN_SIZE_WHEN_DONE = "size_when_done";
    public static final String COLUMN_RATE_DOWNLOAD = "rate_donwload";
    public static final String COLUMN_RATE_UPLOAD = "rate_upload";
    public static final String COLUMN_QUEUE_POSITION = "queue_position";
    public static final String COLUMN_RECHECK_PROGRESS = "recheck_progress";
    public static final String COLUMN_SEED_RATIO_MODE = "seed_ratio_mode";
    public static final String COLUMN_SEED_RATIO_LIMIT = "seed_ratio_limit";
    public static final String COLUMN_DOWNLOADED_EVER = "downloaded_ever";
    public static final String COLUMN_UPLOADED_EVER = "uploaded_ever";
    public static final String COLUMN_UPLOAD_RATIO = "upload_ratio";
    public static final String COLUMN_ADDED_DATE = "added_date";
    public static final String COLUMN_DONE_DATE = "done_date";
    public static final String COLUMN_START_DATE = "start_date";
    public static final String COLUMN_ACTIVITY_DATE = "activity_date";
    public static final String COLUMN_CORRUPT_EVER = "corrupt_ever";
    public static final String COLUMN_DOWNLOAD_DIR = "download_dir";
    public static final String COLUMN_HAVE_UNCHECKED = "have_unchecked";
    public static final String COLUMN_HAVE_VALID = "have_valid";
    public static final String COLUMN_COMMENT = "comment";
    public static final String COLUMN_CREATOR = "comment";
    public static final String COLUMN_DATE_CREATED = "date_created";
    public static final String COLUMN_HASH_STRING = "hash_string";
    public static final String COLUMN_IS_public = "is_public";
    public static final String COLUMN_PIECE_COUNT = "piece_count";
    public static final String COLUMN_PIECE_SIZE = "piece_size";
    public static final String COLUMN_TORRENT_PRIORITY = "torrent_priority";
    public static final String COLUMN_DOWNLOAD_LIMIT = "download_limit";
    public static final String COLUMN_DOWNLOAD_LIMITED = "download_limited";
    public static final String COLUMN_UPLOAD_LIMIT = "upload_limit";
    public static final String COLUMN_UPLOAD_LIMITED = "upload_limited";
    public static final String COLUMN_HONORS_SESSION_LIMITS = "honors_session_limits";
    public static final String COLUMN_WEBSEEDS_SENDING_TO_US = "webseeds_sending_to_us";
    public static final String COLUMN_PEER_LIMIT = "peer_limit";

    public static final String COLUMN_TRAFFIC_TEXT = "traffic_text";
    public static final String COLUMN_STATUS_TEXT = "status_text";

    public static final String COLUMN_TRACKER_ID = "tracker_id";
    public static final String COLUMN_ANNOUNCE = "announce";
    public static final String COLUMN_SCRAPE = "scrape";
    public static final String COLUMN_TIER = "tier";
    public static final String COLUMN_HAS_ANNOUNCED = "has_announced";
    public static final String COLUMN_LAST_ANNOUNCE_TIME = "last_announce_time";
    public static final String COLUMN_LAST_ANNOUNCE_SUCCEEDED = "last_announce_succeeded";
    public static final String COLUMN_LAST_ANNOUNCE_PEER_COUNT = "last_announce_peer_count";
    public static final String COLUMN_LAST_ANNOUNCE_RESULT = "last_announce_result";
    public static final String COLUMN_HAS_SCRAPED = "has_scraped";
    public static final String COLUMN_LAST_SCRAPE_TIME = "last_scrape_time";
    public static final String COLUMN_LAST_SCRAPE_SUCCEEDED = "last_scrape_succeeded";
    public static final String COLUMN_LAST_SCRAPE_RESULT = "last_scrape_result";
    public static final String COLUMN_SEEDER_COUNT = "seeder_count";
    public static final String COLUMN_LEECHER_COUNT = "leecher_count";

    public static final String COLUMN_LENGTH = "length";
    public static final String COLUMN_BYTES_COMPLETED = "bytes_completed";
    public static final String COLUMN_WANTED = "wanted";
    public static final String COLUMN_PRIORITY = "priority";

    public static final String COLUMN_ADDRESS = "address";
    public static final String COLUMN_CLIENT_NAME = "client_name";
    public static final String COLUMN_CLIENT_IS_CHOKED = "client_is_choked";
    public static final String COLUMN_CLIENT_IS_INTERESTED = "client_is_interested";
    public static final String COLUMN_IS_DOWNLOADING_FROM = "is_downloading_from";
    public static final String COLUMN_IS_UPLOADING_TO = "is_uploading_to";
    public static final String COLUMN_IS_ENCRYPTED = "is_encrypted";
    public static final String COLUMN_IS_INCOMING = "is_incoming";
    public static final String COLUMN_PEER_IS_CHOKED = "peer_is_choked";
    public static final String COLUMN_PEER_IS_INTERESTED = "peer_is_interested";
    public static final String COLUMN_PORT = "port";
    public static final String COLUMN_PROGRESS = "progress";
    public static final String COLUMN_RATE_TO_CLIENT = "rate_to_client";
    public static final String COLUMN_RATE_TO_PEER = "rate_to_peer";

    public static final String T_TORRENTS_CREATE = "CREATE TABLE "
        + T_TORRENTS + "("
        + COLUMN_PROFILE + " TEXT NOT NULL, "
        + COLUMN_TORRENT_ID + " INTEGER NOT NULL, "
        + COLUMN_STATUS + " INTEGER NOT NULL, "
        + COLUMN_NAME + " TEXT NOT NULL, "
        + COLUMN_ERROR + " INTEGER, "
        + COLUMN_ERROR_STRING + " TEXT, "
        + COLUMN_METADATA_PERCENT_COMPLETE + " REAL, "
        + COLUMN_PERCENT_DONE + " REAL, "
        + COLUMN_ETA + " INTEGER, "
        + COLUMN_IS_FINISHED + " INTEGER, "
        + COLUMN_IS_STALLED + " INTEGER, "
        + COLUMN_PEERS_CONNECTED + " INTEGER, "
        + COLUMN_PEERS_GETTING_FROM_US + " INTEGER, "
        + COLUMN_PEERS_SENDING_TO_US + " INTEGER, "
        + COLUMN_LEFT_UNTIL_DONE + " INTEGER, "
        + COLUMN_DESIRED_AVAILABLE + " INTEGER, "
        + COLUMN_TOTAL_SIZE + " INTEGER, "
        + COLUMN_SIZE_WHEN_DONE + " INTEGER, "
        + COLUMN_RATE_DOWNLOAD + " INTEGER, "
        + COLUMN_RATE_UPLOAD + " INTEGER, "
        + COLUMN_QUEUE_POSITION + " INTEGER, "
        + COLUMN_RECHECK_PROGRESS + " REAL, "
        + COLUMN_SEED_RATIO_MODE + " INTEGER, "
        + COLUMN_SEED_RATIO_LIMIT + " REAL, "
        + COLUMN_DOWNLOADED_EVER + " INTEGER, "
        + COLUMN_UPLOADED_EVER + " INTEGER, "
        + COLUMN_UPLOAD_RATIO + " REAL, "
        + COLUMN_ADDED_DATE + " INTEGER, "
        + COLUMN_DONE_DATE + " INTEGER, "
        + COLUMN_START_DATE + " INTEGER, "
        + COLUMN_ACTIVITY_DATE + " INTEGER, "
        + COLUMN_CORRUPT_EVER + " INTEGER, "
        + COLUMN_DOWNLOAD_DIR + " TEXT, "
        + COLUMN_HAVE_UNCHECKED + " INTEGER, "
        + COLUMN_HAVE_VALID + " INTEGER, "
        + COLUMN_COMMENT + " TEXT, "
        + COLUMN_CREATOR + " TEXT, "
        + COLUMN_DATE_CREATED + " INTEGER, "
        + COLUMN_HASH_STRING + " TEXT, "
        + COLUMN_IS_public + " INTEGER, "
        + COLUMN_PIECE_COUNT + " INTEGER, "
        + COLUMN_PIECE_SIZE + " INTEGER, "
        + COLUMN_TORRENT_PRIORITY + " INTEGER, "
        + COLUMN_DOWNLOAD_LIMIT + " INTEGER, "
        + COLUMN_DOWNLOAD_LIMITED + " INTEGER, "
        + COLUMN_UPLOAD_LIMIT + " INTEGER, "
        + COLUMN_UPLOAD_LIMITED + " INTEGER, "
        + COLUMN_HONORS_SESSION_LIMITS + " INTEGER, "
        + COLUMN_WEBSEEDS_SENDING_TO_US + " INTEGER, "
        + COLUMN_PEER_LIMIT + " INTEGER, "
        + COLUMN_TRAFFIC_TEXT + " TEXT, "
        + COLUMN_STATUS_TEXT + " TEXT, "

        + "PRIMARY KEY (" + COLUMN_PROFILE + ", " + COLUMN_TORRENT_ID + ")"
        + ");";

    public static final String T_TRACKERS_CREATE = "CREATE TABLE "
        + T_TORRENTS + "("
        + COLUMN_PROFILE + " TEXT NOT NULL, "
        + COLUMN_TORRENT_ID + " INTEGER NOT NULL, "
        + COLUMN_TRACKER_ID + " INTEGER, "
        + COLUMN_ANNOUNCE + " TEXT, "
        + COLUMN_SCRAPE + " TEXT, "
        + COLUMN_TIER + " INTEGER, "
        + COLUMN_HAS_ANNOUNCED + " INTEGER, "
        + COLUMN_LAST_ANNOUNCE_TIME + " INTEGER, "
        + COLUMN_LAST_ANNOUNCE_SUCCEEDED + " INTEGER, "
        + COLUMN_LAST_ANNOUNCE_PEER_COUNT + " INTEGER, "
        + COLUMN_LAST_ANNOUNCE_RESULT + " TEXT, "
        + COLUMN_HAS_SCRAPED + " INTEGER, "
        + COLUMN_LAST_SCRAPE_TIME + " INTEGER, "
        + COLUMN_LAST_SCRAPE_SUCCEEDED + " INTEGER, "
        + COLUMN_LAST_SCRAPE_RESULT + " TEXT, "
        + COLUMN_SEEDER_COUNT + " TEXT, "
        + COLUMN_LEECHER_COUNT + " TEXT, "

        + "PRIMARY KEY (" + COLUMN_PROFILE + ", " + COLUMN_TORRENT_ID + ")"
        + ");";

    public static final String T_FILES_CREATE = "CREATE TABLE "
        + T_FILES + "("
        + COLUMN_PROFILE + " TEXT NOT NULL, "
        + COLUMN_TORRENT_ID + " INTEGER NOT NULL, "
        + COLUMN_NAME + " TEXT, "
        + COLUMN_LENGTH + " INTEGER, "
        + COLUMN_BYTES_COMPLETED + " INTEGER, "
        + COLUMN_WANTED + " INTEGER, "
        + COLUMN_PRIORITY + " INTEGER, "

        + "PRIMARY KEY (" + COLUMN_PROFILE + ", " + COLUMN_TORRENT_ID + ")"
        + ");";

    public static final String T_PEERS_CREATE = "CREATE TABLE "
        + T_PEERS + "("
        + COLUMN_PROFILE + " TEXT NOT NULL, "
        + COLUMN_TORRENT_ID + " INTEGER NOT NULL, "
        + COLUMN_ADDRESS + " TEXT, "
        + COLUMN_CLIENT_NAME + " TEXT, "
        + COLUMN_CLIENT_IS_CHOKED + " INTEGER, "
        + COLUMN_CLIENT_IS_INTERESTED + " INTEGER, "
        + COLUMN_IS_DOWNLOADING_FROM + " INTEGER, "
        + COLUMN_IS_UPLOADING_TO + " INTEGER, "
        + COLUMN_IS_ENCRYPTED + " INTEGER, "
        + COLUMN_IS_INCOMING + " INTEGER, "
        + COLUMN_PEER_IS_CHOKED + " INTEGER, "
        + COLUMN_PEER_IS_INTERESTED + " INTEGER, "
        + COLUMN_PORT + " INTEGER, "
        + COLUMN_PROGRESS + " REAL, "
        + COLUMN_RATE_TO_CLIENT + " INTEGER, "
        + COLUMN_RATE_TO_PEER + " INTEGER, "

        + "PRIMARY KEY (" + COLUMN_PROFILE + ", " + COLUMN_TORRENT_ID + ")"
        + ");";

    public static final String I_TORRENTS_PROFILE_CREATE = "CREATE INDEX "
        + "torrents_idx_profile ON " + T_TORRENTS + "(" + COLUMN_PROFILE + ")";

    public static final String I_TRACKERS_PROFILE_CREATE = "CREATE INDEX "
        + "trackers_idx_profile ON " + T_TRACKERS + "(" + COLUMN_PROFILE + ")";

    public static final String I_FILES_PROFILE_CREATE = "CREATE INDEX "
        + "files_idx_profile ON " + T_FILES + "(" + COLUMN_PROFILE + ")";

    public static final String I_PEERS_PROFILE_CREATE = "CREATE INDEX "
        + "peers_idx_profile ON " + T_FILES + "(" + COLUMN_PROFILE + ")";
}
