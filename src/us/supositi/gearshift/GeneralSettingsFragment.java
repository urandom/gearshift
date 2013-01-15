package us.supositi.gearshift;

import android.os.Bundle;
import android.preference.PreferenceManager;

public class GeneralSettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        addPreferencesFromResource(R.xml.general_preferences);
        
        mSummaryPrefs = new Object[][] {
            {"full_update", getString(R.string.full_update_summary_format), -1, -1, "int"},
            {"update_interval", getString(R.string.update_interval_summary_format),
                R.array.pref_update_interval_values, R.array.pref_update_interval_entries, ""},
        };
    }
}
