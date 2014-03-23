package org.sugr.gearshift.unit;

import android.content.SharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sugr.gearshift.G;
import org.sugr.gearshift.TransmissionProfile;
import org.sugr.gearshift.unit.util.MockEditor;
import org.sugr.gearshift.unit.util.RobolectricGradleTestRunner;

import static org.junit.Assert.*;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import static org.mockito.Mockito.*;

@RunWith(RobolectricGradleTestRunner.class)
public class TransmissionProfileTest {
    @Test
    public void getPreferencesName() {
        assertEquals(G.PROFILES_PREF_NAME, TransmissionProfile.getPreferencesName());
    }

    @Test
    public void setCurrentProfile() {
        SharedPreferences prefs = mock(SharedPreferences.class);
        MockEditor editor = new MockEditor();

        when(prefs.edit()).thenReturn(editor);

        TransmissionProfile.setCurrentProfile(null, prefs);
        assertTrue(editor.data.containsKey(G.PREF_CURRENT_PROFILE));
        assertNull(editor.data.get(G.PREF_CURRENT_PROFILE));
    }
}

