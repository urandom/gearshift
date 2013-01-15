package us.supositi.gearshift;

import java.text.MessageFormat;

import android.app.Activity;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class TorrentProfileSettingsFragment extends BasePreferenceFragment {
    public static final String ARG_PROFILE_ID = "profile_id";
    
    private TorrentProfile mProfile;
    
    private boolean mNew = true;
    private boolean mSaved = false;
            
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        String id = null;
        if (getArguments().containsKey(ARG_PROFILE_ID)) {
            id = getArguments().getString(ARG_PROFILE_ID);
            mNew = false;
        }
        
        if (id == null)
            mProfile = new TorrentProfile();
        else
            mProfile = new TorrentProfile(id, getActivity());
        
        if (TorrentListActivity.DEBUG)
            Log.d(TorrentListActivity.LogTag, MessageFormat.format(
                    "Editing (new ? {0}) profile {1}",
                    new Object[] {mNew, mProfile.getId()}));
        
        String prefname = TorrentProfile.PREF_PREFIX + (id == null ? "temp" : id); 
        mSharedPrefs = getActivity().getSharedPreferences(
                prefname, Activity.MODE_PRIVATE);
        
        getPreferenceManager().setSharedPreferencesName(prefname);
        if (mNew) {
            Editor e = mSharedPrefs.edit();
            
            e.clear();
            e.commit();
        }

        addPreferencesFromResource(R.xml.torrent_profile_preferences);
        PreferenceManager.setDefaultValues(
                getActivity(), TorrentProfile.PREF_PREFIX + (id == null ? "temp" : id),
                Activity.MODE_PRIVATE, R.xml.torrent_profile_preferences, true);
        
        mSummaryPrefs = new Object[][] {
            {TorrentProfile.PREF_NAME, getString(R.string.profile_summary_format), -1, -1, ""},
            {TorrentProfile.PREF_HOST, getString(R.string.profile_summary_format), -1, -1, ""},
            {TorrentProfile.PREF_PORT, getString(R.string.profile_summary_format), -1, -1, ""},
            {TorrentProfile.PREF_USER, getString(R.string.profile_summary_format), -1, -1, ""},
            {TorrentProfile.PREF_PATH, getString(R.string.profile_summary_format), -1, -1, ""},
            {TorrentProfile.PREF_TIMEOUT, getString(R.string.profile_summary_format), -1, -1, ""},
            {TorrentProfile.PREF_RETRIES, getString(R.string.profile_summary_format),
                R.array.pref_con_retries_values, R.array.pref_con_retries_entries, ""},
        };
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        
        inflater.inflate(R.menu.torrent_profile_settings_fragment, menu);
        if (mProfile == null)
            menu.findItem(R.id.delete).setVisible(false);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.done:
                if (mNew) {
                    mProfile.load(mSharedPrefs);
                    mProfile.save(getActivity());
                }
                
                /* TODO: validate. The name and host must not be empty */
                
                mSaved = true;

                ((SettingsActivity) getActivity()).onProfileListChange();

                getActivity().onBackPressed();
                return true;
            case R.id.delete:
                if (!mNew) {                    
                    /* FIXME: show undo bar https://plus.google.com/113735310430199015092/posts/RA9WEEGWYp6 */
                    
                    mProfile.delete(getActivity());
                    
                    mSaved = true;
                }

                ((SettingsActivity) getActivity()).onProfileListChange();

                getActivity().onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    @Override
    public void onDestroy() {
        if (!mSaved) {
            if (mProfile != null) {
                mProfile.save(getActivity());
            }
        }
        
        super.onDestroy();
    }
}
