package org.sugr.gearshift.datasource;

public final class Constants {
    public static final String T_SESSION = "session";
    public static final String T_TORRENT = "torrent";
    public static final String T_TORRENT_PROFILE = "torrent_profile";
    public static final String T_TRACKER = "tracker";
    public static final String T_TORRENT_TRACKER = "torrent_tracker";
    public static final String T_FILE = "file";
    public static final String T_PEER = "peer";

    public static final String C_ID = "_id";
    public static final String C_HASH_STRING = "hash_string";
    public static final String C_TORRENT_ID = "torrent_id";
    public static final String C_NAME = "name";
    public static final String C_VALUE_AFFINITY = "value_affinity";
    public static final String C_VALUE_INTEGER = "value_integer";
    public static final String C_VALUE_REAL = "value_real";
    public static final String C_VALUE_TEXT = "value_text";

    public static final String C_STATUS = "status";

    public static final String C_ERROR = "error";
    public static final String C_ERROR_STRING = "error_string";
    public static final String C_METADATA_PERCENT_COMPLETE = "metadata_percent_complete";
    public static final String C_PERCENT_DONE = "percent_done";
    public static final String C_ETA = "eta";
    public static final String C_IS_FINISHED = "is_finished";
    public static final String C_IS_STALLED = "is_stalled";
    public static final String C_PEERS_CONNECTED = "peers_connected";
    public static final String C_PEERS_GETTING_FROM_US = "peers_getting_from_us";
    public static final String C_PEERS_SENDING_TO_US = "peers_sending_to_us";
    public static final String C_LEFT_UNTIL_DONE = "left_until_done";
    public static final String C_DESIRED_AVAILABLE = "desired_available";
    public static final String C_TOTAL_SIZE = "total_size";
    public static final String C_SIZE_WHEN_DONE = "size_when_done";
    public static final String C_RATE_DOWNLOAD = "rate_donwload";
    public static final String C_RATE_UPLOAD = "rate_upload";
    public static final String C_QUEUE_POSITION = "queue_position";
    public static final String C_RECHECK_PROGRESS = "recheck_progress";
    public static final String C_SEED_RATIO_MODE = "seed_ratio_mode";
    public static final String C_SEED_RATIO_LIMIT = "seed_ratio_limit";
    public static final String C_DOWNLOADED_EVER = "downloaded_ever";
    public static final String C_UPLOADED_EVER = "uploaded_ever";
    public static final String C_UPLOAD_RATIO = "upload_ratio";
    public static final String C_ADDED_DATE = "added_date";
    public static final String C_DONE_DATE = "done_date";
    public static final String C_START_DATE = "start_date";
    public static final String C_ACTIVITY_DATE = "activity_date";
    public static final String C_CORRUPT_EVER = "corrupt_ever";
    public static final String C_DOWNLOAD_DIR = "download_dir";
    public static final String C_HAVE_UNCHECKED = "have_unchecked";
    public static final String C_HAVE_VALID = "have_valid";
    public static final String C_COMMENT = "comment";
    public static final String C_CREATOR = "creator";
    public static final String C_DATE_CREATED = "date_created";
    public static final String C_IS_PRIVATE = "is_private";
    public static final String C_PIECE_COUNT = "piece_count";
    public static final String C_PIECE_SIZE = "piece_size";
    public static final String C_TORRENT_PRIORITY = "torrent_priority";
    public static final String C_DOWNLOAD_LIMIT = "download_limit";
    public static final String C_DOWNLOAD_LIMITED = "download_limited";
    public static final String C_UPLOAD_LIMIT = "upload_limit";
    public static final String C_UPLOAD_LIMITED = "upload_limited";
    public static final String C_HONORS_SESSION_LIMITS = "honors_session_limits";
    public static final String C_WEBSEEDS_SENDING_TO_US = "webseeds_sending_to_us";
    public static final String C_PEER_LIMIT = "peer_limit";

    public static final String C_TRAFFIC_TEXT = "traffic_text";
    public static final String C_STATUS_TEXT = "status_text";

    public static final String C_PROFILE_ID = "profile_id";

    public static final String C_TRACKER_ID = "tracker_id";
    public static final String C_ANNOUNCE = "announce";
    public static final String C_SCRAPE = "scrape";
    public static final String C_TIER = "tier";
    public static final String C_HAS_ANNOUNCED = "has_announced";
    public static final String C_LAST_ANNOUNCE_TIME = "last_announce_time";
    public static final String C_LAST_ANNOUNCE_SUCCEEDED = "last_announce_succeeded";
    public static final String C_LAST_ANNOUNCE_PEER_COUNT = "last_announce_peer_count";
    public static final String C_LAST_ANNOUNCE_RESULT = "last_announce_result";
    public static final String C_HAS_SCRAPED = "has_scraped";
    public static final String C_LAST_SCRAPE_TIME = "last_scrape_time";
    public static final String C_LAST_SCRAPE_SUCCEEDED = "last_scrape_succeeded";
    public static final String C_LAST_SCRAPE_RESULT = "last_scrape_result";
    public static final String C_SEEDER_COUNT = "seeder_count";
    public static final String C_LEECHER_COUNT = "leecher_count";

