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
import org.sugr.gearshift.datasource.TorrentDetails;
import org.sugr.gearshift.datasource.TorrentStatus;
import org.sugr.gearshift.unit.util.RobolectricGradleTestRunner;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

        defaultPrefs.edit().putString(G.PREF_LIST_SORT_BY, G.SortBy.QUEUE.name()).commit();
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
        assertTrue(cursor.getCount() > 5);

        String[] expected = new String[] {
            Constants.T_FILE, Constants.T_PEER, Constants.T_SESSION, Constants.T_TORRENT,
            Constants.T_TORRENT_PROFILE, Constants.T_TRACKER
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
        Cursor cursor = null;

        ds.open();
        try {
            cursor = ds.getTorrentCursor(profile, defaultPrefs);

            assertEquals(0, cursor.getCount());
            cursor.close();

            TorrentStatus status = updateTorrents();
            assertNotNull(status);

            assertTrue(status.hasAdded);
            assertTrue(status.hasRemoved);
            assertTrue(status.hasStatusChanged);
            assertTrue(status.hasIncompleteMetadata);

            /* defaults:
             * base - age:descending, status:ascending
             */

            defaultPrefs.edit().putString(G.PREF_LIST_SORT_BY, G.SortBy.STATUS.name()).commit();
            String[] expectedNames = new String[]{
                "startup.sh", "alpha...-test ", "1 Complete ", "Monster.Test.....-",
                "clock.oiuwer...-aaa", "foo Bar.abc...- ", "grass", "texts..g.sh",
                "", "gamma rotk (foo) []", "who.Who.foo.S06...-testtest", "ray of light 4", "access",
                "Summer ", "block", "preserve.sh", "Somewhere script", "Bla test-exa!",
                "gc14.01.12.test....baba", "8516-.sh", "water test (abc - fao)",
                "water fao - today test fire", "tele.sh.21.calen", "fox", "view.sh",
            };

            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            assertEquals(25, cursor.getCount());

            cursor.moveToFirst();
            int index = -1;
            while (!cursor.isAfterLast()) {
                assertEquals(expectedNames[++index], Torrent.getName(cursor));
                cursor.moveToNext();
            }

            cursor.moveToPosition(10);
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

            cursor.moveToPosition(17);
            assertEquals(Torrent.Error.LOCAL_ERROR, Torrent.getError(cursor));
            assertTrue(Torrent.getErrorString(cursor).contains("No data found!"));
            assertEquals(Torrent.Status.STOPPED, Torrent.getStatus(cursor));

            cursor.moveToPosition(0);
            assertEquals(Torrent.Status.DOWNLOADING, Torrent.getStatus(cursor));
            assertTrue(Torrent.isActive(Torrent.getStatus(cursor)));
            assertEquals("1.12 GB of 1.18 GB (95%) - Remaining time unknown", Torrent.getTrafficText(cursor));
            assertEquals("<b>Downloading</b>  from 0 of 0 connected peers - <i>↓ 346.7 KB/s, ↑ 53.71 KB/s</i>", Torrent.getStatusText(cursor));

            cursor.moveToPosition(5);
            assertEquals(Torrent.Status.SEEDING, Torrent.getStatus(cursor));
            assertTrue(Torrent.isActive(Torrent.getStatus(cursor)));
            assertEquals("10.96 GB, uploaded 243.6 GB (Ratio: 22.2)", Torrent.getTrafficText(cursor));
            assertEquals("<b>Seeding</b>  to 2 of 2 connected peers - <i>↑ 11.72 KB/s</i>", Torrent.getStatusText(cursor));

            cursor.close();

            defaultPrefs.edit().putString(G.PREF_LIST_SORT_BY, G.SortBy.NAME.name()).commit();
            expectedNames = new String[]{
                "", "1 Complete ", "8516-.sh", "access", "alpha...-test ", "Bla test-exa!",
                "block", "clock.oiuwer...-aaa", "foo Bar.abc...- ", "fox", "gamma rotk (foo) []",
                "gc14.01.12.test....baba", "grass", "Monster.Test.....-", "preserve.sh",
                "ray of light 4", "Somewhere script", "startup.sh", "Summer ",
                "tele.sh.21.calen", "texts..g.sh", "view.sh", "water fao - today test fire",
                "water test (abc - fao)", "who.Who.foo.S06...-testtest",
            };

            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            cursor.moveToFirst();
            assertEquals(25, cursor.getCount());

            cursor.moveToFirst();
            index = -1;
            while (!cursor.isAfterLast()) {
                assertEquals(expectedNames[++index], Torrent.getName(cursor));
                cursor.moveToNext();
            }
            cursor.close();

            defaultPrefs.edit().putString(G.PREF_LIST_SORT_BY, G.SortBy.RATE_DOWNLOAD.name()).commit();
            expectedNames = new String[]{
                "startup.sh", "alpha...-test ", "1 Complete ", "gamma rotk (foo) []",
                "who.Who.foo.S06...-testtest", "ray of light 4", "Monster.Test.....-", "access",
                "Summer ", "block", "clock.oiuwer...-aaa", "preserve.sh", "Somewhere script",
                "Bla test-exa!", "gc14.01.12.test....baba", "8516-.sh", "water test (abc - fao)",
                "water fao - today test fire", "tele.sh.21.calen", "foo Bar.abc...- ", "fox",
                "view.sh", "grass", "texts..g.sh", ""
            };
            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            cursor.moveToFirst();
            assertEquals(25, cursor.getCount());

            cursor.moveToFirst();
            index = -1;
            while (!cursor.isAfterLast()) {
                assertEquals(expectedNames[++index], Torrent.getName(cursor));
                cursor.moveToNext();
            }
            cursor.close();

            defaultPrefs.edit().putString(G.PREF_BASE_SORT, G.SortBy.QUEUE.name()).commit();
            expectedNames = new String[]{
                "startup.sh", "alpha...-test ", "1 Complete ", "foo Bar.abc...- ",
                "Monster.Test.....-", "view.sh", "water fao - today test fire", "gamma rotk (foo) []",
                "water test (abc - fao)", "block", "tele.sh.21.calen", "8516-.sh", "preserve.sh",
                "access", "gc14.01.12.test....baba", "texts..g.sh", "clock.oiuwer...-aaa",
                "Somewhere script", "who.Who.foo.S06...-testtest", "grass", "ray of light 4", "fox",
                "Bla test-exa!", "Summer ", "",
            };
            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            cursor.moveToFirst();
            assertEquals(25, cursor.getCount());

            cursor.moveToFirst();
            index = -1;
            while (!cursor.isAfterLast()) {
                assertEquals(expectedNames[++index], Torrent.getName(cursor));
                cursor.moveToNext();
            }
            cursor.close();

            defaultPrefs.edit().putString(G.PREF_LIST_SORT_BY, G.SortBy.LOCATION.name()).commit();
            expectedNames = new String[]{
                "", "water fao - today test fire", "water test (abc - fao)", "foo Bar.abc...- ",
                "Monster.Test.....-", "alpha...-test ", "clock.oiuwer...-aaa", "1 Complete ",
                "gamma rotk (foo) []", "Bla test-exa!", "view.sh", "block", "tele.sh.21.calen",
                "8516-.sh", "startup.sh", "preserve.sh", "access", "gc14.01.12.test....baba",
                "texts..g.sh", "Somewhere script", "grass", "fox", "Summer ",
                "who.Who.foo.S06...-testtest", "ray of light 4",
            };
            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            cursor.moveToFirst();
            assertEquals(25, cursor.getCount());

            cursor.moveToFirst();
            index = -1;
            while (!cursor.isAfterLast()) {
                assertEquals(expectedNames[++index], Torrent.getName(cursor));
                cursor.moveToNext();
            }
            cursor.close();

            defaultPrefs.edit().putString(G.PREF_LIST_SORT_BY, G.SortBy.QUEUE.name()).commit();
            defaultPrefs.edit().putString(G.PREF_LIST_FILTER, G.FilterBy.ACTIVE.name()).commit();
            expectedNames = new String[]{
                "clock.oiuwer...-aaa", "startup.sh", "1 Complete ", "alpha...-test ",
                "Monster.Test.....-", "foo Bar.abc...- ",
            };
            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            cursor.moveToFirst();
            assertEquals(6, cursor.getCount());

            cursor.moveToFirst();
            index = -1;
            while (!cursor.isAfterLast()) {
                assertEquals(expectedNames[++index], Torrent.getName(cursor));
                cursor.moveToNext();
            }
            cursor.close();

            defaultPrefs.edit().putString(G.PREF_LIST_FILTER, G.FilterBy.CHECKING.name()).commit();
            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            assertEquals(0, cursor.getCount());
            cursor.close();

            defaultPrefs.edit().putString(G.PREF_LIST_FILTER, G.FilterBy.DOWNLOADING.name()).commit();
            expectedNames = new String[]{
                "startup.sh", "1 Complete ", "alpha...-test ",
            };

            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            assertEquals(3, cursor.getCount());
            cursor.moveToFirst();

            index = -1;
            while (!cursor.isAfterLast()) {
                assertEquals(expectedNames[++index], Torrent.getName(cursor));
                cursor.moveToNext();
            }
            cursor.close();

            defaultPrefs.edit().putString(G.PREF_LIST_FILTER, G.FilterBy.COMPLETE.name()).commit();
            expectedNames = new String[]{
                "Summer ", "Bla test-exa!", "fox", "ray of light 4", "grass",
                "who.Who.foo.S06...-testtest", "Somewhere script", "clock.oiuwer...-aaa",
                "texts..g.sh", "gc14.01.12.test....baba", "access", "preserve.sh", "8516-.sh",
                "tele.sh.21.calen", "block", "water test (abc - fao)", "gamma rotk (foo) []",
                "water fao - today test fire", "view.sh", "Monster.Test.....-", "foo Bar.abc...- ",
            };

            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            assertEquals(21, cursor.getCount());
            cursor.moveToFirst();

            index = -1;
            while (!cursor.isAfterLast()) {
                assertEquals(expectedNames[++index], Torrent.getName(cursor));
                cursor.moveToNext();
            }
            cursor.close();

            defaultPrefs.edit().putString(G.PREF_LIST_FILTER, G.FilterBy.INCOMPLETE.name()).commit();
            expectedNames = new String[]{
                "", "startup.sh", "1 Complete ", "alpha...-test ",
            };

            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            assertEquals(4, cursor.getCount());
            cursor.moveToFirst();

            index = -1;
            while (!cursor.isAfterLast()) {
                assertEquals(expectedNames[++index], Torrent.getName(cursor));
                cursor.moveToNext();
            }
            cursor.close();

            defaultPrefs.edit().putString(G.PREF_LIST_FILTER, G.FilterBy.PAUSED.name()).commit();
            expectedNames = new String[]{
                "", "Summer ", "Bla test-exa!", "fox", "ray of light 4", "grass",
                "who.Who.foo.S06...-testtest", "Somewhere script", "texts..g.sh",
                "gc14.01.12.test....baba", "access", "preserve.sh", "8516-.sh", "tele.sh.21.calen",
                "block", "water test (abc - fao)", "gamma rotk (foo) []",
                "water fao - today test fire", "view.sh",
            };

            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            assertEquals(19, cursor.getCount());
            cursor.moveToFirst();

            index = -1;
            while (!cursor.isAfterLast()) {
                assertEquals(expectedNames[++index], Torrent.getName(cursor));
                cursor.moveToNext();
            }
            cursor.close();

            defaultPrefs.edit().putString(G.PREF_LIST_FILTER, G.FilterBy.SEEDING.name()).commit();
            expectedNames = new String[]{
                "clock.oiuwer...-aaa", "Monster.Test.....-", "foo Bar.abc...- ",
            };

            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            assertEquals(3, cursor.getCount());
            cursor.moveToFirst();

            index = -1;
            while (!cursor.isAfterLast()) {
                assertEquals(expectedNames[++index], Torrent.getName(cursor));
                cursor.moveToNext();
            }
            cursor.close();

            defaultPrefs.edit().putString(G.PREF_LIST_SEARCH, "a").commit();
            expectedNames = new String[]{
                "clock.oiuwer...-<font", "foo B<font",
            };

            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            assertEquals(2, cursor.getCount());
            cursor.moveToFirst();

            index = -1;
            while (!cursor.isAfterLast()) {
                assertTrue(Torrent.getName(cursor).contains(expectedNames[++index]));
                cursor.moveToNext();
            }
            cursor.close();

            defaultPrefs.edit().putString(G.PREF_LIST_FILTER, G.FilterBy.ALL.name()).commit();
            defaultPrefs.edit().putString(G.PREF_LIST_SEARCH, "es").commit();
            expectedNames = new String[]{
                "Bla t<font", "who.Who.foo.S06...-t<font", "Somewher<font", "t<font",
                "gc14.01.12.t<font", "acc<font", "pr<font", "tel<font", "water t<font",
                "water fao - today t<font", "vi<font", "alpha...-t<font", "Monster.T<font",
            };

            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            assertEquals(13, cursor.getCount());
            cursor.moveToFirst();

            index = -1;
            while (!cursor.isAfterLast()) {
                assertTrue(Torrent.getName(cursor).contains(expectedNames[++index]));
                cursor.moveToNext();
            }
            cursor.close();

            defaultPrefs.edit().putString(G.PREF_LIST_SEARCH, null).commit();
            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            assertEquals(25, cursor.getCount());
            cursor.close();

            defaultPrefs.edit().putString(G.PREF_LIST_DIRECTORY, "/test/foo/gamma Ray").commit();
            expectedNames = new String[]{
                "ray of light 4", "who.Who.foo.S06...-testtest",
            };

            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            assertEquals(2, cursor.getCount());
            cursor.moveToFirst();

            index = -1;
            while (!cursor.isAfterLast()) {
                assertTrue(Torrent.getName(cursor).contains(expectedNames[++index]));
                cursor.moveToNext();
            }
            cursor.close();

            defaultPrefs.edit().remove(G.PREF_LIST_DIRECTORY).commit();
            defaultPrefs.edit().putString(G.PREF_LIST_TRACKER, "udp://tracker.nsa.gov:80").commit();
            expectedNames = new String[]{
                "Summer ", "Bla test-exa!", "fox", "access", "preserve.sh", "startup.sh", "8516-.sh",
                "water test (abc - fao)", "gamma rotk (foo) []", "water fao - today test fire",
                "view.sh", "1 Complete ", "alpha...-test ", "Monster.Test.....-",
            };

            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            assertEquals(14, cursor.getCount());
            cursor.moveToFirst();

            index = -1;
            while (!cursor.isAfterLast()) {
                assertEquals(expectedNames[++index], Torrent.getName(cursor));
                cursor.moveToNext();
            }
            cursor.close();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @Test public void torrentDetails() {
        String profile = "existing";
        TorrentDetails details = null;
        updateTorrents();

        try {
            // Summer
            details = ds.getTorrentDetails(profile, "d4fdfe1cf9549035c7fb1cac28ff722c7bf5e808");

            assertEquals(1, details.torrentCursor.getCount());
            assertEquals(1, details.filesCursor.getCount());
            assertEquals(6, details.trackersCursor.getCount());

            details.torrentCursor.moveToFirst();
            assertEquals("Summer ", Torrent.getName(details.torrentCursor));
            assertEquals(924425644, Torrent.getSizeWhenDone(details.torrentCursor));

            Object[][] expected = {
                {"Summer "},
                {924425644l},
                {921425644l},
                {0},
                {true},
            };

            int index = -1;
            details.filesCursor.moveToFirst();
            while (!details.filesCursor.isAfterLast()) {
                assertEquals(expected[0][++index], Torrent.File.getName(details.filesCursor));
                assertEquals(expected[1][index], Torrent.File.getLength(details.filesCursor));
                assertEquals(expected[2][index], Torrent.File.getBytesCompleted(details.filesCursor));
                assertEquals(expected[3][index], Torrent.File.getPriority(details.filesCursor));
                assertEquals(expected[4][index], Torrent.File.isWanted(details.filesCursor));
                details.filesCursor.moveToNext();
            }

            expected = new Object[][]{
                {
                    "udp://tracker.example.com:80",
                    "udp://tracker.yahoo:80",
                    "udp://tracker.example.biz:6969",
                    "udp://tracker.nsa.gov:80",
                    "udp://open.org.net:1337",
                    "https://server.domain/announce.php?passkey=mypasskey",
                }, //announce
                {0, 1, 2, 3, 4, 5}, //id
                {
                    "", "", "", "", "",
                    "https://server.domain/scrape.php?passkey=mypasskey",
                }, //scrape
                {0, 1, 2, 3, 4, 5}, //tier
                {-1, -1, -1, -1, -1, 91}, //seedercount
                {-1, -1, -1, -1, -1, 2}, //leechercount
                {false, false, false, false, false, true}, //hasannounced
                {0l, 0l, 0l, 0l, 0l, 1395713150l}, //lastannouncetime
                {false, false, false, false, false, true}, //lastannouncesucceeded
                {0, 0, 0, 0, 0, 2}, //lastannouncepeercount
                {
                    "", "", "", "", "", "Success"
                }, //lastannounceresult
                {false, false, false, false, false, true}, //hasscraped
                {0l, 0l, 0l, 0l, 0l, 1395713950l}, //lastscrapetime
                {false, false, false, false, false, true}, //lastscrapesucceeded
                {
                    "", "", "", "", "", ""
                }, //lastscraperesult
            };

            index = -1;
            details.trackersCursor.moveToFirst();
            while (!details.trackersCursor.isAfterLast()) {
                assertEquals(expected[0][++index], Torrent.Tracker.getAnnounce(details.trackersCursor));
                assertEquals(expected[1][index], Torrent.Tracker.getId(details.trackersCursor));
                assertEquals(expected[2][index], Torrent.Tracker.getScrape(details.trackersCursor));
                assertEquals(expected[3][index], Torrent.Tracker.getTier(details.trackersCursor));
                assertEquals(expected[4][index], Torrent.Tracker.getSeederCount(details.trackersCursor));
                assertEquals(expected[5][index], Torrent.Tracker.getLeecherCount(details.trackersCursor));
                assertEquals(expected[6][index], Torrent.Tracker.hasAnnounced(details.trackersCursor));
                assertEquals(expected[7][index], Torrent.Tracker.getLastAnnounceTime(details.trackersCursor));
                assertEquals(expected[8][index], Torrent.Tracker.hasLastAnnounceSucceeded(details.trackersCursor));
                assertEquals(expected[9][index], Torrent.Tracker.getLastAnnouncePeerCount(details.trackersCursor));
                assertEquals(expected[10][index], Torrent.Tracker.getLastAnnounceResult(details.trackersCursor));
                assertEquals(expected[11][index], Torrent.Tracker.hasScraped(details.trackersCursor));
                assertEquals(expected[12][index], Torrent.Tracker.getLastScrapeTime(details.trackersCursor));
                assertEquals(expected[13][index], Torrent.Tracker.hasLastScrapeSucceeded(details.trackersCursor));
                assertEquals(expected[14][index], Torrent.Tracker.getLastScrapeResult(details.trackersCursor));

                details.trackersCursor.moveToNext();
            }
            details.torrentCursor.close();
            details.filesCursor.close();
            details.trackersCursor.close();

            // gc14....
            details = ds.getTorrentDetails(profile, "f0034cc5deafdfb589d0f48c2dd29a62ff52e120");

            assertEquals(1, details.torrentCursor.getCount());
            assertEquals(4, details.filesCursor.getCount());
            assertEquals(4, details.trackersCursor.getCount());

            details.torrentCursor.moveToFirst();
            assertEquals("gc14.01.12.test....baba", Torrent.getName(details.torrentCursor));

            expected = new Object[][]{
                {
                    "gc14.01.12.test....baba/.txt", "gc14.01.12.test....baba/chair.14.01.12.test.baba.",
                    "gc14.01.12.test....baba/picture.tiny", "gc14.01.12.test....baba/log.tiny"
                },
                {33l, 743958774l, 28858l, 248955l},
                {31l, 743958374l, 21858l, 248655l},
                {0, 0, 2, 0},
                {false, true, true, false},
            };

            index = -1;
            details.filesCursor.moveToFirst();
            while (!details.filesCursor.isAfterLast()) {
                assertEquals(expected[0][++index], Torrent.File.getName(details.filesCursor));
                assertEquals(expected[1][index], Torrent.File.getLength(details.filesCursor));
                assertEquals(expected[2][index], Torrent.File.getBytesCompleted(details.filesCursor));
                assertEquals(expected[3][index], Torrent.File.getPriority(details.filesCursor));
                assertEquals(expected[4][index], Torrent.File.isWanted(details.filesCursor));
                details.filesCursor.moveToNext();
            }

            expected = new Object[][]{
                {
                    "http://p2p.google.com:2710/45c8fa2244c08084280785fe891b6e85/announce",
                    "http://p2p.test.net:2710/45c8fa2244c08084280785fe891b6e85/announce",
                    "udp://tracker.example.com:80/announce",
                    "https://server.domain/announce.php?passkey=mypasskey",
                }, //announce
                {0, 1, 2, 3}, //id
                {
                    "http://p2p.google.com:2710/45c8fa2244c08084280785fe891b6e85/scrape",
                    "http://p2p.test.net:2710/45c8fa2244c08084280785fe891b6e85/scrape",
                    "udp://tracker.example.com:80/scrape",
                    "https://server.domain/scrape.php?passkey=mypasskey",
                }, //scrape
                {0, 1, 2, 3}, //tier
                {1, 1, 0, 123}, //seedercount
                {0, 0, 0, 7}, //leechercount
                {false, false, false, true}, //hasannounced
                {0l, 0l, 0l, 1395713941l}, //lastannouncetime
                {false, false, false, true}, //lastannouncesucceeded
                {0, 0, 0, 10}, //lastannouncepeercount
                {
                    "", "", "", "Success"
                }, //lastannounceresult
                {true, true, true, true}, //hasscraped
                {1396699910l, 1396700500l, 1396700910l, 1395713830l}, //lastscrapetime
                {true, true, true, true}, //lastscrapesucceeded
                {
                    "Could not connect to tracker", "Could not connect to tracker",
                    "Connection failed", ""
                }, //lastscraperesult
            };

            index = -1;
            details.trackersCursor.moveToFirst();
            while (!details.trackersCursor.isAfterLast()) {
                assertEquals(expected[0][++index], Torrent.Tracker.getAnnounce(details.trackersCursor));
                assertEquals(expected[1][index], Torrent.Tracker.getId(details.trackersCursor));
                assertEquals(expected[2][index], Torrent.Tracker.getScrape(details.trackersCursor));
                assertEquals(expected[3][index], Torrent.Tracker.getTier(details.trackersCursor));
                assertEquals(expected[4][index], Torrent.Tracker.getSeederCount(details.trackersCursor));
                assertEquals(expected[5][index], Torrent.Tracker.getLeecherCount(details.trackersCursor));
                assertEquals(expected[6][index], Torrent.Tracker.hasAnnounced(details.trackersCursor));
                assertEquals(expected[7][index], Torrent.Tracker.getLastAnnounceTime(details.trackersCursor));
                assertEquals(expected[8][index], Torrent.Tracker.hasLastAnnounceSucceeded(details.trackersCursor));
                assertEquals(expected[9][index], Torrent.Tracker.getLastAnnouncePeerCount(details.trackersCursor));
                assertEquals(expected[10][index], Torrent.Tracker.getLastAnnounceResult(details.trackersCursor));
                assertEquals(expected[11][index], Torrent.Tracker.hasScraped(details.trackersCursor));
                assertEquals(expected[12][index], Torrent.Tracker.getLastScrapeTime(details.trackersCursor));
                assertEquals(expected[13][index], Torrent.Tracker.hasLastScrapeSucceeded(details.trackersCursor));
                assertEquals(expected[14][index], Torrent.Tracker.getLastScrapeResult(details.trackersCursor));

                details.trackersCursor.moveToNext();
            }
            details.torrentCursor.close();
            details.filesCursor.close();
            details.trackersCursor.close();
        } finally {
            if (details != null) {
                details.torrentCursor.close();
                details.filesCursor.close();
                details.trackersCursor.close();
            }
        }
    }

    @Test public void downloadDirectories() {
        String profile = "existing";
        updateTorrents();
        String[] expected = {
            "",
            "/test/Example",
            "/test/foo",
            "/test/foo/alpha",
            "/test/foo/bar",
            "/test/foo/gamma Ray",
        };

        List<String> dirs = ds.getDownloadDirectories(profile);
        assertEquals(6, dirs.size());

        int index = -1;
        for (String d : dirs) {
            assertEquals(expected[++index], d);
        }
    }

    @Test public void tracherAnnounceURLs() {
        String profile = "existing";
        updateTorrents();
        String[] expected = {
            "http://exodus.desync.com:6969/announce",
            "http://from.cold.com:3310/announce",
            "http://p2p.google.com:2710/45c8fa2244c08084280785fe891b6e85/announce",
            "http://p2p.test.net:2710/45c8fa2244c08084280785fe891b6e85/announce",
            "http://test.net/announce.php",
            "http://testtorrents.net:2710/announce",
            "http://tracker.ex.ua/announce",
            "http://tracker.testgoogle.com/announce",
            "https://server.domain/announce.php?passkey=mypasskey",
            "udp://9.trackerexample.biz:2710/announce",
            "udp://fromtracker.cold.com:3310/announce",
            "udp://open.org.net:1337",
            "udp://tracker.1337x.org:80/announce",
            "udp://tracker.example.biz:6969",
            "udp://tracker.example.biz:80",
            "udp://tracker.example.com:80",
            "udp://tracker.example.com:80/announce",
            "udp://tracker.nsa.gov:80",
            "udp://tracker.yahoo:80",
        };

        List<String> urls = ds.getTrackerAnnounceURLs(profile);
        assertEquals(19, urls.size());

        int index = -1;
        for (String u : urls) {
            assertEquals(expected[++index], u);
        }
    }

    @Test public void checkers() {
        String profile = "existing";
        updateTorrents();

        assertFalse(ds.hasCompleteMetadata(profile));
        assertFalse(ds.hasExtraInfo(profile));

        String[] hashStrings = ds.getUnnamedTorrentHashStrings(profile);
        String[] expected = {"35134a5bc91cf4eae1f27a813f275bc6fbf0b166",};

        int index = -1;
        for (String h : hashStrings) {
            assertEquals(expected[++index], h);
        }
    }

    @Test public void actions() {
        String profile = "existing";
        updateTorrents();

        Cursor cursor = null;

        try {
            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            assertEquals(25, cursor.getCount());
            cursor.close();

            String[] removed = {
                "cafe1c1591c5e548cbe542f9g5376b9ce250d3",
                "a7ff11f3517cd8e4abb4d525b8a58155c80b8c9c",
                "1af28f38397ff5ccf2326d4b7392a1f8233083"
            };
            assertTrue(ds.removeTorrents(removed));

            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            assertEquals(22, cursor.getCount());

            Set<String> hashStrings = new HashSet<>();
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                hashStrings.add(Torrent.getHashString(cursor));
                cursor.moveToNext();
            }

            for (String r : removed) {
                assertFalse(hashStrings.contains(r));
            }

            cursor.close();

            int[] ids = { 5, 14, 45, };
            removed = new String[] {
                "d4fdfe1cf9549035c7fb1cac28ff722c7bf5e808",
                "12399a999792837b4461dafbcde0f3bc5a071702",
                "2325f787f7801ca23367a94506e0e43e97aca5cf",
            };

            assertTrue(ds.removeTorrents(profile, ids));

            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            assertEquals(19, cursor.getCount());

            hashStrings.clear();
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                hashStrings.add(Torrent.getHashString(cursor));
                cursor.moveToNext();
            }

            for (String r : removed) {
                assertFalse(hashStrings.contains(r));
            }

            cursor.close();

            String newHashString = "c167a187f780fa823165a975c6b0ed344ea37f5a";
            assertTrue(ds.addTorrent(profile, 312, "foo", newHashString, "/test/bar"));

            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            assertEquals(20, cursor.getCount());

            hashStrings.clear();
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                hashStrings.add(Torrent.getHashString(cursor));
                cursor.moveToNext();
            }

            assertTrue(hashStrings.contains(newHashString));

            cursor.close();

            assertTrue(ds.clearTorrentsForProfile(profile));

            cursor = ds.getTorrentCursor(profile, defaultPrefs);
            assertEquals(0, cursor.getCount());
            cursor.close();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @Test public void trafficSpeed() {
        String profile = "existing";
        updateTorrents();

        long[] speed = ds.getTrafficSpeed(profile);

        assertEquals(530000l, speed[0]);
        assertEquals(197000l, speed[1]);
    }

    /* TODO: test multiple profiles */

    private TorrentStatus updateTorrents() {
        String profile = "existing";
        TorrentDetails details = null;
        InputStream is = null;
        URL url = getClass().getResource("/json/torrents.json");

        ds.open();

        try {
            is = url.openStream();
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            JsonFactory factory = mapper.getFactory();
            JsonParser parser = factory.createParser(is);

            parser.nextToken();
            return ds.updateTorrents(profile, parser, false);
        } catch (IOException e) {
            assertTrue(e.toString(), false);
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (details != null) {
                try {
                    details.torrentCursor.close();
                    details.filesCursor.close();
                    details.trackersCursor.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
