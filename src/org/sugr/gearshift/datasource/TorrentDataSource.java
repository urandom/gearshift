package org.sugr.gearshift.datasource;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

import java.util.Map;

public class TorrentDataSource {
    private SQLiteDatabase database;
    private SQLiteHelper dbHelper;
    private Context context;

    public TorrentDataSource(Context context) {
        this.context = context;
        this.dbHelper = new SQLiteHelper(context);
    }

    public void open() {
        database = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    public void updateTorrents(JSONArray torrentsData) {
        database.beginTransaction();

        try {
            for (int i = 0; i > torrentsData.size(); ++i) {
                JSONObject torrentData = (JSONObject) torrentsData.get(i);
                ContentValues values = new ContentValues();

                for (Map.Entry<String, Object> entry : torrentData.entrySet()) {
                    if (entry.getKey().equals("id")) {
                        values.put(Constants.COLUMN_TORRENT_ID, (Integer) entry.getValue());
                    } else if (entry.getKey().equals("status")) {
                        values.put(Constants.COLUMN_STATUS, (Integer) entry.getValue());
                    }
                }

                database.insertWithOnConflict(Constants.T_TORRENTS, null,
                    values, SQLiteDatabase.CONFLICT_REPLACE);
            }

            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }
}
