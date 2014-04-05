package org.sugr.gearshift.unit.datasource;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.sugr.gearshift.core.TransmissionSession;
import org.sugr.gearshift.datasource.Constants;
import org.sugr.gearshift.datasource.DataSource;
import org.sugr.gearshift.datasource.SQLiteHelper;
import org.sugr.gearshift.unit.util.RobolectricGradleTestRunner;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@Config(emulateSdk = 16)
@RunWith(RobolectricGradleTestRunner.class)
public class DataSourceTest {
    private DataSource ds;
    private SQLiteOpenHelper helper;

    @Before public void setUp() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
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
        Set<String> tables = new HashSet<>();
        cursor.moveToFirst();

        while (!cursor.isAfterLast()) {
            tables.add(cursor.getString(0));
            cursor.moveToNext();
        }
        assertTrue(cursor.getCount() > 6);

        String[] expected = new String[] {
            Constants.T_FILE, Constants.T_PEER, Constants.T_SESSION, Constants.T_TORRENT,
            Constants.T_TORRENT_PROFILE, Constants.T_TORRENT_TRACKER, Constants.T_TRACKER
        };
        for (String expect : expected) {
            assertTrue(tables.contains(expect));
        }

        cursor.close();
    }

    @Test public void session() {
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

            assertEquals(JsonToken.START_OBJECT, parser.nextToken());
            ds.open();
            assertTrue(ds.updateSession(profile, parser));

            TransmissionSession session = ds.getSession(profile);
            assertNotNull(session);

            assertEquals(1000, session.getAltDownloadSpeedLimit());
            assertFalse(session.isAltSpeedLimitEnabled());
            assertEquals(1110, session.getAltSpeedTimeBegin());
            assertEquals(127, session.getAltSpeedTimeDay());
            assertTrue(session.isAltSpeedLimitTimeEnabled());
            assertEquals(150, session.getAltSpeedTimeEnd());
            assertEquals(150, session.getAltUploadSpeedLimit());
            assertTrue(session.isBlocklistEnabled());
            assertEquals(227574, session.getBlocklistSize());
            assertEquals("http://list.iblocklist.com/?list=bt_level1&fileformat=p2p&archiveformat=gz", session.getBlocklistURL());
            assertEquals(20, session.getCacheSize());
            assertEquals("/var/lib/transmission-daemon/info", session.getConfigDir());
            assertTrue(session.isDhtEnabled());
            assertEquals("/test/Downloads", session.getDownloadDir());
            assertEquals(46269779968l, session.getDownloadDirFreeSpace());
            assertTrue(session.isDownloadQueueEnabled());
            assertEquals(2, session.getDownloadQueueSize());
            assertEquals("preferred", session.getEncryption());
            assertEquals(1000, session.getIdleSeedingLimig());
            assertTrue(session.isIdleSeedingLimitEnabled());
            assertEquals("/test/Incomplete", session.getIncompleteDir());
            assertTrue(session.isIncompleteDirEnabled());
            assertTrue(session.isLocalDiscoveryEnabled());
            assertEquals(220, session.getGlobalPeerLimit());
            assertEquals(60, session.getTorrentPeerLimit());
            assertEquals(51015, session.getPeerPort());
            assertFalse(session.isPeerPortRandomOnStart());
            assertTrue(session.isPeerExchangeEnabled());
            assertTrue(session.isPortForwardingEnabled());
            assertTrue(session.isStalledQueueEnabled());
            assertEquals(20, session.getStalledQueueSize());
            assertTrue(session.isRenamePartialFilesEnabled());
            assertEquals(14, session.getRPCVersion());
            assertEquals(1, session.getRPCVersionMin());
            assertFalse(session.isDoneScriptEnabled());
            assertEquals("some-script", session.getDoneScript());
            assertFalse(session.isSeedQueueEnabled());
            assertEquals(10, session.getSeedQueueSize());
            assertEquals(2, session.getSeedRatioLimit(), 0);
            assertTrue(session.isSeedRatioLimitEnabled());
            assertEquals(1600, session.getDownloadSpeedLimit());
            assertTrue(session.isDownloadSpeedLimitEnabled());
            assertEquals(600, session.getUploadSpeedLimit());
            assertTrue(session.isUploadSpeedLimitEnabled());
            assertTrue(session.isStartAddedTorrentsEnabled());
            assertFalse(session.isTrashOriginalTorrentFilesEnabled());
            assertTrue(session.isUtpEnabled());
            assertEquals("2.52 (13304)", session.getVersion());
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

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}
