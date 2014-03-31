package org.sugr.gearshift.unit.datasource;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowSQLiteOpenHelper;
import org.sugr.gearshift.datasource.Constants;
import org.sugr.gearshift.datasource.DataSource;
import org.sugr.gearshift.datasource.SQLiteHelper;
import org.sugr.gearshift.unit.util.RobolectricGradleTestRunner;

import java.io.File;
import java.net.URL;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricGradleTestRunner.class)
public class DataSourceTest {
    private DataSource ds;
    private SQLiteOpenHelper helper;

    @Before public void setUp() throws Exception {
        Activity activity = new Activity();
        helper = new SQLiteHelper(activity.getApplicationContext());
        assertNotNull(helper);

        ds = new DataSource(activity, helper);
        assertNotNull(ds);
    }

    @Test public void openAndClose() {
        ds.open();

        assertTrue(helper.getWritableDatabase().isOpen());
        assertTrue(ds.isOpen());

        Cursor cursor = helper.getWritableDatabase().query("sqlite_master", new String[] { "name" },
            "type = 'table'", null, null, null, "name");

        assertNotNull(cursor);
        assertEquals(7, cursor.getCount());
        cursor.moveToFirst();

        String[] expected = new String[] {
            Constants.T_FILE, Constants.T_PEER, Constants.T_SESSION, Constants.T_TORRENT,
            Constants.T_TORRENT_PROFILE, Constants.T_TORRENT_TRACKER, Constants.T_TRACKER
        };
        int index = 0;

        while (!cursor.isAfterLast()) {
            assertEquals(expected[index++], cursor.getString(cursor.getColumnIndex("name")));
            cursor.moveToNext();
        }
        cursor.close();

    }
}

