package us.supositi.gearshift;

import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Parcelable;

public class TorrentProfileSettingsFragment extends BasePreferenceFragment {
    public static final String ARG_PROFILE_ID = "profile_id";
    public static final String ARG_PROFILES = "profiles";
    
    private String mProfileName;
    private TorrentProfile[] mProfiles;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        addPreferencesFromResource(R.xml.torrent_profile_preferences);
        
        mSummaryPrefs = new Object[][] {
            {"profile_name", getString(R.string.profile_name_summary_format), -1, -1, ""},
        };
        
        if (getArguments().containsKey(ARG_PROFILES)) {
            Parcelable[] parcels = getArguments().getParcelableArray(ARG_PROFILES);
            mProfiles = new TorrentProfile[parcels.length];
            
            for (int i = 0; i < parcels.length; i++)
                mProfiles[i] = (TorrentProfile) parcels[i];
        }
        if (getArguments().containsKey(ARG_PROFILE_ID)) {
            mProfileName = getArguments().getString(ARG_PROFILE_ID);
        }
        
        if (mProfileName != null) {
            Editor e = mSharedPrefs.edit();
            e.putString("profile_name", mProfileName);
            
            e.commit();
        }
    }
}
