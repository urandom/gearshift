package us.supositi.gearshift;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Loader;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;

public class TorrentProfileSettingsFragment extends BasePreferenceFragment {
    public static final String ARG_PROFILE_ID = "profile_id";
    public static final String ARG_LOADER_ID = "loader_id";
    
    private TorrentProfile mProfile;
    
    private boolean mNew = true;
    private boolean mSaved = false;
    
    private Loader<TorrentProfile[]> mLoader;
                
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        String id = null;
        if (getArguments().containsKey(ARG_PROFILE_ID)) {
            id = getArguments().getString(ARG_PROFILE_ID);
            mNew = false;
        }
        
        if (getArguments().containsKey(ARG_LOADER_ID)) {
            int loader_id = getArguments().getInt(ARG_LOADER_ID);
            mLoader = getActivity().getLoaderManager().getLoader(loader_id);
        }
        
        if (id == null)
            mProfile = new TorrentProfile();
        else
            mProfile = new TorrentProfile(id, getActivity());
        
        TorrentListActivity.logD(
            "Editing (new ? {0}) profile {1}",
            new Object[] {mNew, mProfile.getId()});
        
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
        
        ActionBar actionBar = getActivity().getActionBar();
        if (actionBar != null) {
            LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService
                    (Context.LAYOUT_INFLATER_SERVICE);
            
            View customActionBarView = inflater.inflate(R.layout.torrent_profile_settings_action_bar, null);
            View saveMenuItem = customActionBarView.findViewById(R.id.save_menu_item);
            saveMenuItem.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    int errorRes = -1;
                    TorrentListActivity.logD(mSharedPrefs.getString(TorrentProfile.PREF_HOST, "").trim());
                    if (mSharedPrefs.getString(TorrentProfile.PREF_NAME, "").trim().equals("")) {
                        errorRes = R.string.con_name_cannot_be_empty;
                    } else if (mSharedPrefs.getString(TorrentProfile.PREF_HOST, "").trim().equals("")) {
                        errorRes = R.string.con_host_cannot_be_empty;
                    }
                    
                    if (errorRes != -1) {
                        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                        builder.setTitle(R.string.invalid_input_title);
                        builder.setMessage(errorRes);
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.show();
                        
                        return;
                    }
                    
                    mProfile.load(mSharedPrefs);
                    if (mNew) {
                        mProfile.save(getActivity());
                    }
                    
                    if (mLoader != null)
                        mLoader.onContentChanged();
                    
                    /* TODO: validate. The name and host must not be empty */
                    
                    mSaved = true;

                    getActivity().finish();
                }
            });
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                    ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME |
                    ActionBar.DISPLAY_SHOW_TITLE);
            actionBar.setCustomView(customActionBarView);
        }
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
            case R.id.delete:
                if (!mNew) {                    
                    /* FIXME: show undo bar https://plus.google.com/113735310430199015092/posts/RA9WEEGWYp6 */
                    
                    mProfile.delete(getActivity());
                    
                    mSaved = true;
                                        
                    if (mLoader != null)
                        mLoader.onContentChanged();
                }

                getActivity().finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    @Override
    public void onDestroy() {
        if (!mSaved) {
            if (!mNew && mProfile != null) {
                mProfile.save(getActivity());
            }
        }
        
        super.onDestroy();
    }
}
