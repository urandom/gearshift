package org.sugr.gearshift.datasource;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.SparseArray;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;
import org.sugr.gearshift.Torrent;
import org.sugr.gearshift.TransmissionSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class TorrentValues {
    public ContentValues torrent;
    public List<ContentValues> files;
    public List<ContentValues> trackers;
    public List<ContentValues> peers;
}

public class DataSource {
    private SQLiteDatabase database;

    private SQLiteHelper dbHelper;
    private Context context;

    /* Transmission stuff */
    private static final int NEW_STATUS_RPC_VERSION = 14;

    private int rpcVersion = -1;

    public DataSource(Context context) {
        this.context = context;
        this.dbHelper = new SQLiteHelper(context);

        rpcVersion = -1;
    }

    public void open() {
        database = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
        database = null;
    }

    public boolean isOpen() {
        return database != null && database.isOpen();
    }

    public void setRPCVersion(int version) {
        rpcVersion = version;
    }

    public void updateSession(JsonParser parser) throws IOException {
        database.beginTransaction();

        try {
            List<ContentValues> session = jsonToSessionValues(parser);

            for (ContentValues item : session) {
                database.insertWithOnConflict(Constants.T_SESSION, null,
                    item, SQLiteDatabase.CONFLICT_REPLACE);
            }

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public void updateTorrents(JsonParser parser) throws IOException {
        database.beginTransaction();

        try {
            SparseArray<Boolean> trackers = new SparseArray<Boolean>();

            while (parser.nextToken() != JsonToken.END_ARRAY) {
                TorrentValues values = jsonToTorrentValues(parser);

                int torrentId = (Integer) values.torrent.get(Constants.C_TORRENT_ID);

                database.insertWithOnConflict(Constants.T_TORRENT, null,
                    values.torrent, SQLiteDatabase.CONFLICT_REPLACE);

                if (values.trackers != null) {
                    for (ContentValues tracker : values.trackers) {
                        int trackerId = (Integer) tracker.get(Constants.C_TRACKER_ID);
                        if (!trackers.get(trackerId, false)) {
                            trackers.put(trackerId, true);

                            database.insertWithOnConflict(Constants.T_TRACKER, null,
                                tracker, SQLiteDatabase.CONFLICT_REPLACE);
                        }

                        ContentValues m2m = new ContentValues();
                        m2m.put(Constants.C_TORRENT_ID, torrentId);
                        m2m.put(Constants.C_TRACKER_ID, trackerId);
                        database.insertWithOnConflict(Constants.T_TORRENT_TRACKER, null,
                            m2m, SQLiteDatabase.CONFLICT_REPLACE);
                    }
                }

                if (values.files != null) {
                    for (ContentValues file : values.files) {
                        file.put(Constants.C_TORRENT_ID, torrentId);
                        database.insertWithOnConflict(Constants.T_FILE, null,
                            file, SQLiteDatabase.CONFLICT_REPLACE);
                    }
                }

                if (values.peers != null) {
                    for (ContentValues peer : values.peers) {
                        peer.put(Constants.C_TORRENT_ID, torrentId);
                        database.insertWithOnConflict(Constants.T_PEER, null,
                            peer, SQLiteDatabase.CONFLICT_REPLACE);
                    }
                }
            }

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    /* Transmission implementation */
    public TransmissionSession getSession() {
        Cursor cursor = database.query(Constants.T_SESSION, new String[] {
            Constants.C_NAME, Constants.C_VALUE_INTEGER,
            Constants.C_VALUE_REAL, Constants.C_VALUE_TEXT
        }, null, null, null, null, null);

        return cursorToSession(cursor);
    }

    public List<Torrent> getTorrents(String selection, String[] selectionArgs,
                                     String orderBy, boolean details) {
        List<Torrent> torrents = new ArrayList<Torrent>();
        SparseArray<Torrent> torrentMap = new SparseArray<Torrent>();

        String[] columns = Constants.ColumnGroups.TORRENT_OVERVIEW;
        if (details) {
            columns = G.concat(columns, Constants.ColumnGroups.TORRENT_DETAILS);
        }

        Cursor cursor = database.query(Constants.T_TORRENT, columns,
            selection, selectionArgs, null, null, orderBy);

        cursor.moveToFirst();

        while (!cursor.isAfterLast()) {
            Torrent torrent = cursorToTorrent(cursor);

            torrents.add(torrent);
            torrentMap.put(torrent.getId(), torrent);

            cursor.moveToNext();
        }

        cursor.close();

        String select;
        if (details) {
            select = Constants.T_TORRENT_TRACKER + "." + Constants.C_TORRENT_ID + ", "
                + Constants.T_TRACKER + "." + Constants.C_TRACKER_ID + ", "
                + Constants.C_ANNOUNCE + ", " + Constants.C_SCRAPE + ", " + Constants.C_TIER + ", "
                + Constants.C_HAS_ANNOUNCED + ", " + Constants.C_LAST_ANNOUNCE_TIME + ", "
                + Constants.C_LAST_ANNOUNCE_SUCCEEDED + ", " + Constants.C_LAST_ANNOUNCE_PEER_COUNT+ ", "
                + Constants.C_LAST_ANNOUNCE_RESULT + ", " + Constants.C_HAS_SCRAPED + ", "
                + Constants.C_LAST_SCRAPE_TIME + ", " + Constants.C_LAST_SCRAPE_SUCCEEDED + ", "
                + Constants.C_LAST_SCRAPE_RESULT + ", " + Constants.C_SEEDER_COUNT + ", "
                + Constants.C_LEECHER_COUNT;
        } else {
            select = Constants.T_TORRENT_TRACKER + "." + Constants.C_TORRENT_ID + ", "
                + Constants.T_TRACKER + "." + Constants.C_TRACKER_ID + ", "
                + Constants.C_ANNOUNCE + ", " + Constants.C_SCRAPE + ", " + Constants.C_TIER;
        }
        String from;
        if (selection == null) {
           from = Constants.T_TORRENT_TRACKER + " JOIN " + Constants.T_TRACKER
               + " ON " + Constants.T_TRACKER + "." + Constants.C_TRACKER_ID
               + " = " + Constants.T_TORRENT_TRACKER + "." + Constants.C_TRACKER_ID;
        } else {
            from = Constants.T_TORRENT + " JOIN " + Constants.T_TORRENT_TRACKER
                + " ON " + Constants.T_TORRENT + "." + Constants.C_TORRENT_ID
                + " = " + Constants.T_TORRENT_TRACKER + "." + Constants.C_TORRENT_ID
                + " JOIN " + Constants.T_TRACKER
                + " ON " + Constants.T_TORRENT_TRACKER + "." + Constants.C_TRACKER_ID
                + " = " + Constants.T_TRACKER + "." + Constants.C_TRACKER_ID;
        }
        String query = "SELECT " + select + " FROM " + from;
        if (selection != null) {
            query += " WHERE " + selection;
        }

        query += " ORDER BY " + Constants.T_TORRENT_TRACKER + "." + Constants.C_TORRENT_ID
            + ", " + Constants.C_TIER;

        cursor = database.rawQuery(query, selectionArgs);
        cursor.moveToFirst();

        List<Torrent.Tracker> trackers = new ArrayList<Torrent.Tracker>();
        SparseArray<Torrent.Tracker> trackerMap = new SparseArray<Torrent.Tracker>();
        int lastId = -1;
        while (!cursor.isAfterLast()) {
            int torrentId = cursor.getInt(cursor.getColumnIndex(Constants.C_TORRENT_ID));
            if (lastId == -1 || lastId != torrentId) {
                if (lastId != -1) {
                    Torrent torrent = torrentMap.get(lastId);
                    if (torrent != null) {
                        torrent.setTrackers(trackers.toArray(new Torrent.Tracker[trackers.size()]));
                    }
                    trackers.clear();
                }
                lastId = torrentId;
            }

            Torrent.Tracker tracker;
            int id = cursor.getInt(cursor.getColumnIndex(Constants.C_TRACKER_ID));
            if (trackerMap.get(id) == null) {
                tracker = cursorToTracker(cursor);
                trackerMap.put(id, tracker);
            } else {
                tracker = trackerMap.get(id);
            }

            trackers.add(tracker);
            cursor.moveToNext();
        }
        Torrent torrent = torrentMap.get(lastId);
        if (torrent != null) {
            torrent.setTrackers(trackers.toArray(new Torrent.Tracker[trackers.size()]));
        }

        cursor.close();

        if (details) {
            query = "SELECT " + Constants.T_FILE + "." + Constants.C_TORRENT_ID + ", "
                + Constants.C_NAME + ", " + Constants.C_LENGTH + ", " + Constants.C_BYTES_COMPLETED + ", "
                + Constants.C_PRIORITY + ", " + Constants.C_WANTED + " FROM ";

            if (selection == null) {
                query += Constants.T_FILE;
            } else {
                query += Constants.T_TORRENT + " JOIN " + Constants.T_FILE
                    + " ON " + Constants.T_TORRENT + "." + Constants.C_TORRENT_ID
                    + " = " + Constants.T_FILE + "." + Constants.C_TORRENT_ID
                    + " WHERE " + selection;
            }

            query += " ORDER BY " + Constants.T_FILE + "." + Constants.C_TORRENT_ID
                + ", " + Constants.T_FILE + "." + Constants.C_NAME;

            cursor = database.rawQuery(query, selectionArgs);

            cursor.moveToFirst();
            List<Torrent.File> files = new ArrayList<Torrent.File>();
            lastId = -1;
            while (!cursor.isAfterLast()) {
                int torrentId = cursor.getInt(cursor.getColumnIndex(Constants.C_TORRENT_ID));
                if (lastId == -1 || lastId != torrentId) {
                    if (lastId != -1) {
                        torrent = torrentMap.get(lastId);
                        if (torrent != null) {
                            torrent.setFiles(files.toArray(new Torrent.File[files.size()]));
                        }
                        files.clear();
                    }
                    lastId = torrentId;
                }

                Torrent.File file = cursorToFile(cursor);

                files.add(file);
                cursor.moveToNext();
            }

            torrent = torrentMap.get(lastId);
            if (torrent != null) {
                torrent.setFiles(files.toArray(new Torrent.File[files.size()]));
            }

            cursor.close();

        }

        return torrents;
    }

    protected TransmissionSession cursorToSession(Cursor cursor) {
        TransmissionSession session = new TransmissionSession();

        cursor.moveToFirst();

        while (!cursor.isAfterLast()) {
            String name = cursor.getString(0);
            if (name.equals(TransmissionSession.SetterFields.ALT_SPEED_LIMIT_ENABLED)) {
                session.setAltSpeedLimitEnabled(cursor.getInt(1) > 0);
            } else if (name.equals(TransmissionSession.SetterFields.ALT_DOWNLOAD_SPEED_LIMIT)) {
                session.setAltDownloadSpeedLimit(cursor.getLong(1));
            } else if (name.equals(TransmissionSession.SetterFields.ALT_UPLOAD_SPEED_LIMIT)) {
                session.setAltUploadSpeedLimit(cursor.getLong(1));
            } else if (name.equals(TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_ENABLED)) {
                session.setAltSpeedLimitTimeEnabled(cursor.getInt(1) > 0);
            } else if (name.equals(TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_BEGIN)) {
                session.setAltSpeedTimeBegin(cursor.getInt(1));
            } else if (name.equals(TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_END)) {
                session.setAltSpeedTimeEnd(cursor.getInt(1));
            } else if (name.equals(TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_DAY)) {
                session.setAltSpeedTimeDay(cursor.getInt(1));
            } else if (name.equals(TransmissionSession.SetterFields.BLOCKLIST_ENABLED)) {
                session.setBlocklistEnabled(cursor.getInt(1) > 0);
            } else if (name.equals("blocklist-size")) {
                session.setBlocklistSize(cursor.getLong(1));
            } else if (name.equals(TransmissionSession.SetterFields.BLOCKLIST_URL)) {
                session.setBlocklistURL(cursor.getString(3));
            } else if (name.equals(TransmissionSession.SetterFields.DHT)) {
                session.setDHTEnabled(cursor.getInt(1) > 0);
            } else if (name.equals(TransmissionSession.SetterFields.ENCRYPTION)) {
                session.setEncryption(cursor.getString(3));
            } else if (name.equals(TransmissionSession.SetterFields.CACHE_SIZE)) {
                session.setCacheSize(cursor.getLong(1));
            } else if (name.equals("config-dir")) {
                session.setConfigDir(cursor.getString(3));
            } else if (name.equals(TransmissionSession.SetterFields.DOWNLOAD_DIR)) {
                session.setDownloadDir(cursor.getString(3));
            } else if (name.equals("download-dir-free-space")) {
                session.setDownloadDirFreeSpace(cursor.getLong(1));
            } else if (name.equals(TransmissionSession.SetterFields.DOWNLOAD_QUEUE_SIZE)) {
                session.setDownloadQueueSize(cursor.getInt(1));
            } else if (name.equals(TransmissionSession.SetterFields.DOWNLOAD_QUEUE_ENABLED)) {
                session.setDownloadQueueEnabled(cursor.getInt(1) > 0);
            } else if (name.equals(TransmissionSession.SetterFields.INCOMPLETE_DIR)) {
                session.setIncompleteDir(cursor.getString(3));
            } else if (name.equals(TransmissionSession.SetterFields.INCOMPLETE_DIR_ENABLED)) {
                session.setIncompleteDirEnabled(cursor.getInt(1) > 0);
            } else if (name.equals(TransmissionSession.SetterFields.LOCAL_DISCOVERY)) {
                session.setLocalDiscoveryEnabled(cursor.getInt(1) > 0);
            } else if (name.equals(TransmissionSession.SetterFields.UTP)) {
                session.setUTPEnabled(cursor.getInt(1) > 0);
            } else if (name.equals(TransmissionSession.SetterFields.GLOBAL_PEER_LIMIT)) {
                session.setGlobalPeerLimit(cursor.getInt(1));
            } else if (name.equals(TransmissionSession.SetterFields.TORRENT_PEER_LIMIT)) {
                session.setTorrentPeerLimit(cursor.getInt(1));
            } else if (name.equals(TransmissionSession.SetterFields.PEER_EXCHANGE)) {
                session.setPeerExchangeEnabled(cursor.getInt(1) > 0);
            } else if (name.equals(TransmissionSession.SetterFields.PEER_PORT)) {
                session.setPeerPort(cursor.getInt(1));
            } else if (name.equals(TransmissionSession.SetterFields.RANDOM_PORT)) {
                session.setPeerPortRandomOnStart(cursor.getInt(1) > 0);
            } else if (name.equals(TransmissionSession.SetterFields.PORT_FORWARDING)) {
                session.setPortForwardingEnabled(cursor.getInt(1) > 0);
            } else if (name.equals(TransmissionSession.SetterFields.RENAME_PARTIAL)) {
                session.setRenamePartialFilesEnabled(cursor.getInt(1) > 0);
            } else if (name.equals("rpc-version")) {
                session.setRPCVersion(cursor.getInt(1));
            } else if (name.equals("rpc-version-minimum")) {
                session.setRPCVersionMin(cursor.getInt(1));
            } else if (name.equals(TransmissionSession.SetterFields.DONE_SCRIPT)) {
                session.setDoneScript(cursor.getString(3));
            } else if (name.equals(TransmissionSession.SetterFields.DONE_SCRIPT_ENABLED)) {
                session.setDoneScriptEnabled(cursor.getInt(1) > 0);
            } else if (name.equals(TransmissionSession.SetterFields.SEED_QUEUE_SIZE)) {
                session.setSeedQueueSize(cursor.getInt(1));
            } else if (name.equals(TransmissionSession.SetterFields.SEED_QUEUE_ENABLED)) {
                session.setSeedQueueEnabled(cursor.getInt(1) > 0);
            } else if (name.equals(TransmissionSession.SetterFields.SEED_RATIO_LIMIT)) {
                session.setSeedRatioLimit(cursor.getFloat(2));
            } else if (name.equals(TransmissionSession.SetterFields.SEED_RATIO_LIMIT_ENABLED)) {
                session.setSeedRatioLimitEnabled(cursor.getInt(1) > 0);
            } else if (name.equals(TransmissionSession.SetterFields.DOWNLOAD_SPEED_LIMIT)) {
                session.setDownloadSpeedLimit(cursor.getLong(1));
            } else if (name.equals(TransmissionSession.SetterFields.DOWNLOAD_SPEED_LIMIT_ENABLED)) {
                session.setDownloadSpeedLimitEnabled(cursor.getInt(1) > 0);
            } else if (name.equals(TransmissionSession.SetterFields.UPLOAD_SPEED_LIMIT)) {
                session.setUploadSpeedLimit(cursor.getLong(1));
            } else if (name.equals(TransmissionSession.SetterFields.UPLOAD_SPEED_LIMIT_ENABLED)) {
                session.setUploadSpeedLimitEnabled(cursor.getInt(1) > 0);
            } else if (name.equals(TransmissionSession.SetterFields.STALLED_QUEUE_SIZE)) {
                session.setStalledQueueSize(cursor.getInt(1));
            } else if (name.equals(TransmissionSession.SetterFields.STALLED_QUEUE_ENABLED)) {
                session.setStalledQueueEnabled(cursor.getInt(1) > 0);
            } else if (name.equals(TransmissionSession.SetterFields.START_ADDED)) {
                session.setStartAddedTorrentsEnabled(cursor.getInt(1) > 0);
            } else if (name.equals(TransmissionSession.SetterFields.TRASH_ORIGINAL)) {
                session.setTrashOriginalTorrentFilesEnabled(cursor.getInt(1) > 0);
            } else if (name.equals("version")) {
                session.setVersion(cursor.getString(3));
            }

            cursor.moveToNext();
        }

        cursor.close();

        return session;
    }

    protected Torrent cursorToTorrent(Cursor cursor) {
        Torrent torrent = new Torrent();

        int index = 0;
        for (String column : cursor.getColumnNames()) {
            if (column.equals(Constants.C_TORRENT_ID)) {
                torrent.setId(cursor.getInt(index));
            } else if (column.equals(Constants.C_NAME)) {
                torrent.setName(cursor.getString(index));
            } else if (column.equals(Constants.C_STATUS)) {
                torrent.setStatus(cursor.getInt(index));
            } else if (column.equals(Constants.C_ADDED_DATE)) {
                torrent.setAddedDate(cursor.getLong(index));
            } else if (column.equals(Constants.C_TOTAL_SIZE)) {
                torrent.setTotalSize(cursor.getLong(index));
            } else if (column.equals(Constants.C_ERROR)) {
                torrent.setError(cursor.getInt(index));
            } else if (column.equals(Constants.C_ERROR_STRING)) {
                torrent.setErrorString(cursor.getString(index));
            } else if (column.equals(Constants.C_ETA)) {
                torrent.setEta(cursor.getLong(index));
            } else if (column.equals(Constants.C_IS_FINISHED)) {
                torrent.setFinished(cursor.getInt(index) > 0);
            } else if (column.equals(Constants.C_IS_STALLED)) {
                torrent.setStalled(cursor.getInt(index) > 0);
            } else if (column.equals(Constants.C_LEFT_UNTIL_DONE)) {
                torrent.setLeftUntilDone(cursor.getLong(index));
            } else if (column.equals(Constants.C_METADATA_PERCENT_COMPLETE)) {
                torrent.setMetadataPercentComplete(cursor.getFloat(index));
            } else if (column.equals(Constants.C_PEERS_CONNECTED)) {
                torrent.setPeersConnected(cursor.getInt(index));
            } else if (column.equals(Constants.C_PEERS_GETTING_FROM_US)) {
                torrent.setPeersGettingFromUs(cursor.getInt(index));
            } else if (column.equals(Constants.C_PEERS_SENDING_TO_US)) {
                torrent.setPeersSendingToUs(cursor.getInt(index));
            } else if (column.equals(Constants.C_PERCENT_DONE)) {
                torrent.setPercentDone(cursor.getFloat(index));
            } else if (column.equals(Constants.C_QUEUE_POSITION)) {
                torrent.setQueuePosition(cursor.getInt(index));
            } else if (column.equals(Constants.C_RATE_DOWNLOAD)) {
                torrent.setRateDownload(cursor.getLong(index));
            } else if (column.equals(Constants.C_RATE_UPLOAD)) {
                torrent.setRateUpload(cursor.getLong(index));
            } else if (column.equals(Constants.C_RECHECK_PROGRESS)) {
                torrent.setRecheckProgress(cursor.getFloat(index));
            } else if (column.equals(Constants.C_SEED_RATIO_MODE)) {
                torrent.setSeedRatioMode(cursor.getInt(index));
            } else if (column.equals(Constants.C_SEED_RATIO_LIMIT)) {
                torrent.setSeedRatioLimit(cursor.getFloat(index));
            } else if (column.equals(Constants.C_SIZE_WHEN_DONE)) {
                torrent.setSizeWhenDone(cursor.getLong(index));
            } else if (column.equals(Constants.C_UPLOADED_EVER)) {
                torrent.setUploadedEver(cursor.getLong(index));
            } else if (column.equals(Constants.C_UPLOAD_RATIO)) {
                torrent.setUploadRatio(cursor.getFloat(index));
            } else if (column.equals(Constants.C_DOWNLOAD_DIR)) {
                torrent.setDownloadDir(cursor.getString(index));
            } else if (column.equals(Constants.C_TRAFFIC_TEXT)) {
                torrent.setTrafficText(cursor.getString(index));
            } else if (column.equals(Constants.C_STATUS_TEXT)) {
                torrent.setStatusText(cursor.getString(index));
            } else if (column.equals(Constants.C_COMMENT)) {
                torrent.setComment(cursor.getString(index));
            } else if (column.equals(Constants.C_CREATOR)) {
                torrent.setCreator(cursor.getString(index));
            } else if (column.equals(Constants.C_DATE_CREATED)) {
                torrent.setDateCreated(cursor.getLong(index));
            } else if (column.equals(Constants.C_HASH_STRING)) {
                torrent.setHashString(cursor.getString(index));
            } else if (column.equals(Constants.C_IS_PRIVATE)) {
                torrent.setPrivate(cursor.getInt(index) > 0);
            } else if (column.equals(Constants.C_PIECE_COUNT)) {
                torrent.setPieceCount(cursor.getInt(index));
            } else if (column.equals(Constants.C_PIECE_SIZE)) {
                torrent.setPieceSize(cursor.getLong(index));
            } else if (column.equals(Constants.C_ACTIVITY_DATE)) {
                torrent.setActivityDate(cursor.getLong(index));
            } else if (column.equals(Constants.C_TORRENT_PRIORITY)) {
                torrent.setTorrentPriority(cursor.getInt(index));
            } else if (column.equals(Constants.C_CORRUPT_EVER)) {
                torrent.setCorruptEver(cursor.getLong(index));
            } else if (column.equals(Constants.C_DESIRED_AVAILABLE)) {
                torrent.setDesiredAvailable(cursor.getLong(index));
            } else if (column.equals(Constants.C_DOWNLOADED_EVER)) {
                torrent.setDownloadedEver(cursor.getLong(index));
            } else if (column.equals(Constants.C_DOWNLOAD_LIMIT)) {
                torrent.setDownloadLimit(cursor.getLong(index));
            } else if (column.equals(Constants.C_DOWNLOAD_LIMITED)) {
                torrent.setDownloadLimited(cursor.getInt(index) > 0);
            } else if (column.equals(Constants.C_HAVE_UNCHECKED)) {
                torrent.setHaveUnchecked(cursor.getLong(index));
            } else if (column.equals(Constants.C_HAVE_VALID)) {
                torrent.setHaveValid(cursor.getLong(index));
            } else if (column.equals(Constants.C_HONORS_SESSION_LIMITS)) {
                torrent.setHonorsSessionLimits(cursor.getInt(index) > 0);
            } else if (column.equals(Constants.C_PEER_LIMIT)) {
                torrent.setPeerLimit(cursor.getInt(index));
            } else if (column.equals(Constants.C_START_DATE)) {
                torrent.setStartDate(cursor.getLong(index));
            } else if (column.equals(Constants.C_UPLOAD_LIMIT)) {
                torrent.setUploadLimit(cursor.getLong(index));
            } else if (column.equals(Constants.C_UPLOAD_LIMITED)) {
                torrent.setUploadLimited(cursor.getInt(index) > 0);
            } else if (column.equals(Constants.C_WEBSEEDS_SENDING_TO_US)) {
                torrent.setWebseedsSendingToUs(cursor.getInt(index));
            }

            ++index;
        }

        return torrent;
    }

    protected Torrent.Tracker cursorToTracker(Cursor cursor) {
        Torrent.Tracker tracker = new Torrent.Tracker();

        int index = 0;
        for (String column : cursor.getColumnNames()) {
           if (column.equals(Constants.C_TRACKER_ID)) {
                tracker.setId(cursor.getInt(index));
            } else if (column.equals(Constants.C_ANNOUNCE)) {
                tracker.setAnnounce(cursor.getString(index));
            } else if (column.equals(Constants.C_SCRAPE)) {
                tracker.setScrape(cursor.getString(index));
            } else if (column.equals(Constants.C_TIER)) {
                tracker.setTier(cursor.getInt(index));
            } else if (column.equals(Constants.C_HAS_ANNOUNCED)) {
                tracker.setAnnounced(cursor.getInt(index) > 0);
            } else if (column.equals(Constants.C_LAST_ANNOUNCE_TIME)) {
                tracker.setLastAnnounceTime(cursor.getLong(index));
            } else if (column.equals(Constants.C_LAST_ANNOUNCE_SUCCEEDED)) {
                tracker.setLastAnnounceSucceeded(cursor.getInt(index) > 0);
            } else if (column.equals(Constants.C_LAST_ANNOUNCE_PEER_COUNT)) {
                tracker.setLastAnnouncePeerCount(cursor.getInt(index));
            } else if (column.equals(Constants.C_LAST_ANNOUNCE_RESULT)) {
                tracker.setLastAnnounceResult(cursor.getString(index));
            } else if (column.equals(Constants.C_HAS_SCRAPED)) {
                tracker.setScraped(cursor.getInt(index) > 0);
            } else if (column.equals(Constants.C_LAST_SCRAPE_TIME)) {
                tracker.setLastScrapeTime(cursor.getLong(index));
            } else if (column.equals(Constants.C_LAST_SCRAPE_SUCCEEDED)) {
                tracker.setLastScrapeSucceeded(cursor.getInt(index) > 0);
            } else if (column.equals(Constants.C_LAST_SCRAPE_RESULT)) {
                tracker.setLastScrapeResult(cursor.getString(index));
            } else if (column.equals(Constants.C_SEEDER_COUNT)) {
                tracker.setSeederCount(cursor.getInt(index));
            } else if (column.equals((Constants.C_LEECHER_COUNT))) {
                tracker.setLeecherCount(cursor.getInt(index));
            }
            ++index;
        }
        return tracker;
    }

    protected Torrent.File cursorToFile(Cursor cursor) {
        Torrent.File file = new Torrent.File();

        int index = 0;
        for (String column : cursor.getColumnNames()) {
            if (column.equals(Constants.C_BYTES_COMPLETED)) {
                file.setBytesCompleted(cursor.getLong(index));
            } else if (column.equals(Constants.C_NAME)) {
                file.setName(cursor.getString(index));
            } else if (column.equals(Constants.C_LENGTH)) {
                file.setLength(cursor.getLong(index));
            } else if (column.equals(Constants.C_PRIORITY)) {
                file.setPriority(cursor.getInt(index));
            } else if (column.equals(Constants.C_WANTED)) {
                file.setWanted(cursor.getInt(index) > 0);
            }
            ++index;
        }
        return file;
    }

    protected List<ContentValues> jsonToSessionValues(JsonParser parser) throws IOException {
        List<ContentValues> values = new ArrayList<ContentValues>();

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.getCurrentName();
            ContentValues item = new ContentValues();
            boolean valid = true;

            parser.nextToken();
            if (name.equals(TransmissionSession.SetterFields.ALT_SPEED_LIMIT_ENABLED)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
            } else if (name.equals(TransmissionSession.SetterFields.ALT_DOWNLOAD_SPEED_LIMIT)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_LONG);
                item.put(Constants.C_VALUE_INTEGER, parser.getLongValue());
            } else if (name.equals(TransmissionSession.SetterFields.ALT_UPLOAD_SPEED_LIMIT)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_LONG);
                item.put(Constants.C_VALUE_INTEGER, parser.getLongValue());
            } else if (name.equals(TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_ENABLED)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
            } else if (name.equals(TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_BEGIN)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
            } else if (name.equals(TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_END)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
            } else if (name.equals(TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_DAY)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
            } else if (name.equals(TransmissionSession.SetterFields.BLOCKLIST_ENABLED)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
            } else if (name.equals("blocklist-size")) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_LONG);
                item.put(Constants.C_VALUE_INTEGER, parser.getLongValue());
            } else if (name.equals(TransmissionSession.SetterFields.BLOCKLIST_URL)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_STRING);
                item.put(Constants.C_VALUE_TEXT, parser.getText());
            } else if (name.equals(TransmissionSession.SetterFields.DHT)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
            } else if (name.equals(TransmissionSession.SetterFields.ENCRYPTION)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_STRING);
                item.put(Constants.C_VALUE_TEXT, parser.getText());
            } else if (name.equals(TransmissionSession.SetterFields.CACHE_SIZE)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_LONG);
                item.put(Constants.C_VALUE_INTEGER, parser.getLongValue());
            } else if (name.equals("config-dir")) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_STRING);
                item.put(Constants.C_VALUE_TEXT, parser.getText());
            } else if (name.equals(TransmissionSession.SetterFields.DOWNLOAD_DIR)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_STRING);
                item.put(Constants.C_VALUE_TEXT, parser.getText());
            } else if (name.equals("download-dir-free-space")) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_LONG);
                item.put(Constants.C_VALUE_INTEGER, parser.getLongValue());
            } else if (name.equals(TransmissionSession.SetterFields.DOWNLOAD_QUEUE_SIZE)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
            } else if (name.equals(TransmissionSession.SetterFields.DOWNLOAD_QUEUE_ENABLED)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
            } else if (name.equals(TransmissionSession.SetterFields.INCOMPLETE_DIR)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_STRING);
                item.put(Constants.C_VALUE_TEXT, parser.getText());
            } else if (name.equals(TransmissionSession.SetterFields.INCOMPLETE_DIR_ENABLED)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
            } else if (name.equals(TransmissionSession.SetterFields.LOCAL_DISCOVERY)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
            } else if (name.equals(TransmissionSession.SetterFields.UTP)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
            } else if (name.equals(TransmissionSession.SetterFields.GLOBAL_PEER_LIMIT)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
            } else if (name.equals(TransmissionSession.SetterFields.TORRENT_PEER_LIMIT)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
            } else if (name.equals(TransmissionSession.SetterFields.PEER_EXCHANGE)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
            } else if (name.equals(TransmissionSession.SetterFields.PEER_PORT)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
            } else if (name.equals(TransmissionSession.SetterFields.RANDOM_PORT)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
            } else if (name.equals(TransmissionSession.SetterFields.PORT_FORWARDING)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
            } else if (name.equals(TransmissionSession.SetterFields.RENAME_PARTIAL)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
            } else if (name.equals("rpc-version")) {
                setRPCVersion(parser.getIntValue());

                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                item.put(Constants.C_VALUE_INTEGER, rpcVersion);
            } else if (name.equals("rpc-version-minimum")) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
            } else if (name.equals(TransmissionSession.SetterFields.DONE_SCRIPT)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_STRING);
                item.put(Constants.C_VALUE_TEXT, parser.getText());
            } else if (name.equals(TransmissionSession.SetterFields.DONE_SCRIPT_ENABLED)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
            } else if (name.equals(TransmissionSession.SetterFields.SEED_QUEUE_SIZE)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
            } else if (name.equals(TransmissionSession.SetterFields.SEED_QUEUE_ENABLED)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
            } else if (name.equals(TransmissionSession.SetterFields.SEED_RATIO_LIMIT)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_FLOAT);
                item.put(Constants.C_VALUE_REAL, parser.getFloatValue());
            } else if (name.equals(TransmissionSession.SetterFields.SEED_RATIO_LIMIT_ENABLED)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
            } else if (name.equals(TransmissionSession.SetterFields.DOWNLOAD_SPEED_LIMIT)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_LONG);
                item.put(Constants.C_VALUE_INTEGER, parser.getLongValue());
            } else if (name.equals(TransmissionSession.SetterFields.DOWNLOAD_SPEED_LIMIT_ENABLED)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
            } else if (name.equals(TransmissionSession.SetterFields.UPLOAD_SPEED_LIMIT)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_LONG);
                item.put(Constants.C_VALUE_INTEGER, parser.getLongValue());
            } else if (name.equals(TransmissionSession.SetterFields.UPLOAD_SPEED_LIMIT_ENABLED)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
            } else if (name.equals(TransmissionSession.SetterFields.STALLED_QUEUE_SIZE)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
            } else if (name.equals(TransmissionSession.SetterFields.STALLED_QUEUE_ENABLED)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
            } else if (name.equals(TransmissionSession.SetterFields.START_ADDED)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
            } else if (name.equals(TransmissionSession.SetterFields.TRASH_ORIGINAL)) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
            } else if (name.equals("version")) {
                item.put(Constants.C_NAME, name);
                item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_STRING);
                item.put(Constants.C_VALUE_TEXT, parser.getText());
            } else {
                valid = false;
                parser.skipChildren();
            }

            if (valid) {
                values.add(item);
            }
        }

        return values;
    }

    protected TorrentValues jsonToTorrentValues(JsonParser parser) throws IOException {
        TorrentValues values = new TorrentValues();
        ContentValues torrent = new ContentValues();

        values.torrent = torrent;

        int status = 0, peersConnected = 0, peersGettingFromUs = 0, peersSendingToUs = 0;
        long eta = 0, leftUntilDone = 0, sizeWhenDone = 0, uploadedEver = 0,
            rateDownload = 0, rateUpload = 0;
        float metadataPercentComplete = 0, percentDone = 0, uploadRatio = 0,
            recheckProgress = 0, seedLimit = 0;
        boolean isStalled = false;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.getCurrentName();
            
            parser.nextToken();

            if (name.equals("id")) {
                torrent.put(Constants.C_TORRENT_ID, parser.getIntValue());
            } else if (name.equals("status")) {
                status = parser.getIntValue();
                if (rpcVersion != -1 && rpcVersion < NEW_STATUS_RPC_VERSION) {
                    switch(status) {
                        case Torrent.OldStatus.CHECK_WAITING:
                            status = Torrent.Status.CHECK_WAITING;
                            break;
                        case Torrent.OldStatus.CHECKING:
                            status = Torrent.Status.CHECKING;
                            break;
                        case Torrent.OldStatus.DOWNLOADING:
                            status = Torrent.Status.DOWNLOADING;
                            break;
                        case Torrent.OldStatus.SEEDING:
                            status = Torrent.Status.SEEDING;
                            break;
                        case Torrent.OldStatus.STOPPED:
                            status = Torrent.Status.STOPPED;
                            break;
                    }
                }
                torrent.put(Constants.C_STATUS, status);
            } else if (name.equals("name")) {
                torrent.put(Constants.C_NAME, parser.getText());
            } else if (name.equals("error")) {
                torrent.put(Constants.C_ERROR, parser.getIntValue());
            } else if (name.equals("errorString")) {
                torrent.put(Constants.C_ERROR_STRING, parser.getText());
            } else if (name.equals("metadataPercentComplete")) {
                metadataPercentComplete = parser.getFloatValue();
                torrent.put(Constants.C_METADATA_PERCENT_COMPLETE, metadataPercentComplete);
            } else if (name.equals("percentDone")) {
                percentDone = parser.getFloatValue();
                torrent.put(Constants.C_PERCENT_DONE, percentDone);
            } else if (name.equals("eta")) {
                eta = parser.getLongValue();
                torrent.put(Constants.C_ETA, eta);
            } else if (name.equals("isFinished")) {
                torrent.put(Constants.C_IS_FINISHED, parser.getBooleanValue());
            } else if (name.equals("isStalled")) {
                isStalled = parser.getBooleanValue();
                torrent.put(Constants.C_IS_STALLED, isStalled);
            } else if (name.equals("peersConnected")) {
                peersConnected = parser.getIntValue();
                torrent.put(Constants.C_PEERS_CONNECTED, peersConnected);
            } else if (name.equals("peersGettingFromUs")) {
                peersGettingFromUs = parser.getIntValue();
                torrent.put(Constants.C_PEERS_GETTING_FROM_US, peersGettingFromUs);
            } else if (name.equals("peersSendingToUs")) {
                peersSendingToUs = parser.getIntValue();
                torrent.put(Constants.C_PEERS_SENDING_TO_US, peersSendingToUs);
            } else if (name.equals("leftUntilDone")) {
                leftUntilDone = parser.getLongValue();
                torrent.put(Constants.C_LEFT_UNTIL_DONE, leftUntilDone);
            } else if (name.equals("desiredAvailable")) {
                torrent.put(Constants.C_DESIRED_AVAILABLE, parser.getLongValue());
            } else if (name.equals("totalSize")) {
                torrent.put(Constants.C_TOTAL_SIZE, parser.getLongValue());
            } else if (name.equals("sizeWhenDone")) {
                sizeWhenDone = parser.getLongValue();
                torrent.put(Constants.C_SIZE_WHEN_DONE, sizeWhenDone);
            } else if (name.equals("rateDownload")) {
                rateDownload = parser.getLongValue();
                torrent.put(Constants.C_RATE_DOWNLOAD, rateDownload);
            } else if (name.equals("rateUpload")) {
                rateUpload = parser.getLongValue();
                torrent.put(Constants.C_RATE_UPLOAD, rateUpload);
            } else if (name.equals(Torrent.SetterFields.QUEUE_POSITION)) {
                torrent.put(Constants.C_QUEUE_POSITION, parser.getIntValue());
            } else if (name.equals("recheckProgress")) {
                recheckProgress = parser.getFloatValue();
                torrent.put(Constants.C_RECHECK_PROGRESS, recheckProgress);
            } else if (name.equals(Torrent.SetterFields.SEED_RATIO_MODE)) {
                torrent.put(Constants.C_SEED_RATIO_MODE, parser.getIntValue());
            } else if (name.equals(Torrent.SetterFields.SEED_RATIO_LIMIT)) {
                seedLimit = parser.getFloatValue();
                torrent.put(Constants.C_SEED_RATIO_LIMIT, seedLimit);
            } else if (name.equals("uploadedEver")) {
                uploadedEver = parser.getLongValue();
                torrent.put(Constants.C_UPLOADED_EVER, uploadedEver);
            } else if (name.equals("uploadRatio")) {
                uploadRatio = parser.getFloatValue();
                torrent.put(Constants.C_UPLOAD_RATIO, uploadRatio);
            } else if (name.equals("addedDate")) {
                torrent.put(Constants.C_ADDED_DATE, parser.getLongValue());
            } else if (name.equals("doneDate")) {
                torrent.put(Constants.C_DONE_DATE, parser.getLongValue());
            } else if (name.equals("startDate")) {
                torrent.put(Constants.C_START_DATE, parser.getLongValue());
            } else if (name.equals("activityDate")) {
                torrent.put(Constants.C_ACTIVITY_DATE, parser.getLongValue());
            } else if (name.equals("corruptEver")) {
                torrent.put(Constants.C_CORRUPT_EVER, parser.getLongValue());
            } else if (name.equals("downloadDir")) {
                torrent.put(Constants.C_DOWNLOAD_DIR, parser.getText());
            } else if (name.equals("downloadedEver")) {
                torrent.put(Constants.C_DOWNLOADED_EVER, parser.getLongValue());
            } else if (name.equals("haveUnchecked")) {
                torrent.put(Constants.C_HAVE_UNCHECKED, parser.getLongValue());
            } else if (name.equals("haveValid")) {
                torrent.put(Constants.C_HAVE_VALID, parser.getLongValue());
            } else if (name.equals("comment")) {
                torrent.put(Constants.C_COMMENT, parser.getText());
            } else if (name.equals("creator")) {
                torrent.put(Constants.C_CREATOR, parser.getText());
            } else if (name.equals("dateCreated")) {
                torrent.put(Constants.C_DATE_CREATED, parser.getLongValue());
            } else if (name.equals("hashString")) {
                torrent.put(Constants.C_HASH_STRING, parser.getText());
            } else if (name.equals("isPrivate")) {
                torrent.put(Constants.C_IS_PRIVATE, parser.getBooleanValue());
            } else if (name.equals("pieceCount")) {
                torrent.put(Constants.C_PIECE_COUNT, parser.getIntValue());
            } else if (name.equals("pieceSize")) {
                torrent.put(Constants.C_PIECE_SIZE, parser.getLongValue());
            } else if (name.equals(Torrent.SetterFields.TORRENT_PRIORITY)) {
                torrent.put(Constants.C_TORRENT_PRIORITY, parser.getIntValue());
            } else if (name.equals(Torrent.SetterFields.DOWNLOAD_LIMIT)) {
                torrent.put(Constants.C_DOWNLOAD_LIMIT, parser.getLongValue());
            } else if (name.equals(Torrent.SetterFields.DOWNLOAD_LIMITED)) {
                torrent.put(Constants.C_DOWNLOAD_LIMITED, parser.getBooleanValue());
            } else if (name.equals(Torrent.SetterFields.SESSION_LIMITS)) {
                torrent.put(Constants.C_HONORS_SESSION_LIMITS, parser.getBooleanValue());
            } else if (name.equals(Torrent.SetterFields.UPLOAD_LIMIT)) {
                torrent.put(Constants.C_UPLOAD_LIMIT, parser.getLongValue());
            } else if (name.equals(Torrent.SetterFields.UPLOAD_LIMITED)) {
                torrent.put(Constants.C_UPLOAD_LIMITED, parser.getBooleanValue());
            } else if (name.equals("webseedsSendingToUs")) {
                torrent.put(Constants.C_WEBSEEDS_SENDING_TO_US, parser.getIntValue());
            } else if (name.equals(Torrent.SetterFields.PEER_LIMIT)) {
                torrent.put(Constants.C_PEER_LIMIT, parser.getIntValue());
            } else if (name.equals("trackers")) {
                if (values.trackers == null) {
                    values.trackers = new ArrayList<ContentValues>();
                }

                int index = 0;
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    ContentValues tracker;
                    if (index >= values.trackers.size()) {
                        tracker = new ContentValues();
                        values.trackers.add(index, tracker);
                    } else {
                        tracker = values.trackers.get(index);
                    }

                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String argname = parser.getCurrentName();
                        parser.nextToken();

                        if (argname.equals("id")) {
                            tracker.put(Constants.C_TRACKER_ID, parser.getIntValue());
                        } else if (argname.equals("announce")) {
                            tracker.put(Constants.C_ANNOUNCE, parser.getText());
                        } else if (argname.equals("scrape")) {
                            tracker.put(Constants.C_SCRAPE, parser.getText());
                        } else if (argname.equals("tier")) {
                            tracker.put(Constants.C_TIER, parser.getIntValue());
                        }
                    }

                    ++index;
                }
            } else if (name.equals("trackerStats")) {
                if (values.trackers == null) {
                    values.trackers = new ArrayList<ContentValues>();
                }

                int index = 0;
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    ContentValues tracker;
                    if (index >= values.trackers.size()) {
                        tracker = new ContentValues();
                        values.trackers.add(index, tracker);
                    } else {
                        tracker = values.trackers.get(index);
                    }

                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String argname = parser.getCurrentName();
                        parser.nextToken();

                        if (argname.equals("id")) {
                            tracker.put(Constants.C_TRACKER_ID, parser.getIntValue());
                        } else if (argname.equals("hasAnnounced")) {
                            tracker.put(Constants.C_HAS_ANNOUNCED, parser.getBooleanValue());
                        } else if (argname.equals("lastAnnounceTime")) {
                            tracker.put(Constants.C_LAST_ANNOUNCE_TIME, parser.getLongValue());
                        } else if (argname.equals("lastAnnounceSucceeded")) {
                            tracker.put(Constants.C_LAST_ANNOUNCE_SUCCEEDED, parser.getBooleanValue());
                        } else if (argname.equals("lastAnnouncePeerCount")) {
                            tracker.put(Constants.C_LAST_ANNOUNCE_PEER_COUNT, parser.getIntValue());
                        } else if (argname.equals("lastAnnounceResult")) {
                            tracker.put(Constants.C_LAST_ANNOUNCE_RESULT, parser.getText());
                        } else if (argname.equals("hasScraped")) {
                            tracker.put(Constants.C_HAS_SCRAPED, parser.getBooleanValue());
                        } else if (argname.equals("lastScrapeTime")) {
                            tracker.put(Constants.C_LAST_SCRAPE_TIME, parser.getLongValue());
                        } else if (argname.equals("lastScrapeSucceeded")) {
                            tracker.put(Constants.C_LAST_SCRAPE_SUCCEEDED, parser.getBooleanValue());
                        } else if (argname.equals("lastScrapeResult")) {
                            tracker.put(Constants.C_LAST_SCRAPE_RESULT, parser.getText());
                        } else if (argname.equals("seederCount")) {
                            tracker.put(Constants.C_SEEDER_COUNT, parser.getIntValue());
                        } else if (argname.equals("leecherCount")) {
                            tracker.put(Constants.C_LEECHER_COUNT, parser.getIntValue());
                        }
                    }

                    ++index;
                }
            } else if (name.equals("files")) {
                if (values.files == null) {
                    values.files = new ArrayList<ContentValues>();
                }

                int index = 0;
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    ContentValues file;
                    if (index >= values.files.size()) {
                        file = new ContentValues();
                        values.files.add(index, file);
                    } else {
                        file = values.files.get(index);
                    }

                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String argname = parser.getCurrentName();
                        parser.nextToken();

                        if (argname.equals("name")) {
                            file.put(Constants.C_NAME, parser.getText());
                        } else if (argname.equals("length")) {
                            file.put(Constants.C_LENGTH, parser.getLongValue());
                        }
                    }

                    ++index;
                }
            } else if (name.equals("fileStats")) {
                if (values.files == null) {
                    values.files = new ArrayList<ContentValues>();
                }

                int index = 0;
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    ContentValues file;
                    if (index >= values.files.size()) {
                        file = new ContentValues();
                        values.files.add(index, file);
                    } else {
                        file = values.files.get(index);
                    }

                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String argname = parser.getCurrentName();
                        parser.nextToken();

                        if (argname.equals("bytesCompleted")) {
                            file.put(Constants.C_BYTES_COMPLETED, parser.getLongValue());
                        } else if (argname.equals("wanted")) {
                            file.put(Constants.C_WANTED, parser.getBooleanValue());
                        } else if (argname.equals("priority")) {
                            file.put(Constants.C_PRIORITY, parser.getIntValue());
                        }
                    }

                    ++index;
                }
            } else if (name.equals("peers")) {
                if (values.peers == null) {
                    values.peers = new ArrayList<ContentValues>();
                }
                int index = 0;
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    ContentValues peer;
                    if (index >= values.peers.size()) {
                        peer = new ContentValues();
                        values.peers.add(index, peer);
                    } else {
                        peer = values.peers.get(index);
                    }

                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String argname = parser.getCurrentName();
                        parser.nextToken();

                        if (argname.equals("address")) {
                            peer.put(Constants.C_ADDRESS, parser.getText());
                        } else if (argname.equals("clientName")) {
                            peer.put(Constants.C_CLIENT_NAME, parser.getText());
                        } else if (argname.equals("clientIsChoked")) {
                            peer.put(Constants.C_CLIENT_IS_CHOKED, parser.getBooleanValue());
                        } else if (argname.equals("clientIsInterested")) {
                            peer.put(Constants.C_CLIENT_IS_INTERESTED, parser.getBooleanValue());
                        } else if (argname.equals("isDownloadingFrom")) {
                            peer.put(Constants.C_IS_DOWNLOADING_FROM, parser.getBooleanValue());
                        } else if (argname.equals("isEncrypted")) {
                            peer.put(Constants.C_IS_ENCRYPTED, parser.getBooleanValue());
                        } else if (argname.equals("isIncoming")) {
                            peer.put(Constants.C_IS_INCOMING, parser.getBooleanValue());
                        } else if (argname.equals("isUploadingTo")) {
                            peer.put(Constants.C_IS_UPLOADING_TO, parser.getBooleanValue());
                        } else if (argname.equals("peerIsChoked")) {
                            peer.put(Constants.C_PEER_IS_CHOKED, parser.getBooleanValue());
                        } else if (argname.equals("peerIsInterested")) {
                            peer.put(Constants.C_PEER_IS_INTERESTED, parser.getBooleanValue());
                        } else if (argname.equals("port")) {
                            peer.put(Constants.C_PORT, parser.getIntValue());
                        } else if (argname.equals("progress")) {
                            peer.put(Constants.C_PROGRESS, parser.getFloatValue());
                        } else if (argname.equals("rateToClient")) {
                            peer.put(Constants.C_RATE_TO_CLIENT, parser.getLongValue());
                        } else if (argname.equals("rateToPeer")) {
                            peer.put(Constants.C_RATE_TO_PEER, parser.getLongValue());
                        }
                    }

                    ++index;
                }
            } else {
                parser.skipChildren();
            }

        }
        String trafficText = null;
        switch (status) {
            case Torrent.Status.DOWNLOAD_WAITING:
            case Torrent.Status.DOWNLOADING:
                trafficText = String.format(
                    context.getString(R.string.traffic_downloading_format),
                    G.readableFileSize(sizeWhenDone - leftUntilDone),
                    G.readableFileSize(sizeWhenDone),
                    String.format(context.getString(R.string.traffic_downloading_percentage_format),
                           G.readablePercent(percentDone * 100)),
                    eta < 0
                        ? context.getString(R.string.traffic_remaining_time_unknown)
                        : String.format(context.getString(R.string.traffic_remaining_time_format),
                           G.readableRemainingTime(eta, context))
                );
                break;
            case Torrent.Status.SEED_WAITING:
            case Torrent.Status.SEEDING:
                trafficText = String.format(
                                context.getString(R.string.traffic_seeding_format),
                    G.readableFileSize(sizeWhenDone),
                    G.readableFileSize(uploadedEver),
                    String.format(context.getString(R.string.traffic_seeding_ratio_format),
                           G.readablePercent(uploadRatio),
                           seedLimit <= 0 ? "" : String.format(
                               context.getString(R.string.traffic_seeding_ratio_goal_format),
                               G.readablePercent(seedLimit))
                    ),
                    seedLimit <= 0
                        ? ""
                        : eta < 0
                            ? context.getString(R.string.traffic_remaining_time_unknown)
                            : String.format(context.getString(R.string.traffic_remaining_time_format),
                               G.readableRemainingTime(eta, context))
                );
                break;
            case Torrent.Status.CHECK_WAITING:
                break;
            case Torrent.Status.CHECKING:
                break;
            case Torrent.Status.STOPPED:
                if (percentDone < 1) {
                    trafficText = String.format(
                                    context.getString(R.string.traffic_downloading_format),
                        G.readableFileSize(sizeWhenDone - leftUntilDone),
                        G.readableFileSize(sizeWhenDone),
                        String.format(context.getString(R.string.traffic_downloading_percentage_format),
                               G.readablePercent(percentDone * 100)),
                        "<br/>" + String.format(
                                        context.getString(R.string.traffic_seeding_format),
                            G.readableFileSize(sizeWhenDone),
                            G.readableFileSize(uploadedEver),
                            String.format(context.getString(R.string.traffic_seeding_ratio_format),
                                   uploadRatio < 0 ? 0 : G.readablePercent(uploadRatio),
                                   seedLimit <= 0 ? "" : String.format(
                                       context.getString(R.string.traffic_seeding_ratio_goal_format),
                                       G.readablePercent(seedLimit))
                            ),
                            ""
                        )
                    );
                } else {
                    trafficText = String.format(
                                    context.getString(R.string.traffic_seeding_format),
                        G.readableFileSize(sizeWhenDone),
                        G.readableFileSize(uploadedEver),
                        String.format(context.getString(R.string.traffic_seeding_ratio_format),
                               G.readablePercent(uploadRatio),
                               seedLimit <= 0 ? "" : String.format(
                                   context.getString(R.string.traffic_seeding_ratio_goal_format),
                                   G.readablePercent(seedLimit))
                        ),
                        ""
                    );
                }

                break;
            default:
                break;
        }

        String statusText = null;
        String statusFormat = context.getString(R.string.status_format);
        String statusType, statusMoreFormat, statusSpeedFormat, statusSpeed;
        switch (status) {
            case Torrent.Status.DOWNLOAD_WAITING:
            case Torrent.Status.DOWNLOADING:
                statusType = context.getString(status == Torrent.Status.DOWNLOADING
                        ? metadataPercentComplete < 1
                            ? R.string.status_state_downloading_metadata
                            : R.string.status_state_downloading
                        : R.string.status_state_download_waiting);
                statusMoreFormat = context.getString(R.string.status_more_downloading_format);
                statusSpeedFormat = context.getString(R.string.status_more_downloading_speed_format);

                if (isStalled) {
                    statusSpeed = context.getString(R.string.status_more_idle);
                } else {
                    statusSpeed = String.format(statusSpeedFormat,
                        G.readableFileSize(rateDownload),
                        G.readableFileSize(rateUpload)
                    );
                }

                statusText = String.format(statusFormat, statusType,
                        String.format(statusMoreFormat,
                            peersSendingToUs, peersConnected, statusSpeed
                        )
                    );
                break;
            case Torrent.Status.SEED_WAITING:
            case Torrent.Status.SEEDING:
                statusType = context.getString(status == Torrent.Status.SEEDING
                        ? R.string.status_state_seeding : R.string.status_state_seed_waiting);
                statusMoreFormat = context.getString(R.string.status_more_seeding_format);
                statusSpeedFormat = context.getString(R.string.status_more_seeding_speed_format);

                if (isStalled) {
                    statusSpeed = context.getString(R.string.status_more_idle);
                } else {
                    statusSpeed = String.format(statusSpeedFormat,
                        G.readableFileSize(rateUpload)
                    );
                }

                statusText = String.format(statusFormat, statusType,
                        String.format(statusMoreFormat,
                            peersGettingFromUs, peersConnected, statusSpeed
                        )
                    );
                break;
            case Torrent.Status.CHECK_WAITING:
                statusType = context.getString(R.string.status_state_check_waiting);

                statusText = String.format(statusFormat,
                    statusType,
                    "-" + context.getString(R.string.status_more_idle)
                );
                break;
            case Torrent.Status.CHECKING:
                statusText = String.format(
                    context.getString(R.string.status_state_checking),
                    G.readablePercent(recheckProgress * 100));

                break;
            case Torrent.Status.STOPPED:
                statusText = context.getString(
                    status == Torrent.Status.STOPPED || uploadRatio < seedLimit
                        ? R.string.status_state_paused
                        : R.string.status_state_finished
                );

                break;
            default:
                break;
        }

        if (trafficText != null) {
            torrent.put(Constants.C_TRAFFIC_TEXT, trafficText);
        }
        if (statusText != null) {
            torrent.put(Constants.C_STATUS_TEXT, statusText);
        }

        return values;
    }
}
