package org.sugr.gearshift.ui;

import android.app.ActionBar;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;

public class SortSettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        addPreferencesFromResource(R.xml.sort_preferences);

        summaryPrefs = new Object[][] {
            {G.PREF_BASE_SORT, getString(R.string.update_interval_summary_format),
             R.array.pref_base_sort_method_values, R.array.pref_base_sort_method_entries, ""},
            {G.PREF_BASE_SORT_ORDER, getString(R.string.update_interval_summary_format),
                    R.array.pref_base_sort_order_values, R.array.pref_base_sort_order_entries, ""},
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
