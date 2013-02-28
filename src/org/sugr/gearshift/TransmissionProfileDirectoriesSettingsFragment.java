package org.sugr.gearshift;

import java.util.HashSet;
import java.util.Set;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListFragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class TransmissionProfileDirectoriesSettingsFragment extends ListFragment {
    public static final String ARG_PROFILE_ID = "profile_id";

    private SharedPreferences mSharedPrefs;
    private Set<String> mDirectories = new HashSet<String>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

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

                    if (errorRes != -1) {
                        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                        builder.setTitle(R.string.invalid_input_title);
                        builder.setMessage(errorRes);
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.show();

                        return;
                    }

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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_torrent_list, container, false);

        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();

        if (args != null && args.containsKey(ARG_PROFILE_ID)) {
            String prefname = args.getString(ARG_PROFILE_ID);

            mSharedPrefs = getActivity().getSharedPreferences(
                    prefname, Activity.MODE_PRIVATE);
        }

        if (mSharedPrefs != null) {
            mDirectories = mSharedPrefs.getStringSet(TransmissionProfile.PREF_DIRECTORIES, new HashSet<String>());
            setListAdapter(new ArrayAdapter<String>(getActivity(),
                    android.R.layout.simple_list_item_activated_1,
                    android.R.id.text1,
                    mDirectories.toArray(new String[mDirectories.size()])
            ));
            setEmptyText(R.string.no_download_dirs);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.clear();

        inflater.inflate(R.menu.add_directory_option, menu);
        if (mSharedPrefs == null)
            menu.findItem(R.id.menu_add_directory).setVisible(false);
    }

    public void setEmptyText(int stringId) {
        Spanned text = Html.fromHtml(getString(stringId));

        ((TextView) getListView().getEmptyView()).setText(text);
    }
}
