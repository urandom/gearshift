package org.sugr.gearshift.ui.settings;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;
import org.sugr.gearshift.core.TransmissionProfile;
import org.sugr.gearshift.service.DataService;
import org.sugr.gearshift.service.DataServiceManager;

import java.util.Set;

public class TransmissionProfileSettingsFragment extends BasePreferenceFragment {
    private TransmissionProfile profile;

    private boolean isNew = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String id;
        Bundle args = getArguments();
        if (args.containsKey(G.ARG_PROFILE_ID)) {
            id = args.getString(G.ARG_PROFILE_ID);
        } else {
            id = null;
        }

        TransmissionProfile.cleanTemporaryPreferences(getActivity());

        sharedPrefs = getActivity().getSharedPreferences(TransmissionProfile.getPreferencesName(),
            Activity.MODE_PRIVATE);

        Set<String> directories = sharedPrefs.getStringSet(G.PREF_DIRECTORIES, null);

        if (id == null) {
            profile = new TransmissionProfile(getActivity(),
                PreferenceManager.getDefaultSharedPreferences(getActivity()));
            isNew = true;
            profile.save();
            id = profile.getId();
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

        PreferenceManager pm = getPreferenceManager();
        for (String key : G.UNPREFIXED_PROFILE_PREFERENCE_KEYS) {
            Preference pref = pm.findPreference(key);
            if (pref == null) {
                continue;
            }

            pref.setKey(key + id);
            if (!TextUtils.isEmpty(pref.getDependency())) {
                pref.setDependency(pref.getDependency() + id);
            }
        }

        summaryPrefs = new Object[][] {
            {G.PREF_NAME + id, getString(R.string.profile_summary_format), -1, -1, ""},
            {G.PREF_HOST + id, getString(R.string.profile_summary_format), -1, -1, ""},
            {G.PREF_PORT + id, getString(R.string.profile_summary_format), -1, -1, ""},
            {G.PREF_USER + id, getString(R.string.profile_summary_format), -1, -1, ""},
            {G.PREF_PATH + id, getString(R.string.profile_summary_format), -1, -1, ""},
            {G.PREF_TIMEOUT + id, getString(R.string.profile_summary_format), -1, -1, ""},
            /* {G.PREF_RETRIES + id, getString(R.string.profile_summary_format),
                R.array.pref_con_retries_values, R.array.pref_con_retries_entries, ""}, */
            {G.PREF_FULL_UPDATE + id, getString(R.string.full_update_summary_format), -1, -1, "int"},
            {G.PREF_UPDATE_INTERVAL + id, getString(R.string.update_interval_summary_format),
                    R.array.pref_update_interval_values, R.array.pref_update_interval_entries, ""},
        };

        pm.findPreference(G.PREF_DIRECTORIES + id)
            .setOnPreferenceClickListener(preference -> {
                Bundle args1 = getArguments();
                Bundle fragmentArgs = new Bundle();

                fragmentArgs.putString(G.ARG_PROFILE_ID, profile.getId());
                if (args1.containsKey(G.ARG_DIRECTORIES)) {
                    fragmentArgs.putStringArrayList(G.ARG_DIRECTORIES,
                        args1.getStringArrayList(G.ARG_DIRECTORIES));
                }

                ((SettingsActivity) getActivity()).addFragment(profile.getId() + "-directories",
                    SettingsActivity.Type.PROFILE_DIRECTORIES, fragmentArgs);

                return true;
            });
        pm.findPreference(G.PREF_NAME + id).setOnPreferenceChangeListener((preference, newValue) -> {
            if (TextUtils.isEmpty(newValue.toString())) {
                showErrorDialog(R.string.con_name_cannot_be_empty);

                return false;
            } else {
                return true;
            }
        });
        pm.findPreference(G.PREF_HOST + id).setOnPreferenceChangeListener((preference, newValue) -> {
            if (TextUtils.isEmpty(newValue.toString()) || newValue.toString().equals("example.com")) {
                showErrorDialog(R.string.con_host_cannot_be_empty);

                return false;
            } else {
                return true;
            }
        });
        pm.findPreference(G.PREF_PORT + id).setOnPreferenceChangeListener((preference, newValue) -> {
            try {
                 int port = Integer.parseInt(newValue.toString());
                if (port < 1 || port > 65535) {
                    throw new RuntimeException("Invalid port value");
                }
            } catch (Exception ignored) {
                showErrorDialog(R.string.con_port_not_valid);

                return false;
            }

            return true;
        });
        pm.findPreference(G.PREF_PROXY_HOST + id).setOnPreferenceChangeListener((preference, newValue) -> {
            if (sharedPrefs.getBoolean(G.PREF_PROXY + profile.getId(), false) &&
                    (TextUtils.isEmpty(newValue.toString()) || newValue.toString().equals("example.com"))) {
                showErrorDialog(R.string.con_proxy_host_cannot_be_empty);

                return false;
            } else {
                return true;
            }
        });
        pm.findPreference(G.PREF_PROXY_PORT + id).setOnPreferenceChangeListener((preference, newValue) -> {
            if (sharedPrefs.getBoolean(G.PREF_PROXY + profile.getId(), false)) {
                try {
                    int port = Integer.parseInt(newValue.toString());
                    if (port < 1 || port > 65535) {
                        throw new RuntimeException("Invalid port value");
                    }
                } catch (Exception ignored) {
                    showErrorDialog(R.string.con_proxy_port_not_valid);

                    return false;
                }
            }

            return true;
        });
    }

    @Override public void onResume() {
        super.onResume();

        Toolbar toolbar = (Toolbar) getActivity().findViewById(R.id.toolbar);
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
                close();

                return true;
            case R.id.delete:
                LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
                        new ServiceReceiver(), new IntentFilter(G.INTENT_SERVICE_ACTION_COMPLETE));

                new DataServiceManager(getActivity(), profile).removeProfile();

                item.setActionView(R.layout.action_progress_bar);

                close();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void close() {
        SettingsActivity context = (SettingsActivity) getActivity();

        context.closePreferences();
    }

    private void showErrorDialog(int messageId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.invalid_input_title);
        builder.setMessage(messageId);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
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
