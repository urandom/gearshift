package org.sugr.gearshift.unit.core;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowPreferenceManager;
import org.sugr.gearshift.BuildConfig;
import org.sugr.gearshift.G;
import org.sugr.gearshift.core.TransmissionProfile;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class)
public class TransmissionProfileTest {
    private SharedPreferences defaultPrefs;
    private SharedPreferences prefs;
    private Context context;
    private static final String existingId = "existing";

    @Before public void setUp() {
        defaultPrefs = ShadowPreferenceManager.getDefaultSharedPreferences(
                RuntimeEnvironment.application.getApplicationContext());
        prefs = ShadowApplication.getInstance().getSharedPreferences(
                "profiles", Activity.MODE_PRIVATE);
        context = mock(Context.class);

        when(context.getFilesDir()).thenReturn(new File("/mock/dir"));

        when(
            context.getSharedPreferences(TransmissionProfile.getPreferencesName(), Activity.MODE_PRIVATE)
        ).thenReturn(prefs);

        prefs.edit().putString(G.PREF_NAME + existingId, "name").commit();
        prefs.edit().putString(G.PREF_HOST + existingId, "host").commit();
        prefs.edit().putString(G.PREF_PORT + existingId, "9911").commit();
        prefs.edit().putString(G.PREF_PATH + existingId, "/transmission/rpc").commit();
        prefs.edit().putString(G.PREF_USER + existingId, "").commit();
        prefs.edit().putString(G.PREF_PASS + existingId, "").commit();
        prefs.edit().putBoolean(G.PREF_SSL + existingId, true).commit();
        prefs.edit().putString(G.PREF_TIMEOUT + existingId, "2").commit();
        prefs.edit().putString(G.PREF_RETRIES + existingId, "15").commit();
        Set<String> directories = new HashSet<>();
        directories.add("/foo");
        directories.add("/bar/baz");
        prefs.edit().putStringSet(G.PREF_DIRECTORIES + existingId, directories).commit();
        prefs.edit().putString(G.PREF_LAST_DIRECTORY + existingId, "/alpha").commit();
        prefs.edit().putBoolean(G.PREF_MOVE_DATA + existingId, true).commit();
        prefs.edit().putBoolean(G.PREF_DELETE_LOCAL + existingId, false).commit();
        prefs.edit().putBoolean(G.PREF_START_PAUSED + existingId, false).commit();
    }

    @Test public void readProfiles() {
        prefs.edit().putStringSet(G.PREF_PROFILES, new HashSet<String>()).commit();

        TransmissionProfile[] profiles = TransmissionProfile.readProfiles(prefs);
        assertEquals(0, profiles.length);
    }

    @Test public void getPreferencesName() {
        assertEquals(G.PROFILES_PREF_NAME, TransmissionProfile.getPreferencesName());
    }

    @Test public void cleanTemporaryPreferences() {
        prefs.edit().putString(G.PREF_NAME, "name").commit();
        prefs.edit().putString(G.PREF_HOST, "host").commit();
        prefs.edit().putString(G.PREF_PORT, "9911").commit();
        prefs.edit().putBoolean(G.PREF_SSL, true).commit();
        prefs.edit().putString(G.PREF_RETRIES, "15").commit();

        TransmissionProfile.cleanTemporaryPreferences();
        assertFalse(prefs.contains(G.PREF_NAME));
        assertFalse(prefs.contains(G.PREF_HOST));
        assertFalse(prefs.contains(G.PREF_PORT));
        assertFalse(prefs.contains(G.PREF_PATH));
        assertFalse(prefs.contains(G.PREF_USER));
        assertFalse(prefs.contains(G.PREF_PASS));
        assertFalse(prefs.contains(G.PREF_SSL));
        assertFalse(prefs.contains(G.PREF_TIMEOUT));
        assertFalse(prefs.contains(G.PREF_RETRIES));
        assertFalse(prefs.contains(G.PREF_DIRECTORIES));
    }

    @Test public void setCurrentProfile() {
        TransmissionProfile.setCurrentProfile(null, defaultPrefs);
        assertNull(defaultPrefs.getString(G.PREF_CURRENT_PROFILE, null));
    }

