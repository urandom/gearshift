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
            {"profile_name", getString(R.string.profile_summary_format), -1, -1, ""},
            {"profile_host", getString(R.string.profile_summary_format), -1, -1, ""},
            {"profile_port", getString(R.string.profile_summary_format), -1, -1, ""},
            {"profile_username", getString(R.string.profile_summary_format), -1, -1, ""},
            {"profile_path", getString(R.string.profile_summary_format), -1, -1, ""},
            {"profile_timeout", getString(R.string.profile_summary_format), -1, -1, ""},
            {"profile_retries", getString(R.string.profile_summary_format),
                R.array.pref_con_retries_values, R.array.pref_con_retries_entries, ""},
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
            TorrentProfile profile = null;
            
            for (TorrentProfile prof : mProfiles) {
                if (prof.getName().equals(mProfileName)) {
                    profile = prof;
                    break;
                }
            }
            if (profile != null) {
                e.putString("profile_host", profile.getHost());
                e.putString("profile_port", String.format("%d", profile.getPort()));
                e.putString("profile_path", profile.getPath());
                e.putString("profile_username", profile.getUsername());
                e.putString("profile_password", profile.getPassword());
                e.putBoolean("profile_use_ssl", profile.isUseSSL());
                e.putString("profile_timeout", String.format("%d", profile.getTimeout()));
                e.putString("profile_retries", String.format("%d", profile.getRetries()));
            }
            
            e.commit();
        }
    }
}
