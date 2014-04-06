package org.sugr.gearshift.unit.datasource;

import android.app.Activity;
import android.content.SharedPreferences;
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
import org.robolectric.tester.android.content.TestSharedPreferences;
import org.sugr.gearshift.G;
import org.sugr.gearshift.core.Torrent;
import org.sugr.gearshift.core.TransmissionSession;
import org.sugr.gearshift.datasource.Constants;
import org.sugr.gearshift.datasource.DataSource;
import org.sugr.gearshift.datasource.SQLiteHelper;
import org.sugr.gearshift.datasource.TorrentStatus;
import org.sugr.gearshift.unit.util.RobolectricGradleTestRunner;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Config(emulateSdk = 16)
@RunWith(RobolectricGradleTestRunner.class)
public class DataSourceTest {
    private DataSource ds;
    private SQLiteOpenHelper helper;
    private SharedPreferences defaultPrefs;

    @Before public void setUp() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).create().get();
        helper = new SQLiteHelper(activity.getApplicationContext());
        assertNotNull(helper);

        ds = new DataSource(activity, helper);
        assertNotNull(ds);

        defaultPrefs = new TestSharedPreferences(new HashMap<String, Map<String, Object>>(),
            "default", Activity.MODE_PRIVATE);
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
            assertEquals("/test/Example", session.getDownloadDir());
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

    @Test public void torrent() {
        String profile = "existing";
        Cursor cursor;

        ds.open();
        cursor = ds.getTorrentCursor(profile, defaultPrefs);

        assertEquals(0, cursor.getCount());
        cursor.close();

        URL url = getClass().getResource("/json/torrents.json");
        assertNotNull(url);


        InputStream is = null;
        try {
            is = url.openStream();
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            JsonFactory factory = mapper.getFactory();
            JsonParser parser = factory.createParser(is);

            assertEquals(JsonToken.START_ARRAY, parser.nextToken());
            TorrentStatus status = ds.updateTorrents(profile, parser, false);
            assertNotNull(status);

            /* defaults:
             * base - age:descending, status:ascending
             */

            String[] expectedNames = new String[] {
                "startup.sh", "alpha...-test ", "1 Complete ", "Monster.Test.....-",
                "clock.oiuwer...-aaa", "foo Bar.abc...- ", "grass", "texts..g.sh",
                "gamma rotk (foo) []", "who.Who.foo.S06...-testtest", "ray of light 4", "access",
                "Summer ", "block", "preserve.sh", "Somewhere script", "Bla test-exa!",
                "gc14.01.12.test....baba", "8516-.sh", "water test (abc - fao)",
                "water fao - today test fire", "tele.sh.21.calen", "fox", "view.sh",
            };

            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            assertEquals(24, cursor.getCount());

            cursor.moveToFirst();
            int index = -1;
            while (!cursor.isAfterLast()) {
                assertEquals(expectedNames[++index], Torrent.getName(cursor));
                cursor.moveToNext();
            }

            cursor.moveToPosition(9);
            assertEquals(1d, Torrent.getMetadataPercentDone(cursor), 0);
            assertEquals(1d, Torrent.getPercentDone(cursor), 0);
            assertEquals(Torrent.SeedRatioMode.GLOBAL_LIMIT, Torrent.getSeedRatioMode(cursor));
            assertEquals(2d, Torrent.getUploadRatio(cursor), 0);
            assertEquals("28.42 GB, uploaded 56.89 GB (Ratio: 2)", Torrent.getTrafficText(cursor));
            assertEquals("<b>Finished</b>", Torrent.getStatusText(cursor));
            assertEquals(Torrent.Status.STOPPED, Torrent.getStatus(cursor));
            assertFalse(Torrent.isActive(Torrent.getStatus(cursor)));
            assertEquals(Torrent.Error.OK, Torrent.getError(cursor));
            assertEquals("", Torrent.getErrorString(cursor));

            cursor.moveToPosition(16);
            assertEquals(Torrent.Error.LOCAL_ERROR, Torrent.getError(cursor));
            assertTrue(Torrent.getErrorString(cursor).contains("No data found!"));
            assertEquals(Torrent.Status.STOPPED, Torrent.getStatus(cursor));

            cursor.moveToPosition(0);
            assertEquals(Torrent.Status.DOWNLOADING, Torrent.getStatus(cursor));
            assertTrue(Torrent.isActive(Torrent.getStatus(cursor)));
            assertEquals("1.18 GB of 1.18 GB (100%) - Remaining time unknown", Torrent.getTrafficText(cursor));
            assertEquals("<b>Downloading</b>  from 0 of 0 connected peers - <i>↓ 346.7 KB/s, ↑ 53.71 KB/s</i>", Torrent.getStatusText(cursor));

            cursor.moveToPosition(5);
            assertEquals(Torrent.Status.SEEDING, Torrent.getStatus(cursor));
            assertTrue(Torrent.isActive(Torrent.getStatus(cursor)));
            assertEquals("10.96 GB, uploaded 243.6 GB (Ratio: 22.2)", Torrent.getTrafficText(cursor));
            assertEquals("<b>Seeding</b>  to 2 of 2 connected peers - <i>↑ 11.72 KB/s</i>", Torrent.getStatusText(cursor));

            cursor.close();

            defaultPrefs.edit().putString(G.PREF_LIST_SORT_BY, G.SortBy.NAME.name()).commit();
            expectedNames = new String[] {
                "1 Complete ", "8516-.sh", "access", "alpha...-test ", "Bla test-exa!",
                "block", "clock.oiuwer...-aaa", "foo Bar.abc...- ", "fox", "gamma rotk (foo) []",
                "gc14.01.12.test....baba", "grass", "Monster.Test.....-", "preserve.sh",
                "ray of light 4", "Somewhere script", "startup.sh", "Summer ",
                "tele.sh.21.calen", "texts..g.sh", "view.sh", "water fao - today test fire",
                "water test (abc - fao)", "who.Who.foo.S06...-testtest",
            };

            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            cursor.moveToFirst();
            assertEquals(24, cursor.getCount());

            cursor.moveToFirst();
            index = -1;
            while (!cursor.isAfterLast()) {
                assertEquals(expectedNames[++index], Torrent.getName(cursor));
                cursor.moveToNext();
            }
            cursor.close();

            defaultPrefs.edit().putString(G.PREF_LIST_SORT_BY, G.SortBy.RATE_DOWNLOAD.name()).commit();
            expectedNames = new String[] {
                "startup.sh", "alpha...-test ", "1 Complete ", "gamma rotk (foo) []",
                "who.Who.foo.S06...-testtest", "ray of light 4", "Monster.Test.....-", "access",
                "Summer ", "block", "clock.oiuwer...-aaa", "preserve.sh", "Somewhere script",
                "Bla test-exa!", "gc14.01.12.test....baba", "8516-.sh", "water test (abc - fao)",
                "water fao - today test fire", "tele.sh.21.calen", "foo Bar.abc...- ", "fox",
                "view.sh", "grass", "texts..g.sh",
            };
            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            cursor.moveToFirst();
            assertEquals(24, cursor.getCount());

            cursor.moveToFirst();
            index = -1;
            while (!cursor.isAfterLast()) {
                assertEquals(expectedNames[++index], Torrent.getName(cursor));
                cursor.moveToNext();
            }
            cursor.close();

            defaultPrefs.edit().putString(G.PREF_BASE_SORT, G.SortBy.QUEUE.name()).commit();
            expectedNames = new String[] {
                "startup.sh", "alpha...-test ", "1 Complete ", "foo Bar.abc...- ",
                "Monster.Test.....-", "view.sh", "water fao - today test fire", "gamma rotk (foo) []",
                "water test (abc - fao)", "block", "tele.sh.21.calen", "8516-.sh", "preserve.sh",
                "access", "gc14.01.12.test....baba", "texts..g.sh", "clock.oiuwer...-aaa",
                "Somewhere script", "who.Who.foo.S06...-testtest", "grass", "ray of light 4", "fox",
                "Bla test-exa!", "Summer ",
            };
            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            cursor.moveToFirst();
            assertEquals(24, cursor.getCount());

            cursor.moveToFirst();
            index = -1;
            while (!cursor.isAfterLast()) {
                assertEquals(expectedNames[++index], Torrent.getName(cursor));
                cursor.moveToNext();
            }
            cursor.close();

            defaultPrefs.edit().putString(G.PREF_LIST_SORT_BY, G.SortBy.LOCATION.name()).commit();
            expectedNames = new String[] {
                "water fao - today test fire", "water test (abc - fao)", "foo Bar.abc...- ",
                "Monster.Test.....-", "alpha...-test ", "clock.oiuwer...-aaa", "1 Complete ",
                "gamma rotk (foo) []", "Bla test-exa!", "view.sh", "block", "tele.sh.21.calen",
                "8516-.sh", "startup.sh", "preserve.sh", "access", "gc14.01.12.test....baba",
                "texts..g.sh", "Somewhere script", "grass", "fox", "Summer ",
                "who.Who.foo.S06...-testtest", "ray of light 4",
            };
            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            cursor.moveToFirst();
            assertEquals(24, cursor.getCount());

            cursor.moveToFirst();
            index = -1;
            while (!cursor.isAfterLast()) {
                assertEquals(expectedNames[++index], Torrent.getName(cursor));
                cursor.moveToNext();
            }
            cursor.close();

            defaultPrefs.edit().putString(G.PREF_LIST_SORT_BY, G.SortBy.QUEUE.name()).commit();
            defaultPrefs.edit().putString(G.PREF_LIST_FILTER, G.FilterBy.ACTIVE.name()).commit();
            expectedNames = new String[] {
                "clock.oiuwer...-aaa", "startup.sh", "1 Complete ", "alpha...-test ",
                "Monster.Test.....-", "foo Bar.abc...- ",
            };
            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            cursor.moveToFirst();
            assertEquals(6, cursor.getCount());

            cursor.moveToFirst();
            index = -1;
            while (!cursor.isAfterLast()) {
//                System.out.println("\"" + Torrent.getName(cursor) + "\",");
                assertEquals(expectedNames[++index], Torrent.getName(cursor));
                cursor.moveToNext();
            }
            cursor.close();
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
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception ignored) {}
            }
        }
    }
}
