package org.sugr.gearshift;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
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
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;

import org.sugr.gearshift.G.FilterBy;
import org.sugr.gearshift.G.SortBy;
import org.sugr.gearshift.G.SortOrder;
import org.sugr.gearshift.datasource.Constants;
import org.sugr.gearshift.datasource.DataSource;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A list fragment representing a list of Torrents. This fragment
 * also supports tablet devices by allowing list items to be given an
 * 'activated' state upon selection. This helps indicate which item is
 * currently being viewed in a {@link TorrentDetailFragment}.
 * <p>
 * Activities containing this fragment MUST implement the {@link Callbacks}
 * interface.
 */
public class TorrentListFragment extends ListFragment implements TorrentListNotification {
    /**
     * The serialization (saved instance state) Bundle key representing the
     * activated item position. Only used on tablets.
     */
    private static final String STATE_ACTIVATED_POSITION = "activated_position";
    private static final String STATE_FIND_SHOWN = "find_shown";
    private static final String STATE_FIND_QUERY = "find_query";

    /**
     * The fragment's current callback object, which is notified of list item
     * clicks.
     */
    private Callbacks mCallbacks = sDummyCallbacks;

    /**
     * The current activated item position. Only used on tablets.
     */
    private int mActivatedPosition = ListView.INVALID_POSITION;

    private ActionMode mActionMode;

    private int mChoiceMode = ListView.CHOICE_MODE_NONE;

    //private TorrentListAdapter mTorrentListAdapter;
    private TorrentCursorAdapter torrentAdapter;

    private boolean mScrollToTop = false;

    private boolean findVisible = false;
    private String findQuery;

    private SharedPreferences mSharedPrefs;

    private Menu menu;

    /**
     * A callback interface that all activities containing this fragment must
     * implement. This mechanism allows activities to be notified of item
     * selections.
     */
    public interface Callbacks {
        /**
         * Callback for when an item has been selected.
         */
        public void onItemSelected(int position);
    }

    /**
     * A dummy implementation of the {@link Callbacks} interface that does
     * nothing. Used only when this fragment is not attached to an activity.
     */
    private static Callbacks sDummyCallbacks = new Callbacks() {
        @Override
        public void onItemSelected(int position) {
        }
    };

