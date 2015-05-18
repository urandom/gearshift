package org.sugr.gearshift.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;
import org.sugr.gearshift.core.TransmissionProfile;
import org.sugr.gearshift.ui.util.Colorizer;

public class ColorizedToolbarActivity extends AppCompatActivity {
    private String profileId;
    private int profileColor;

    private static final String PROFILE_ID = "profile-id";
    private static final String PROFILE_COLOR = "profile-color";

    /* The callback will get garbage collected if its a mere anon class */
    private SharedPreferences.OnSharedPreferenceChangeListener profileChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    if (profileId == null) return;

                    if (!key.startsWith(G.PREF_COLOR) ||
                            !key.endsWith(profileId)) return;

                    profileColor = prefs.getInt(key,
                            Colorizer.defaultColor(ColorizedToolbarActivity.this));
                    colorizeToolbar();
                }
            };

    @Override protected void onCreate(Bundle savedInstanceState) {
        if (savedInstanceState != null && savedInstanceState.containsKey(PROFILE_ID)) {
            profileId = savedInstanceState.getString(PROFILE_ID);
            profileColor = savedInstanceState.getInt(PROFILE_COLOR, Colorizer.defaultColor(this));
        }

        super.onCreate(savedInstanceState);
    }

    @Override public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (profileId != null) {
            outState.putString(PROFILE_ID, profileId);
            outState.putInt(PROFILE_COLOR, profileColor);
        }
    }

    @Override protected void onResume() {
        super.onResume();

        SharedPreferences prefs = getSharedPreferences(
                TransmissionProfile.getPreferencesName(),
                Activity.MODE_PRIVATE);

        prefs.registerOnSharedPreferenceChangeListener(profileChangeListener);
        if (profileId != null) {
            colorizeToolbar();
        }
    }

    @Override protected void onPause() {
        super.onPause();

        SharedPreferences prefs = getSharedPreferences(
                TransmissionProfile.getPreferencesName(),
                Activity.MODE_PRIVATE);

        prefs.unregisterOnSharedPreferenceChangeListener(profileChangeListener);
    }

    protected void colorize(TransmissionProfile profile) {
        profileId = profile.getId();
        profileColor = profile.getColor();

        colorizeToolbar();
    }

    private void colorizeToolbar() {
        View toolbar = ColorizedToolbarActivity.this.findViewById(R.id.toolbar);
        if (toolbar == null) {
            return;
        }

        toolbar.setBackgroundColor(profileColor);
    }
}
