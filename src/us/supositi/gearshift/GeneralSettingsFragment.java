package us.supositi.gearshift;

import java.text.MessageFormat;
import java.util.Arrays;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

public class GeneralSettingsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {
    private SharedPreferences mSharedPrefs;
    
    private Object[][] mSummaryPrefs = {

    };
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        addPreferencesFromResource(R.xml.general_preferences);
        
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mSummaryPrefs = new Object[][] {
            {"full_update", getString(R.string.full_update_summary_format), -1, -1},
            {"update_interval", getString(R.string.update_interval_summary_format),
                R.array.pref_update_interval_values, R.array.pref_update_interval_entries},
        };
    }
    
    @Override
    public void onResume() {
        super.onResume();
        updatePrefSummary(null);
        mSharedPrefs.registerOnSharedPreferenceChangeListener(this);
    }
    
    @Override
    public void onPause() {
        super.onPause();
        mSharedPrefs.unregisterOnSharedPreferenceChangeListener(this);
    }
    
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updatePrefSummary(key);
    }
    
    private void updatePrefSummary(String aKey) {
        for (int i = 0; i < mSummaryPrefs.length; i++) {
            String key;
            if (aKey != null) {
                if (aKey.equals((String) mSummaryPrefs[i][0]))
                    key = aKey;
                else
                    continue;
            } else {
                key = (String) mSummaryPrefs[i][0];
            }
            
            Preference pref = findPreference((String) key);
            if (pref == null)
                continue;
            
            String summary = (String) mSummaryPrefs[i][1];
            if ((Integer) mSummaryPrefs[i][2] == -1)
                pref.setSummary(MessageFormat.format(summary,
                        Integer.parseInt(mSharedPrefs.getString(key, mSharedPrefs.getString(key, null)))));
            else {
                String[] values = getResources().getStringArray((Integer) mSummaryPrefs[i][2]);
                String[] entries = getResources().getStringArray((Integer) mSummaryPrefs[i][3]);
                int index = Arrays.asList(values).indexOf(
                        mSharedPrefs.getString(key, mSharedPrefs.getString(key, null)));
                if (index > -1 && entries.length > index)
                    pref.setSummary(MessageFormat.format(summary, entries[index]));
            }
        }
    }
}
