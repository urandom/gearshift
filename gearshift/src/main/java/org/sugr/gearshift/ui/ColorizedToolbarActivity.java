package org.sugr.gearshift.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;
import org.sugr.gearshift.core.TransmissionProfile;

public class ColorizedToolbarActivity extends AppCompatActivity {
    private TransmissionProfile profile;

    /* The callback will get garbage collected if its a mere anon class */
    private SharedPreferences.OnSharedPreferenceChangeListener profileChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    if (profile == null) return;

                    if (!key.startsWith(G.PREF_COLOR) ||
                            !key.endsWith(profile.getId())) return;

                    colorizeToolbar();
                }
            };

    @Override protected void onResume() {
        super.onResume();

        SharedPreferences prefs = getSharedPreferences(
                TransmissionProfile.getPreferencesName(),
                Activity.MODE_PRIVATE);

        prefs.registerOnSharedPreferenceChangeListener(profileChangeListener);
        if (profile != null) {
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
        this.profile = profile;

        colorizeToolbar();
    }

    private void colorizeToolbar() {
        View toolbar = ColorizedToolbarActivity.this.findViewById(R.id.toolbar);
        if (toolbar == null) {
            return;
        }

        toolbar.setBackgroundColor(profile.getColor());
    }
}