    @Test public void load() {
        TransmissionProfile profile = new TransmissionProfile("nonexisting", defaultPrefs);
        assertEquals("", profile.getName());
        assertEquals("", profile.getHost());
        assertEquals(9091, profile.getPort());
        assertEquals("", profile.getPath());
        assertEquals("", profile.getUsername());
        assertEquals("", profile.getPassword());
        assertFalse(profile.isUseSSL());
        assertEquals(-1, profile.getTimeout());
        assertEquals(-1, profile.getRetries());
        assertEquals(0, profile.getDirectories().size());
        assertEquals("", profile.getLastDownloadDirectory());
        assertTrue(profile.getMoveData());
        assertFalse(profile.getDeleteLocal());
        assertFalse(profile.getStartPaused());
        assertFalse(prefs.contains(G.PREF_NAME + "nonexisting"));

        profile = new TransmissionProfile(existingId, defaultPrefs);
        assertEquals("name", profile.getName());
        assertEquals("host", profile.getHost());
        assertEquals(9911, profile.getPort());
        assertEquals("/transmission/rpc", profile.getPath());
        assertEquals("", profile.getUsername());
        assertEquals("", profile.getPassword());
        assertTrue(profile.isUseSSL());
        assertEquals(2, profile.getTimeout());
        assertEquals(15, profile.getRetries());
        assertEquals(2, profile.getDirectories().size());
        assertEquals("/alpha", profile.getLastDownloadDirectory());
        assertTrue(profile.getMoveData());
        assertFalse(profile.getDeleteLocal());
        assertFalse(profile.getStartPaused());
    }

    @Test public void save() {
        TransmissionProfile profile = new TransmissionProfile(existingId, defaultPrefs);
        profile.setName("another name");
        profile.setHost("some host");
        profile.setTimeout(22);
        profile.setUsername("example");
        profile.setUseSSL(false);
        profile.setLastDownloadDirectory("/foo/bar");
        profile.setDeleteLocal(true);
        profile.setStartPaused(true);
        profile.setMoveData(false);

        profile.save();

        assertEquals(prefs.getString(G.PREF_NAME + existingId, ""), "another name");
        assertEquals(prefs.getString(G.PREF_HOST + existingId, ""), "some host");
        assertEquals(prefs.getString(G.PREF_TIMEOUT + existingId, ""), "22");
        assertEquals(prefs.getString(G.PREF_USER + existingId, ""), "example");
        assertFalse(prefs.getBoolean(G.PREF_SSL + existingId, true));
        assertEquals("/foo/bar", prefs.getString(G.PREF_LAST_DIRECTORY + existingId, ""));
        assertTrue(prefs.getBoolean(G.PREF_DELETE_LOCAL + existingId, true));
        assertTrue(prefs.getBoolean(G.PREF_START_PAUSED + existingId, true));
        assertFalse(prefs.getBoolean(G.PREF_MOVE_DATA + existingId, true));
    }

    @Test public void delete() {
        assertFalse(prefs.contains(G.PREF_NAME + "nonexisting"));
        TransmissionProfile profile = new TransmissionProfile("nonexisting", defaultPrefs);
        profile.delete();
        assertFalse(prefs.contains(G.PREF_NAME + "nonexisting"));

        assertTrue(prefs.contains(G.PREF_NAME + existingId));
        assertTrue(prefs.contains(G.PREF_HOST + existingId));
        assertTrue(prefs.contains(G.PREF_PORT + existingId));
        assertTrue(prefs.contains(G.PREF_PATH + existingId));
        assertTrue(prefs.contains(G.PREF_USER + existingId));
        assertTrue(prefs.contains(G.PREF_PASS + existingId));
        assertTrue(prefs.contains(G.PREF_SSL + existingId));
        assertTrue(prefs.contains(G.PREF_TIMEOUT + existingId));
        assertTrue(prefs.contains(G.PREF_RETRIES + existingId));
        assertTrue(prefs.contains(G.PREF_DIRECTORIES + existingId));
        assertTrue(prefs.contains(G.PREF_LAST_DIRECTORY + existingId));
        assertTrue(prefs.contains(G.PREF_MOVE_DATA + existingId));
        assertTrue(prefs.contains(G.PREF_DELETE_LOCAL + existingId));
        assertTrue(prefs.contains(G.PREF_START_PAUSED + existingId));

        profile = new TransmissionProfile(existingId, defaultPrefs);
        profile.delete();
        assertFalse(prefs.contains(G.PREF_NAME + existingId));
        assertFalse(prefs.contains(G.PREF_HOST + existingId));
        assertFalse(prefs.contains(G.PREF_PORT + existingId));
        assertFalse(prefs.contains(G.PREF_PATH + existingId));
        assertFalse(prefs.contains(G.PREF_USER + existingId));
        assertFalse(prefs.contains(G.PREF_PASS + existingId));
        assertFalse(prefs.contains(G.PREF_SSL + existingId));
        assertFalse(prefs.contains(G.PREF_TIMEOUT + existingId));
        assertFalse(prefs.contains(G.PREF_RETRIES + existingId));
        assertFalse(prefs.contains(G.PREF_DIRECTORIES + existingId));
        assertFalse(prefs.contains(G.PREF_LAST_DIRECTORY + existingId));
        assertFalse(prefs.contains(G.PREF_MOVE_DATA + existingId));
        assertFalse(prefs.contains(G.PREF_DELETE_LOCAL + existingId));
        assertFalse(prefs.contains(G.PREF_START_PAUSED + existingId));
    }