    public static final String C_FILE_INDEX = "file_index";
    public static final String C_LENGTH = "length";
    public static final String C_BYTES_COMPLETED = "bytes_completed";
    public static final String C_WANTED = "wanted";
    public static final String C_PRIORITY = "priority";

    public static final String C_ADDRESS = "address";
    public static final String C_CLIENT_NAME = "client_name";
    public static final String C_CLIENT_IS_CHOKED = "client_is_choked";
    public static final String C_CLIENT_IS_INTERESTED = "client_is_interested";
    public static final String C_IS_DOWNLOADING_FROM = "is_downloading_from";
    public static final String C_IS_UPLOADING_TO = "is_uploading_to";
    public static final String C_IS_ENCRYPTED = "is_encrypted";
    public static final String C_IS_INCOMING = "is_incoming";
    public static final String C_PEER_IS_CHOKED = "peer_is_choked";
    public static final String C_PEER_IS_INTERESTED = "peer_is_interested";
    public static final String C_PORT = "port";
    public static final String C_PROGRESS = "progress";
    public static final String C_RATE_TO_CLIENT = "rate_to_client";
    public static final String C_RATE_TO_PEER = "rate_to_peer";

    public static final String T_SESSION_CREATE = "CREATE TABLE "
        + T_SESSION + "("
        + C_NAME + " TEXT, "
        + C_PROFILE_ID + " TEXT, "
        + C_VALUE_AFFINITY + " TEXT NOT NULL, "
        + C_VALUE_INTEGER + " INTEGER, "
        + C_VALUE_REAL + " REAL, "
        + C_VALUE_TEXT + " TEXT NOT NULL DEFAULT '', "

        + "PRIMARY KEY (" + C_NAME + ", " + C_PROFILE_ID + ")"
        + ");";

    public static final String T_TORRENT_CREATE = "CREATE TABLE "
        + T_TORRENT + "("
        + C_HASH_STRING + " TEXT PRIMARY KEY, "
        + C_TORRENT_ID + " INTEGER NOT NULL, "
        + C_STATUS + " INTEGER NOT NULL, "
        + C_NAME + " TEXT NOT NULL DEFAULT '', "
        + C_ERROR + " INTEGER, "
        + C_ERROR_STRING + " TEXT NOT NULL DEFAULT '', "
        + C_METADATA_PERCENT_COMPLETE + " REAL NOT NULL DEFAULT 0, "
        + C_PERCENT_DONE + " REAL NOT NULL DEFAULT 0, "
        + C_ETA + " INTEGER, "
        + C_IS_FINISHED + " INTEGER NOT NULL DEFAULT 0, "
        + C_IS_STALLED + " INTEGER NOT NULL DEFAULT 0, "
        + C_PEERS_CONNECTED + " INTEGER, "
        + C_PEERS_GETTING_FROM_US + " INTEGER, "
        + C_PEERS_SENDING_TO_US + " INTEGER, "
        + C_LEFT_UNTIL_DONE + " INTEGER, "
        + C_DESIRED_AVAILABLE + " INTEGER, "
        + C_TOTAL_SIZE + " INTEGER NOT NULL DEFAULT 0, "
        + C_SIZE_WHEN_DONE + " INTEGER, "
        + C_RATE_DOWNLOAD + " INTEGER, "
        + C_RATE_UPLOAD + " INTEGER, "
        + C_QUEUE_POSITION + " INTEGER, "
        + C_RECHECK_PROGRESS + " REAL, "
        + C_SEED_RATIO_MODE + " INTEGER, "
        + C_SEED_RATIO_LIMIT + " REAL, "
        + C_DOWNLOADED_EVER + " INTEGER, "
        + C_UPLOADED_EVER + " INTEGER, "
        + C_UPLOAD_RATIO + " REAL, "
        + C_ADDED_DATE + " INTEGER, "
        + C_DONE_DATE + " INTEGER, "
        + C_START_DATE + " INTEGER, "
        + C_ACTIVITY_DATE + " INTEGER, "
        + C_CORRUPT_EVER + " INTEGER, "
        + C_DOWNLOAD_DIR + " TEXT NOT NULL DEFAULT '', "
        + C_HAVE_UNCHECKED + " INTEGER, "
        + C_HAVE_VALID + " INTEGER, "
        + C_COMMENT + " TEXT NOT NULL DEFAULT '', "
        + C_CREATOR + " TEXT NOT NULL DEFAULT '', "
        + C_DATE_CREATED + " INTEGER, "
        + C_IS_PRIVATE + " INTEGER NOT NULL DEFAULT 0, "
        + C_PIECE_COUNT + " INTEGER NOT NULL DEFAULT 0, "
        + C_PIECE_SIZE + " INTEGER NOT NULL DEFAULT 0, "
        + C_TORRENT_PRIORITY + " INTEGER, "
        + C_DOWNLOAD_LIMIT + " INTEGER, "
        + C_DOWNLOAD_LIMITED + " INTEGER, "
        + C_UPLOAD_LIMIT + " INTEGER, "
        + C_UPLOAD_LIMITED + " INTEGER, "
        + C_HONORS_SESSION_LIMITS + " INTEGER, "
        + C_WEBSEEDS_SENDING_TO_US + " INTEGER, "
        + C_PEER_LIMIT + " INTEGER, "
        + C_TRAFFIC_TEXT + " TEXT NOT NULL DEFAULT '', "
        + C_STATUS_TEXT + " TEXT NOT NULL DEFAULT '' "

