package org.sugr.gearshift.ui.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;
import org.sugr.gearshift.core.TransmissionProfile;
import org.sugr.gearshift.core.TransmissionSession;
import org.sugr.gearshift.service.DataServiceManager;
import org.sugr.gearshift.service.DataServiceManagerInterface;
import org.sugr.gearshift.ui.BaseTorrentActivity;
import org.sugr.gearshift.ui.TransmissionProfileDirectoryAdapter;
import org.sugr.gearshift.ui.TransmissionSessionInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocationDialogHelper {
    public static class Location {
        public String directory;
        public boolean isCustom;
        public boolean isDefault;

        public TransmissionProfile profile;
        public boolean isPaused;

        public boolean deleteLocal;
        public boolean moveData;
    }

    private BaseTorrentActivity activity;
    private AlertDialog dialog;

    public LocationDialogHelper(BaseTorrentActivity activity) {
        if (!(activity instanceof TransmissionSessionInterface)
            || !(activity instanceof DataServiceManagerInterface)) {
            throw new IllegalArgumentException("Invalid activity instance");
        }

        this.activity = activity;
    }

    public AlertDialog showDialog(int layout, int title,
                                  DialogInterface.OnClickListener cancelListener,
                                  DialogInterface.OnClickListener okListener) {
        LayoutInflater inflater = activity.getLayoutInflater();

        final TransmissionSession session = activity.getSession();
        final DataServiceManager manager = activity.getDataServiceManager();
        if (session == null || manager == null) {
            return null;
        }

        final View view = inflater.inflate(layout, null);
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity)
            .setCancelable(false)
            .setView(view)
            .setTitle(title)
            .setNegativeButton(android.R.string.cancel, cancelListener)
            .setPositiveButton(android.R.string.ok, okListener);

        final TransmissionProfileDirectoryAdapter locationAdapter =
            new TransmissionProfileDirectoryAdapter(
                activity, android.R.layout.simple_spinner_item);


        locationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        locationAdapter.addAll(session.getDownloadDirectories());
        locationAdapter.sort();
        locationAdapter.add(activity.getString(R.string.spinner_custom_directory));

        final Spinner location = (Spinner) view.findViewById(R.id.location_choice);
        final AutoCompleteTextView entry = (AutoCompleteTextView) view.findViewById(R.id.location_entry);
        final LinearLayout container = (LinearLayout) view.findViewById(R.id.location_container);
        final int duration = activity.getResources().getInteger(android.R.integer.config_shortAnimTime);
        final Runnable swapLocationSpinner = new Runnable() {
            @Override public void run() {
                container.setAlpha(0f);
                container.setVisibility(View.VISIBLE);
                container.animate().alpha(1f).setDuration(duration);

                location.animate().alpha(0f).setDuration(duration).withEndAction(new Runnable() {
                    @Override public void run() {
                        location.setVisibility(View.GONE);
                        location.animate().setListener(null).cancel();
                        if (location.getSelectedItemPosition() != locationAdapter.getCount() - 1) {
                            entry.setText((String) location.getSelectedItem());
                        }
                        entry.requestFocusFromTouch();
                        InputMethodManager imm =
                            (InputMethodManager) entry.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.showSoftInput(entry, InputMethodManager.SHOW_IMPLICIT);
                    }
                });
            }
        };
        location.setAdapter(locationAdapter);
        location.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(final View v) {
                swapLocationSpinner.run();
                return true;
            }
        });
        location.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (locationAdapter.getCount() == i + 1) {
                    swapLocationSpinner.run();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> adapterView) {}
        });

        ArrayAdapter<String> entryAdapter = new ArrayAdapter<String>(view.getContext(),
            android.R.layout.simple_spinner_dropdown_item);
        List<String> directories = new ArrayList<>(session.getDownloadDirectories());
        Collections.sort(directories, G.SIMPLE_STRING_COMPARATOR);
        entryAdapter.addAll(directories);
        entry.setAdapter(entryAdapter);

        final Runnable setInitialLocation = new Runnable() {
            @Override public void run() {
                TransmissionProfile profile = activity.getProfile();
                if (profile != null && profile.getLastDownloadDirectory() != null) {
                    int position = locationAdapter.getPosition(profile.getLastDownloadDirectory());

                    location.setSelection(position == -1 ? 0 : position);
                }
            }
        };

        setInitialLocation.run();

        View collapse = view.findViewById(R.id.location_collapse);
        collapse.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                location.setAlpha(0f);
                location.setVisibility(View.VISIBLE);
                location.animate().alpha(1f).setDuration(duration);
                if (location.getSelectedItemPosition() == locationAdapter.getCount() - 1) {
                    setInitialLocation.run();
                }

                container.animate().alpha(0f).setDuration(duration).withEndAction(new Runnable() {
                    @Override public void run() {
                        container.setVisibility(View.GONE);
                        container.animate().setListener(null).cancel();
                    }
                });
            }
        });

        TextView profilesLabel = (TextView) view.findViewById(R.id.new_torrent_profile_label);
        Spinner profilesSpinner = (Spinner) view.findViewById(R.id.new_torrent_profile_spinner);

        if (profilesLabel != null && profilesSpinner != null) {
            List<TransmissionProfile> profiles = activity.getProfiles();
            final TransmissionProfile currentProfile = activity.getProfile();

            final TransmissionProfileListAdapter profileAdapter = new TransmissionProfileListAdapter(activity);
            profileAdapter.addAll(profiles);

            profilesSpinner.setAdapter(profileAdapter);
            profilesSpinner.setSelection(profileAdapter.getPosition(currentProfile));

            profilesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    TransmissionProfile profile = profileAdapter.getItem(position);
                    locationAdapter.setNotifyOnChange(false);

                    locationAdapter.clear();
                    if (profile.equals(currentProfile)) {
                        locationAdapter.addAll(session.getDownloadDirectories());
                        locationAdapter.sort();
                        locationAdapter.add(activity.getString(R.string.spinner_custom_directory));
                    } else {
                        location.setSelection(0);
                        locationAdapter.add(activity.getString(R.string.spinner_default_directory));
                        locationAdapter.add(activity.getString(R.string.spinner_custom_directory));
                    }

                    locationAdapter.notifyDataSetChanged();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {  }
            });

            if (profiles.size() < 2) {
                profilesLabel.setVisibility(View.GONE);
                profilesSpinner.setVisibility(View.GONE);
            } else {
                profilesLabel.setVisibility(View.VISIBLE);
                profilesSpinner.setVisibility(View.VISIBLE);
            }
        }

        if (dialog != null) {
            dialog.dismiss();
        }
        dialog = builder.create();

        dialog.show();
        return dialog;
    }

    public void reset() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    public Location getLocation() {
        if (dialog == null) {
            return null;
        }

        Location location = new Location();

        Spinner profileSpinner = (Spinner) dialog.findViewById(R.id.new_torrent_profile_spinner);
        if (profileSpinner == null) {
            location.profile = activity.getProfile();
        } else {
            location.profile = (TransmissionProfile) profileSpinner.getSelectedItem();
        }

        if (location.profile == null) {
            return null;
        }

        Spinner locationSpinner = (Spinner) dialog.findViewById(R.id.location_choice);
        EditText locationEntry = (EditText) dialog.findViewById(R.id.location_entry);

        if (locationSpinner.getVisibility() == View.GONE) {
            location.directory = locationEntry.getText().toString().trim();

            if (TextUtils.isEmpty(location.directory)) {
                location.directory = null;
            }

            location.isCustom = true;
        } else {
            if (location.profile.equals(activity.getProfile())) {
                location.directory = (String) locationSpinner.getSelectedItem();
            } else {
                location.isDefault = true;
            }
        }

        CheckBox paused = (CheckBox) dialog.findViewById(R.id.start_paused);
        if (paused != null) {
            location.isPaused = paused.isChecked();
        }

        CheckBox deleteLocal = (CheckBox) dialog.findViewById(R.id.delete_local);
        if (deleteLocal != null) {
            location.deleteLocal = deleteLocal.isChecked();
        }

        CheckBox moveData = (CheckBox) dialog.findViewById(R.id.move);
        if (moveData != null) {
            location.moveData = moveData.isChecked();
        }

        return location;
    }

    private static class TransmissionProfileListAdapter extends ArrayAdapter<TransmissionProfile> {
        public TransmissionProfileListAdapter(Context context) {
            super(context, 0);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView;
            TransmissionProfile profile = getItem(position);

            if (rowView == null) {
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                rowView = vi.inflate(R.layout.torrent_profile_selector, null);
            }

            TextView name = (TextView) rowView.findViewById(R.id.name);
            TextView summary = (TextView) rowView.findViewById(R.id.summary);

            name.setText(profile.getName());
            if (summary != null)
                summary.setText((profile.getUsername().length() > 0 ? profile.getUsername() + "@" : "")
                    + profile.getHost() + ":" + profile.getPort());

            return rowView;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView;

            if (rowView == null) {
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                rowView = vi.inflate(R.layout.torrent_profile_selector_dropdown, null);
            }

            return getView(position, rowView, parent);
        }
    }
}
