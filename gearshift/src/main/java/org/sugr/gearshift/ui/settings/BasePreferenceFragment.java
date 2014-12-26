package org.sugr.gearshift.ui.settings;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.widget.Toolbar;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;

import java.util.Arrays;

public class BasePreferenceFragment extends PreferenceFragment
        implements OnSharedPreferenceChangeListener {

    protected SharedPreferences sharedPrefs;
    protected Object[][] summaryPrefs = {
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
    }

    @Override public void onResume() {
        super.onResume();
        updatePrefSummary(null);
        sharedPrefs.registerOnSharedPreferenceChangeListener(this);

        Toolbar toolbar = (Toolbar) getActivity().findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_close_white_24dp);
    }

    @Override public void onPause() {
        super.onPause();
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(this);

        Toolbar toolbar = (Toolbar) getActivity().findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
        toolbar.setTitle(R.string.settings);
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(G.PREF_DEBUG)) {
            G.DEBUG = sharedPreferences.getBoolean(key, false);
        } else {
            G.requestBackup(getActivity());
            updatePrefSummary(key);
        }
    }

    protected void updatePrefSummary(String aKey) {
        for (Object[] summaryPref : summaryPrefs) {
            String key;
            if (aKey != null) {
                if (aKey.equals(summaryPref[0]))
                    key = aKey;
                else
                    continue;
            } else {
                key = (String) summaryPref[0];
            }

            Preference pref = findPreference(key);
            if (pref == null)
                continue;

            String summary = (String) summaryPref[1];

            if ((Integer) summaryPref[2] == -1)
                pref.setSummary(String.format(summary, summaryPref[4] == "int"
                    ? Integer.parseInt(sharedPrefs.getString(key, "-1"))
                    : sharedPrefs.getString(key, "")
                ));
            else {
                String[] values = getResources().getStringArray((Integer) summaryPref[2]);
                String[] entries = getResources().getStringArray((Integer) summaryPref[3]);
                int index = Arrays.asList(values).indexOf(
                    sharedPrefs.getString(key, sharedPrefs.getString(key, null)));
                if (index > -1 && entries.length > index)
                    pref.setSummary(String.format(summary, entries[index]));
            }
        }
    }
}
