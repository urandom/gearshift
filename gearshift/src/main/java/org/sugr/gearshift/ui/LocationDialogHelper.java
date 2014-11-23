package org.sugr.gearshift.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.Spinner;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;
import org.sugr.gearshift.core.TransmissionProfile;
import org.sugr.gearshift.core.TransmissionSession;
import org.sugr.gearshift.service.DataServiceManager;
import org.sugr.gearshift.service.DataServiceManagerInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocationDialogHelper {
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

        final TransmissionProfileDirectoryAdapter adapter =
            new TransmissionProfileDirectoryAdapter(
                activity, android.R.layout.simple_spinner_item);


        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        adapter.addAll(session.getDownloadDirectories());
        adapter.sort();
        adapter.add(activity.getString(R.string.spinner_custom_directory));

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
                        if (location.getSelectedItemPosition() != adapter.getCount() - 1) {
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
        location.setAdapter(adapter);
        location.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(final View v) {
                swapLocationSpinner.run();
                return true;
            }
        });
        location.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (adapter.getCount() == i + 1) {
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
                    int position = adapter.getPosition(profile.getLastDownloadDirectory());

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
                if (location.getSelectedItemPosition() == adapter.getCount() - 1) {
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
}
