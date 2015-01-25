package org.sugr.gearshift.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;
import org.sugr.gearshift.core.TransmissionProfile;
import org.sugr.gearshift.service.DataService;
import org.sugr.gearshift.service.DataServiceManager;

import java.util.HashSet;
import java.util.Set;

public class TransmissionProfileSettingsFragment extends BasePreferenceFragment {
    private TransmissionProfile profile;

    private boolean isNew = false;
    private boolean deleted = false;
    private boolean saved = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String id;
        Bundle args = getArguments();
        if (args.containsKey(G.ARG_PROFILE_ID)) {
            id = args.getString(G.ARG_PROFILE_ID);
        } else {
            id = null;
        }

        sharedPrefs = getActivity().getSharedPreferences(TransmissionProfile.getPreferencesName(),
            Activity.MODE_PRIVATE);

        Set<String> directories = sharedPrefs.getStringSet(G.PREF_DIRECTORIES, null);

        if (id == null) {
            TransmissionProfile.cleanTemporaryPreferences(getActivity());
            profile = new TransmissionProfile(getActivity(),
                PreferenceManager.getDefaultSharedPreferences(getActivity()));
            isNew = true;
        } else {
            profile = new TransmissionProfile(id, getActivity(),
                PreferenceManager.getDefaultSharedPreferences(getActivity()));
            profile.fillTemporatyPreferences();
        }

        if (directories != null) {
            sharedPrefs.edit().putStringSet(G.PREF_DIRECTORIES, directories).apply();
            profile.setDirectories(directories);
        }

        getPreferenceManager().setSharedPreferencesName(G.PROFILES_PREF_NAME);

        addPreferencesFromResource(R.xml.torrent_profile_preferences);
        PreferenceManager.setDefaultValues(
            getActivity(), G.PROFILES_PREF_NAME,
            Activity.MODE_PRIVATE, R.xml.torrent_profile_preferences, true);

        summaryPrefs = new Object[][] {
            {G.PREF_NAME, getString(R.string.profile_summary_format), -1, -1, ""},
            {G.PREF_HOST, getString(R.string.profile_summary_format), -1, -1, ""},
            {G.PREF_PORT, getString(R.string.profile_summary_format), -1, -1, ""},
            {G.PREF_USER, getString(R.string.profile_summary_format), -1, -1, ""},
            {G.PREF_PATH, getString(R.string.profile_summary_format), -1, -1, ""},
            {G.PREF_TIMEOUT, getString(R.string.profile_summary_format), -1, -1, ""},
            /* {G.PREF_RETRIES, getString(R.string.profile_summary_format),
                R.array.pref_con_retries_values, R.array.pref_con_retries_entries, ""}, */
        };

        getPreferenceManager().findPreference(G.PREF_DIRECTORIES)
            .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override public boolean onPreferenceClick(Preference preference) {
                Bundle args = getArguments();
                Bundle fragmentArgs = new Bundle();

                fragmentArgs.putString(G.ARG_PROFILE_ID, id);
                if (args.containsKey(G.ARG_DIRECTORIES)) {
                    fragmentArgs.putStringArrayList(G.ARG_DIRECTORIES,
                        args.getStringArrayList(G.ARG_DIRECTORIES));
                }

                ((SettingsActivity) getActivity()).addFragment(id + "-directories",
                    SettingsActivity.Type.PROFILE_DIRECTORIES, fragmentArgs);

                return true;
            }
        });
    }

    @Override public void onResume() {
        super.onResume();

        Toolbar toolbar = (Toolbar) getActivity().findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_done_white_24dp);
        if (isNew) {
            toolbar.setTitle(R.string.new_profile);
        } else {
            toolbar.setTitle(profile.getName());
        }
    }

    @Override public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.clear();

        inflater.inflate(R.menu.torrent_profile_settings_fragment, menu);
        MenuItem item = menu.findItem(R.id.delete);
        if (isNew) {
            item.setTitle(android.R.string.cancel);
        }

        if (profile == null) {
            item.setVisible(false);
        }
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                int errorRes = -1;
                if (sharedPrefs.getString(G.PREF_NAME, "").trim().equals("")) {
                    errorRes = R.string.con_name_cannot_be_empty;
                } else if (sharedPrefs.getString(G.PREF_HOST, "").trim().equals("")) {
                    errorRes = R.string.con_host_cannot_be_empty;
                } else if (sharedPrefs.getBoolean(G.PREF_PROXY, false) &&
                           sharedPrefs.getString(G.PREF_PROXY_HOST, "").trim().equals("")) {
                    errorRes = R.string.con_proxy_host_cannot_be_empty;
                }

                if (errorRes != -1) {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setTitle(R.string.invalid_input_title);
                    builder.setMessage(errorRes);
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();

                    return true;
                }

                saved = true;

                close();

                return true;
            case R.id.delete:
                deleted = true;

                if (!isNew) {
                    LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
                        new ServiceReceiver(), new IntentFilter(G.INTENT_SERVICE_ACTION_COMPLETE));

                    new DataServiceManager(getActivity(), profile.getId()).removeProfile();

                    item.setActionView(R.layout.action_progress_bar);
                }

                close();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void close() {
        SettingsActivity context = (SettingsActivity) getActivity();

        if (saved) {
            profile.save(true);
        }

        TransmissionProfile.cleanTemporaryPreferences(getActivity());

        if (saved || deleted) {
            G.requestBackup(getActivity());
        }

        context.closePreferences();
    }

    private class ServiceReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            int error = intent.getIntExtra(G.ARG_ERROR, 0);

            String type = intent.getStringExtra(G.ARG_REQUEST_TYPE);
            switch (type) {
                case DataService.Requests.REMOVE_PROFILE:
                    LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
                    getActivity().invalidateOptionsMenu();
                    if (error != 0) {
                        Toast.makeText(context, R.string.error_removing_profile, Toast.LENGTH_LONG)
                            .show();
                    }
                    close();
                    break;
            }
        }
    }
}