    @Test public void fillTemporaryPreferences() {
        assertFalse(prefs.contains(G.PREF_NAME));
        assertFalse(prefs.contains(G.PREF_HOST));
        assertFalse(prefs.contains(G.PREF_PORT));
        assertFalse(prefs.contains(G.PREF_PATH));
        assertFalse(prefs.contains(G.PREF_USER));
        assertFalse(prefs.contains(G.PREF_PASS));
        assertFalse(prefs.contains(G.PREF_SSL));
        assertFalse(prefs.contains(G.PREF_TIMEOUT));
        assertFalse(prefs.contains(G.PREF_RETRIES));
        assertFalse(prefs.contains(G.PREF_DIRECTORIES));

        TransmissionProfile profile = new TransmissionProfile(existingId, defaultPrefs);
        profile.fillTemporatyPreferences();

        assertTrue(prefs.contains(G.PREF_NAME));
        assertTrue(prefs.contains(G.PREF_HOST));
        assertTrue(prefs.contains(G.PREF_PORT));
        assertTrue(prefs.contains(G.PREF_PATH));
        assertTrue(prefs.contains(G.PREF_USER));
        assertTrue(prefs.contains(G.PREF_PASS));
        assertTrue(prefs.contains(G.PREF_SSL));
        assertTrue(prefs.contains(G.PREF_TIMEOUT));
        assertTrue(prefs.contains(G.PREF_RETRIES));
        assertTrue(prefs.contains(G.PREF_DIRECTORIES));

        TransmissionProfile.cleanTemporaryPreferences();
        assertFalse(prefs.contains(G.PREF_NAME));
        assertFalse(prefs.contains(G.PREF_HOST));
        assertFalse(prefs.contains(G.PREF_PORT));
        assertFalse(prefs.contains(G.PREF_PATH));
        assertFalse(prefs.contains(G.PREF_USER));
        assertFalse(prefs.contains(G.PREF_PASS));
        assertFalse(prefs.contains(G.PREF_SSL));
        assertFalse(prefs.contains(G.PREF_TIMEOUT));
        assertFalse(prefs.contains(G.PREF_RETRIES));
        assertFalse(prefs.contains(G.PREF_DIRECTORIES));
    }

    @Test public void name() {
        TransmissionProfile profile = new TransmissionProfile(existingId, defaultPrefs);
        assertEquals("name", profile.getName());

        profile.setName("test");
        assertEquals("test", profile.getName());
    }

    @Test public void host() {
        TransmissionProfile profile = new TransmissionProfile(existingId, defaultPrefs);
        assertEquals("host", profile.getHost());

        profile.setHost("test");
        assertEquals("test", profile.getHost());
    }

    @Test public void port() {
        TransmissionProfile profile = new TransmissionProfile(existingId, defaultPrefs);
        assertEquals(9911, profile.getPort());

        profile.setPort(14111);
        assertEquals(14111, profile.getPort());
    }

    @Test public void path() {
        TransmissionProfile profile = new TransmissionProfile(existingId, defaultPrefs);
        assertEquals("/transmission/rpc", profile.getPath());

        profile.setPath("/foo/bar");
        assertEquals("/foo/bar", profile.getPath());
    }

