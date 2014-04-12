package org.sugr.gearshift.datasource;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;
import org.sugr.gearshift.core.Torrent;
import org.sugr.gearshift.core.TransmissionSession;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private SQLiteOpenHelper dbHelper;
    private Context context;

    /* Transmission stuff */
    private static final int NEW_STATUS_RPC_VERSION = 14;

    private TransmissionSession session;

    public DataSource(Context context) {
        setContext(context);
        setHelper(new SQLiteHelper(context.getApplicationContext()));
    }

    public DataSource(Context context, SQLiteOpenHelper helper) {
        setContext(context);
        setHelper(helper);
    }

    public void open() {
        synchronized (DataSource.class) {
            database = dbHelper.getWritableDatabase();
        }
    }

    public void close() {
        synchronized (DataSource.class) {
            dbHelper.close();
            database = null;
        }
    }

    public boolean isOpen() {
        return database != null && database.isOpen();
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void setHelper(SQLiteOpenHelper helper) {
        this.dbHelper = helper;
    }

    public boolean updateSession(String profile, JsonParser parser) throws IOException {
        if (!isOpen())
            return false;

        List<ContentValues> session = jsonToSessionValues(profile, parser);

        synchronized (DataSource.class) {
            database.beginTransactionNonExclusive();
            try {
                for (ContentValues item : session) {
                    database.insertWithOnConflict(Constants.T_SESSION, null,
                        item, SQLiteDatabase.CONFLICT_REPLACE);
                }

                database.setTransactionSuccessful();

                return true;
            } finally {
                if (database.inTransaction()) {
                    database.endTransaction();
                }
            }
        }
    }

    public TorrentStatus updateTorrents(String profile, JsonParser parser, boolean removeObsolete)
            throws IOException {
        if (!isOpen())
            return null;

        int[] idChanges = queryTorrentIdChanges();
        int[] status = queryStatusCount();

        if (session == null) {
            session = getSession(profile);
        }

        synchronized (DataSource.class) {
            database.beginTransactionNonExclusive();
            try {
                List<String> validHashStrings = null;
                if (removeObsolete) {
                    validHashStrings = new ArrayList<>();
                }

                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    TorrentValues values = jsonToTorrentValues(parser);

                    String hash = (String) values.torrent.get(Constants.C_HASH_STRING);
                    if (removeObsolete) {
                        validHashStrings.add(hash);
                    }

                    int updated = database.update(
                        Constants.T_TORRENT, values.torrent, Constants.C_HASH_STRING + " = ?",
                        new String[] { hash });

                    if (updated < 1) {
                        database.insertWithOnConflict(Constants.T_TORRENT, null,
                            values.torrent, SQLiteDatabase.CONFLICT_REPLACE);
                    }

                    ContentValues profileValues = new ContentValues();
                    profileValues.put(Constants.C_HASH_STRING, hash);
                    profileValues.put(Constants.C_PROFILE_ID, profile);
                    database.insertWithOnConflict(Constants.T_TORRENT_PROFILE, null,
                        profileValues, SQLiteDatabase.CONFLICT_IGNORE);

                    if (values.trackers != null) {
                        for (ContentValues tracker : values.trackers) {
                            Integer trackerId = (Integer) tracker.get(Constants.C_TRACKER_ID);
                            tracker.put(Constants.C_HASH_STRING, hash);

                            updated = database.update(Constants.T_TRACKER, tracker,
                                Constants.C_HASH_STRING + " = ? AND " + Constants.C_TRACKER_ID + " = ?",
                                new String[] { hash, trackerId.toString() });

                            if (updated < 1) {
                                database.insertWithOnConflict(Constants.T_TRACKER, null,
                                    tracker, SQLiteDatabase.CONFLICT_REPLACE);
                            }
                        }
                    }

                    if (values.files != null) {
                        for (ContentValues file : values.files) {
                            Integer index = (Integer) file.get(Constants.C_FILE_INDEX);
                            file.put(Constants.C_HASH_STRING, hash);

                            updated = database.update(Constants.T_FILE, file,
                                Constants.C_HASH_STRING + " = ? AND " + Constants.C_FILE_INDEX + " = ?",
                                new String[] { hash, index.toString() });

                            if (updated < 1) {
                                database.insertWithOnConflict(Constants.T_FILE, null,
                                    file, SQLiteDatabase.CONFLICT_REPLACE);
                            }
                        }
                    }

                    if (values.peers != null) {
                        for (ContentValues peer : values.peers) {
                            peer.put(Constants.C_HASH_STRING, hash);
                            database.insertWithOnConflict(Constants.T_PEER, null,
                                peer, SQLiteDatabase.CONFLICT_REPLACE);
                        }
                    }
                }

                if (removeObsolete) {
                    removeObsolete(profile, validHashStrings);
                }

                database.setTransactionSuccessful();

                int[] updatedIdChanges = queryTorrentIdChanges();
                int[] updatedStatus = queryStatusCount();

                boolean added = false, removed = false, statusChanged = false;
                boolean incompleteMetadata = !hasCompleteMetadata(profile);

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
                if (database.inTransaction()) {
                    database.endTransaction();
                }
            }
        }
    }

    public boolean removeTorrents(String... hashStrings) {
        if (!isOpen())
            return false;

        synchronized (DataSource.class) {
            database.beginTransactionNonExclusive();
            try {
                for (String hash : hashStrings) {
                    removeTorrent(hash);
                }

                database.setTransactionSuccessful();

                return true;
            } finally {
                if (database.inTransaction()) {
                    database.endTransaction();
                }
            }
        }
    }

    public boolean removeTorrents(String profile, int... ids) {
        if (!isOpen())
            return false;

        synchronized (DataSource.class) {
            database.beginTransactionNonExclusive();
            try {
                for (int id : ids) {
                    removeTorrent(profile, id);
                }

                database.setTransactionSuccessful();

                return true;
            } finally {
                if (database.inTransaction()) {
                    database.endTransaction();
                }
            }
        }
    }


    /* Transmission implementation */
    public TransmissionSession getSession(String profile) {
        if (!isOpen())
            return null;

        Cursor cursor = null;
        try {
            cursor = database.query(Constants.T_SESSION, new String[] {
                Constants.C_NAME, Constants.C_VALUE_INTEGER,
                Constants.C_VALUE_REAL, Constants.C_VALUE_TEXT
            }, Constants.C_PROFILE_ID + " = ?", new String[] { profile }, null, null, null);

            return cursorToSession(cursor);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public boolean hasExtraInfo(String profile) {
        if (!isOpen())
            return false;

        Cursor cursor = null;
        try {
            cursor = database.rawQuery(
                "SELECT count(" + Constants.T_FILE + ".rowid), count(CASE "
                    + Constants.T_FILE + "." + Constants.C_NAME
                    + " WHEN '' THEN 1 ELSE NULL END) FROM "
                    + Constants.T_TORRENT_PROFILE
                    + " JOIN " + Constants.T_TORRENT
                    + " ON " + Constants.T_TORRENT_PROFILE + "." + Constants.C_HASH_STRING
                    + " = " + Constants.T_TORRENT + "." + Constants.C_HASH_STRING
                    + " JOIN " + Constants.T_FILE
                    + " ON " + Constants.T_FILE + "." + Constants.C_HASH_STRING
                    + " = " + Constants.T_TORRENT + "." + Constants.C_HASH_STRING
                    + " WHERE " + Constants.C_PROFILE_ID + " = ?",
                new String[] { profile }
            );

            cursor.moveToFirst();

            return cursor.getInt(0) != 0 && cursor.getInt(1) == 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public boolean hasCompleteMetadata(String profile) {
        if (!isOpen())
            return false;

        Cursor cursor = null;
        try {
            cursor = database.rawQuery(
                "SELECT count(" + Constants.T_TORRENT + ".rowid) FROM "
                    + Constants.T_TORRENT_PROFILE
                    + " JOIN " + Constants.T_TORRENT
                    + " ON " + Constants.T_TORRENT_PROFILE + "." + Constants.C_HASH_STRING
                    + " = " + Constants.T_TORRENT + "." + Constants.C_HASH_STRING
                    + " WHERE " + Constants.C_TOTAL_SIZE + " = 0 AND ("
                    + Constants.C_STATUS + " = "
                    + Torrent.Status.CHECKING
                    + " OR "
                    + Constants.C_STATUS + " = "
                    + Torrent.Status.DOWNLOADING
                    + " OR "
                    + Constants.C_STATUS + " = "
                    + Torrent.Status.SEEDING
                    + ") AND " + Constants.C_PROFILE_ID + " = ?",
                new String[] { profile }
            );

            cursor.moveToFirst();

            return cursor.getInt(0) == 0;
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    public String[] getUnnamedTorrentHashStrings(String profile) {
        if (!isOpen())
            return null;

        Cursor cursor = null;
        try {
            cursor = database.rawQuery(
                "SELECT " + Constants.T_TORRENT + "." + Constants.C_HASH_STRING
                    + " FROM " + Constants.T_TORRENT_PROFILE
                    + " JOIN " + Constants.T_TORRENT
                    + " ON " + Constants.T_TORRENT_PROFILE + "." + Constants.C_HASH_STRING
                    + " = " + Constants.T_TORRENT + "." + Constants.C_HASH_STRING
                    + " WHERE " + Constants.C_PROFILE_ID + " = ?"
                    + " AND " + Constants.C_NAME + " = ''",
                new String[] { profile }
            );

            String[] hashStrings = new String[cursor.getCount()];
            int index = 0;
            cursor.moveToFirst();

            while (!cursor.isAfterLast()) {
                hashStrings[index++] = cursor.getString(0);
                cursor.moveToNext();
            }

            return hashStrings;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public boolean addTorrent(String profile, int id, String name, String hash, String location) {
        if (!isOpen())
            return false;

        synchronized (DataSource.class) {
            database.beginTransactionNonExclusive();
            try {
                ContentValues values = new ContentValues();

                values.put(Constants.C_HASH_STRING, hash);
                values.put(Constants.C_TORRENT_ID, id);
                values.put(Constants.C_NAME, name);
                values.put(Constants.C_STATUS, Torrent.Status.STOPPED);
                values.put(Constants.C_ADDED_DATE, new Date().getTime() / 1000);
                values.put(Constants.C_DOWNLOAD_DIR, location);

                long result = database.insert(Constants.T_TORRENT, null, values);

                if (result > -1) {
                    values = new ContentValues();
                    values.put(Constants.C_HASH_STRING, hash);
                    values.put(Constants.C_PROFILE_ID, profile);

                    result = database.insert(Constants.T_TORRENT_PROFILE, null, values);
                }

                database.setTransactionSuccessful();

                return result > -1;
            } finally {
                if (database.inTransaction()) {
                    database.endTransaction();
                }
            }
        }
    }

    public boolean clearTorrentsForProfile(String profile) {
        if (!isOpen())
            return false;

        synchronized (DataSource.class) {
            database.beginTransactionNonExclusive();
            try {
                String[] args = new String[] { profile };

                /* Delete all torrents that are only present for the given profile
                    DELETE FROM torrent
                    WHERE hash_string IN (
                        SELECT hash_string FROM torrent_profile t1 WHERE NOT EXISTS (
                            SELECT 1 FROM torrent_profile t2
                            WHERE t1.hash_string = t2.hash_string
                            AND t1.profile_id != t2.profile_id
                        ) AND t1.profile_id = ?
                    )
                */

                database.delete(Constants.T_TORRENT, Constants.C_HASH_STRING
                    + " IN ("
                    + " SELECT " + Constants.C_HASH_STRING
                    + " FROM " + Constants.T_TORRENT_PROFILE + " t1"
                    + " WHERE NOT EXISTS ("
                    + " SELECT 1"
                    + " FROM " + Constants.T_TORRENT_PROFILE + " t2"
                    + " WHERE t1." + Constants.C_HASH_STRING + " = t2." + Constants.C_HASH_STRING
                    + " AND t1." + Constants.C_PROFILE_ID + " != t2." + Constants.C_PROFILE_ID
                    + ")"
                    + " AND t1." + Constants.C_PROFILE_ID + " = ?"
                    + ")",
                    args);

                database.delete(Constants.T_TORRENT_PROFILE, Constants.C_PROFILE_ID + " = ?", args);
                database.delete(Constants.T_SESSION, Constants.C_PROFILE_ID + " = ?", args);

                database.setTransactionSuccessful();

                return true;
            } finally {
                if (database.inTransaction()) {
                    database.endTransaction();
                }
            }
        }
    }

    public List<String> getTrackerAnnounceURLs(String profile) {
        if (!isOpen())
            return null;

        List<String> urls = new ArrayList<>();

        Cursor cursor = null;
        try {
            cursor = database.rawQuery(
                "SELECT DISTINCT " + Constants.C_ANNOUNCE
                    + " FROM " + Constants.T_TORRENT_PROFILE
                    + " JOIN " + Constants.T_TRACKER
                    + " ON " + Constants.T_TRACKER + "." + Constants.C_HASH_STRING
                    + " = " + Constants.T_TORRENT_PROFILE + "." + Constants.C_HASH_STRING
                    + " WHERE " + Constants.C_PROFILE_ID + " = ?"
                    + " ORDER BY " + Constants.C_ANNOUNCE + " COLLATE NOCASE",
                new String[] { profile }
            );

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

    public List<String> getTrackerAnnounceAuthorities(String profile) {
        Set<String> authorities = new HashSet<>();

        for (String url : getTrackerAnnounceURLs(profile)) {
            try {
                URI uri = new URI(url);

                authorities.add(uri.getAuthority());
            } catch (URISyntaxException ignored) {}
        }

        List<String> authorityList = new ArrayList<>(authorities);

        Collections.sort(authorityList);

        return authorityList;
    }

    public List<String> getDownloadDirectories(String profile) {
        if (!isOpen())
            return null;

        List<String> directories = new ArrayList<>();

        Cursor cursor = null;
        try {
            cursor = database.rawQuery(
                "SELECT DISTINCT " + Constants.C_DOWNLOAD_DIR
                    + " FROM " + Constants.T_TORRENT_PROFILE
                    + " JOIN " + Constants.T_TORRENT
                    + " ON " + Constants.T_TORRENT_PROFILE + "." + Constants.C_HASH_STRING
                    + " = " + Constants.T_TORRENT + "." + Constants.C_HASH_STRING
                    + " WHERE " + Constants.C_PROFILE_ID + " = ?"
                    + " ORDER BY " + Constants.C_DOWNLOAD_DIR + " COLLATE NOCASE",
                new String[] { profile }
            );

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

    public long[] getTrafficSpeed(String profile) {
        if (!isOpen())
            return null;

        long[] speed = new long[2];

        Cursor cursor = null;
        try {
            cursor = database.rawQuery(
                "SELECT SUM(" + Constants.C_RATE_DOWNLOAD + "), SUM(" + Constants.C_RATE_UPLOAD + ")"
                    + " FROM " + Constants.T_TORRENT_PROFILE
                    + " JOIN " + Constants.T_TORRENT
                    + " ON " + Constants.T_TORRENT_PROFILE + "." + Constants.C_HASH_STRING
                    + " = " + Constants.T_TORRENT + "." + Constants.C_HASH_STRING
                    + " WHERE " + Constants.C_PROFILE_ID + " = ?",
                new String[] { profile }
            );

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

    public Cursor getTorrentCursor(String profile, SharedPreferences prefs) {
        if (!isOpen())
            return null;

        if (session == null) {
            session = getSession(profile);
        }

        TorrentCursorArgs args = getTorrentCursorArgs(prefs);

        Cursor cursor = getTorrentCursor(profile, args.selection, args.selectionArgs,
            args.orderBy, false);

        String search = prefs.getString(G.PREF_LIST_SEARCH, null);

        if (!TextUtils.isEmpty(search)) {
            MatrixCursor matrix = new MatrixCursor(cursor.getColumnNames());

            String prefixString = search.toLowerCase(Locale.getDefault());
            Pattern prefixPattern = null;
            int hiPrimary = context.getResources().getColor(R.color.filter_highlight_primary);
            int hiSecondary = context.getResources().getColor(R.color.filter_highlight_secondary);

            if (prefixString.length() > 0) {
                String[] split = prefixString.split("");
                StringBuilder pattern = new StringBuilder();
                for (int i = 0; i < split.length; i++) {
                    if (split[i].equals("")) {
                        continue;
                    }
                    pattern.append("\\Q").append(split[i]).append("\\E");
                    if (i < split.length - 1) {
                        pattern.append(".{0,2}?");
                    }
                }

                prefixPattern = Pattern.compile(pattern.toString());
            }

            cursor.moveToFirst();

            while (!cursor.isAfterLast()) {
                String name = Torrent.getName(cursor);

                Matcher m = prefixPattern.matcher(name.toLowerCase(Locale.getDefault()));
                if (m.find()) {
                    SpannableString spannedName = new SpannableString(name);
                    spannedName.setSpan(
                        new ForegroundColorSpan(hiPrimary),
                        m.start(),
                        m.start() + 1,
                        Spanned.SPAN_INCLUSIVE_INCLUSIVE
                    );
                    if (m.end() - m.start() > 2) {
                        spannedName.setSpan(
                            new ForegroundColorSpan(hiSecondary),
                            m.start() + 1,
                            m.end() - 1,
                            Spanned.SPAN_INCLUSIVE_INCLUSIVE
                        );
                    }
                    if (m.end() - m.start() > 1) {
                        spannedName.setSpan(
                            new ForegroundColorSpan(hiPrimary),
                            m.end() - 1,
                            m.end(),
                            Spanned.SPAN_INCLUSIVE_INCLUSIVE
                        );
                    }
                    name = Html.toHtml(spannedName);

                    MatrixCursor.RowBuilder row = matrix.newRow();

                    int index = 0;
                    for (String column : cursor.getColumnNames()) {
                        switch (column) {
                            case Constants.C_NAME:
                                row.add(name);
                                break;
                            case Constants.C_ID:
                            case Constants.C_STATUS:
                            case Constants.C_ERROR:
                            case Constants.C_SEED_RATIO_MODE:
                                row.add(cursor.getInt(index));
                                break;
                            case Constants.C_HASH_STRING:
                            case Constants.C_TRAFFIC_TEXT:
                            case Constants.C_STATUS_TEXT:
                            case Constants.C_ERROR_STRING:
                                row.add(cursor.getString(index));
                                break;
                            case Constants.C_METADATA_PERCENT_COMPLETE:
                            case Constants.C_PERCENT_DONE:
                            case Constants.C_UPLOAD_RATIO:
                            case Constants.C_SEED_RATIO_LIMIT:
                                row.add(cursor.getFloat(index));
                                break;
                            default:
                                throw new IllegalStateException("Unexpected column: " + column);
                        }

                        ++index;
                    }
                }

                cursor.moveToNext();
            }

            cursor.close();

            return matrix;
        }

        /* Fill the cursor window */
        cursor.getCount();

        return cursor;
    }

    public Cursor getTorrentCursor(String profile, String selection, String[] selectionArgs,
                                   String orderBy, boolean details) {
        if (!isOpen())
            return null;

        String[] columnList = Constants.ColumnGroups.TORRENT_OVERVIEW;
        if (details) {
            columnList = G.concat(columnList, Constants.ColumnGroups.TORRENT_DETAILS);
        }

        String columns = Constants.T_TORRENT + ".rowid AS " + Constants.C_ID + ", "
            + Constants.T_TORRENT + "." + Constants.C_HASH_STRING + ", "
            + TextUtils.join(", ", columnList);

        String query = "SELECT " + columns
            + " FROM " + Constants.T_TORRENT_PROFILE
            + " JOIN " + Constants.T_TORRENT
            + " ON " + Constants.T_TORRENT_PROFILE + "." + Constants.C_HASH_STRING
            + " = " + Constants.T_TORRENT + "." + Constants.C_HASH_STRING
            + " AND " + Constants.T_TORRENT_PROFILE + "." + Constants.C_PROFILE_ID + " = ?";

        if (selection != null && selection.length() > 0) {
            query = query + " WHERE " + selection;
        }
        if (orderBy != null && orderBy.length() > 0) {
            query = query + " ORDER BY " + orderBy;
        }

        return database.rawQuery(query, G.concat(new String[] { profile }, selectionArgs));
    }

    public TorrentNameStatus getTorrentNameStatus(String profile, String hash) {
        if (!isOpen())
            return null;

        Cursor cursor = null;
        try {
            cursor = database.rawQuery(
                "SELECT " + Constants.C_NAME + ", " + Constants.C_STATUS
                    + " FROM " + Constants.T_TORRENT_PROFILE
                    + " JOIN " + Constants.T_TORRENT
                    + " ON " + Constants.T_TORRENT_PROFILE + "." + Constants.C_HASH_STRING
                    + " = " + Constants.T_TORRENT + "." + Constants.C_HASH_STRING
                    + " WHERE " + Constants.C_PROFILE_ID + " = ?"
                    + " AND " + Constants.T_TORRENT + "." + Constants.C_HASH_STRING + " = ?",
                new String[] { profile, hash }
            );

            cursor.moveToFirst();

            return new TorrentNameStatus(cursor.getString(0), cursor.getInt(1));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public TorrentDetails getTorrentDetails(String profile, String hash) {
        if (!isOpen())
            return null;

         String[] selectionArgs = new String[] { hash };

         Cursor torrent = getTorrentCursor(profile,
             Constants.T_TORRENT + "." + Constants.C_HASH_STRING + " = ?",
             selectionArgs, null, true);

        Cursor trackers = database.query(Constants.T_TRACKER, Constants.ColumnGroups.TRACKER,
            Constants.C_HASH_STRING + " = ?", selectionArgs, null, null, null);

        Cursor files = database.query(Constants.T_FILE, Constants.ColumnGroups.FILE,
            Constants.C_HASH_STRING + " = ?", selectionArgs, null, null, null);

         return new TorrentDetails(torrent, trackers, files);
    }

    protected TransmissionSession cursorToSession(Cursor cursor) {
        session = new TransmissionSession();

        cursor.moveToFirst();

        while (!cursor.isAfterLast()) {
            String name = cursor.getString(0);
            switch (name) {
                case TransmissionSession.SetterFields.ALT_SPEED_LIMIT_ENABLED:
                    session.setAltSpeedLimitEnabled(cursor.getInt(1) > 0);
                    break;
                case TransmissionSession.SetterFields.ALT_DOWNLOAD_SPEED_LIMIT:
                    session.setAltDownloadSpeedLimit(cursor.getLong(1));
                    break;
                case TransmissionSession.SetterFields.ALT_UPLOAD_SPEED_LIMIT:
                    session.setAltUploadSpeedLimit(cursor.getLong(1));
                    break;
                case TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_ENABLED:
                    session.setAltSpeedLimitTimeEnabled(cursor.getInt(1) > 0);
                    break;
                case TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_BEGIN:
                    session.setAltSpeedTimeBegin(cursor.getInt(1));
                    break;
                case TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_END:
                    session.setAltSpeedTimeEnd(cursor.getInt(1));
                    break;
                case TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_DAY:
                    session.setAltSpeedTimeDay(cursor.getInt(1));
                    break;
                case TransmissionSession.SetterFields.BLOCKLIST_ENABLED:
                    session.setBlocklistEnabled(cursor.getInt(1) > 0);
                    break;
                case "blocklist-size":
                    session.setBlocklistSize(cursor.getLong(1));
                    break;
                case TransmissionSession.SetterFields.BLOCKLIST_URL:
                    session.setBlocklistURL(cursor.getString(3));
                    break;
                case TransmissionSession.SetterFields.DHT:
                    session.setDhtEnabled(cursor.getInt(1) > 0);
                    break;
                case TransmissionSession.SetterFields.ENCRYPTION:
                    session.setEncryption(cursor.getString(3));
                    break;
                case TransmissionSession.SetterFields.CACHE_SIZE:
                    session.setCacheSize(cursor.getLong(1));
                    break;
                case "config-dir":
                    session.setConfigDir(cursor.getString(3));
                    break;
                case TransmissionSession.SetterFields.DOWNLOAD_DIR:
                    session.setDownloadDir(cursor.getString(3));
                    break;
                case "download-dir-free-space":
                    session.setDownloadDirFreeSpace(cursor.getLong(1));
                    break;
                case TransmissionSession.SetterFields.DOWNLOAD_QUEUE_SIZE:
                    session.setDownloadQueueSize(cursor.getInt(1));
                    break;
                case TransmissionSession.SetterFields.DOWNLOAD_QUEUE_ENABLED:
                    session.setDownloadQueueEnabled(cursor.getInt(1) > 0);
                    break;
                case TransmissionSession.SetterFields.IDLE_SEEDING_LIMIT:
                    session.setIdleSeedingLimig(cursor.getLong(1));
                    break;
                case TransmissionSession.SetterFields.IDLE_SEEDING_LIMIT_ENABLED:
                    session.setIdleSeedingLimitEnabled(cursor.getInt(1) > 0);
                    break;
                case TransmissionSession.SetterFields.INCOMPLETE_DIR:
                    session.setIncompleteDir(cursor.getString(3));
                    break;
                case TransmissionSession.SetterFields.INCOMPLETE_DIR_ENABLED:
                    session.setIncompleteDirEnabled(cursor.getInt(1) > 0);
                    break;
                case TransmissionSession.SetterFields.LOCAL_DISCOVERY:
                    session.setLocalDiscoveryEnabled(cursor.getInt(1) > 0);
                    break;
                case TransmissionSession.SetterFields.UTP:
                    session.setUtpEnabled(cursor.getInt(1) > 0);
                    break;
                case TransmissionSession.SetterFields.GLOBAL_PEER_LIMIT:
                    session.setGlobalPeerLimit(cursor.getInt(1));
                    break;
                case TransmissionSession.SetterFields.TORRENT_PEER_LIMIT:
                    session.setTorrentPeerLimit(cursor.getInt(1));
                    break;
                case TransmissionSession.SetterFields.PEER_EXCHANGE:
                    session.setPeerExchangeEnabled(cursor.getInt(1) > 0);
                    break;
                case TransmissionSession.SetterFields.PEER_PORT:
                    session.setPeerPort(cursor.getInt(1));
                    break;
                case TransmissionSession.SetterFields.RANDOM_PORT:
                    session.setPeerPortRandomOnStart(cursor.getInt(1) > 0);
                    break;
                case TransmissionSession.SetterFields.PORT_FORWARDING:
                    session.setPortForwardingEnabled(cursor.getInt(1) > 0);
                    break;
                case TransmissionSession.SetterFields.RENAME_PARTIAL:
                    session.setRenamePartialFilesEnabled(cursor.getInt(1) > 0);
                    break;
                case "rpc-version":
                    session.setRPCVersion(cursor.getInt(1));
                    break;
                case "rpc-version-minimum":
                    session.setRPCVersionMin(cursor.getInt(1));
                    break;
                case TransmissionSession.SetterFields.DONE_SCRIPT:
                    session.setDoneScript(cursor.getString(3));
                    break;
                case TransmissionSession.SetterFields.DONE_SCRIPT_ENABLED:
                    session.setDoneScriptEnabled(cursor.getInt(1) > 0);
                    break;
                case TransmissionSession.SetterFields.SEED_QUEUE_SIZE:
                    session.setSeedQueueSize(cursor.getInt(1));
                    break;
                case TransmissionSession.SetterFields.SEED_QUEUE_ENABLED:
                    session.setSeedQueueEnabled(cursor.getInt(1) > 0);
                    break;
                case TransmissionSession.SetterFields.SEED_RATIO_LIMIT:
                    session.setSeedRatioLimit(cursor.getFloat(2));
                    break;
                case TransmissionSession.SetterFields.SEED_RATIO_LIMIT_ENABLED:
                    session.setSeedRatioLimitEnabled(cursor.getInt(1) > 0);
                    break;
                case TransmissionSession.SetterFields.DOWNLOAD_SPEED_LIMIT:
                    session.setDownloadSpeedLimit(cursor.getLong(1));
                    break;
                case TransmissionSession.SetterFields.DOWNLOAD_SPEED_LIMIT_ENABLED:
                    session.setDownloadSpeedLimitEnabled(cursor.getInt(1) > 0);
                    break;
                case TransmissionSession.SetterFields.UPLOAD_SPEED_LIMIT:
                    session.setUploadSpeedLimit(cursor.getLong(1));
                    break;
                case TransmissionSession.SetterFields.UPLOAD_SPEED_LIMIT_ENABLED:
                    session.setUploadSpeedLimitEnabled(cursor.getInt(1) > 0);
                    break;
                case TransmissionSession.SetterFields.STALLED_QUEUE_SIZE:
                    session.setStalledQueueSize(cursor.getInt(1));
                    break;
                case TransmissionSession.SetterFields.STALLED_QUEUE_ENABLED:
                    session.setStalledQueueEnabled(cursor.getInt(1) > 0);
                    break;
                case TransmissionSession.SetterFields.START_ADDED:
                    session.setStartAddedTorrentsEnabled(cursor.getInt(1) > 0);
                    break;
                case TransmissionSession.SetterFields.TRASH_ORIGINAL:
                    session.setTrashOriginalTorrentFilesEnabled(cursor.getInt(1) > 0);
                    break;
                case "version":
                    session.setVersion(cursor.getString(3));
                    break;
            }

            cursor.moveToNext();
        }

        return session;
    }

    protected List<ContentValues> jsonToSessionValues(String profile, JsonParser parser)
            throws IOException {
        List<ContentValues> values = new ArrayList<>();

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.getCurrentName();
            ContentValues item = new ContentValues();
            boolean valid = true;

            parser.nextToken();
            item.put(Constants.C_PROFILE_ID, profile);
            switch (name) {
                case TransmissionSession.SetterFields.ALT_SPEED_LIMIT_ENABLED:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case TransmissionSession.SetterFields.ALT_DOWNLOAD_SPEED_LIMIT:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_LONG);
                    item.put(Constants.C_VALUE_INTEGER, parser.getLongValue());
                    break;
                case TransmissionSession.SetterFields.ALT_UPLOAD_SPEED_LIMIT:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_LONG);
                    item.put(Constants.C_VALUE_INTEGER, parser.getLongValue());
                    break;
                case TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_ENABLED:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_BEGIN:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                    item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
                    break;
                case TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_END:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                    item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
                    break;
                case TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_DAY:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                    item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
                    break;
                case TransmissionSession.SetterFields.BLOCKLIST_ENABLED:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case "blocklist-size":
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_LONG);
                    item.put(Constants.C_VALUE_INTEGER, parser.getLongValue());
                    break;
                case TransmissionSession.SetterFields.BLOCKLIST_URL:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_STRING);
                    item.put(Constants.C_VALUE_TEXT, parser.getText());
                    break;
                case TransmissionSession.SetterFields.DHT:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case TransmissionSession.SetterFields.ENCRYPTION:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_STRING);
                    item.put(Constants.C_VALUE_TEXT, parser.getText());
                    break;
                case TransmissionSession.SetterFields.CACHE_SIZE:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_LONG);
                    item.put(Constants.C_VALUE_INTEGER, parser.getLongValue());
                    break;
                case "config-dir":
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_STRING);
                    item.put(Constants.C_VALUE_TEXT, parser.getText());
                    break;
                case TransmissionSession.SetterFields.DOWNLOAD_DIR:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_STRING);
                    item.put(Constants.C_VALUE_TEXT, parser.getText());
                    break;
                case "download-dir-free-space":
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_LONG);
                    item.put(Constants.C_VALUE_INTEGER, parser.getLongValue());
                    break;
                case TransmissionSession.SetterFields.DOWNLOAD_QUEUE_SIZE:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                    item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
                    break;
                case TransmissionSession.SetterFields.DOWNLOAD_QUEUE_ENABLED:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case TransmissionSession.SetterFields.IDLE_SEEDING_LIMIT:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_LONG);
                    item.put(Constants.C_VALUE_INTEGER, parser.getLongValue());
                    break;
                case TransmissionSession.SetterFields.IDLE_SEEDING_LIMIT_ENABLED:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case TransmissionSession.SetterFields.INCOMPLETE_DIR:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_STRING);
                    item.put(Constants.C_VALUE_TEXT, parser.getText());
                    break;
                case TransmissionSession.SetterFields.INCOMPLETE_DIR_ENABLED:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case TransmissionSession.SetterFields.LOCAL_DISCOVERY:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case TransmissionSession.SetterFields.UTP:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case TransmissionSession.SetterFields.GLOBAL_PEER_LIMIT:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                    item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
                    break;
                case TransmissionSession.SetterFields.TORRENT_PEER_LIMIT:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                    item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
                    break;
                case TransmissionSession.SetterFields.PEER_EXCHANGE:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case TransmissionSession.SetterFields.PEER_PORT:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                    item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
                    break;
                case TransmissionSession.SetterFields.RANDOM_PORT:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case TransmissionSession.SetterFields.PORT_FORWARDING:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case TransmissionSession.SetterFields.RENAME_PARTIAL:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case "rpc-version":
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                    item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
                    break;
                case "rpc-version-minimum":
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                    item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
                    break;
                case TransmissionSession.SetterFields.DONE_SCRIPT:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_STRING);
                    item.put(Constants.C_VALUE_TEXT, parser.getText());
                    break;
                case TransmissionSession.SetterFields.DONE_SCRIPT_ENABLED:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case TransmissionSession.SetterFields.SEED_QUEUE_SIZE:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                    item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
                    break;
                case TransmissionSession.SetterFields.SEED_QUEUE_ENABLED:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case TransmissionSession.SetterFields.SEED_RATIO_LIMIT:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_FLOAT);
                    item.put(Constants.C_VALUE_REAL, parser.getFloatValue());
                    break;
                case TransmissionSession.SetterFields.SEED_RATIO_LIMIT_ENABLED:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case TransmissionSession.SetterFields.DOWNLOAD_SPEED_LIMIT:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_LONG);
                    item.put(Constants.C_VALUE_INTEGER, parser.getLongValue());
                    break;
                case TransmissionSession.SetterFields.DOWNLOAD_SPEED_LIMIT_ENABLED:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case TransmissionSession.SetterFields.UPLOAD_SPEED_LIMIT:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_LONG);
                    item.put(Constants.C_VALUE_INTEGER, parser.getLongValue());
                    break;
                case TransmissionSession.SetterFields.UPLOAD_SPEED_LIMIT_ENABLED:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case TransmissionSession.SetterFields.STALLED_QUEUE_SIZE:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_INT);
                    item.put(Constants.C_VALUE_INTEGER, parser.getIntValue());
                    break;
                case TransmissionSession.SetterFields.STALLED_QUEUE_ENABLED:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case TransmissionSession.SetterFields.START_ADDED:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case TransmissionSession.SetterFields.TRASH_ORIGINAL:
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_BOOLEAN);
                    item.put(Constants.C_VALUE_INTEGER, parser.getBooleanValue());
                    break;
                case "version":
                    item.put(Constants.C_NAME, name);
                    item.put(Constants.C_VALUE_AFFINITY, Constants.TYPE_STRING);
                    item.put(Constants.C_VALUE_TEXT, parser.getText());
                    break;
                default:
                    valid = false;
                    parser.skipChildren();
                    break;
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

        int status = 0, peersConnected = 0, peersGettingFromUs = 0, peersSendingToUs = 0,
            seedRatioMode = Torrent.SeedRatioMode.GLOBAL_LIMIT;
        long eta = 0, leftUntilDone = 0, sizeWhenDone = 0, uploadedEver = 0,
            rateDownload = 0, rateUpload = 0;
        float metadataPercentComplete = 0, percentDone = 0, uploadRatio = 0,
            recheckProgress = 0, seedLimit = 0;
        boolean isStalled = false;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.getCurrentName();

            parser.nextToken();

            switch (name) {
                case "id":
                    torrent.put(Constants.C_TORRENT_ID, parser.getIntValue());
                    break;
                case "hashString":
                    torrent.put(Constants.C_HASH_STRING, parser.getText());
                    break;
                case "status":
                    status = parser.getIntValue();
                    if (session.getRPCVersion() < NEW_STATUS_RPC_VERSION) {
                        switch (status) {
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
                    break;
                case "name":
                    torrent.put(Constants.C_NAME, parser.getText());
                    break;
                case "error":
                    torrent.put(Constants.C_ERROR, parser.getIntValue());
                    break;
                case "errorString":
                    torrent.put(Constants.C_ERROR_STRING, parser.getText());
                    break;
                case "metadataPercentComplete":
                    metadataPercentComplete = parser.getFloatValue();
                    torrent.put(Constants.C_METADATA_PERCENT_COMPLETE, metadataPercentComplete);
                    break;
                case "percentDone":
                    percentDone = parser.getFloatValue();
                    torrent.put(Constants.C_PERCENT_DONE, percentDone);
                    break;
                case "eta":
                    eta = parser.getLongValue();
                    torrent.put(Constants.C_ETA, eta);
                    break;
                case "isFinished":
                    torrent.put(Constants.C_IS_FINISHED, parser.getBooleanValue());
                    break;
                case "isStalled":
                    isStalled = parser.getBooleanValue();
                    torrent.put(Constants.C_IS_STALLED, isStalled);
                    break;
                case "peersConnected":
                    peersConnected = parser.getIntValue();
                    torrent.put(Constants.C_PEERS_CONNECTED, peersConnected);
                    break;
                case "peersGettingFromUs":
                    peersGettingFromUs = parser.getIntValue();
                    torrent.put(Constants.C_PEERS_GETTING_FROM_US, peersGettingFromUs);
                    break;
                case "peersSendingToUs":
                    peersSendingToUs = parser.getIntValue();
                    torrent.put(Constants.C_PEERS_SENDING_TO_US, peersSendingToUs);
                    break;
                case "leftUntilDone":
                    leftUntilDone = parser.getLongValue();
                    torrent.put(Constants.C_LEFT_UNTIL_DONE, leftUntilDone);
                    break;
                case "desiredAvailable":
                    torrent.put(Constants.C_DESIRED_AVAILABLE, parser.getLongValue());
                    break;
                case "totalSize":
                    torrent.put(Constants.C_TOTAL_SIZE, parser.getLongValue());
                    break;
                case "sizeWhenDone":
                    sizeWhenDone = parser.getLongValue();
                    torrent.put(Constants.C_SIZE_WHEN_DONE, sizeWhenDone);
                    break;
                case "rateDownload":
                    rateDownload = parser.getLongValue();
                    torrent.put(Constants.C_RATE_DOWNLOAD, rateDownload);
                    break;
                case "rateUpload":
                    rateUpload = parser.getLongValue();
                    torrent.put(Constants.C_RATE_UPLOAD, rateUpload);
                    break;
                case Torrent.SetterFields.QUEUE_POSITION:
                    torrent.put(Constants.C_QUEUE_POSITION, parser.getIntValue());
                    break;
                case "recheckProgress":
                    recheckProgress = parser.getFloatValue();
                    torrent.put(Constants.C_RECHECK_PROGRESS, recheckProgress);
                    break;
                case Torrent.SetterFields.SEED_RATIO_MODE:
                    seedRatioMode = parser.getIntValue();
                    torrent.put(Constants.C_SEED_RATIO_MODE, seedRatioMode);
                    break;
                case Torrent.SetterFields.SEED_RATIO_LIMIT:
                    seedLimit = parser.getFloatValue();
                    torrent.put(Constants.C_SEED_RATIO_LIMIT, seedLimit);
                    break;
                case "uploadedEver":
                    uploadedEver = parser.getLongValue();
                    torrent.put(Constants.C_UPLOADED_EVER, uploadedEver);
                    break;
                case "uploadRatio":
                    uploadRatio = parser.getFloatValue();
                    torrent.put(Constants.C_UPLOAD_RATIO, uploadRatio);
                    break;
                case "addedDate":
                    torrent.put(Constants.C_ADDED_DATE, parser.getLongValue());
                    break;
                case "doneDate":
                    torrent.put(Constants.C_DONE_DATE, parser.getLongValue());
                    break;
                case "startDate":
                    torrent.put(Constants.C_START_DATE, parser.getLongValue());
                    break;
                case "activityDate":
                    torrent.put(Constants.C_ACTIVITY_DATE, parser.getLongValue());
                    break;
                case "corruptEver":
                    torrent.put(Constants.C_CORRUPT_EVER, parser.getLongValue());
                    break;
                case "downloadDir":
                    torrent.put(Constants.C_DOWNLOAD_DIR, parser.getText());
                    break;
                case "downloadedEver":
                    torrent.put(Constants.C_DOWNLOADED_EVER, parser.getLongValue());
                    break;
                case "haveUnchecked":
                    torrent.put(Constants.C_HAVE_UNCHECKED, parser.getLongValue());
                    break;
                case "haveValid":
                    torrent.put(Constants.C_HAVE_VALID, parser.getLongValue());
                    break;
                case "comment":
                    torrent.put(Constants.C_COMMENT, parser.getText());
                    break;
                case "creator":
                    torrent.put(Constants.C_CREATOR, parser.getText());
                    break;
                case "dateCreated":
                    torrent.put(Constants.C_DATE_CREATED, parser.getLongValue());
                    break;
                case "isPrivate":
                    torrent.put(Constants.C_IS_PRIVATE, parser.getBooleanValue());
                    break;
                case "pieceCount":
                    torrent.put(Constants.C_PIECE_COUNT, parser.getIntValue());
                    break;
                case "pieceSize":
                    torrent.put(Constants.C_PIECE_SIZE, parser.getLongValue());
                    break;
                case Torrent.SetterFields.TORRENT_PRIORITY:
                    torrent.put(Constants.C_TORRENT_PRIORITY, parser.getIntValue());
                    break;
                case Torrent.SetterFields.DOWNLOAD_LIMIT:
                    torrent.put(Constants.C_DOWNLOAD_LIMIT, parser.getLongValue());
                    break;
                case Torrent.SetterFields.DOWNLOAD_LIMITED:
                    torrent.put(Constants.C_DOWNLOAD_LIMITED, parser.getBooleanValue());
                    break;
                case Torrent.SetterFields.SESSION_LIMITS:
                    torrent.put(Constants.C_HONORS_SESSION_LIMITS, parser.getBooleanValue());
                    break;
                case Torrent.SetterFields.UPLOAD_LIMIT:
                    torrent.put(Constants.C_UPLOAD_LIMIT, parser.getLongValue());
                    break;
                case Torrent.SetterFields.UPLOAD_LIMITED:
                    torrent.put(Constants.C_UPLOAD_LIMITED, parser.getBooleanValue());
                    break;
                case "webseedsSendingToUs":
                    torrent.put(Constants.C_WEBSEEDS_SENDING_TO_US, parser.getIntValue());
                    break;
                case Torrent.SetterFields.PEER_LIMIT:
                    torrent.put(Constants.C_PEER_LIMIT, parser.getIntValue());
                    break;
                case "trackers": {
                    if (values.trackers == null) {
                        values.trackers = new ArrayList<>();
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

                            switch (argname) {
                                case "id":
                                    tracker.put(Constants.C_TRACKER_ID, parser.getIntValue());
                                    break;
                                case "announce":
                                    tracker.put(Constants.C_ANNOUNCE, parser.getText());
                                    break;
                                case "scrape":
                                    tracker.put(Constants.C_SCRAPE, parser.getText());
                                    break;
                                case "tier":
                                    tracker.put(Constants.C_TIER, parser.getIntValue());
                                    break;
                            }
                        }

                        ++index;
                    }
                    break;
                }
                case "trackerStats": {
                    if (values.trackers == null) {
                        values.trackers = new ArrayList<>();
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

                            switch (argname) {
                                case "id":
                                    tracker.put(Constants.C_TRACKER_ID, parser.getIntValue());
                                    break;
                                case "hasAnnounced":
                                    tracker.put(Constants.C_HAS_ANNOUNCED, parser.getBooleanValue());
                                    break;
                                case "lastAnnounceTime":
                                    tracker.put(Constants.C_LAST_ANNOUNCE_TIME, parser.getLongValue());
                                    break;
                                case "lastAnnounceSucceeded":
                                    tracker.put(Constants.C_LAST_ANNOUNCE_SUCCEEDED, parser.getBooleanValue());
                                    break;
                                case "lastAnnouncePeerCount":
                                    tracker.put(Constants.C_LAST_ANNOUNCE_PEER_COUNT, parser.getIntValue());
                                    break;
                                case "lastAnnounceResult":
                                    tracker.put(Constants.C_LAST_ANNOUNCE_RESULT, parser.getText());
                                    break;
                                case "hasScraped":
                                    tracker.put(Constants.C_HAS_SCRAPED, parser.getBooleanValue());
                                    break;
                                case "lastScrapeTime":
                                    tracker.put(Constants.C_LAST_SCRAPE_TIME, parser.getLongValue());
                                    break;
                                case "lastScrapeSucceeded":
                                    tracker.put(Constants.C_LAST_SCRAPE_SUCCEEDED, parser.getBooleanValue());
                                    break;
                                case "lastScrapeResult":
                                    tracker.put(Constants.C_LAST_SCRAPE_RESULT, parser.getText());
                                    break;
                                case "seederCount":
                                    tracker.put(Constants.C_SEEDER_COUNT, parser.getIntValue());
                                    break;
                                case "leecherCount":
                                    tracker.put(Constants.C_LEECHER_COUNT, parser.getIntValue());
                                    break;
                            }
                        }

                        ++index;
                    }
                    break;
                }
                case "files": {
                    if (values.files == null) {
                        values.files = new ArrayList<>();
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
                    break;
                }
                case "fileStats": {
                    if (values.files == null) {
                        values.files = new ArrayList<>();
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

                            switch (argname) {
                                case "bytesCompleted":
                                    file.put(Constants.C_BYTES_COMPLETED, parser.getLongValue());
                                    break;
                                case "wanted":
                                    file.put(Constants.C_WANTED, parser.getBooleanValue());
                                    break;
                                case "priority":
                                    file.put(Constants.C_PRIORITY, parser.getIntValue());
                                    break;
                            }
                        }

                        ++index;
                    }
                    break;
                }
                case "peers": {
                    if (values.peers == null) {
                        values.peers = new ArrayList<>();
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

                            switch (argname) {
                                case "address":
                                    peer.put(Constants.C_ADDRESS, parser.getText());
                                    break;
                                case "clientName":
                                    peer.put(Constants.C_CLIENT_NAME, parser.getText());
                                    break;
                                case "clientIsChoked":
                                    peer.put(Constants.C_CLIENT_IS_CHOKED, parser.getBooleanValue());
                                    break;
                                case "clientIsInterested":
                                    peer.put(Constants.C_CLIENT_IS_INTERESTED, parser.getBooleanValue());
                                    break;
                                case "isDownloadingFrom":
                                    peer.put(Constants.C_IS_DOWNLOADING_FROM, parser.getBooleanValue());
                                    break;
                                case "isEncrypted":
                                    peer.put(Constants.C_IS_ENCRYPTED, parser.getBooleanValue());
                                    break;
                                case "isIncoming":
                                    peer.put(Constants.C_IS_INCOMING, parser.getBooleanValue());
                                    break;
                                case "isUploadingTo":
                                    peer.put(Constants.C_IS_UPLOADING_TO, parser.getBooleanValue());
                                    break;
                                case "peerIsChoked":
                                    peer.put(Constants.C_PEER_IS_CHOKED, parser.getBooleanValue());
                                    break;
                                case "peerIsInterested":
                                    peer.put(Constants.C_PEER_IS_INTERESTED, parser.getBooleanValue());
                                    break;
                                case "port":
                                    peer.put(Constants.C_PORT, parser.getIntValue());
                                    break;
                                case "progress":
                                    peer.put(Constants.C_PROGRESS, parser.getFloatValue());
                                    break;
                                case "rateToClient":
                                    peer.put(Constants.C_RATE_TO_CLIENT, parser.getLongValue());
                                    break;
                                case "rateToPeer":
                                    peer.put(Constants.C_RATE_TO_PEER, parser.getLongValue());
                                    break;
                            }
                        }

                        ++index;
                    }
                    break;
                }
                default:
                    parser.skipChildren();
                    break;
            }

        }

        if (seedRatioMode == Torrent.SeedRatioMode.NO_LIMIT) {
            seedLimit = 0;
        } else if (seedRatioMode == Torrent.SeedRatioMode.GLOBAL_LIMIT) {
            seedLimit = session.isSeedRatioLimitEnabled() ? session.getSeedRatioLimit() : 0;
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
                statusText = context.getString(uploadRatio < seedLimit
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

    protected void removeTorrent(String hash) {
        database.delete(Constants.T_TORRENT, Constants.C_HASH_STRING + " = ?",
            new String[] { hash } );
    }

    protected void removeTorrent(String profile, int id) {
        /* Delete the torrent for a given profile and torrent id
            DELETE FROM torrent
            WHERE hash_string IN (
                SELECT torrent.hash_string FROM torrent_profile JOIN torrent
                ON torrent_profile.hash_string = torrent.hash_string
                WHERE profile_id = ? AND torrent_id = ?
            )
        */
        database.delete(Constants.T_TORRENT, Constants.C_HASH_STRING
                + " IN ("
                + " SELECT " + Constants.T_TORRENT + "." + Constants.C_HASH_STRING
                + " FROM " + Constants.T_TORRENT_PROFILE
                + " JOIN " + Constants.T_TORRENT
                + " ON " + Constants.T_TORRENT_PROFILE + "." + Constants.C_HASH_STRING
                + " = " + Constants.T_TORRENT + "." + Constants.C_HASH_STRING
                + " WHERE " + Constants.C_PROFILE_ID + " = ?"
                + " AND " + Constants.C_TORRENT_ID + " = ?"
                + ")",
            new String[] { profile, Integer.toString(id) });
    }

    protected void removeObsolete(String profile, List<String> validHashStrings) {
        String[] args = new String[] { profile };
        List<String> where = new ArrayList<>();

        for (String hash : validHashStrings) {
            where.add(DatabaseUtils.sqlEscapeString(hash));
        }

        database.delete(Constants.T_TORRENT, Constants.C_HASH_STRING + " NOT IN ("
            + TextUtils.join(", ", where)
            + ") AND " + Constants.C_HASH_STRING
            + " IN ("
            + " SELECT " + Constants.C_HASH_STRING
            + " FROM " + Constants.T_TORRENT_PROFILE + " t1"
            + " WHERE NOT EXISTS ("
            + " SELECT 1"
            + " FROM " + Constants.T_TORRENT_PROFILE + " t2"
            + " WHERE t1." + Constants.C_HASH_STRING + " = t2." + Constants.C_HASH_STRING
            + " AND t1." + Constants.C_PROFILE_ID + " != t2." + Constants.C_PROFILE_ID
            + ")"
            + " AND t1." + Constants.C_PROFILE_ID + " = ?"
            + ")",
            args);

        database.delete(Constants.T_TORRENT_PROFILE, Constants.C_HASH_STRING + " NOT IN ("
            + TextUtils.join(", ", where)
            + ") AND " + Constants.C_PROFILE_ID + " = ?", args);
    }

    protected int[] queryTorrentIdChanges() {
        Cursor cursor = null;
        try {
            cursor = database.rawQuery(
                "SELECT max(rowid), count(rowid) FROM " + Constants.T_TORRENT, null
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

    protected TorrentCursorArgs getTorrentCursorArgs(SharedPreferences prefs) {
        TorrentCursorArgs args = new TorrentCursorArgs();

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
                switch (pref) {
                    case "ASCENDING":
                        baseSortOrder = G.SortOrder.ASCENDING;
                        break;
                    case "DESCENDING":
                        baseSortOrder = G.SortOrder.DESCENDING;
                        break;
                    case "PRIMARY":
                        baseSortOrder = sortOrder;
                        break;
                    case "REVERSE":
                        baseSortOrder = sortOrder == G.SortOrder.ASCENDING
                            ? G.SortOrder.DESCENDING
                            : G.SortOrder.ASCENDING;
                        break;
                }
            }
        }

        List<String> selection = new ArrayList<>();
        List<String> selectionArgs = new ArrayList<>();
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

        if (!TextUtils.isEmpty(tracker)) {
            selection.add(Constants.T_TORRENT + "." + Constants.C_HASH_STRING + " IN ("
                + "SELECT " + Constants.C_HASH_STRING
                + " FROM " + Constants.T_TRACKER
                + " WHERE " + Constants.C_ANNOUNCE + " LIKE "
                + DatabaseUtils.sqlEscapeString("%" + tracker + "%")
                + ")");
        }

        if (filter != G.FilterBy.ALL) {
            switch (filter) {
                case DOWNLOADING:
                    selection.add(Constants.C_STATUS + " = ?");
                    selectionArgs.add(Integer.toString(Torrent.Status.DOWNLOADING));
                    break;
                case SEEDING:
                    selection.add(Constants.C_STATUS + " = ?");
                    selectionArgs.add(Integer.toString(Torrent.Status.SEEDING));
                    break;
                case PAUSED:
                    selection.add(Constants.C_STATUS + " = ?");
                    selectionArgs.add(Integer.toString(Torrent.Status.STOPPED));
                    break;
                case COMPLETE:
                    selection.add(Constants.C_PERCENT_DONE + " = 1");
                    break;
                case INCOMPLETE:
                    selection.add(Constants.C_PERCENT_DONE + " < 1");
                    break;
                case ACTIVE:
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
                    break;
                case CHECKING:
                    selection.add(Constants.C_STATUS + " = ?");
                    selectionArgs.add(Integer.toString(Torrent.Status.CHECKING));
                    break;
                case ERRORS:
                    selection.add(Constants.C_ERROR + " != 0");
                    break;
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
                    + " THEN (CASE WHEN "
                    + Constants.C_SEED_RATIO_MODE + " = " + Torrent.SeedRatioMode.NO_LIMIT
                    + " OR ("
                    + Constants.C_SEED_RATIO_MODE + " = " + Torrent.SeedRatioMode.GLOBAL_LIMIT
                    + " AND "
                    + Constants.C_UPLOAD_RATIO + " < " + Float.toString(session.getSeedRatioLimit())
                    + ") OR "
                    + Constants.C_UPLOAD_RATIO + " < " + Constants.C_SEED_RATIO_LIMIT
                    + " THEN " + (Torrent.Status.STOPPED + 40)
                    + " ELSE " + (Torrent.Status.STOPPED + 50)
                    + " END)"
                    + " WHEN " + Torrent.Status.CHECK_WAITING
                    + " THEN " + (Torrent.Status.CHECK_WAITING + 100)
                    + " WHEN " + Torrent.Status.DOWNLOAD_WAITING
                    + " THEN " + (Torrent.Status.DOWNLOAD_WAITING + 10)
                    + " WHEN " + Torrent.Status.SEED_WAITING
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
