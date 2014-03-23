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

import static org.junit.Assert.*;

import static org.mockito.Mockito.*;

@RunWith(RobolectricGradleTestRunner.class)
public class TransmissionProfileTest {
    private TestSharedPreferences defaultPrefs;
    private TestSharedPreferences prefs;
    private Context context;

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
    }

    @Test public void readProfiles() {
        prefs.edit().putStringSet(G.PREF_PROFILES, new HashSet<String>()).commit();

        TransmissionProfile[] profiles = TransmissionProfile.readProfiles(context, prefs);
        assertEquals(0, profiles.length);
    }

    @Test public void getPreferencesName() {
        assertEquals(G.PROFILES_PREF_NAME, TransmissionProfile.getPreferencesName());
    }

    @Test public void setCurrentProfile() {
        TransmissionProfile.setCurrentProfile(null, defaultPrefs);
        assertTrue(defaultPrefs.contains(G.PREF_CURRENT_PROFILE));
        assertNull(defaultPrefs.getString(G.PREF_CURRENT_PROFILE, null));
    }

    @Test public void load() {
        TransmissionProfile profile = new TransmissionProfile("nonexistent", context, defaultPrefs);
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
    }
}

