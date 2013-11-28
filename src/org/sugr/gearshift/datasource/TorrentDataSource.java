package org.sugr.gearshift.datasource;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

import org.sugr.gearshift.Torrent;

import java.util.Map;

class TorrentValues {
    public ContentValues torrent;
    public ContentValues[] files;
    public ContentValues[] trackers;
    public ContentValues[] peers;
}

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
                TorrentValues values = jsonToTorrentValues(torrentData);

                database.insertWithOnConflict(Constants.T_TORRENTS, null,
                    values.torrent, SQLiteDatabase.CONFLICT_REPLACE);

                if (values.files != null) {
                    for (ContentValues file : values.files) {
                        database.insertWithOnConflict(Constants.T_FILES, null,
                            file, SQLiteDatabase.CONFLICT_REPLACE);
                    }
                }

                if (values.trackers != null) {
                    for (ContentValues tracker : values.trackers) {
                        database.insertWithOnConflict(Constants.T_TRACKERS, null,
                            tracker, SQLiteDatabase.CONFLICT_REPLACE);
                    }
                }

                if (values.peers != null) {
                    for (ContentValues peer : values.peers) {
                        database.insertWithOnConflict(Constants.T_PEERS, null,
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
    protected TorrentValues jsonToTorrentValues(JSONObject torrentData) {
        TorrentValues values = new TorrentValues();
        ContentValues torrent = new ContentValues();

        values.torrent = torrent;

        if (torrentData.containsKey("files")) {
            if (torrentData.containsKey("fileStats")) {
            }
        }
        if (torrentData.containsKey("trackers")) {
            if (torrentData.containsKey("trackerStats")) {
            }
        }
        if (torrentData.containsKey("peers")) {
        }

        for (Map.Entry<String, Object> entry : torrentData.entrySet()) {
            if (entry.getKey().equals("id")) {
                torrent.put(Constants.COLUMN_TORRENT_ID, (Integer) entry.getValue());
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
                torrent.put(Constants.COLUMN_STATUS, status);
            } else if (entry.getKey().equals("name")) {
                torrent.put(Constants.COLUMN_NAME, (String) entry.getValue());
            } else if (entry.getKey().equals("error")) {
                torrent.put(Constants.COLUMN_ERROR, (Integer) entry.getValue());
            } else if (entry.getKey().equals("errorString")) {
                torrent.put(Constants.COLUMN_ERROR_STRING, (String) entry.getValue());
            } else if (entry.getKey().equals("metadataPercentComplete")) {
                torrent.put(Constants.COLUMN_METADATA_PERCENT_COMPLETE, (Float) entry.getValue());
            } else if (entry.getKey().equals("percentDone")) {
                torrent.put(Constants.COLUMN_PERCENT_DONE, (Float) entry.getValue());
            } else if (entry.getKey().equals("eta")) {
                torrent.put(Constants.COLUMN_ETA, (Long) entry.getValue());
            } else if (entry.getKey().equals("isFinished")) {
                torrent.put(Constants.COLUMN_IS_FINISHED, (Boolean) entry.getValue());
            } else if (entry.getKey().equals("isStalled")) {
                torrent.put(Constants.COLUMN_IS_STALLED, (Boolean) entry.getValue());
            } else if (entry.getKey().equals("peersConnected")) {
                torrent.put(Constants.COLUMN_PEERS_CONNECTED, (Integer) entry.getValue());
            } else if (entry.getKey().equals("peersGettingFromUs")) {
                torrent.put(Constants.COLUMN_PEERS_GETTING_FROM_US, (Integer) entry.getValue());
            } else if (entry.getKey().equals("peersSendingToUs")) {
                torrent.put(Constants.COLUMN_PEERS_SENDING_TO_US, (Integer) entry.getValue());
            } else if (entry.getKey().equals("leftUntilDone")) {
                torrent.put(Constants.COLUMN_LEFT_UNTIL_DONE, (Long) entry.getValue());
            } else if (entry.getKey().equals("desiredAvailable")) {
                torrent.put(Constants.COLUMN_DESIRED_AVAILABLE, (Long) entry.getValue());
            } else if (entry.getKey().equals("totalSize")) {
                torrent.put(Constants.COLUMN_TOTAL_SIZE, (Long) entry.getValue());
            } else if (entry.getKey().equals("sizeWhenDone")) {
                torrent.put(Constants.COLUMN_SIZE_WHEN_DONE, (Long) entry.getValue());
            } else if (entry.getKey().equals("rateDownload")) {
                torrent.put(Constants.COLUMN_RATE_DOWNLOAD, (Long) entry.getValue());
            } else if (entry.getKey().equals("rateUpload")) {
                torrent.put(Constants.COLUMN_RATE_UPLOAD, (Long) entry.getValue());
            } else if (entry.getKey().equals(Torrent.SetterFields.QUEUE_POSITION)) {
                torrent.put(Constants.COLUMN_QUEUE_POSITION, (Integer) entry.getValue());
            } else if (entry.getKey().equals("recheckProgress")) {
                torrent.put(Constants.COLUMN_RECHECK_PROGRESS, (Float) entry.getValue());
            } else if (entry.getKey().equals(Torrent.SetterFields.SEED_RATIO_MODE)) {
                torrent.put(Constants.COLUMN_SEED_RATIO_MODE, (Integer) entry.getValue());
            } else if (entry.getKey().equals(Torrent.SetterFields.SEED_RATIO_LIMIT)) {
                torrent.put(Constants.COLUMN_SEED_RATIO_LIMIT, (Float) entry.getValue());
            } else if (entry.getKey().equals("uploadedEver")) {
                torrent.put(Constants.COLUMN_UPLOADED_EVER, (Long) entry.getValue());
            } else if (entry.getKey().equals("uploadRatio")) {
                torrent.put(Constants.COLUMN_UPLOAD_RATIO, (Float) entry.getValue());
            } else if (entry.getKey().equals("addedDate")) {
                torrent.put(Constants.COLUMN_ADDED_DATE, (Long) entry.getValue());
            } else if (entry.getKey().equals("doneDate")) {
                torrent.put(Constants.COLUMN_DONE_DATE, (Long) entry.getValue());
            } else if (entry.getKey().equals("startDate")) {
                torrent.put(Constants.COLUMN_START_DATE, (Long) entry.getValue());
            } else if (entry.getKey().equals("activityDate")) {
                torrent.put(Constants.COLUMN_ACTIVITY_DATE, (Long) entry.getValue());
            } else if (entry.getKey().equals("corruptEver")) {
                torrent.put(Constants.COLUMN_CORRUPT_EVER, (Long) entry.getValue());
            } else if (entry.getKey().equals("downloadDir")) {
                torrent.put(Constants.COLUMN_DOWNLOAD_DIR, (String) entry.getValue());
            } else if (entry.getKey().equals("downloadedEver")) {
                torrent.put(Constants.COLUMN_DOWNLOADED_EVER, (Long) entry.getValue());
            } else if (entry.getKey().equals("haveUnchecked")) {
                torrent.put(Constants.COLUMN_HAVE_UNCHECKED, (Long) entry.getValue());
            } else if (entry.getKey().equals("haveValid")) {
                torrent.put(Constants.COLUMN_HAVE_VALID, (Long) entry.getValue());
            } else if (entry.getKey().equals("comment")) {
                torrent.put(Constants.COLUMN_COMMENT, (String) entry.getValue());
            } else if (entry.getKey().equals("creator")) {
                torrent.put(Constants.COLUMN_CREATOR, (String) entry.getValue());
            } else if (entry.getKey().equals("dateCreated")) {
                torrent.put(Constants.COLUMN_DATE_CREATED, (Long) entry.getValue());
            } else if (entry.getKey().equals("hashString")) {
                torrent.put(Constants.COLUMN_HASH_STRING, (String) entry.getValue());
            } else if (entry.getKey().equals("isPrivate")) {
                torrent.put(Constants.COLUMN_IS_PRIVATE, (Boolean) entry.getValue());
            } else if (entry.getKey().equals("pieceCount")) {
                torrent.put(Constants.COLUMN_PIECE_COUNT, (Integer) entry.getValue());
            } else if (entry.getKey().equals("pieceSize")) {
                torrent.put(Constants.COLUMN_PIECE_SIZE, (Long) entry.getValue());
            } else if (entry.getKey().equals(Torrent.SetterFields.TORRENT_PRIORITY)) {
                torrent.put(Constants.COLUMN_TORRENT_PRIORITY, (Integer) entry.getValue());
            } else if (entry.getKey().equals(Torrent.SetterFields.DOWNLOAD_LIMIT)) {
                torrent.put(Constants.COLUMN_DOWNLOAD_LIMIT, (Long) entry.getValue());
            } else if (entry.getKey().equals(Torrent.SetterFields.DOWNLOAD_LIMITED)) {
                torrent.put(Constants.COLUMN_DOWNLOAD_LIMITED, (Boolean) entry.getValue());
            } else if (entry.getKey().equals(Torrent.SetterFields.SESSION_LIMITS)) {
                torrent.put(Constants.COLUMN_HONORS_SESSION_LIMITS, (Boolean) entry.getValue());
            } else if (entry.getKey().equals(Torrent.SetterFields.UPLOAD_LIMIT)) {
                torrent.put(Constants.COLUMN_UPLOAD_LIMIT, (Long) entry.getValue());
            } else if (entry.getKey().equals(Torrent.SetterFields.UPLOAD_LIMITED)) {
                torrent.put(Constants.COLUMN_UPLOAD_LIMITED, (Boolean) entry.getValue());
            } else if (entry.getKey().equals("webseedsSendingToUs")) {
                torrent.put(Constants.COLUMN_WEBSEEDS_SENDING_TO_US, (Integer) entry.getValue());
            } else if (entry.getKey().equals(Torrent.SetterFields.PEER_LIMIT)) {
                torrent.put(Constants.COLUMN_PEER_LIMIT, (Integer) entry.getValue());
            }

            /* TODO: trafficText, statusText */
        }

        return values;
    }
}
