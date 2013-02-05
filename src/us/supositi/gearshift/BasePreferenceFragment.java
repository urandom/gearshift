package us.supositi.gearshift;

import java.util.Arrays;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.view.Menu;
import android.view.MenuInflater;

public class BasePreferenceFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {
    protected SharedPreferences mSharedPrefs;
    protected Object[][] mSummaryPrefs = {
    };
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
                
        setHasOptionsMenu(true);
    }

    
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        
        menu.clear();
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
    
    protected void updatePrefSummary(String aKey) {
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
                pref.setSummary(String.format(summary,
                    mSummaryPrefs[i][4] == "int"
                        ? Integer.parseInt(mSharedPrefs.getString(key, mSharedPrefs.getString(key, "-1")))
                        : mSharedPrefs.getString(key, mSharedPrefs.getString(key, ""))
                ));
            else {
                String[] values = getResources().getStringArray((Integer) mSummaryPrefs[i][2]);
                String[] entries = getResources().getStringArray((Integer) mSummaryPrefs[i][3]);
                int index = Arrays.asList(values).indexOf(
                        mSharedPrefs.getString(key, mSharedPrefs.getString(key, null)));
                if (index > -1 && entries.length > index)
                    pref.setSummary(String.format(summary, entries[index]));
            }
        }
    }
}
