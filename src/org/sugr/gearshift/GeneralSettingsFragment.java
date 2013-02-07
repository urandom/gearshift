package org.sugr.gearshift;

import android.os.Bundle;
import android.preference.PreferenceManager;

public class GeneralSettingsFragment extends BasePreferenceFragment {

    public static final String PREF_UPDATE_ACTIVE = "update_active_torrents";
    public static final String PREF_FULL_UPDATE = "full_update";
    public static final String PREF_UPDATE_INTERVAL = "update_interval";
    public static final String PREF_START_PAUSED = "start_paused";
    public static final String PREF_DELETE_LOCAL_TORRENT = "delete_local_torrent_file";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        addPreferencesFromResource(R.xml.general_preferences);
        
        mSummaryPrefs = new Object[][] {
            {PREF_FULL_UPDATE, getString(R.string.full_update_summary_format), -1, -1, "int"},
            {PREF_UPDATE_INTERVAL, getString(R.string.update_interval_summary_format),
                R.array.pref_update_interval_values, R.array.pref_update_interval_entries, ""},
        };
    }
}
