package org.sugr.gearshift.datasource;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.SparseArray;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import org.sugr.gearshift.Torrent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class TorrentValues {
    public ContentValues torrent;
    public List<ContentValues> files;
    public List<ContentValues> trackers;
    public List<ContentValues> peers;
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

    public void updateTorrents(JsonParser parser) throws IOException {
        database.beginTransaction();

        try {
            SparseArray<Boolean> trackers = new SparseArray<Boolean>();

            while (parser.nextToken() != JsonToken.END_ARRAY) {
                TorrentValues values = jsonToTorrentValues(parser);

                int torrentId = (Integer) values.torrent.get(Constants.COLUMN_TORRENT_ID);

                if (values.trackers != null) {
                    for (ContentValues tracker : values.trackers) {
                        int trackerId = (Integer) tracker.get(Constants.COLUMN_TRACKER_ID);
                        if (!trackers.get(trackerId, false)) {
                            trackers.put(trackerId, true);

                            database.insertWithOnConflict(Constants.T_TRACKER, null,
                                tracker, SQLiteDatabase.CONFLICT_REPLACE);
                        }

                        ContentValues m2m = new ContentValues();
                        m2m.put(Constants.COLUMN_TORRENT_ID, torrentId);
                        m2m.put(Constants.COLUMN_TRACKER_ID, trackerId);
                        database.insertWithOnConflict(Constants.T_TORRENT_TRACKER, null,
                            m2m, SQLiteDatabase.CONFLICT_REPLACE);
                    }
                }

                database.insertWithOnConflict(Constants.T_TORRENT, null,
                    values.torrent, SQLiteDatabase.CONFLICT_REPLACE);

                if (values.files != null) {
                    for (ContentValues file : values.files) {
                        database.insertWithOnConflict(Constants.T_FILE, null,
                            file, SQLiteDatabase.CONFLICT_REPLACE);
                    }
                }

                if (values.peers != null) {
                    for (ContentValues peer : values.peers) {
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
    protected TorrentValues jsonToTorrentValues(JsonParser parser) throws IOException {
        TorrentValues values = new TorrentValues();
        ContentValues torrent = new ContentValues();

        values.torrent = torrent;

        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new IOException("The server data is expected to be an object");
        }

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.getCurrentName();
            
            parser.nextToken();

            if (name.equals("id")) {
                torrent.put(Constants.COLUMN_TORRENT_ID, parser.getIntValue());
            } else if (name.equals("status")) {
                int status = parser.getIntValue();
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
            } else if (name.equals("name")) {
                torrent.put(Constants.COLUMN_NAME, parser.getText());
            } else if (name.equals("error")) {
                torrent.put(Constants.COLUMN_ERROR, parser.getIntValue());
            } else if (name.equals("errorString")) {
                torrent.put(Constants.COLUMN_ERROR_STRING, parser.getText());
            } else if (name.equals("metadataPercentComplete")) {
                torrent.put(Constants.COLUMN_METADATA_PERCENT_COMPLETE, parser.getFloatValue());
            } else if (name.equals("percentDone")) {
                torrent.put(Constants.COLUMN_PERCENT_DONE, parser.getFloatValue());
            } else if (name.equals("eta")) {
                torrent.put(Constants.COLUMN_ETA, parser.getLongValue());
            } else if (name.equals("isFinished")) {
                torrent.put(Constants.COLUMN_IS_FINISHED, parser.getBooleanValue());
            } else if (name.equals("isStalled")) {
                torrent.put(Constants.COLUMN_IS_STALLED, parser.getBooleanValue());
            } else if (name.equals("peersConnected")) {
                torrent.put(Constants.COLUMN_PEERS_CONNECTED, parser.getIntValue());
            } else if (name.equals("peersGettingFromUs")) {
                torrent.put(Constants.COLUMN_PEERS_GETTING_FROM_US, parser.getIntValue());
            } else if (name.equals("peersSendingToUs")) {
                torrent.put(Constants.COLUMN_PEERS_SENDING_TO_US, parser.getIntValue());
            } else if (name.equals("leftUntilDone")) {
                torrent.put(Constants.COLUMN_LEFT_UNTIL_DONE, parser.getLongValue());
            } else if (name.equals("desiredAvailable")) {
                torrent.put(Constants.COLUMN_DESIRED_AVAILABLE, parser.getLongValue());
            } else if (name.equals("totalSize")) {
                torrent.put(Constants.COLUMN_TOTAL_SIZE, parser.getLongValue());
            } else if (name.equals("sizeWhenDone")) {
                torrent.put(Constants.COLUMN_SIZE_WHEN_DONE, parser.getLongValue());
            } else if (name.equals("rateDownload")) {
                torrent.put(Constants.COLUMN_RATE_DOWNLOAD, parser.getLongValue());
            } else if (name.equals("rateUpload")) {
                torrent.put(Constants.COLUMN_RATE_UPLOAD, parser.getLongValue());
            } else if (name.equals(Torrent.SetterFields.QUEUE_POSITION)) {
                torrent.put(Constants.COLUMN_QUEUE_POSITION, parser.getIntValue());
            } else if (name.equals("recheckProgress")) {
                torrent.put(Constants.COLUMN_RECHECK_PROGRESS, parser.getFloatValue());
            } else if (name.equals(Torrent.SetterFields.SEED_RATIO_MODE)) {
                torrent.put(Constants.COLUMN_SEED_RATIO_MODE, parser.getIntValue());
            } else if (name.equals(Torrent.SetterFields.SEED_RATIO_LIMIT)) {
                torrent.put(Constants.COLUMN_SEED_RATIO_LIMIT, parser.getFloatValue());
            } else if (name.equals("uploadedEver")) {
                torrent.put(Constants.COLUMN_UPLOADED_EVER, parser.getLongValue());
            } else if (name.equals("uploadRatio")) {
                torrent.put(Constants.COLUMN_UPLOAD_RATIO, parser.getFloatValue());
            } else if (name.equals("addedDate")) {
                torrent.put(Constants.COLUMN_ADDED_DATE, parser.getLongValue());
            } else if (name.equals("doneDate")) {
                torrent.put(Constants.COLUMN_DONE_DATE, parser.getLongValue());
            } else if (name.equals("startDate")) {
                torrent.put(Constants.COLUMN_START_DATE, parser.getLongValue());
            } else if (name.equals("activityDate")) {
                torrent.put(Constants.COLUMN_ACTIVITY_DATE, parser.getLongValue());
            } else if (name.equals("corruptEver")) {
                torrent.put(Constants.COLUMN_CORRUPT_EVER, parser.getLongValue());
            } else if (name.equals("downloadDir")) {
                torrent.put(Constants.COLUMN_DOWNLOAD_DIR, parser.getText());
            } else if (name.equals("downloadedEver")) {
                torrent.put(Constants.COLUMN_DOWNLOADED_EVER, parser.getLongValue());
            } else if (name.equals("haveUnchecked")) {
                torrent.put(Constants.COLUMN_HAVE_UNCHECKED, parser.getLongValue());
            } else if (name.equals("haveValid")) {
                torrent.put(Constants.COLUMN_HAVE_VALID, parser.getLongValue());
            } else if (name.equals("comment")) {
                torrent.put(Constants.COLUMN_COMMENT, parser.getText());
            } else if (name.equals("creator")) {
                torrent.put(Constants.COLUMN_CREATOR, parser.getText());
            } else if (name.equals("dateCreated")) {
                torrent.put(Constants.COLUMN_DATE_CREATED, parser.getLongValue());
            } else if (name.equals("hashString")) {
                torrent.put(Constants.COLUMN_HASH_STRING, parser.getText());
            } else if (name.equals("isPrivate")) {
                torrent.put(Constants.COLUMN_IS_PRIVATE, parser.getBooleanValue());
            } else if (name.equals("pieceCount")) {
                torrent.put(Constants.COLUMN_PIECE_COUNT, parser.getIntValue());
            } else if (name.equals("pieceSize")) {
                torrent.put(Constants.COLUMN_PIECE_SIZE, parser.getLongValue());
            } else if (name.equals(Torrent.SetterFields.TORRENT_PRIORITY)) {
                torrent.put(Constants.COLUMN_TORRENT_PRIORITY, parser.getIntValue());
            } else if (name.equals(Torrent.SetterFields.DOWNLOAD_LIMIT)) {
                torrent.put(Constants.COLUMN_DOWNLOAD_LIMIT, parser.getLongValue());
            } else if (name.equals(Torrent.SetterFields.DOWNLOAD_LIMITED)) {
                torrent.put(Constants.COLUMN_DOWNLOAD_LIMITED, parser.getBooleanValue());
            } else if (name.equals(Torrent.SetterFields.SESSION_LIMITS)) {
                torrent.put(Constants.COLUMN_HONORS_SESSION_LIMITS, parser.getBooleanValue());
            } else if (name.equals(Torrent.SetterFields.UPLOAD_LIMIT)) {
                torrent.put(Constants.COLUMN_UPLOAD_LIMIT, parser.getLongValue());
            } else if (name.equals(Torrent.SetterFields.UPLOAD_LIMITED)) {
                torrent.put(Constants.COLUMN_UPLOAD_LIMITED, parser.getBooleanValue());
            } else if (name.equals("webseedsSendingToUs")) {
                torrent.put(Constants.COLUMN_WEBSEEDS_SENDING_TO_US, parser.getIntValue());
            } else if (name.equals(Torrent.SetterFields.PEER_LIMIT)) {
                torrent.put(Constants.COLUMN_PEER_LIMIT, parser.getIntValue());
            } else if (name.equals("trackers")) {
                if (values.trackers == null) {
                    values.trackers = new ArrayList<ContentValues>();
                }
            } else if (name.equals("trackerStats")) {
                if (values.trackers == null) {
                    values.trackers = new ArrayList<ContentValues>();
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
                            file.put(Constants.COLUMN_NAME, parser.getText());
                        } else if (argname.equals("length")) {
                            file.put(Constants.COLUMN_LENGTH, parser.getLongValue());
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
                            file.put(Constants.COLUMN_BYTES_COMPLETED, parser.getLongValue());
                        } else if (argname.equals("wanted")) {
                            file.put(Constants.COLUMN_WANTED, parser.getBooleanValue());
                        } else if (argname.equals("priority")) {
                            file.put(Constants.COLUMN_PRIORITY, parser.getIntValue());
                        }
                    }

                    ++index;
                }
            } else if (name.equals("peers")) {
                if (values.peers == null) {
                    values.peers = new ArrayList<ContentValues>();
                }
            }

            /* TODO: trafficText, statusText */
        }

        return values;
    }
}
