package org.sugr.gearshift.ui;

import android.app.ActionBar;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;
import org.sugr.gearshift.ui.BasePreferenceFragment;

public class GeneralSettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        addPreferencesFromResource(R.xml.general_preferences);

        summaryPrefs = new Object[][] {
            {G.PREF_FULL_UPDATE, getString(R.string.full_update_summary_format), -1, -1, "int"},
            {G.PREF_UPDATE_INTERVAL, getString(R.string.update_interval_summary_format),
                R.array.pref_update_interval_values, R.array.pref_update_interval_entries, ""},
        };

        ActionBar actionBar = getActivity().getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_HOME,
                    ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME |
                    ActionBar.DISPLAY_SHOW_TITLE);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        SettingsActivity context = (SettingsActivity) getActivity();

        if (!context.isPreferencesAlwaysVisible() && context.isPreferencesOpen()) {
            menu.clear();
        }
    }
}
