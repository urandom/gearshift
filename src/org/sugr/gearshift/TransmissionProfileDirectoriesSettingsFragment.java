package org.sugr.gearshift;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.text.Html;
import android.text.Spanned;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public class TransmissionProfileDirectoriesSettingsFragment extends ListFragment {
    public static final String ARG_PROFILE_ID = "profile_id";

    private SharedPreferences mSharedPrefs;
    private Set<String> mDirectories = new HashSet<String>();
    private ArrayAdapter<String> mAdapter;

    private ActionMode mActionMode;

    private Comparator<String> mDirComparator = new Comparator<String>() {
        @Override
        public int compare(String lhs, String rhs) {
            return lhs.compareToIgnoreCase(rhs);
        }

    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_torrent_list, container, false);

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
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

                    if (mSharedPrefs != null) {
                        Editor e = mSharedPrefs.edit();
                        e.putStringSet(TransmissionProfile.PREF_DIRECTORIES, mDirectories);
                        e.commit();
                    }

                    PreferenceActivity context = (PreferenceActivity) getActivity();

                    if (context.onIsHidingHeaders() || !context.onIsMultiPane()) {
                        getActivity().finish();
                    } else {
                        getActivity().onBackPressed();
                    }
                }
            });
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                    ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME |
                    ActionBar.DISPLAY_SHOW_TITLE);
            actionBar.setCustomView(customActionBarView);
        }
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
            mDirectories = new HashSet<String>(
                    mSharedPrefs.getStringSet(TransmissionProfile.PREF_DIRECTORIES, new HashSet<String>()));
            mAdapter = new ArrayAdapter<String>(getActivity(),
                    android.R.layout.simple_list_item_activated_1,
                    android.R.id.text1
            );
            mAdapter.addAll(mDirectories);
            mAdapter.sort(mDirComparator);
            setListAdapter(mAdapter);

            setEmptyText(R.string.no_download_dirs);

            final ListView list = getListView();
            list.setChoiceMode(ListView.CHOICE_MODE_NONE);
            list.setOnItemLongClickListener(new OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view,
                        int position, long id) {

                    list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
                    list.setItemChecked(position, true);
                    return true;
                }});

            list.setMultiChoiceModeListener(new MultiChoiceModeListener() {
                private Set<String> mSelectedDirectories;

                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    MenuInflater inflater = mode.getMenuInflater();
                    inflater.inflate(R.menu.download_directories_multiselect, menu);

                    mSelectedDirectories = new HashSet<String>();
                    mActionMode = mode;
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                @Override
                public boolean onActionItemClicked(final ActionMode mode,
                        MenuItem item) {

                    switch (item.getItemId()) {
                        case R.id.select_all:
                            ListView v = getListView();
                            for (int i = 0; i < mAdapter.getCount(); i++) {
                                if (!v.isItemChecked(i)) {
                                    v.setItemChecked(i, true);
                                }
                            }
                            return true;
                        case R.id.remove:
                            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                                .setCancelable(false)
                                .setNegativeButton(android.R.string.no, null);

                            builder.setPositiveButton(android.R.string.yes,
                                new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    for (String directory : mSelectedDirectories) {
                                        mDirectories.remove(directory);
                                    }

                                    mode.finish();
                                    setAdapterDirectories();
                                }
                            })
                                .setMessage(R.string.remove_selected_directories_confirmation)
                                .show();

                            return true;
                    }
                    return false;
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    mActionMode = null;
                    mSelectedDirectories = null;
                }

                @Override
                public void onItemCheckedStateChanged(ActionMode mode,
                        int position, long id, boolean checked) {

                    String directory = mAdapter.getItem(position);

                    if (checked) {
                        mSelectedDirectories.add(directory);
                    } else {
                        mSelectedDirectories.remove(directory);
                    }
                }
            });
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case R.id.menu_add_directory:
            createEntryDialog(new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    EditText text = (EditText) ((AlertDialog) dialog).findViewById(R.id.dialog_entry);

                    mDirectories.add(text.getText().toString().trim());

                    setAdapterDirectories();
                }
            });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        super.onListItemClick(listView, view, position, id);

        if (mActionMode == null) {
            listView.setChoiceMode(ListView.CHOICE_MODE_NONE);

            final String directory = mAdapter.getItem(position);

            createEntryDialog(new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    EditText text = (EditText) ((AlertDialog) dialog).findViewById(R.id.dialog_entry);

                    mDirectories.remove(directory);
                    mDirectories.add(text.getText().toString().trim());

                    setAdapterDirectories();
                }
            }, directory);
        } else {
            listView.setItemChecked(position, true);
        }
    }

    public void setEmptyText(int stringId) {
        Spanned text = Html.fromHtml(getString(stringId));

        ((TextView) getListView().getEmptyView()).setText(text);
    }

    private void createEntryDialog(DialogInterface.OnClickListener clickListener) {
        createEntryDialog(clickListener, null);
    }

    private void createEntryDialog(DialogInterface.OnClickListener clickListener, String text) {
        AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());

        alert.setTitle(R.string.directory);
        final EditText input = new EditText(getActivity());
        input.setId(R.id.dialog_entry);
        input.setText(text);
        input.setSelectAllOnFocus(true);
        alert.setView(input);

        alert.setPositiveButton(android.R.string.ok, clickListener);
        alert.setNegativeButton(android.R.string.cancel, null);
        alert.show();
    }

    private void setAdapterDirectories() {
        mAdapter.setNotifyOnChange(false);
        mAdapter.clear();
        mAdapter.addAll(mDirectories);
        mAdapter.sort(mDirComparator);
        mAdapter.notifyDataSetChanged();
    }
}