    @Test public void username() {
        TransmissionProfile profile = new TransmissionProfile(existingId, defaultPrefs);
        assertEquals("", profile.getUsername());

        profile.setUsername("another");
        assertEquals("another", profile.getUsername());
    }

    @Test public void password() {
        TransmissionProfile profile = new TransmissionProfile(existingId, defaultPrefs);
        assertEquals("", profile.getPassword());

        profile.setPassword("pass");
        assertEquals("pass", profile.getPassword());
    }

    @Test public void ssl() {
        TransmissionProfile profile = new TransmissionProfile(existingId, defaultPrefs);
        assertTrue(profile.isUseSSL());

        profile.setUseSSL(false);
        assertFalse(profile.isUseSSL());
    }

    @Test public void timeout() {
        TransmissionProfile profile = new TransmissionProfile(existingId, defaultPrefs);
        assertEquals(2, profile.getTimeout());

        profile.setTimeout(14);
        assertEquals(14, profile.getTimeout());
    }

    @Test public void retries() {
        TransmissionProfile profile = new TransmissionProfile(existingId, defaultPrefs);
        assertEquals(15, profile.getRetries());

        profile.setRetries(10);
        assertEquals(10, profile.getRetries());
    }

    @Test public void directories() {
        TransmissionProfile profile = new TransmissionProfile(existingId, defaultPrefs);
        Set<String> dirs = profile.getDirectories();

        Set<String> expected = new HashSet<>();
        expected.add("/foo");
        expected.add("/bar/baz");
        assertEquals(expected, dirs);

        dirs.add("/alpha/beta");
        profile.setDirectories(dirs);
        expected.add("/alpha/beta");
        assertEquals(expected, profile.getDirectories());
    }

    @Test public void lastDownloadDirectory() {
        TransmissionProfile profile = new TransmissionProfile(existingId, defaultPrefs);
        assertEquals("/alpha", profile.getLastDownloadDirectory());

        profile.setLastDownloadDirectory("/beta");
        assertEquals("/beta", profile.getLastDownloadDirectory());
    }

    @Test public void moveData() {
        TransmissionProfile profile = new TransmissionProfile(existingId, defaultPrefs);
        assertTrue(profile.getMoveData());

        profile.setMoveData(false);
        assertFalse(profile.getMoveData());
    }

    @Test public void deleteLocal() {
        TransmissionProfile profile = new TransmissionProfile(existingId, defaultPrefs);
        assertFalse(profile.getDeleteLocal());

        profile.setDeleteLocal(true);
        assertTrue(profile.getDeleteLocal());
    }

    @Test public void startPaused() {
        TransmissionProfile profile = new TransmissionProfile(existingId, defaultPrefs);
        assertFalse(profile.getStartPaused());

        profile.setStartPaused(true);
        assertTrue(profile.getStartPaused());
    }

    @Test public void parceling() {
        /* FIXME: parcels are currently broken in robolectric-2.3-SNAPSHOT
        TransmissionProfile profile = new TransmissionProfile(existingId, context, defaultPrefs);
        profile.setMoveData(false);
        profile.setStartPaused(true);
        profile.setPath("/foo/bar");

        Parcel p = Parcel.obtain();
        TransmissionProfile clone = TransmissionProfile.CREATOR.createFromParcel(p);

        assertNotNull(clone);
        assertEquals(profile.getId(), clone.getId());
        assertEquals(profile.getName(), clone.getName());
        assertEquals(profile.getHost(), clone.getHost());
        assertEquals(profile.getPort(), clone.getPort());
        assertEquals(profile.getPath(), clone.getPath());
        assertEquals(profile.getUsername(), clone.getUsername());
        assertEquals(profile.getPassword(), clone.getPassword());
        assertEquals(profile.isUseSSL(), clone.isUseSSL());
        assertEquals(profile.getTimeout(), clone.getTimeout());
        assertEquals(profile.getRetries(), clone.getRetries());
        assertEquals(profile.getDirectories(), clone.getDirectories());
        assertEquals(profile.getLastDownloadDirectory(), clone.getLastDownloadDirectory());
        assertEquals(profile.getMoveData(), clone.getMoveData());
        assertEquals(profile.getDeleteLocal(), clone.getDeleteLocal());
        assertEquals(profile.getStartPaused(), clone.getStartPaused());
        */
    }
}