        + ");";


    public static final String T_TORRENT_PROFILE_CREATE = "CREATE TABLE "
        + T_TORRENT_PROFILE + "("
        + C_HASH_STRING + " TEXT REFERENCES " + T_TORRENT + "(" + C_HASH_STRING + ") ON DELETE CASCADE, "
        + C_PROFILE_ID + " TEXT, "

        + "PRIMARY KEY (" + C_HASH_STRING + ", " + C_PROFILE_ID + ")"
        + ");";

    /* The tracker is not associated with a torrent */
    public static final String T_TRACKER_CREATE = "CREATE TABLE "
        + T_TRACKER + "("
        + C_TRACKER_ID + " INTEGER, "
        + C_ANNOUNCE + " TEXT NOT NULL, "
        + C_SCRAPE + " TEXT NOT NULL DEFAULT '', "
        + C_TIER + " INTEGER, "
        + C_HAS_ANNOUNCED + " INTEGER NOT NULL DEFAULT 0, "
        + C_LAST_ANNOUNCE_TIME + " INTEGER, "
        + C_LAST_ANNOUNCE_SUCCEEDED + " INTEGER NOT NULL DEFAULT 0, "
        + C_LAST_ANNOUNCE_PEER_COUNT + " INTEGER, "
        + C_LAST_ANNOUNCE_RESULT + " TEXT NOT NULL DEFAULT '', "
        + C_HAS_SCRAPED + " INTEGER NOT NULL DEFAULT 0, "
        + C_LAST_SCRAPE_TIME + " INTEGER, "
        + C_LAST_SCRAPE_SUCCEEDED + " INTEGER NOT NULL DEFAULT 0, "
        + C_LAST_SCRAPE_RESULT + " TEXT NOT NULL DEFAULT '', "
        + C_SEEDER_COUNT + " TEXT NOT NULL DEFAULT '', "
        + C_LEECHER_COUNT + " TEXT NOT NULL DEFAULT '', "

        + "PRIMARY KEY (" + C_TRACKER_ID + ")"
        + ");";

    public static final String T_TORRENT_TRACKER_CREATE = "CREATE TABLE "
        + T_TORRENT_TRACKER + "("
        + C_HASH_STRING + " TEXT REFERENCES " + T_TORRENT + "(" + C_HASH_STRING + ") ON DELETE CASCADE, "
        + C_TRACKER_ID + " INTEGER REFERENCES " + T_TRACKER + "(" + C_TRACKER_ID + ") ON DELETE CASCADE, "

        + "PRIMARY KEY (" + C_HASH_STRING + ", " + C_TRACKER_ID + ")"
        + ");";

    public static final String T_FILE_CREATE = "CREATE TABLE "
        + T_FILE + "("
        + C_HASH_STRING + " TEXT REFERENCES " + T_TORRENT + "(" + C_HASH_STRING + ") ON DELETE CASCADE, "
        + C_FILE_INDEX + " INTEGER NOT NULL, "
        + C_NAME + " TEXT NOT NULL DEFAULT '', "
        + C_LENGTH + " INTEGER, "
        + C_BYTES_COMPLETED + " INTEGER, "
        + C_WANTED + " INTEGER, "
        + C_PRIORITY + " INTEGER, "

        + "PRIMARY KEY (" + C_HASH_STRING + ", " + C_FILE_INDEX + ")"
        + ");";