    private MultiChoiceModeListener mListChoiceListener = new MultiChoiceModeListener() {
        private SparseIntArray selectedTorrentIds;
        private boolean hasQueued = false;

        @Override
        public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
            final Loader<TransmissionData> loader = getActivity().getSupportLoaderManager()
                    .getLoader(G.TORRENTS_LOADER_ID);

            if (loader == null)
                return false;

            final int[] ids = new int[selectedTorrentIds.size()];
            for (int i = 0; i < selectedTorrentIds.size(); ++i) {
                ids[i] = selectedTorrentIds.valueAt(i);
            }

            AlertDialog.Builder builder;
            switch (item.getItemId()) {
                case R.id.select_all:
                    ListView v = getListView();
                    for (int i = 0; i < torrentAdapter.getCount(); i++) {
                        if (!v.isItemChecked(i)) {
                            v.setItemChecked(i, true);
                        }
                    }
                    return true;
                case R.id.remove:
                case R.id.delete:
                    builder = new AlertDialog.Builder(getActivity())
                            .setCancelable(false)
                            .setNegativeButton(android.R.string.no, null);

                    builder.setPositiveButton(android.R.string.yes,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    ((TransmissionDataLoader) loader).setTorrentsRemove(ids, item.getItemId() == R.id.delete);
                                    ((TransmissionSessionInterface) getActivity()).setRefreshing(true);

                                    mode.finish();
                                }
                            })
                            .setMessage(item.getItemId() == R.id.delete
                                    ? R.string.delete_selected_confirmation
                                    : R.string.remove_selected_confirmation)
                            .show();
                    return true;
                case R.id.resume:
                    ((TransmissionDataLoader) loader).setTorrentsAction(
                        hasQueued ? "torrent-start-now" : "torrent-start",
                        ids);
                    break;
                case R.id.pause:
                    ((TransmissionDataLoader) loader).setTorrentsAction("torrent-stop", ids);
                    break;
                case R.id.move:
                    return showMoveDialog(ids);
                case R.id.verify:
                    ((TransmissionDataLoader) loader).setTorrentsAction("torrent-verify", ids);
                    break;
                case R.id.reannounce:
                    ((TransmissionDataLoader) loader).setTorrentsAction("torrent-reannounce", ids);
                    break;
                default:
                    return true;
            }

            ((TransmissionSessionInterface) getActivity()).setRefreshing(true);

            mode.finish();
            return true;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            if (inflater != null)
                inflater.inflate(R.menu.torrent_list_multiselect, menu);

            selectedTorrentIds = new SparseIntArray();
            mActionMode = mode;
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            G.logD("Destroying context menu");
            mActionMode = null;
            selectedTorrentIds = null;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode,
                                              int position, long id, boolean checked) {

            int torrentId = (int) torrentAdapter.getItemId(position);
            if (checked)
                selectedTorrentIds.append(position, torrentId);
            else
                selectedTorrentIds.delete(position);

            boolean hasPaused = false;
            boolean hasRunning = false;

            hasQueued = false;
            for (int i = 0; i < selectedTorrentIds.size(); ++i) {
                Cursor cursor = (Cursor) torrentAdapter.getItem(selectedTorrentIds.keyAt(i));
                int status = Torrent.getStatus(cursor);
                if (status == Torrent.Status.STOPPED) {
                    hasPaused = true;
                } else if (Torrent.isActive(status)) {
                    hasQueued = true;
                } else {
                    hasRunning = true;
                }
            }

            Menu menu = mode.getMenu();
            MenuItem item = menu.findItem(R.id.resume);
            if (item != null)
                item.setVisible(hasPaused || hasQueued).setEnabled(hasPaused || hasQueued);

            item = menu.findItem(R.id.pause);
            if (item != null)
                item.setVisible(hasRunning).setEnabled(hasRunning);
        }
    };

    /* The callback will get garbage collected if its a mere anon class */
    private SharedPreferences.OnSharedPreferenceChangeListener mSettingsChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    if (key.equals(G.PREF_BASE_SORT_ORDER) || key.equals(G.PREF_BASE_SORT_ORDER)) {
                        torrentAdapter.getFilter().filter(prefs.getString(G.PREF_LIST_SEARCH, ""));
                    } else if (key.equals(G.PREF_PROFILES)) {
                        if (getActivity() != null) {
                            ((TransmissionSessionInterface) getActivity()).setRefreshing(true);
                        }
                    } else if (key.equals(G.PREF_CURRENT_PROFILE)) {
                        if (prefs.getString(key, null) == null) {
                            setEmptyText(R.string.no_profiles_empty_list);
                        }
                    } else if (key.equals(G.PREF_SHOW_STATUS) && getView() != null) {
                        View status = getView().findViewById(R.id.status_bar_text);
                        status.setVisibility(prefs.getBoolean(key, false) ? View.VISIBLE : View.GONE);
                    }
                }
            };

    private Handler mFindHandler = new Handler();
    private Runnable mFindRunnable = new Runnable() {
        @Override public void run() {
            if (getActivity() == null) {
                return;
            }
            G.logD("Search query " + findQuery);
            setListFilter(findQuery);
        }
    };

    private LoaderManager.LoaderCallbacks<TorrentTrafficLoader.TorrentTrafficOutputData> torrentTrafficLoaderCallbacks
        = new LoaderManager.LoaderCallbacks<TorrentTrafficLoader.TorrentTrafficOutputData>() {

        @Override public Loader<TorrentTrafficLoader.TorrentTrafficOutputData> onCreateLoader(int id, Bundle bundle) {
            if (id == G.TORRENT_LIST_TRAFFIC_LOADER_ID) {
                return new TorrentTrafficLoader(getActivity(),
                    mSharedPrefs.getBoolean(G.PREF_SHOW_STATUS, false), false, false);
            }
            return null;
        }

        @Override public void onLoadFinished(Loader<TorrentTrafficLoader.TorrentTrafficOutputData> loader,
                                             TorrentTrafficLoader.TorrentTrafficOutputData data) {
            TransmissionSession session = ((TransmissionSessionInterface) getActivity()).getSession();
            TextView status = (TextView) getView().findViewById(R.id.status_bar_text);
            String limitDown = "";
            String limitUp = "";
            long free = 0;

            if (session != null) {
                free = session.getDownloadDirFreeSpace();
                if (session.isDownloadSpeedLimitEnabled() || session.isAltSpeedLimitEnabled()) {
                    limitDown = String.format(
                        getString(R.string.status_bar_limit_format),
                        G.readableFileSize((
                            session.isAltSpeedLimitEnabled()
                                ? session.getAltDownloadSpeedLimit()
                                : session.getDownloadSpeedLimit()) * 1024) + "/s"
                    );
                }
                if (session.isUploadSpeedLimitEnabled() || session.isAltSpeedLimitEnabled()) {
                    limitUp = String.format(
                        getString(R.string.status_bar_limit_format),
                        G.readableFileSize((
                            session.isAltSpeedLimitEnabled()
                                ? session.getAltUploadSpeedLimit()
                                : session.getUploadSpeedLimit()) * 1024) + "/s"
                    );
                }
            }

            status.setText(Html.fromHtml(String.format(
                getString(R.string.status_bar_format),
                G.readableFileSize(data.downloadSpeed),
                limitDown,
                G.readableFileSize(data.uploadSpeed),
                limitUp,
                free == 0 ? getString(R.string.unknown) : G.readableFileSize(free)
            )));
        }

        @Override
        public void onLoaderReset(Loader<TorrentTrafficLoader.TorrentTrafficOutputData> loader) {
        }
    };

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public TorrentListFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        getActivity().setProgressBarIndeterminateVisibility(true);

        if (savedInstanceState == null) {
            mSharedPrefs.registerOnSharedPreferenceChangeListener(mSettingsChangeListener);
        }
    }

    @Override
    public void onViewCreated(View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Restore the previously serialized activated item position.
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(STATE_FIND_SHOWN)) {
                findVisible = savedInstanceState.getBoolean(STATE_FIND_SHOWN);
                if (savedInstanceState.containsKey(STATE_FIND_QUERY)) {
                    findQuery = savedInstanceState.getString(STATE_FIND_QUERY);
                }
            }

            ((TransmissionSessionInterface) getActivity()).setRefreshing(false);
        }
        if (!findVisible) {
            Editor e = mSharedPrefs.edit();
            e.putString(G.PREF_LIST_SEARCH, "");
            e.apply();
        }

        torrentAdapter = new TorrentCursorAdapter(getActivity(), null);
        setListAdapter(torrentAdapter);
        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_ACTIVATED_POSITION)) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    if (getActivity() == null) {
                        return;
                    }

                    /* FIXME: this has to happen after we have a cursor */
                    if (savedInstanceState.containsKey(STATE_ACTIVATED_POSITION)) {
                        setActivatedPosition(savedInstanceState.getInt(STATE_ACTIVATED_POSITION));
                    } else {
                        getListView().setItemChecked(getListView().getCheckedItemPosition(), false);
                    }
                }
            });

        }

        TextView status = (TextView) view.findViewById(R.id.status_bar_text);
        /* Enable the marquee animation */
        status.setSelected(true);
        if (mSharedPrefs.getBoolean(G.PREF_SHOW_STATUS, false)) {
            status.setVisibility(View.VISIBLE);
        }

        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final ListView list = getListView();
        list.setChoiceMode(mChoiceMode);
        list.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                    int position, long id) {

                if (!((TorrentListActivity) getActivity()).isDetailPanelShown()) {
                    list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
                    setActivatedPosition(position);
                    return true;
                }
                return false;
            }});

        list.setMultiChoiceModeListener(mListChoiceListener);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Activities containing this fragment must implement its callbacks.
        if (!(activity instanceof Callbacks)) {
            throw new IllegalStateException("Activity must implement fragment's callbacks.");
        }

        mCallbacks = (Callbacks) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();

        // Reset the active callbacks interface to the dummy implementation.
        mCallbacks = sDummyCallbacks;

    }

    @Override public void onDestroyView() {
        torrentAdapter.clearResources();

        super.onDestroyView();
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        super.onListItemClick(listView, view, position, id);

        if (mActionMode == null)
            listView.setChoiceMode(mChoiceMode);

        // Notify the active callbacks interface (the activity, if the
        // fragment is attached to one) that an item has been selected.
        mCallbacks.onItemSelected(position);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mActivatedPosition != ListView.INVALID_POSITION) {
            // Serialize and persist the activated item position.
            outState.putInt(STATE_ACTIVATED_POSITION, mActivatedPosition);
        }
        outState.putBoolean(STATE_FIND_SHOWN, findVisible);
        outState.putString(STATE_FIND_QUERY, findQuery);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_torrent_list, container, false);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        this.menu = menu;

        inflater.inflate(R.menu.torrent_list_fragment, menu);
        MenuItem item = menu.findItem(R.id.find);

        SearchView findView = new SearchView(
            getActivity().getActionBar().getThemedContext());
        findView.setQueryHint(getActivity().getString(R.string.filter));
        findView.setIconifiedByDefault(true);
        findView.setIconified(true);
        findView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override public boolean onQueryTextChange(String newText) {
                findQuery = newText;
                mFindHandler.removeCallbacks(mFindRunnable);
                mFindHandler.postDelayed(mFindRunnable, 500);
                return true;
            }
        });

        item.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override public boolean onMenuItemActionCollapse(MenuItem item) {
                item.setVisible(false);
                findQuery = null;
                findVisible = false;
                setListFilter((String) null);

                return true;
            }
        });

        item.setActionView(findView);

        if (findVisible) {
            setFindVisibility(findVisible);
        }
    }

    @Override
    public void notifyTorrentListChanged(Cursor cursor, int error, boolean added, boolean removed,
                                         boolean statusChanged, boolean metadataNeeded) {
        if (error == -1) {
            return;
        }

        String query = mSharedPrefs.getString(G.PREF_LIST_SEARCH, null);
        boolean filtered = false;
        if (cursor != null && query != null && !query.equals("")) {
            filtered = true;
        }

        int count = cursor != null ? cursor.getCount() : 0;

        if (count > 0 || error > 0) {
            if (error > 0) {
                if (error != TransmissionData.Errors.DUPLICATE_TORRENT
                    && error != TransmissionData.Errors.INVALID_TORRENT
                    && mActionMode != null) {

                    mActionMode.finish();
                    mActionMode = null;
                }
            }

            if (error == 0) {
                getActivity().getSupportLoaderManager().restartLoader(G.TORRENT_LIST_TRAFFIC_LOADER_ID,
                    null, torrentTrafficLoaderCallbacks);

                FragmentManager manager = getActivity().getSupportFragmentManager();
                TorrentListMenuFragment menu = (TorrentListMenuFragment) manager.findFragmentById(R.id.torrent_list_menu);

                if (menu != null) {
                    menu.notifyTorrentListChanged(cursor, error, added, removed,
                        statusChanged, metadataNeeded);
                }

                if (((TorrentListActivity) getActivity()).isDetailPanelShown() && (!filtered || statusChanged)) {
                    TorrentDetailFragment detail = (TorrentDetailFragment) manager.findFragmentByTag(
                        G.DETAIL_FRAGMENT_TAG);
                    if (detail != null) {
                        detail.notifyTorrentListChanged(cursor, error, added, removed,
                            statusChanged, metadataNeeded);
                    }
                }
                if (filtered) {
                    torrentAdapter.setTemporaryFilterCursor(cursor);
                    torrentAdapter.getFilter().filter(query);
                } else {
                    torrentAdapter.changeCursor(cursor);
                }
            }
        }
    }


    public void setEmptyText(int stringId) {
        Spanned text = Html.fromHtml(getString(stringId));

        ((TextView) getListView().getEmptyView()).setText(text);
    }

    public void setEmptyText(String text) {
        ((TextView) getListView().getEmptyView()).setText(text);
    }

    /**
     * Turns on activate-on-click mode. When this mode is on, list items will be
     * given the 'activated' state when touched.
     */
    public void setActivateOnItemClick(boolean activateOnItemClick) {
        // When setting CHOICE_MODE_SINGLE, ListView will automatically
        // give items the 'activated' state when touched.
        mChoiceMode = activateOnItemClick
                ? ListView.CHOICE_MODE_SINGLE
                : ListView.CHOICE_MODE_NONE;
        getListView().setChoiceMode(mChoiceMode);
    }

    public void setListFilter(String query) {
        torrentAdapter.applyFilter(query, G.PREF_LIST_SEARCH, false);
        mScrollToTop = true;
    }

    public void setListFilter(FilterBy e) {
        torrentAdapter.applyFilter(e.name(), G.PREF_LIST_FILTER, true);
        mScrollToTop = true;
    }

    public void setListFilter(SortBy e) {
        torrentAdapter.applyFilter(e.name(), G.PREF_LIST_SORT_BY, true);
        mScrollToTop = true;
    }

    public void setListFilter(SortOrder e) {
        torrentAdapter.applyFilter(e.name(), G.PREF_LIST_SORT_ORDER, true);
        mScrollToTop = true;
    }

    public void setListDirectoryFilter(String e) {
        torrentAdapter.applyFilter(e, G.PREF_LIST_DIRECTORY, true);
        mScrollToTop = true;
    }

    public void setListTrackerFilter(String e) {
        torrentAdapter.applyFilter(e, G.PREF_LIST_TRACKER, true);
        mScrollToTop = true;
    }

    public void showFind() {
        setFindVisibility(true);
    }

    public boolean isFindShown() {
        return findVisible;
    }

    public Cursor getCursor() {
        return torrentAdapter.getCursor();
    }

    private void setActivatedPosition(int position) {
        if (position == ListView.INVALID_POSITION) {
            getListView().setItemChecked(mActivatedPosition, false);
        } else {
            getListView().setItemChecked(position, true);
        }

        mActivatedPosition = position;
    }

    private boolean showMoveDialog(final int[] ids) {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        final Loader<TransmissionData> loader = getActivity().getSupportLoaderManager()
            .getLoader(G.TORRENTS_LOADER_ID);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
            .setTitle(R.string.set_location)
            .setCancelable(false)
            .setNegativeButton(android.R.string.no, null)
            .setPositiveButton(android.R.string.yes,
            new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int id) {
                Spinner location = (Spinner) ((AlertDialog) dialog).findViewById(R.id.location_choice);
                CheckBox move = (CheckBox) ((AlertDialog) dialog).findViewById(R.id.move);

                String dir = (String) location.getSelectedItem();
                ((TransmissionDataLoader) loader).setTorrentsLocation(
                        ids, dir, move.isChecked());
                ((TransmissionSessionInterface) getActivity()).setRefreshing(true);

                if (mActionMode != null) {
                    mActionMode.finish();
                }
            }
        }).setView(inflater.inflate(R.layout.torrent_location_dialog, null));

        TransmissionSession session = ((TransmissionSessionInterface) getActivity()).getSession();
        if (session == null) {
            return true;
        }

        AlertDialog dialog = builder.create();
        dialog.show();

        TransmissionProfileDirectoryAdapter adapter =
                new TransmissionProfileDirectoryAdapter(
                getActivity(), android.R.layout.simple_spinner_item);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        adapter.addAll(session.getDownloadDirectories());
        adapter.sort();

        Spinner location = (Spinner) dialog.findViewById(R.id.location_choice);
        location.setAdapter(adapter);

        TransmissionProfile profile = ((TransmissionSessionInterface) getActivity()).getProfile();

        if (profile != null && profile.getLastDownloadDirectory() != null) {
            int position = adapter.getPosition(profile.getLastDownloadDirectory());

            if (position > -1) {
                location.setSelection(position);
            }
        }

        ((CheckBox) dialog.findViewById(R.id.move)).setChecked(
            profile != null && profile.getMoveData());

        return true;
    }

    private void setFindVisibility(boolean visible) {
        MenuItem item = menu.findItem(R.id.find);
        if (visible) {
            if (mActionMode != null) {
                mActionMode.finish();
            }

            findVisible = true;
            findQuery = mSharedPrefs.getString(G.PREF_LIST_SEARCH, "");
            item.setVisible(true);
            item.expandActionView();

            SearchView findView = (SearchView) item.getActionView();

            if (!findQuery.equals("")) {
                findView.setQuery(findQuery, false);
            }
        } else {
            item.collapseActionView();
        }
    }


    private class TorrentCursorAdapter extends CursorAdapter {
        private SparseBooleanArray torrentAdded= new SparseBooleanArray();
        private DataSource readDataSource;
        private Cursor temporaryFilterCursor;

        private boolean resourcesCleared = false;

        private final Object lock = new Object();

        public TorrentCursorAdapter(Context context, Cursor cursor) {
            super(context, cursor, 0);

            resourcesCleared = false;
            readDataSource = new DataSource(context);

            setFilterQueryProvider(new FilterQueryProvider() {
                @Override public Cursor runQuery(CharSequence charSequence) {
                    Cursor originalCursor, filteredCursor;

                    synchronized (lock) {
                        if (resourcesCleared) {
                            return null;
                        }
                        readDataSource.open();

                        if (temporaryFilterCursor != null) {
                            originalCursor = temporaryFilterCursor;
                            temporaryFilterCursor = null;
                        } else {
                            originalCursor = readDataSource.getTorrentCursor();
                        }
                    }

                    if (charSequence != null && charSequence.length() > 0) {
                        MatrixCursor cursor = new MatrixCursor(G.concat(
                            new String[] { Constants.C_ID }, Constants.ColumnGroups.TORRENT_OVERVIEW));

                        String prefixString = charSequence.toString().toLowerCase(Locale.getDefault());
                        Pattern prefixPattern = null;
                        int hiPrimary = getResources().getColor(R.color.filter_highlight_primary);
                        int hiSecondary = getResources().getColor(R.color.filter_highlight_secondary);

                        if (prefixString.length() > 0) {
                            String[] split = prefixString.split("");
                            StringBuilder pattern = new StringBuilder();
                            for (int i = 0; i < split.length; i++) {
                                if (split[i].equals("")) {
                                    continue;
                                }
                                pattern.append("\\Q").append(split[i]).append("\\E");
                                if (i < split.length - 1) {
                                    pattern.append(".{0,2}?");
                                }
                            }

                            prefixPattern = Pattern.compile(pattern.toString());
                        }

                        originalCursor.moveToFirst();

                        while (!originalCursor.isAfterLast()) {
                            String name = Torrent.getName(originalCursor);

                            Matcher m = prefixPattern.matcher(name.toLowerCase(Locale.getDefault()));
                            if (m.find()) {
                                SpannableString spannedName = new SpannableString(name);
                                spannedName.setSpan(
                                    new ForegroundColorSpan(hiPrimary),
                                    m.start(),
                                    m.start() + 1,
                                    Spanned.SPAN_INCLUSIVE_INCLUSIVE
                                );
                                if (m.end() - m.start() > 2) {
                                    spannedName.setSpan(
                                        new ForegroundColorSpan(hiSecondary),
                                        m.start() + 1,
                                        m.end() - 1,
                                        Spanned.SPAN_INCLUSIVE_INCLUSIVE
                                    );
                                }
                                if (m.end() - m.start() > 1) {
                                    spannedName.setSpan(
                                        new ForegroundColorSpan(hiPrimary),
                                        m.end() - 1,
                                        m.end(),
                                        Spanned.SPAN_INCLUSIVE_INCLUSIVE
                                    );
                                }
                                name = Html.toHtml(spannedName);

                                MatrixCursor.RowBuilder row = cursor.newRow();

                                int index = 0;
                                for (String column : originalCursor.getColumnNames()) {
                                    if (column.equals(Constants.C_ID)) {
                                        row.add(originalCursor.getInt(index));
                                    } else if (column.equals(Constants.C_NAME)) {
                                        row.add(name);
                                    } else if (column.equals(Constants.C_STATUS)) {
                                        row.add(originalCursor.getInt(index));
                                    } else if (column.equals(Constants.C_METADATA_PERCENT_COMPLETE)) {
                                        row.add(originalCursor.getFloat(index));
                                    } else if (column.equals(Constants.C_PERCENT_DONE)) {
                                        row.add(originalCursor.getFloat(index));
                                    } else if (column.equals(Constants.C_UPLOAD_RATIO)) {
                                        row.add(originalCursor.getFloat(index));
                                    } else if (column.equals(Constants.C_SEED_RATIO_LIMIT)) {
                                        row.add(originalCursor.getFloat(index));
                                    } else if (column.equals(Constants.C_TRAFFIC_TEXT)) {
                                        row.add(originalCursor.getString(index));
                                    } else if (column.equals(Constants.C_STATUS_TEXT)) {
                                        row.add(originalCursor.getString(index));
                                    } else if (column.equals(Constants.C_ERROR)) {
                                        row.add(originalCursor.getInt(index));
                                    } else if (column.equals(Constants.C_ERROR_STRING)) {
                                        row.add(originalCursor.getString(index));
                                    }

                                    ++index;
                                }
                            }

                            originalCursor.moveToNext();
                        }

                        filteredCursor = cursor;
                        originalCursor.close();
                    } else {
                        filteredCursor = originalCursor;
                    }

                    return filteredCursor;
                }
            });
        }

        public void clearResources() {
            synchronized (lock) {
                resourcesCleared = true;
                readDataSource.close();
                if (temporaryFilterCursor != null) {
                    temporaryFilterCursor.close();
                    temporaryFilterCursor = null;
                }
            }
        }

        public void setTemporaryFilterCursor(Cursor cursor) {
            temporaryFilterCursor = cursor;
        }

        private void applyFilter(String value, String pref, boolean animate) {
            if (mActionMode != null) {
                mActionMode.finish();
            }

            if (pref != null) {
                Editor e = mSharedPrefs.edit();
                e.putString(pref, value);
                e.apply();
                G.requestBackup(getActivity());
            }

            getFilter().filter(mSharedPrefs.getString(G.PREF_LIST_SEARCH, ""));
            if (animate) {
                torrentAdded = new SparseBooleanArray();
            }
        }

        @Override public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
            LayoutInflater vi = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            return vi.inflate(R.layout.torrent_list_item, viewGroup, false);
        }

        @Override public void bindView(View view, Context context, Cursor cursor) {
            int id = Torrent.getId(cursor);

            TextView name = (TextView) view.findViewById(R.id.name);

            TextView traffic = (TextView) view.findViewById(R.id.traffic);
            ProgressBar progress = (ProgressBar) view.findViewById(R.id.progress);
            TextView status = (TextView) view.findViewById(R.id.status);
            TextView errorText = (TextView) view.findViewById(R.id.error_text);

            if (findQuery != null && !findQuery.equals("")) {
                name.setText(Html.fromHtml(Torrent.getName(cursor)));
            } else {
                name.setText(Torrent.getName(cursor));
            }

            float metadata = Torrent.getMetadataPercentDone(cursor);
            float percent = Torrent.getPercentDone(cursor);
            if (metadata < 1) {
                progress.setSecondaryProgress((int) (metadata * 100));
                progress.setProgress(0);
            } else if (percent < 1) {
                progress.setSecondaryProgress((int) (percent * 100));
                progress.setProgress(0);
            } else {
                progress.setSecondaryProgress(100);

                float limit = Torrent.getSeedRatioLimit(cursor);
                float current = Torrent.getUploadRatio(cursor);

                if (limit == -1) {
                    progress.setProgress(100);
                } else {
                    if (current >= limit) {
                        progress.setProgress(100);
                    } else {
                        progress.setProgress((int) (current / limit * 100));
                    }
                }
            }

            traffic.setText(Html.fromHtml(Torrent.getTrafficText(cursor)));
            status.setText(Html.fromHtml(Torrent.getStatusText(cursor)));

            int torrentStatus = Torrent.getStatus(cursor);
            boolean enabled = Torrent.isActive(torrentStatus);

            name.setEnabled(enabled);
            traffic.setEnabled(enabled);
            status.setEnabled(enabled);
            errorText.setEnabled(enabled);

            if (Torrent.getError(cursor) == Torrent.Error.OK) {
                errorText.setVisibility(View.GONE);
            } else {
                errorText.setVisibility(View.VISIBLE);
                errorText.setText(Torrent.getErrorString(cursor));
            }

            if (!torrentAdded.get(id, false)) {
                view.setTranslationY(100);
                view.setAlpha((float) 0.3);
                view.setRotationX(10);
                view.animate().setDuration(300).translationY(0).alpha(1).rotationX(0).start();
                torrentAdded.append(id, true);
            }
        }

        @Override public Cursor swapCursor(Cursor newCursor) {
            Cursor oldCursor = super.swapCursor(newCursor);

            if (((TorrentListActivity) getActivity()).isDetailPanelShown()) {
                FragmentManager manager = getActivity().getSupportFragmentManager();
                TorrentDetailFragment detail = (TorrentDetailFragment) manager.findFragmentByTag(
                    G.DETAIL_FRAGMENT_TAG);
                if (detail != null) {
                    detail.notifyTorrentListChanged(newCursor, 0, true, true, false, false);
                }
            }
            if (newCursor.getCount() == 0) {
                if (mSharedPrefs.getString(G.PREF_LIST_SEARCH, "").equals("")
                    && mSharedPrefs.getString(G.PREF_LIST_DIRECTORY, "").equals("")
                    && mSharedPrefs.getString(G.PREF_LIST_TRACKER, "").equals("")
                    && mSharedPrefs.getString(G.PREF_LIST_FILTER, FilterBy.ALL.name()).equals(FilterBy.ALL.name())) {
                    setEmptyText(R.string.no_torrents_empty_list);
                } else {
                    setEmptyText(R.string.no_filtered_torrents_empty_list);
                }
            }

            if (mScrollToTop) {
                mScrollToTop = false;
                getListView().setSelectionAfterHeaderView();
            }

            return oldCursor;
        }
    }
}
