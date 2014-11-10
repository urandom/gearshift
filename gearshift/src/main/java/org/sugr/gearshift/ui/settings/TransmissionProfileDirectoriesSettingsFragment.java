package org.sugr.gearshift.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListFragment;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spanned;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class TransmissionProfileDirectoriesSettingsFragment extends ListFragment {
    private SharedPreferences mSharedPrefs;
    private Set<String> mDirectories = new HashSet<String>();
    private ArrayAdapter<String> mAdapter;
    private String mProfileId;

    private ActionMode mActionMode;

    private Comparator<String> mDirComparator = new Comparator<String>() {
        @Override
        public int compare(String lhs, String rhs) {
            return lhs.compareToIgnoreCase(rhs);
        }

    };

    private ArrayList<String> mSessionDirectories = new ArrayList<String>();

    private static final String STATE_DIRECTORIES = "directories";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        Bundle args = getArguments();
        if (args.containsKey(G.ARG_DIRECTORIES)) {
            ArrayList<String> directories = args.getStringArrayList(G.ARG_DIRECTORIES);
            if (directories != null && directories.size() > 0) {
                mSessionDirectories.clear();
                mSessionDirectories.addAll(directories);
            }
        }
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_transmission_profile_directories_settings,
            container, false);
    }

    @Override public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();

        if (args != null && args.containsKey(G.ARG_PROFILE_ID)) {
            mProfileId = args.getString(G.ARG_PROFILE_ID);
        }

        mSharedPrefs = getActivity().getSharedPreferences(
                G.PROFILES_PREF_NAME, Activity.MODE_PRIVATE);

        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_DIRECTORIES)) {
            mDirectories.clear();
            mDirectories.addAll(savedInstanceState.getStringArrayList(STATE_DIRECTORIES));
        } else if (mProfileId != null) {
            mDirectories = new HashSet<String>(
                    mSharedPrefs.getStringSet(G.PREF_DIRECTORIES + mProfileId,
                            new HashSet<String>()));
        }

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

    @Override public void onDestroy() {
        if (mSharedPrefs != null) {
            Editor e = mSharedPrefs.edit();
            e.putStringSet(G.PREF_DIRECTORIES, mDirectories);
            e.commit();
        }

        G.requestBackup(getActivity());

        super.onDestroy();
    }

    @Override public void onResume() {
        super.onResume();

        Toolbar toolbar = (Toolbar) getActivity().findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_close_white_24dp);
        toolbar.setTitle(R.string.profile_directories);
    }

    @Override public void onPause() {
        super.onPause();

        Toolbar toolbar = (Toolbar) getActivity().findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
        toolbar.setTitle(R.string.settings);
    }

    @Override public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        ArrayList<String> directories = new ArrayList<String>();
        directories.addAll(mDirectories);

        outState.putStringArrayList(STATE_DIRECTORIES, directories);
    }

    @Override public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.clear();

        inflater.inflate(R.menu.add_directory_option, menu);
        if (mProfileId == null)
            menu.findItem(R.id.menu_add_directory).setVisible(false);

        MenuItem item = menu.findItem(R.id.import_directories);
        item.setVisible(mSessionDirectories.size() > 0);
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_add_directory:
                createEntryDialog(new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        EditText text = (EditText) ((AlertDialog) dialog).findViewById(R.id.dialog_entry);
                        String dir = text.getText().toString().trim();

                        while (dir.endsWith("/")) {
                            dir = dir.substring(0, dir.length() - 1);
                        }

                        mDirectories.add(dir);

                        setAdapterDirectories();
                    }
                });
                return true;
            case R.id.import_directories:
                mDirectories.addAll(mSessionDirectories);
                setAdapterDirectories();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override public void onListItemClick(ListView listView, View view, int position, long id) {
        super.onListItemClick(listView, view, position, id);

        if (mActionMode == null) {
            listView.setChoiceMode(ListView.CHOICE_MODE_NONE);

            final String directory = mAdapter.getItem(position);

            createEntryDialog(new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    EditText text = (EditText) ((AlertDialog) dialog).findViewById(R.id.dialog_entry);

                    mDirectories.remove(directory);
                    String dir = text.getText().toString().trim();

                    while (dir.endsWith("/")) {
                        dir = dir.substring(0, dir.length() - 1);
                    }

                    mDirectories.add(dir);

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
