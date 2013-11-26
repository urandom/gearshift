package org.sugr.gearshift.datasource;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

import org.sugr.gearshift.Torrent;

import java.util.Map;

public class TorrentDataSource {
    private SQLiteDatabase database;

    private SQLiteHelper dbHelper;
    private Context context;

    /* Transmission stuff */
    private static final int NEW_STATUS_RPC_VERSION = 14;

    private int rpcVersion;

    public TorrentDataSource(Context context) {
        this.context = context;
        this.dbHelper = new SQLiteHelper(context);

        rpcVersion = -1;
    }

    public void open() {
        database = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    public void setRPCVersion(int version) {
        rpcVersion = version;
    }

    public void updateTorrents(JSONArray torrentsData) {
        database.beginTransaction();

        try {
            for (int i = 0; i > torrentsData.size(); ++i) {
                JSONObject torrentData = (JSONObject) torrentsData.get(i);
                ContentValues values = jsonToValues(torrentData);

                database.insertWithOnConflict(Constants.T_TORRENTS, null,
                    values, SQLiteDatabase.CONFLICT_REPLACE);
            }

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    /* Transmission implementation */
    protected ContentValues jsonToValues(JSONObject torrentData) {
        ContentValues values = new ContentValues();

        for (Map.Entry<String, Object> entry : torrentData.entrySet()) {
            if (entry.getKey().equals("id")) {
                values.put(Constants.COLUMN_TORRENT_ID, (Integer) entry.getValue());
            } else if (entry.getKey().equals("status")) {
                int status = entry.getValue() == null
                    ? Torrent.Status.STOPPED : ((Integer) entry.getValue());
                if (rpcVersion > -1 && rpcVersion < NEW_STATUS_RPC_VERSION) {
                    switch(status) {
                        case Torrent.OldStatus.CHECK_WAITING:
                            status = Torrent.Status.CHECK_WAITING;
                        case Torrent.OldStatus.CHECKING:
                            status = Torrent.Status.CHECKING;
                        case Torrent.OldStatus.DOWNLOADING:
                            status = Torrent.Status.DOWNLOADING;
                        case Torrent.OldStatus.SEEDING:
                            status = Torrent.Status.SEEDING;
                        case Torrent.OldStatus.STOPPED:
                            status = Torrent.Status.STOPPED;
                    }
                }
                values.put(Constants.COLUMN_STATUS, status);
            } else if (entry.getKey().equals("name")) {
                values.put(Constants.COLUMN_NAME, (String) entry.getValue());
            } else if (entry.getKey().equals("error")) {
                values.put(Constants.COLUMN_ERROR, (Integer) entry.getValue());
            } else if (entry.getKey().equals("errorString")) {
                values.put(Constants.COLUMN_ERROR_STRING, (String) entry.getValue());
            } else if (entry.getKey().equals("metadataPercentComplete")) {
                values.put(Constants.COLUMN_METADATA_PERCENT_COMPLETE, (Float) entry.getValue());
            } else if (entry.getKey().equals("percentDone")) {
                values.put(Constants.COLUMN_PERCENT_DONE, (Float) entry.getValue());
            } else if (entry.getKey().equals("eta")) {
                values.put(Constants.COLUMN_ETA, (Long) entry.getValue());
            } else if (entry.getKey().equals("isFinished")) {
                values.put(Constants.COLUMN_IS_FINISHED, (Boolean) entry.getValue());
            } else if (entry.getKey().equals("isStalled")) {
                values.put(Constants.COLUMN_IS_STALLED, (Boolean) entry.getValue());
            } else if (entry.getKey().equals("peersConnected")) {
                values.put(Constants.COLUMN_PEERS_CONNECTED, (Integer) entry.getValue());
            } else if (entry.getKey().equals("peersGettingFromUs")) {
                values.put(Constants.COLUMN_PEERS_GETTING_FROM_US, (Integer) entry.getValue());
            } else if (entry.getKey().equals("peersSendingToUs")) {
                values.put(Constants.COLUMN_PEERS_SENDING_TO_US, (Integer) entry.getValue());
            } else if (entry.getKey().equals("leftUntilDone")) {
                values.put(Constants.COLUMN_LEFT_UNTIL_DONE, (Long) entry.getValue());
            } else if (entry.getKey().equals("desiredAvailable")) {
                values.put(Constants.COLUMN_DESIRED_AVAILABLE, (Long) entry.getValue());
            } else if (entry.getKey().equals("totalSize")) {
                values.put(Constants.COLUMN_TOTAL_SIZE, (Long) entry.getValue());
            } else if (entry.getKey().equals("sizeWhenDone")) {
                values.put(Constants.COLUMN_SIZE_WHEN_DONE, (Long) entry.getValue());
            } else if (entry.getKey().equals("rateDownload")) {
                values.put(Constants.COLUMN_RATE_DOWNLOAD, (Long) entry.getValue());
            } else if (entry.getKey().equals("rateUpload")) {
                values.put(Constants.COLUMN_RATE_UPLOAD, (Long) entry.getValue());
            } else if (entry.getKey().equals(Torrent.SetterFields.QUEUE_POSITION)) {
                values.put(Constants.COLUMN_QUEUE_POSITION, (Integer) entry.getValue());
            } else if (entry.getKey().equals("recheckProgress")) {
                values.put(Constants.COLUMN_RECHECK_PROGRESS, (Float) entry.getValue());
            } else if (entry.getKey().equals(Torrent.SetterFields.SEED_RATIO_MODE)) {
                values.put(Constants.COLUMN_SEED_RATIO_MODE, (Integer) entry.getValue());
            } else if (entry.getKey().equals(Torrent.SetterFields.SEED_RATIO_LIMIT)) {
                values.put(Constants.COLUMN_SEED_RATIO_LIMIT, (Float) entry.getValue());
            } else if (entry.getKey().equals("uploadedEver")) {
                values.put(Constants.COLUMN_UPLOADED_EVER, (Long) entry.getValue());
            } else if (entry.getKey().equals("uploadRatio")) {
                values.put(Constants.COLUMN_UPLOAD_RATIO, (Float) entry.getValue());
            } else if (entry.getKey().equals("addedDate")) {
                values.put(Constants.COLUMN_ADDED_DATE, (Long) entry.getValue());
            } else if (entry.getKey().equals("doneDate")) {
                values.put(Constants.COLUMN_DONE_DATE, (Long) entry.getValue());
            } else if (entry.getKey().equals("startDate")) {
                values.put(Constants.COLUMN_START_DATE, (Long) entry.getValue());
            } else if (entry.getKey().equals("activityDate")) {
                values.put(Constants.COLUMN_ACTIVITY_DATE, (Long) entry.getValue());
            } else if (entry.getKey().equals("corruptEver")) {
                values.put(Constants.COLUMN_CORRUPT_EVER, (Long) entry.getValue());
            } else if (entry.getKey().equals("downloadDir")) {
                values.put(Constants.COLUMN_DOWNLOAD_DIR, (String) entry.getValue());
            } else if (entry.getKey().equals("downloadedEver")) {
                values.put(Constants.COLUMN_DOWNLOADED_EVER, (Long) entry.getValue());
            } else if (entry.getKey().equals("haveUnchecked")) {
                values.put(Constants.COLUMN_HAVE_UNCHECKED, (Long) entry.getValue());
            } else if (entry.getKey().equals("haveValid")) {
                values.put(Constants.COLUMN_HAVE_VALID, (Long) entry.getValue());
            } else if (entry.getKey().equals("trackers")) {
                /* TODO */
            } else if (entry.getKey().equals("trackerStats")) {
                /* TODO */
            } else if (entry.getKey().equals("comment")) {
                values.put(Constants.COLUMN_COMMENT, (String) entry.getValue());
            } else if (entry.getKey().equals("creator")) {
                values.put(Constants.COLUMN_CREATOR, (String) entry.getValue());
            } else if (entry.getKey().equals("dateCreated")) {
                values.put(Constants.COLUMN_DATE_CREATED, (Long) entry.getValue());
            } else if (entry.getKey().equals("files")) {
                /* TODO */
            } else if (entry.getKey().equals("fileStats")) {
                /* TODO */
            } else if (entry.getKey().equals("hashString")) {
                values.put(Constants.COLUMN_HASH_STRING, (String) entry.getValue());
            } else if (entry.getKey().equals("isPrivate")) {
                values.put(Constants.COLUMN_IS_PRIVATE, (Boolean) entry.getValue());
            } else if (entry.getKey().equals("pieceCount")) {
                values.put(Constants.COLUMN_PIECE_COUNT, (Integer) entry.getValue());
            } else if (entry.getKey().equals("pieceSize")) {
                values.put(Constants.COLUMN_PIECE_SIZE, (Long) entry.getValue());
            } else if (entry.getKey().equals(Torrent.SetterFields.TORRENT_PRIORITY)) {
                values.put(Constants.COLUMN_TORRENT_PRIORITY, (Integer) entry.getValue());
            } else if (entry.getKey().equals(Torrent.SetterFields.DOWNLOAD_LIMIT)) {
                values.put(Constants.COLUMN_DOWNLOAD_LIMIT, (Long) entry.getValue());
            } else if (entry.getKey().equals(Torrent.SetterFields.DOWNLOAD_LIMITED)) {
                values.put(Constants.COLUMN_DOWNLOAD_LIMITED, (Boolean) entry.getValue());
            } else if (entry.getKey().equals(Torrent.SetterFields.SESSION_LIMITS)) {
                values.put(Constants.COLUMN_HONORS_SESSION_LIMITS, (Boolean) entry.getValue());
            } else if (entry.getKey().equals(Torrent.SetterFields.UPLOAD_LIMIT)) {
                values.put(Constants.COLUMN_UPLOAD_LIMIT, (Long) entry.getValue());
            } else if (entry.getKey().equals(Torrent.SetterFields.UPLOAD_LIMITED)) {
                values.put(Constants.COLUMN_UPLOAD_LIMITED, (Boolean) entry.getValue());
            } else if (entry.getKey().equals("webseedsSendingToUs")) {
                values.put(Constants.COLUMN_WEBSEEDS_SENDING_TO_US, (Integer) entry.getValue());
            } else if (entry.getKey().equals("peers")) {
                /* TODO */
            } else if (entry.getKey().equals(Torrent.SetterFields.PEER_LIMIT)) {
                values.put(Constants.COLUMN_PEER_LIMIT, (Integer) entry.getValue());
            }

            /* TODO: trafficText, statusText */
        }

        return values;
    }
}
