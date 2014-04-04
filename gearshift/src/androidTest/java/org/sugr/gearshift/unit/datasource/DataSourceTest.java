package org.sugr.gearshift.unit.datasource;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sugr.gearshift.datasource.Constants;
import org.sugr.gearshift.datasource.DataSource;
import org.sugr.gearshift.datasource.SQLiteHelper;
import org.sugr.gearshift.unit.util.RobolectricGradleTestRunner;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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

    @Test public void openAndClose() throws Exception {
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

    @Test public void updateSession() {
        String profile = "existing";

        URL url = getClass().getResource("/json/session.json");
        assertNotNull(url);

        InputStream is = null;
        try {
            is = url.openStream();
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            JsonFactory factory = mapper.getFactory();
            JsonParser parser = factory.createParser(is);

            ds.open();
            ds.updateSession(profile, parser);
        } catch (IOException e) {
            assertTrue(e.toString(), false);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

