package org.sugr.gearshift.datasource;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.sugr.gearshift.G;

public class SQLiteHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 4;
    private static final String DATABASE_NAME = "gearshift.db";

    public SQLiteHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(Constants.T_SESSION_CREATE);
        db.execSQL(Constants.T_TORRENT_CREATE);
        db.execSQL(Constants.T_TORRENT_PROFILE_CREATE);
        db.execSQL(Constants.T_TRACKER_CREATE);
        db.execSQL(Constants.T_FILE_CREATE);
        db.execSQL(Constants.T_PEER_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        G.logD("Upgrading data source from version " + oldVersion + " to " + newVersion);

        db.execSQL("DROP TABLE IF EXISTS " + Constants.T_SESSION);
        db.execSQL("DROP TABLE IF EXISTS " + Constants.T_TORRENT);
        db.execSQL("DROP TABLE IF EXISTS " + Constants.T_TORRENT_PROFILE);
        db.execSQL("DROP TABLE IF EXISTS " + Constants.T_TRACKER);
        db.execSQL("DROP TABLE IF EXISTS " + Constants.T_FILE);
        db.execSQL("DROP TABLE IF EXISTS " + Constants.T_PEER);

        db.execSQL("DROP TABLE IF EXISTS torrent_tracker");

        onCreate(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            // Enable foreign key constraints
            db.execSQL("PRAGMA foreign_keys=ON;");
        }
    }
}
