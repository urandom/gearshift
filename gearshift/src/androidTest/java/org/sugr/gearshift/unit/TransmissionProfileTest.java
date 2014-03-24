package org.sugr.gearshift.unit;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.tester.android.content.TestSharedPreferences;
import org.sugr.gearshift.G;
import org.sugr.gearshift.TransmissionProfile;
import org.sugr.gearshift.unit.util.RobolectricGradleTestRunner;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

import static org.mockito.Mockito.*;

@RunWith(RobolectricGradleTestRunner.class)
public class TransmissionProfileTest {
    private TestSharedPreferences defaultPrefs;
    private TestSharedPreferences prefs;
    private Context context;
    private static final String existingId = "existing";

    @Before public void setUp() {
        defaultPrefs = new TestSharedPreferences(new HashMap<String, Map<String, Object>>(),
            "default", Activity.MODE_PRIVATE);
        prefs = new TestSharedPreferences(new HashMap<String, Map<String, Object>>(),
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

        TransmissionProfile[] profiles = TransmissionProfile.readProfiles(context, prefs);
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

        TransmissionProfile.cleanTemporaryPreferences(context);
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
        assertTrue(defaultPrefs.contains(G.PREF_CURRENT_PROFILE));
        assertNull(defaultPrefs.getString(G.PREF_CURRENT_PROFILE, null));
    }

    @Test public void load() {
        TransmissionProfile profile = new TransmissionProfile("nonexisting", context, defaultPrefs);
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

        profile = new TransmissionProfile(existingId, context, defaultPrefs);
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
        TransmissionProfile profile = new TransmissionProfile(existingId, context, defaultPrefs);
        profile.setName("another name");
        profile.setHost("some host");
        profile.setTimeout(22);
        profile.setUsername("example");
        profile.setUseSSL(false);

        profile.save();

        assertEquals(prefs.getString(G.PREF_NAME + existingId, ""), "another name");
        assertEquals(prefs.getString(G.PREF_HOST + existingId, ""), "some host");
        assertEquals(prefs.getString(G.PREF_TIMEOUT + existingId, ""), "22");
        assertEquals(prefs.getString(G.PREF_USER + existingId, ""), "example");
        assertFalse(prefs.getBoolean(G.PREF_SSL + existingId, true));
    }

    @Test public void name() {
        TransmissionProfile profile = new TransmissionProfile(existingId, context, defaultPrefs);
        assertEquals("name", profile.getName());

        profile.setName("test");
        assertEquals("test", profile.getName());
    }

    @Test public void host() {
        TransmissionProfile profile = new TransmissionProfile(existingId, context, defaultPrefs);
        assertEquals("host", profile.getHost());

        profile.setHost("test");
        assertEquals("test", profile.getHost());
    }

    @Test public void port() {
        TransmissionProfile profile = new TransmissionProfile(existingId, context, defaultPrefs);
        assertEquals(9911, profile.getPort());

        profile.setPort(14111);
        assertEquals(14111, profile.getPort());
    }

    @Test public void ssl() {
        TransmissionProfile profile = new TransmissionProfile(existingId, context, defaultPrefs);
        assertTrue(profile.isUseSSL());

        profile.setUseSSL(false);
        assertFalse(profile.isUseSSL());
    }
}

