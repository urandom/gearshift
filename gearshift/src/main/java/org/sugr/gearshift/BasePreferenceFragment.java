package org.sugr.gearshift;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import java.util.Arrays;

public class BasePreferenceFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {
    protected SharedPreferences sharedPrefs;
    protected Object[][] summaryPrefs = {
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePrefSummary(null);
        sharedPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(G.PREF_DEBUG)) {
            G.DEBUG = sharedPreferences.getBoolean(key, false);
        } else {
            G.requestBackup(getActivity());
            updatePrefSummary(key);
        }
    }

    protected void updatePrefSummary(String aKey) {
        for (int i = 0; i < summaryPrefs.length; i++) {
            String key;
            if (aKey != null) {
                if (aKey.equals(summaryPrefs[i][0]))
                    key = aKey;
                else
                    continue;
            } else {
                key = (String) summaryPrefs[i][0];
            }

            Preference pref = findPreference(key);
            if (pref == null)
                continue;

            String summary = (String) summaryPrefs[i][1];

            if ((Integer) summaryPrefs[i][2] == -1)
                pref.setSummary(String.format(summary,
                    summaryPrefs[i][4] == "int"
                        ? Integer.parseInt(sharedPrefs.getString(key, sharedPrefs.getString(key, "-1")))
                        : sharedPrefs.getString(key, sharedPrefs.getString(key, ""))
                ));
            else {
                String[] values = getResources().getStringArray((Integer) summaryPrefs[i][2]);
                String[] entries = getResources().getStringArray((Integer) summaryPrefs[i][3]);
                int index = Arrays.asList(values).indexOf(
                        sharedPrefs.getString(key, sharedPrefs.getString(key, null)));
                if (index > -1 && entries.length > index)
                    pref.setSummary(String.format(summary, entries[index]));
            }
        }
    }
}