    public static final String T_PEER_CREATE = "CREATE TABLE "
        + T_PEER + "("
        + C_HASH_STRING + " TEXT REFERENCES " + T_TORRENT + "(" + C_HASH_STRING + ") ON DELETE CASCADE, "
        + C_ADDRESS + " TEXT NOT NULL DEFAULT '', "
        + C_CLIENT_NAME + " TEXT NOT NULL DEFAULT '', "
        + C_CLIENT_IS_CHOKED + " INTEGER, "
        + C_CLIENT_IS_INTERESTED + " INTEGER, "
        + C_IS_DOWNLOADING_FROM + " INTEGER NOT NULL DEFAULT 0, "
        + C_IS_UPLOADING_TO + " INTEGER NOT NULL DEFAULT 0, "
        + C_IS_ENCRYPTED + " INTEGER NOT NULL DEFAULT 0, "
        + C_IS_INCOMING + " INTEGER NOT NULL DEFAULT 0, "
        + C_PEER_IS_CHOKED + " INTEGER, "
        + C_PEER_IS_INTERESTED + " INTEGER, "
        + C_PORT + " INTEGER, "
        + C_PROGRESS + " REAL, "
        + C_RATE_TO_CLIENT + " INTEGER, "
        + C_RATE_TO_PEER + " INTEGER, "

        + "PRIMARY KEY (" + C_HASH_STRING + ")"
        + ");";

    public static final String TYPE_INT = "int";
    public static final String TYPE_BOOLEAN = "boolean";
    public static final String TYPE_LONG = "long";
    public static final String TYPE_FLOAT = "float";
    public static final String TYPE_STRING = "string";

    public static class ColumnGroups {
        public static final String[] TORRENT_OVERVIEW = {
            C_NAME, C_STATUS, C_METADATA_PERCENT_COMPLETE, C_PERCENT_DONE,
            C_UPLOAD_RATIO, C_SEED_RATIO_LIMIT, C_TRAFFIC_TEXT, C_STATUS_TEXT,
            C_ERROR, C_ERROR_STRING
        };

        public static final String[] TORRENT_DETAILS = {
            C_COMMENT, C_CREATOR, C_DATE_CREATED, C_IS_PRIVATE, C_PIECE_COUNT, C_PIECE_SIZE,
            C_ACTIVITY_DATE, C_TORRENT_PRIORITY, C_CORRUPT_EVER, C_DESIRED_AVAILABLE,
            C_DOWNLOADED_EVER, C_DOWNLOAD_LIMIT, C_DOWNLOAD_LIMITED,
            C_HAVE_UNCHECKED, C_HAVE_VALID, C_HONORS_SESSION_LIMITS,
            C_PEER_LIMIT, C_START_DATE, C_UPLOAD_LIMIT, C_UPLOAD_LIMITED,
            C_WEBSEEDS_SENDING_TO_US,

            C_ADDED_DATE, C_TOTAL_SIZE,
            C_ETA, C_IS_FINISHED, C_IS_STALLED,
            C_LEFT_UNTIL_DONE, C_PEERS_CONNECTED,
            C_PEERS_GETTING_FROM_US, C_PEERS_SENDING_TO_US,
            C_QUEUE_POSITION, C_RATE_DOWNLOAD, C_RATE_UPLOAD,
            C_RECHECK_PROGRESS, C_SEED_RATIO_MODE,
            C_SIZE_WHEN_DONE, C_UPLOADED_EVER, C_DOWNLOAD_DIR
        };

        public static final String[] TRACKER = {
            C_TRACKER_ID, C_ANNOUNCE, C_SCRAPE, C_TIER, C_HAS_ANNOUNCED,
            C_LAST_ANNOUNCE_TIME, C_LAST_ANNOUNCE_SUCCEEDED,
            C_LAST_ANNOUNCE_PEER_COUNT, C_LAST_ANNOUNCE_RESULT,
            C_HAS_SCRAPED, C_LAST_SCRAPE_TIME, C_LAST_SCRAPE_SUCCEEDED,
            C_LAST_SCRAPE_RESULT, C_SEEDER_COUNT, C_LEECHER_COUNT
        };

        public static final String[] FILE = {
            C_NAME, C_LENGTH, C_BYTES_COMPLETED, C_WANTED, C_PRIORITY
        };

        public static final String[] PEER = {
            C_ADDRESS, C_CLIENT_NAME, C_CLIENT_IS_CHOKED,
            C_CLIENT_IS_INTERESTED, C_IS_DOWNLOADING_FROM,
            C_IS_UPLOADING_TO, C_IS_ENCRYPTED, C_IS_INCOMING,
            C_PEER_IS_CHOKED, C_PEER_IS_INTERESTED, C_PORT,
            C_PROGRESS, C_RATE_TO_CLIENT, C_RATE_TO_PEER
        };
    }
}
