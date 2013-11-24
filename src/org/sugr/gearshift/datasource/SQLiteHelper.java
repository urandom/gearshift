package org.sugr.gearshift.datasource;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.sugr.gearshift.G;

public class SQLiteHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "gearshift.db";

    public SQLiteHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(Constants.T_TORRENTS_CREATE);
        db.execSQL(Constants.T_TRACKERS_CREATE);
        db.execSQL(Constants.T_FILES_CREATE);
        db.execSQL(Constants.T_PEERS_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        G.logD("Upgrading data source from version " + oldVersion + " to " + newVersion);

        db.execSQL("DROP TABLE IF EXISTS " + Constants.T_TORRENTS);
        db.execSQL("DROP TABLE IF EXISTS " + Constants.T_TRACKERS);
        db.execSQL("DROP TABLE IF EXISTS " + Constants.T_FILES);
        db.execSQL("DROP TABLE IF EXISTS " + Constants.T_PEERS);
        onCreate(db);
    }
}
