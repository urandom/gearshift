package org.sugr.gearshift.datasource;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.SparseBooleanArray;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;
import org.sugr.gearshift.Torrent;
import org.sugr.gearshift.TransmissionSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class TorrentValues {
    public ContentValues torrent;
    public List<ContentValues> files;
    public List<ContentValues> trackers;
    public List<ContentValues> peers;
}

class TorrentCursorArgs {
    public String selection;
    public String[] selectionArgs;
    public String orderBy;
}

public class DataSource {
    private SQLiteDatabase database;

    private SQLiteHelper dbHelper;
    private Context context;

    /* Transmission stuff */
    private static final int NEW_STATUS_RPC_VERSION = 14;

    private int rpcVersion = -1;

    private static final Object lock = new Object();

    public DataSource(Context context) {
        setContext(context);
    }

    public void open() {
        synchronized (lock) {
            database = dbHelper.getWritableDatabase();
        }
    }

    public void close() {
        synchronized (lock) {
            dbHelper.close();
            database = null;
        }
    }

    public boolean isOpen() {
        return database != null && database.isOpen();
    }

    public void setRPCVersion(int version) {
        rpcVersion = version;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
        this.dbHelper = new SQLiteHelper(context.getApplicationContext());
    }

    public void clearTorrents() {
        if (!isOpen())
            return;

        synchronized (lock) {
            try {
                database.beginTransaction();

                database.delete(Constants.T_TORRENT, null, null);
                database.delete(Constants.T_TORRENT_TRACKER, null, null);
                database.delete(Constants.T_TRACKER, null, null);
                database.delete(Constants.T_FILE, null, null);
                database.delete(Constants.T_PEER, null, null);

                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }
    }

    public boolean updateSession(JsonParser parser) throws IOException {
        if (!isOpen())
            return false;

        List<ContentValues> session = jsonToSessionValues(parser);

        synchronized (lock) {
            try {
                database.beginTransaction();

                for (ContentValues item : session) {
                    database.insertWithOnConflict(Constants.T_SESSION, null,
                        item, SQLiteDatabase.CONFLICT_REPLACE);
                }

                database.setTransactionSuccessful();

                return true;
            } finally {
                database.endTransaction();
            }
        }
    }

    public TorrentStatus updateTorrents(JsonParser parser) throws IOException {
        if (!isOpen())
            return null;

        int[] idChanges = queryTorrentIdChanges();
        int[] status = queryStatusCount();

        synchronized (lock) {
            try {
                database.beginTransaction();

                SparseBooleanArray trackers = new SparseBooleanArray();

                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    TorrentValues values = jsonToTorrentValues(parser);

                    int torrentId = (Integer) values.torrent.get(Constants.C_TORRENT_ID);
                    int updated = database.update(
                        Constants.T_TORRENT, values.torrent, Constants.C_TORRENT_ID + " = ?",
                        new String[] { Integer.toString(torrentId )});

                    if (updated < 1) {
                        database.insertWithOnConflict(Constants.T_TORRENT, null,
                            values.torrent, SQLiteDatabase.CONFLICT_REPLACE);
                    }

                    if (values.trackers != null) {
                        for (ContentValues tracker : values.trackers) {
                            int trackerId = (Integer) tracker.get(Constants.C_TRACKER_ID);
                            if (!trackers.get(trackerId, false)) {
                                trackers.put(trackerId, true);

                                updated = database.update(Constants.T_TRACKER, tracker,
                                    Constants.C_TRACKER_ID + " = ?",
                                    new String[] { Integer.toString(trackerId) });

                                if (updated < 1) {
                                    database.insertWithOnConflict(Constants.T_TRACKER, null,
                                        tracker, SQLiteDatabase.CONFLICT_REPLACE);
                                }
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
                            Integer index = (Integer) file.get(Constants.C_FILE_INDEX);
                            file.put(Constants.C_TORRENT_ID, torrentId);

                            updated = database.update(Constants.T_FILE, file,
                                Constants.C_TORRENT_ID + " = ? AND " + Constants.C_FILE_INDEX + " = ?",
                                new String[] { Integer.toString(torrentId), index.toString() });

                            if (updated < 1) {
                                database.insertWithOnConflict(Constants.T_FILE, null,
                                    file, SQLiteDatabase.CONFLICT_REPLACE);
                            }
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

                int[] updatedIdChanges = queryTorrentIdChanges();
                int[] updatedStatus = queryStatusCount();

                boolean added = false, removed = false, statusChanged = false;
                boolean incompleteMetadata = !hasCompleteMetadata();

                if (idChanges != null && updatedIdChanges != null) {
                    added = idChanges[0] < updatedIdChanges[0];
                    removed = idChanges[0] > updatedIdChanges[0] || idChanges[1] < updatedIdChanges[1];

                    if (added || removed) {
                        statusChanged = true;
                    }
                }

                if (status != null && updatedStatus != null && status.length == updatedStatus.length) {
                    for (int i = 0; i < status.length && !statusChanged; ++i) {
                        statusChanged = status[i] != updatedStatus[i];
                    }
                }

                return new TorrentStatus(
                    added,
                    removed,
                    statusChanged,
                    incompleteMetadata
                );
            } finally {
                database.endTransaction();
            }
        }
    }

    public boolean removeTorrents(int... ids) {
        if (!isOpen())
            return false;

        synchronized (lock) {
            try {
                database.beginTransaction();

                for (int id : ids) {
                    removeTorrent(id);
                }

                database.setTransactionSuccessful();

                return true;
            } finally {
                database.endTransaction();
            }
        }
    }

    /* Transmission implementation */
    public TransmissionSession getSession() {
        if (!isOpen())
            return null;

        Cursor cursor = null;
        try {
            cursor = database.query(Constants.T_SESSION, new String[] {
                Constants.C_NAME, Constants.C_VALUE_INTEGER,
                Constants.C_VALUE_REAL, Constants.C_VALUE_TEXT
            }, null, null, null, null, null);

            return cursorToSession(cursor);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public boolean hasExtraInfo() {
        if (!isOpen())
            return false;

        Cursor cursor = null;
        try {
            cursor = database.rawQuery(
                "SELECT count(" + Constants.C_TORRENT_ID + "), count(CASE "
                + Constants.C_NAME + " WHEN '' THEN 1 ELSE NULL END) FROM "
                + Constants.T_FILE, null
            );

            cursor.moveToFirst();

            return cursor.getInt(0) != 0 && cursor.getInt(1) == 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public boolean hasCompleteMetadata() {
        if (!isOpen())
            return false;

        Cursor cursor = null;
        try {
            cursor = database.rawQuery(
                "SELECT count(" + Constants.C_TORRENT_ID + ") FROM "
                    + Constants.T_TORRENT + " WHERE "
                    + Constants.C_TOTAL_SIZE + " = 0 AND ("
                    + Constants.C_STATUS + " = "
                    + Torrent.Status.CHECKING
                    + " OR "
                    + Constants.C_STATUS + " = "
                    + Torrent.Status.DOWNLOADING
                    + " OR "
                    + Constants.C_STATUS + " = "
                    + Torrent.Status.SEEDING
                    + ")", null
            );

            cursor.moveToFirst();

            return cursor.getInt(0) == 0;
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    public int[] getUnnamedTorrentIds() {
        if (!isOpen())
            return null;

        Cursor cursor = null;
        try {
            cursor = database.query(Constants.T_TORRENT, new String[] { Constants.C_TORRENT_ID },
                Constants.C_NAME + " = ''", null, null, null, null);

            int[] ids = new int[cursor.getCount()];
            int index = 0;
            cursor.moveToFirst();

            while (!cursor.isAfterLast()) {
                ids[index++] = cursor.getInt(0);
                cursor.moveToNext();
            }

            return ids;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public boolean addTorrent(int id, String name, String hash) {
        if (!isOpen())
            return false;

        synchronized (lock) {
            try {
                database.beginTransaction();

                ContentValues values = new ContentValues();

                values.put(Constants.C_TORRENT_ID, id);
                values.put(Constants.C_NAME, name);
                values.put(Constants.C_HASH_STRING, hash);
                values.put(Constants.C_STATUS, Torrent.Status.STOPPED);

                long result = database.insert(Constants.T_TORRENT, null, values);

                database.setTransactionSuccessful();

                return result > -1;
            } finally {
                database.endTransaction();
            }
        }
    }

    public Set<String> getTrackerAnnounceURLs() {
        if (!isOpen())
            return null;

        Set<String> urls = new HashSet<String>();

        Cursor cursor = null;
        try {
            cursor = database.query(true, Constants.T_TRACKER, new String[] { Constants.C_ANNOUNCE },
                null, null, null, null, Constants.C_ANNOUNCE, null);

            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                urls.add(cursor.getString(0));
                cursor.moveToNext();
            }

            return urls;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public Set<String> getDownloadDirectories() {
        if (!isOpen())
            return null;

        Set<String> directories = new HashSet<String>();

        Cursor cursor = null;
        try {
            cursor = database.query(true, Constants.T_TORRENT,
                new String[]{Constants.C_DOWNLOAD_DIR}, null, null, null, null,
                null, null);

            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                directories.add(cursor.getString(0));
                cursor.moveToNext();
            }

            return directories;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public long[] getTrafficSpeed() {
        if (!isOpen())
            return null;

        long[] speed = new long[2];

        Cursor cursor = null;
        try {
            cursor = database.rawQuery("SELECT SUM("
                + Constants.C_RATE_DOWNLOAD + "), SUM("
                + Constants.C_RATE_UPLOAD + ") FROM "
                + Constants.T_TORRENT, null);

            cursor.moveToFirst();

            speed[0] = cursor.getLong(0);
            speed[1] = cursor.getLong(1);

            return speed;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public Cursor getTorrentCursor() {
        if (!isOpen())
            return null;

        TorrentCursorArgs args = getTorrentCursorArgs();

        return getTorrentCursor(args.selection, args.selectionArgs, args.orderBy, false);
    }

    public Cursor getTorrentCursor(String selection, String[] selectionArgs, String orderBy, boolean details) {
        if (!isOpen())
            return null;

        String[] columnList = Constants.ColumnGroups.TORRENT_OVERVIEW;
        if (details) {
            columnList = G.concat(columnList, Constants.ColumnGroups.TORRENT_DETAILS);
        }

        String columns = Constants.C_TORRENT_ID + " AS " + Constants.C_ID + ", "
            + TextUtils.join(", ", columnList);

        String query = "SELECT " + columns
            + " FROM " + Constants.T_TORRENT;

        if (selection != null && selection.length() > 0) {
            query = query + " WHERE " + selection;
        }
        if (orderBy != null && orderBy.length() > 0) {
            query = query + " ORDER BY " + orderBy;
        }

        return database.rawQuery(query, selectionArgs);
    }

    public TorrentNameStatus getTorrentNameStatus(int id) {
        if (!isOpen())
            return null;

        Cursor cursor = null;
        try {
            cursor = database.query(Constants.T_TORRENT,
                new String[] { Constants.C_NAME, Constants.C_STATUS },
                Constants.C_TORRENT_ID + " = ?",
                new String[] { Integer.toString(id) },
                null, null, null
            );

            cursor.moveToFirst();

            return new TorrentNameStatus(cursor.getString(0), cursor.getInt(1));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public TorrentDetails getTorrentDetails(int id) {
        if (!isOpen())
            return null;

         String[] selectionArgs = new String[] { Integer.toString(id) };

         Cursor torrent = getTorrentCursor(Constants.C_ID + " = ?", selectionArgs, null, true);

         String select = Constants.T_TRACKER + "." + Constants.C_TRACKER_ID + ", "
             + Constants.C_ANNOUNCE + ", " + Constants.C_SCRAPE + ", " + Constants.C_TIER + ", "
             + Constants.C_HAS_ANNOUNCED + ", " + Constants.C_LAST_ANNOUNCE_TIME + ", "
             + Constants.C_LAST_ANNOUNCE_SUCCEEDED + ", " + Constants.C_LAST_ANNOUNCE_PEER_COUNT+ ", "
             + Constants.C_LAST_ANNOUNCE_RESULT + ", " + Constants.C_HAS_SCRAPED + ", "
             + Constants.C_LAST_SCRAPE_TIME + ", " + Constants.C_LAST_SCRAPE_SUCCEEDED + ", "
             + Constants.C_LAST_SCRAPE_RESULT + ", " + Constants.C_SEEDER_COUNT + ", "
             + Constants.C_LEECHER_COUNT;

         String from = Constants.T_TORRENT_TRACKER + " JOIN " + Constants.T_TRACKER
             + " ON " + Constants.T_TRACKER + "." + Constants.C_TRACKER_ID
             + " = " + Constants.T_TORRENT_TRACKER + "." + Constants.C_TRACKER_ID;

         String query = "SELECT " + select + " FROM " + from
             + " WHERE " + Constants.T_TORRENT_TRACKER + "." + Constants.C_TORRENT_ID + " = ?";

         Cursor trackers = database.rawQuery(query, selectionArgs);

         Cursor files = database.query(Constants.T_FILE, Constants.ColumnGroups.FILE,
             Constants.C_TORRENT_ID + " = ?", selectionArgs, null, null, null);

         return new TorrentDetails(torrent, trackers, files);
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

        return session;
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
                        file.put(Constants.C_FILE_INDEX, index);
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
                        file.put(Constants.C_FILE_INDEX, index);
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

    protected void removeTorrent(int id) {
        database.delete(Constants.T_TORRENT, Constants.C_TORRENT_ID + " = ?",
            new String[] { Integer.toString(id) } );
    }

    protected int[] queryTorrentIdChanges() {
        Cursor cursor = null;
        try {
            cursor = database.rawQuery(
                "SELECT max("
                + Constants.C_TORRENT_ID
                + "), count("
                + Constants.C_TORRENT_ID
                + ") FROM " + Constants.T_TORRENT, null
            );

            cursor.moveToFirst();

            return new int [] { cursor.getInt(0), cursor.getInt(1) };
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    protected int[] queryStatusCount() {
        Cursor cursor = null;
        try {
            cursor = database.rawQuery(
                "SELECT count(CASE WHEN "
                    + Constants.C_STATUS + " = "
                    + Torrent.Status.CHECK_WAITING
                    + " THEN 1 ELSE NULL END), count(CASE WHEN "
                    + Constants.C_STATUS + " = "
                    + Torrent.Status.CHECKING
                    + " THEN 1 ELSE NULL END), count(CASE WHEN "
                    + Constants.C_STATUS + " = "
                    + Torrent.Status.DOWNLOAD_WAITING
                    + " THEN 1 ELSE NULL END), count(CASE WHEN "
                    + Constants.C_STATUS + " = "
                    + Torrent.Status.DOWNLOADING
                    + " THEN 1 ELSE NULL END), count(CASE WHEN "
                    + Constants.C_STATUS + " = "
                    + Torrent.Status.SEED_WAITING
                    + " THEN 1 ELSE NULL END), count(CASE WHEN "
                    + Constants.C_STATUS + " = "
                    + Torrent.Status.SEEDING
                    + " THEN 1 ELSE NULL END), count(CASE WHEN "
                    + Constants.C_STATUS + " = "
                    + Torrent.Status.STOPPED
                    + " THEN 1 ELSE NULL END) FROM " + Constants.T_TORRENT, null
            );

            cursor.moveToFirst();

            return new int[] { cursor.getInt(0), cursor.getInt(1), cursor.getInt(2),
                cursor.getInt(3), cursor.getInt(4), cursor.getInt(5), cursor.getInt(6) };
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    protected TorrentCursorArgs getTorrentCursorArgs() {
        TorrentCursorArgs args = new TorrentCursorArgs();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String query = prefs.getString(G.PREF_LIST_SEARCH, null);
        String directory = prefs.getString(G.PREF_LIST_DIRECTORY, null);
        String tracker = prefs.getString(G.PREF_LIST_TRACKER, null);

        G.FilterBy filter = G.FilterBy.ALL;
        if (prefs.contains(G.PREF_LIST_FILTER)) {
            try {
                filter = G.FilterBy.valueOf(
                    prefs.getString(G.PREF_LIST_FILTER, G.FilterBy.ALL.name())
                );
            } catch (Exception ignored) { }
        }
        G.SortBy sortBy = G.SortBy.STATUS;
        if (prefs.contains(G.PREF_LIST_SORT_BY)) {
            try {
                sortBy = G.SortBy.valueOf(
                    prefs.getString(G.PREF_LIST_SORT_BY, G.SortBy.STATUS.name())
                );
            } catch (Exception ignored) { }
        }
        G.SortOrder sortOrder = G.SortOrder.ASCENDING;
        if (prefs.contains(G.PREF_LIST_SORT_ORDER)) {
            try {
                sortOrder = G.SortOrder.valueOf(
                    prefs.getString(G.PREF_LIST_SORT_ORDER, G.SortOrder.ASCENDING.name())
                );
            } catch (Exception ignored) { }
        }

        G.SortBy baseSortBy = G.SortBy.AGE;
        if (prefs.contains(G.PREF_BASE_SORT)) {
            try {
                baseSortBy = G.SortBy.valueOf(
                    prefs.getString(G.PREF_BASE_SORT, G.SortBy.AGE.name())
                );
            } catch (Exception ignored) { }
        }

        G.SortOrder baseSortOrder = G.SortOrder.DESCENDING;
        if (prefs.contains(G.PREF_BASE_SORT_ORDER)) {
            String pref = prefs.getString(G.PREF_BASE_SORT_ORDER, "PRIMARY");

            if (pref != null) {
                if (pref.equals("ASCENDING")) {
                    baseSortOrder = G.SortOrder.ASCENDING;
                } else if (pref.equals("DESCENDING")) {
                    baseSortOrder = G.SortOrder.DESCENDING;
                } else if (pref.equals("PRIMARY")) {
                    baseSortOrder = sortOrder;
                } else if (pref.equals("REVERSE")) {
                    baseSortOrder = sortOrder == G.SortOrder.ASCENDING
                        ? G.SortOrder.DESCENDING
                        : G.SortOrder.ASCENDING;
                }
            }
        }

        List<String> selection = new ArrayList<String>();
        List<String> selectionArgs = new ArrayList<String>();
        if (query != null && query.length() > 0) {
            StringBuilder queryPattern = new StringBuilder();
            String[] split = query.toLowerCase(Locale.getDefault()).split("");

            for (int i = 0; i < split.length; ++i) {
                if (split[i].equals("") || split[i].equals(";")) {
                    continue;
                }
                queryPattern.append(split[i].equals("'") ? "''" : split[i]);
                if (i < split.length - 1) {
                    queryPattern.append("%");
                }
            }
            selection.add("LOWER(" + Constants.C_NAME + ") LIKE '%" + queryPattern.toString() +"%'");
        }

        if (directory != null && directory.length() > 0) {
            selection.add(Constants.C_DOWNLOAD_DIR + " = ?");
            selectionArgs.add(directory);
        }

        if (tracker != null && tracker.length() > 0) {
            selection.add(Constants.C_TORRENT_ID + " IN ("
                + "SELECT " + Constants.C_TORRENT_ID
                + " FROM " + Constants.T_TORRENT_TRACKER
                + " JOIN " + Constants.T_TRACKER
                + " ON " + Constants.T_TORRENT_TRACKER + "." + Constants.C_TRACKER_ID
                + " = " + Constants.T_TRACKER + "." + Constants.C_TRACKER_ID
                + " AND " + Constants.C_ANNOUNCE + " = ?"
                + ")");
            selectionArgs.add(tracker);
        }

        if (filter != G.FilterBy.ALL) {
            if (filter == G.FilterBy.DOWNLOADING) {
                selection.add(Constants.C_STATUS + " = ?");
                selectionArgs.add(Integer.toString(Torrent.Status.DOWNLOADING));
            } else if (filter == G.FilterBy.SEEDING) {
                selection.add(Constants.C_STATUS + " = ?");
                selectionArgs.add(Integer.toString(Torrent.Status.SEEDING));
            } else if (filter == G.FilterBy.PAUSED) {
                selection.add(Constants.C_STATUS + " = ?");
                selectionArgs.add(Integer.toString(Torrent.Status.STOPPED));
            } else if (filter == G.FilterBy.COMPLETE) {
                selection.add(Constants.C_PERCENT_DONE + " = 1");
            } else if (filter == G.FilterBy.INCOMPLETE) {
                selection.add(Constants.C_PERCENT_DONE + " < 1");
            } else if (filter == G.FilterBy.ACTIVE) {
                selection.add(
                    Constants.C_IS_STALLED + " != 1"
                    + " AND " + Constants.C_IS_FINISHED + " != 1"
                    + " AND ("
                        + Constants.C_STATUS + " = ?"
                        + " OR " + Constants.C_STATUS + " = ?"
                    + ")"
                );
                selectionArgs.add(Integer.toString(Torrent.Status.DOWNLOADING));
                selectionArgs.add(Integer.toString(Torrent.Status.SEEDING));
            } else if (filter == G.FilterBy.CHECKING) {
                selection.add(Constants.C_STATUS + " = ?");
                selectionArgs.add(Integer.toString(Torrent.Status.CHECKING));
            }
        }

        args.selection = TextUtils.join(" AND ", selection);
        args.selectionArgs = selectionArgs.toArray(new String[selectionArgs.size()]);

        String mainOrder = sortToOrder(sortBy, sortOrder);
        String baseOrder = sortToOrder(baseSortBy, baseSortOrder);

        if (mainOrder != null && baseOrder != null) {
            args.orderBy = mainOrder + ", " + baseOrder;
        }

        return args;
    }

    protected String sortToOrder(G.SortBy sortBy, G.SortOrder sortOrder) {
        String sort;

        switch(sortBy) {
            case NAME:
                sort = "LOWER(" + Constants.C_NAME + ")";
                break;
            case SIZE:
                sort = Constants.C_TOTAL_SIZE;
                break;
            case STATUS:
                sort = "(CASE " + Constants.C_STATUS
                    + " WHEN " + Torrent.Status.STOPPED
                    + " THEN " + (Torrent.Status.STOPPED + 40)
                    + " WHEN " + Torrent.Status.CHECK_WAITING
                    + " THEN " + (Torrent.Status.CHECK_WAITING + 100)
                    + " WHEN " + Torrent.Status.DOWNLOAD_WAITING
                    + " THEN " + (Torrent.Status.DOWNLOAD_WAITING + 10)
                    + " WHEN " + Torrent.Status.CHECK_WAITING
                    + " THEN " + (Torrent.Status.SEED_WAITING + 20)
                    + " ELSE " + Constants.C_STATUS
                    + " END)";
                break;
            case ACTIVITY:
                sort = "(" + Constants.C_RATE_DOWNLOAD + " + " + Constants.C_RATE_UPLOAD + ")";
                sortOrder = sortOrder == G.SortOrder.ASCENDING
                    ? G.SortOrder.DESCENDING : G.SortOrder.ASCENDING;
                break;
            case AGE:
                sort = Constants.C_ADDED_DATE;
                sortOrder = sortOrder == G.SortOrder.ASCENDING
                    ? G.SortOrder.DESCENDING : G.SortOrder.ASCENDING;
                break;
            case LOCATION:
                sort = "LOWER(" + Constants.C_DOWNLOAD_DIR + ")";
                break;
            case PEERS:
                sort = Constants.C_PEERS_CONNECTED;
                break;
            case PROGRESS:
                sort = Constants.C_PERCENT_DONE;
                break;
            case QUEUE:
                sort = Constants.C_QUEUE_POSITION;
                break;
            case RATE_DOWNLOAD:
                sort = Constants.C_RATE_DOWNLOAD;
                sortOrder = sortOrder == G.SortOrder.ASCENDING
                    ? G.SortOrder.DESCENDING : G.SortOrder.ASCENDING;
                break;
            case RATE_UPLOAD:
                sort = Constants.C_RATE_UPLOAD;
                sortOrder = sortOrder == G.SortOrder.ASCENDING
                    ? G.SortOrder.DESCENDING : G.SortOrder.ASCENDING;
                break;
            case RATIO:
                sort = Constants.C_UPLOAD_RATIO;
                sortOrder = sortOrder == G.SortOrder.ASCENDING
                    ? G.SortOrder.DESCENDING : G.SortOrder.ASCENDING;
                break;
            default:
                return null;
        }

        return sort + " " + (sortOrder == G.SortOrder.ASCENDING ? "ASC" : "DESC");
    }
}
