package org.sugr.gearshift;

import java.util.Map;
import java.util.HashMap;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;

import org.sugr.gearshift.G.FilterBy;
import org.sugr.gearshift.G.SortBy;
import org.sugr.gearshift.G.SortOrder;
import org.sugr.gearshift.datasource.DataSource;
import org.sugr.gearshift.service.DataService;
import org.sugr.gearshift.service.DataServiceManager;
import org.sugr.gearshift.service.DataServiceManagerInterface;
import org.w3c.dom.Text;

/**
 * A list fragment representing a list of Torrents. This fragment
 * also supports tablet devices by allowing list items to be given an
 * 'activated' state upon selection. This helps indicate which item is
 * currently being viewed in a {@link TorrentDetailFragment}.
 * <p>
 * Activities containing this fragment MUST implement the {@link Callbacks}
 * interface.
 */
public class TorrentListFragment extends ListFragment implements TorrentListNotificationInterface {
    /**
     * The serialization (saved instance state) Bundle key representing the
     * activated item position. Only used on tablets.
     */
    private static final String STATE_FIND_SHOWN = "find_shown";
    private static final String STATE_FIND_QUERY = "find_query";

    /**
     * The fragment's current callback object, which is notified of list item
     * clicks.
     */
    private Callbacks callbacks = dummyCallbacks;

    /**
     * The current activated item position. Only used on tablets.
     */
    private int activatedPosition = ListView.INVALID_POSITION;

    private ActionMode actionMode;

    private int listChoiceMode = ListView.CHOICE_MODE_NONE;

    private TorrentCursorAdapter torrentAdapter;

    private boolean scrollToTop = false;
    private boolean findVisible = false;
    private boolean initialLoading = false;

    private String findQuery = "";

    private SharedPreferences sharedPrefs;

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
    private static Callbacks dummyCallbacks = new Callbacks() {
        @Override
        public void onItemSelected(int position) {
        }
    };

    private MultiChoiceModeListener listChoiceListener = new MultiChoiceModeListener() {
        private SparseArray<String> selectedTorrentIds;
        private boolean hasQueued = false;

        @Override
        public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
            final DataServiceManager manager =
                ((DataServiceManagerInterface) getActivity()).getDataServiceManager();

            if (manager == null)
                return false;

            final String[] hashStrings = new String[selectedTorrentIds.size()];
            for (int i = 0; i < selectedTorrentIds.size(); ++i) {
                hashStrings[i] = selectedTorrentIds.valueAt(i);
            }

            AlertDialog.Builder builder;
            String action;
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
                                    manager.removeTorrent(hashStrings, item.getItemId() == R.id.delete);
                                    ((TransmissionSessionInterface) getActivity()).setRefreshing(true,
                                        DataService.Requests.REMOVE_TORRENT);

                                    mode.finish();
                                }
                            })
                            .setMessage(item.getItemId() == R.id.delete
                                    ? R.string.delete_selected_confirmation
                                    : R.string.remove_selected_confirmation)
                            .show();
                    return true;
                case R.id.resume:
                    action = hasQueued ? "torrent-start-now" : "torrent-start";
                    break;
                case R.id.pause:
                    action = "torrent-stop";
                    break;
                case R.id.move:
                    return showMoveDialog(hashStrings);
                case R.id.verify:
                    action = "torrent-verify";
                    break;
                case R.id.reannounce:
                    action = "torrent-reannounce";
                    break;
                default:
                    return true;
            }

            manager.setTorrentAction(hashStrings, action);
            ((TransmissionSessionInterface) getActivity()).setRefreshing(true,
                DataService.Requests.SET_TORRENT_ACTION);

            mode.finish();
            return true;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            if (inflater != null)
                inflater.inflate(R.menu.torrent_list_multiselect, menu);

            selectedTorrentIds = new SparseArray<>();
            actionMode = mode;
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            G.logD("Destroying context menu");
            actionMode = null;
            selectedTorrentIds = null;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode,
                                              int position, long id, boolean checked) {

            Cursor cursor = (Cursor) torrentAdapter.getItem(position);
            String hash = Torrent.getHashString(cursor);
            if (checked)
                selectedTorrentIds.append(position, hash);
            else
                selectedTorrentIds.delete(position);

            boolean hasPaused = false;
            boolean hasRunning = false;

            hasQueued = false;
            for (int i = 0; i < selectedTorrentIds.size(); ++i) {
                cursor = (Cursor) torrentAdapter.getItem(selectedTorrentIds.keyAt(i));
                int status = Torrent.getStatus(cursor);
                if (status == Torrent.Status.STOPPED) {
                    hasPaused = true;
                } else if (Torrent.isActive(status)) {
                    hasRunning = true;
                } else {
                    hasQueued = true;
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
    private SharedPreferences.OnSharedPreferenceChangeListener settingsChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    if (key.equals(G.PREF_BASE_SORT_ORDER) || key.equals(G.PREF_BASE_SORT_ORDER)) {
                        torrentAdapter.getFilter().filter("");
                    } else if (key.equals(G.PREF_PROFILES)) {
                        if (getActivity() != null) {
                            ((TransmissionSessionInterface) getActivity()).setRefreshing(true,
                                DataService.Requests.GET_TORRENTS);
                        }
                    } else if (key.equals(G.PREF_CURRENT_PROFILE)) {
                        if (prefs.getString(key, null) == null && getActivity() != null) {
                            setEmptyText(R.string.no_profiles_empty_list);
                        }
                    } else if (key.equals(G.PREF_SHOW_STATUS) && getView() != null) {
                        toggleStatusBar();
                    }
                }
            };

    private Handler findHandler = new Handler();
    private Runnable findRunnable = new Runnable() {
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
                TransmissionSessionInterface context = (TransmissionSessionInterface) getActivity();
                if (context == null) {
                    return null;
                }
                return new TorrentTrafficLoader(getActivity(), context.getProfile().getId(),
                    sharedPrefs.getBoolean(G.PREF_SHOW_STATUS, false), false, false);
            }
            return null;
        }

        @Override public void onLoadFinished(Loader<TorrentTrafficLoader.TorrentTrafficOutputData> loader,
                                             TorrentTrafficLoader.TorrentTrafficOutputData data) {
            TransmissionSessionInterface context = (TransmissionSessionInterface) getActivity();
            if (context == null) {
                return;
            }

            TransmissionSession session = context.getSession();
            TextView status = (TextView) getView().findViewById(R.id.status_bar_text);

            if (!sharedPrefs.getBoolean(G.PREF_SHOW_STATUS, false)) {
                return;
            }

            if (status.getVisibility() == View.GONE) {
                toggleStatusBar(status);
            }

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

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        getActivity().setProgressBarIndeterminateVisibility(true);

        initialLoading = true;
        if (savedInstanceState == null) {
            sharedPrefs.registerOnSharedPreferenceChangeListener(settingsChangeListener);
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
        }
        if (!findVisible) {
            Editor e = sharedPrefs.edit();
            e.putString(G.PREF_LIST_SEARCH, "");
            e.apply();
        }

        torrentAdapter = new TorrentCursorAdapter(getActivity(), savedInstanceState);
        setListAdapter(torrentAdapter);

        TextView status = (TextView) view.findViewById(R.id.status_bar_text);
        /* Enable the marquee animation */
        status.setSelected(true);

        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final ListView list = getListView();
        list.setChoiceMode(listChoiceMode);
        list.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                    int position, long id) {

                if (!((TorrentListActivity) getActivity()).isDetailPanelVisible()) {
                    list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
                    setActivatedPosition(position);
                    return true;
                }
                return false;
            }});

        list.setMultiChoiceModeListener(listChoiceListener);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Activities containing this fragment must implement its callbacks.
        if (!(activity instanceof Callbacks)) {
            throw new IllegalStateException("Activity must implement fragment's callbacks.");
        }

        callbacks = (Callbacks) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();

        // Reset the active callbacks interface to the dummy implementation.
        callbacks =dummyCallbacks;

    }

    @Override public void onDestroyView() {
        torrentAdapter.clearResources();

        super.onDestroyView();
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        super.onListItemClick(listView, view, position, id);

        if (actionMode == null)
            listView.setChoiceMode(listChoiceMode);

        // Notify the active callbacks interface (the activity, if the
        // fragment is attached to one) that an item has been selected.
        callbacks.onItemSelected(position);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(STATE_FIND_SHOWN, findVisible);
        outState.putString(STATE_FIND_QUERY, findQuery);
        torrentAdapter.onSaveInstanceState(outState);
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
                if (!newText.equals(findQuery)) {
                    findQuery = newText;
                    findHandler.removeCallbacks(findRunnable);
                    findHandler.postDelayed(findRunnable, 500);
                }
                return true;
            }
        });

        item.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override public boolean onMenuItemActionCollapse(MenuItem item) {
                item.setVisible(false);
                findQuery = "";
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
                                         boolean statusChanged, boolean metadataNeeded,
                                         boolean connected) {
        if (error == -1) {
            return;
        }

        if (error > 0) {
            if (error != TransmissionData.Errors.DUPLICATE_TORRENT
                && error != TransmissionData.Errors.INVALID_TORRENT
                && actionMode != null) {

                actionMode.finish();
                actionMode = null;
            }
        } else if (cursor != null) {
            if (connected) {
                getActivity().getSupportLoaderManager().restartLoader(G.TORRENT_LIST_TRAFFIC_LOADER_ID,
                    null, torrentTrafficLoaderCallbacks);
            }

            if (removed) {
                final Map<Long, Integer> idTopMap = new HashMap<>();
                final ListView listview = getListView();

                int firstVisible = listview.getFirstVisiblePosition();
                for (int i = 0; i < listview.getChildCount(); ++i) {
                    View child = listview.getChildAt(i);
                    int position = firstVisible + i;
                    long id = torrentAdapter.getItemId(position);
                    idTopMap.put(id, child.getTop());
                }

                listview.setEnabled(false);

                torrentAdapter.changeCursor(cursor);

                final ViewTreeObserver observer = listview.getViewTreeObserver();
                observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    public boolean onPreDraw() {
                        observer.removeOnPreDrawListener(this);

                        boolean firstAnimation = true;
                        int firstVisible = listview.getFirstVisiblePosition();
                        for (int i = 0; i < listview.getChildCount(); ++i) {
                            final View child = listview.getChildAt(i);
                            int position = firstVisible + i;
                            long id = torrentAdapter.getItemId(position);
                            Integer startTop = idTopMap.get(id);
                            int top = child.getTop();

                            if (startTop == null) {
                                int childHeight = child.getHeight() + listview.getDividerHeight();
                                startTop = top + (i > 0 ? childHeight : -childHeight);
                            }

                            int delta = startTop - top;

                            if (delta != 0) {
                                child.animate().setDuration(150);
                                ObjectAnimator anim = ObjectAnimator.ofFloat(child, View.TRANSLATION_Y, delta, 0);
                                anim.setDuration(150);
                                anim.start();

                                if (firstAnimation) {
                                    anim.addListener(new AnimatorListenerAdapter() {
                                        @Override public void onAnimationEnd(Animator animation) {
                                            listview.setEnabled(true);
                                        }
                                    });
                                }

                                firstAnimation = false;
                            }
                        }

                        return true;
                    }
                });
            } else {
                torrentAdapter.changeCursor(cursor);
            }

        }
    }


    public void setEmptyText(int stringId) {
        Spanned text = Html.fromHtml(getString(stringId));

        ((TextView) getListView().getEmptyView()).setText(text);
    }

    /**
     * Turns on activate-on-click mode. When this mode is on, list items will be
     * given the 'activated' state when touched.
     */
    public void setActivateOnItemClick(boolean activateOnItemClick) {
        // When setting CHOICE_MODE_SINGLE, ListView will automatically
        // give items the 'activated' state when touched.
        listChoiceMode = activateOnItemClick
                ? ListView.CHOICE_MODE_SINGLE
                : ListView.CHOICE_MODE_NONE;
        getListView().setChoiceMode(listChoiceMode);
    }

    public void setListFilter(String query) {
        torrentAdapter.applyFilter(query, G.PREF_LIST_SEARCH, false);
        scrollToTop = true;
    }

    public void setListFilter(FilterBy e) {
        torrentAdapter.applyFilter(e.name(), G.PREF_LIST_FILTER, true);
        scrollToTop = true;
    }

    public void setListFilter(SortBy e) {
        torrentAdapter.applyFilter(e.name(), G.PREF_LIST_SORT_BY, true);
        scrollToTop = true;
    }

    public void setListFilter(SortOrder e) {
        torrentAdapter.applyFilter(e.name(), G.PREF_LIST_SORT_ORDER, true);
        scrollToTop = true;
    }

    public void setListDirectoryFilter(String e) {
        torrentAdapter.applyFilter(e, G.PREF_LIST_DIRECTORY, true);
        scrollToTop = true;
    }

    public void setListTrackerFilter(String e) {
        torrentAdapter.applyFilter(e, G.PREF_LIST_TRACKER, true);
        scrollToTop = true;
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
            getListView().setItemChecked(activatedPosition, false);
        } else {
            getListView().setItemChecked(position, true);
        }

        activatedPosition = position;
    }

    private boolean showMoveDialog(final String[] hashStrings) {
        final DataServiceManager manager =
            ((DataServiceManagerInterface) getActivity()).getDataServiceManager();

        if (manager == null) {
            return true;
        }

        final TransmissionSession session = ((TransmissionSessionInterface) getActivity()).getSession();
        if (session == null) {
            return true;
        }

        AlertDialog dialog = ((LocationDialogHelperInterface) getActivity())
            .getLocationDialogHelper().showDialog(R.layout.torrent_location_dialog,
                R.string.set_location, null, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    Spinner location = (Spinner) ((AlertDialog) dialog).findViewById(R.id.location_choice);
                    EditText entry = (EditText) ((AlertDialog) dialog).findViewById(R.id.location_entry);
                    CheckBox move = (CheckBox) ((AlertDialog) dialog).findViewById(R.id.move);
                    String dir;

                    if (location.getVisibility() != View.GONE) {
                        dir = (String) location.getSelectedItem();
                    } else {
                        dir = entry.getText().toString();
                    }

                    if (TextUtils.isEmpty(dir)) {
                        dir = session.getDownloadDir();
                    }

                    manager.setTorrentLocation(hashStrings, dir, move.isChecked());
                    ((TransmissionSessionInterface) getActivity()).setRefreshing(true,
                        DataService.Requests.SET_TORRENT_LOCATION);

                    if (actionMode != null) {
                        actionMode.finish();
                    }
                }
            }
        );

        TransmissionProfile profile = ((TransmissionSessionInterface) getActivity()).getProfile();
        ((CheckBox) dialog.findViewById(R.id.move)).setChecked(
            profile != null && profile.getMoveData());

        return true;
    }

    private void setFindVisibility(boolean visible) {
        MenuItem item = menu.findItem(R.id.find);
        if (visible) {
            if (actionMode != null) {
                actionMode.finish();
            }

            findVisible = true;
            findQuery = sharedPrefs.getString(G.PREF_LIST_SEARCH, "");
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

    private void toggleStatusBar() {
        View status = getView().findViewById(R.id.status_bar_text);
        toggleStatusBar(status);
    }

    private void toggleStatusBar(View status) {
        if (sharedPrefs.getBoolean(G.PREF_SHOW_STATUS, false)) {
            status.setVisibility(View.VISIBLE);
            status.setTranslationY(100);
            status.animate().alpha((float) 1.0).translationY(0).setStartDelay(500);
        } else {
            status.setVisibility(View.GONE);
            status.setAlpha((float) 0.3);
        }
    }

    private class TorrentCursorAdapter extends CursorAdapter {
        private SparseBooleanArray addedTorrents = new SparseBooleanArray();
        private DataSource readDataSource;

        private boolean resourcesCleared = false;

        private final Object lock = new Object();

        private static final String STATE_ADDED_TORRENTS = "adapter_added_torrents";

        public TorrentCursorAdapter(Context context, Bundle state) {
            super(context, null, 0);

            resourcesCleared = false;
            readDataSource = new DataSource(context);

            if (state != null) {
                onRestoreInstanceState(state);
            }

            setFilterQueryProvider(new FilterQueryProvider() {
                @Override public Cursor runQuery(CharSequence charSequence) {
                    synchronized (lock) {
                        if (resourcesCleared) {
                            return null;
                        }
                        TransmissionSessionInterface context
                            = (TransmissionSessionInterface) getActivity();
                        if (context == null) {
                            return null;
                        }

                        readDataSource.open();

                        return readDataSource.getTorrentCursor(context.getProfile().getId());
                    }
                }
            });
        }

        public void clearResources() {
            synchronized (lock) {
                resourcesCleared = true;
                readDataSource.close();
            }
        }

        public void onSaveInstanceState(Bundle outState) {
            int[] ids = new int[addedTorrents.size()];

            for (int i = 0; i < addedTorrents.size(); ++i) {
                int id = addedTorrents.keyAt(i);
                ids[i] = id;
            }

            outState.putIntArray(STATE_ADDED_TORRENTS, ids);
        }

        public void onRestoreInstanceState(Bundle state) {
            if (state.containsKey(STATE_ADDED_TORRENTS)) {
                int[] ids = state.getIntArray(STATE_ADDED_TORRENTS);
                if (ids != null) {
                    for (int id : ids) {
                        addedTorrents.append(id, true);
                    }
                }
            }
        }

        private void applyFilter(String value, String pref, boolean animate) {
            if (actionMode != null) {
                actionMode.finish();
            }

            if (pref != null) {
                Editor e = sharedPrefs.edit();
                e.putString(pref, value);
                e.apply();
                G.requestBackup(getActivity());
            }

            getFilter().filter("");
            if (animate) {
                addedTorrents = new SparseBooleanArray();
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

            String search = sharedPrefs.getString(G.PREF_LIST_SEARCH, null);
            if (TextUtils.isEmpty(search)) {
                name.setText(Torrent.getName(cursor));
            } else {
                name.setText(G.trimTrailingWhitespace(Html.fromHtml(Torrent.getName(cursor))));
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

                int mode = Torrent.getSeedRatioMode(cursor);
                float limit = Torrent.getSeedRatioLimit(cursor);
                float current = Torrent.getUploadRatio(cursor);

                if (mode == Torrent.SeedRatioMode.NO_LIMIT) {
                    limit = 0;
                } else if (mode == Torrent.SeedRatioMode.GLOBAL_LIMIT) {
                    TransmissionSession session = ((TransmissionSessionInterface) getActivity()).getSession();
                    if (session != null) {
                        if (session.isSeedRatioLimitEnabled()) {
                            limit = session.getSeedRatioLimit();
                        } else {
                            limit = 0;
                        }
                    }
                }

                if (limit <= 0) {
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

            if (!addedTorrents.get(id, false)) {
                view.setTranslationY(100);
                view.setAlpha((float) 0.3);
                view.setRotationX(10);
                view.animate().setDuration(300).translationY(0).alpha(1).rotationX(0).start();
                addedTorrents.append(id, true);
            }
        }

        @Override public Cursor swapCursor(Cursor newCursor) {
            Cursor oldCursor = super.swapCursor(newCursor);

            if (newCursor.getCount() == 0) {
                if (sharedPrefs.getString(G.PREF_LIST_SEARCH, "").equals("")
                    && sharedPrefs.getString(G.PREF_LIST_DIRECTORY, "").equals("")
                    && sharedPrefs.getString(G.PREF_LIST_TRACKER, "").equals("")
                    && sharedPrefs.getString(G.PREF_LIST_FILTER, FilterBy.ALL.name()).equals(FilterBy.ALL.name())) {
                    setEmptyText(R.string.no_torrents_empty_list);
                } else {
                    setEmptyText(R.string.no_filtered_torrents_empty_list);
                }
            }

            if (scrollToTop) {
                scrollToTop = false;
                if (TorrentListFragment.this.getView() != null) {
                    getListView().setSelectionAfterHeaderView();
                }
            }

            if (initialLoading) {
                initialLoading = false;
                final int position = getListView().getCheckedItemPosition();
                if (position != ListView.INVALID_POSITION) {
                    new Handler().postDelayed(new Runnable() {
                        @Override public void run() {
                            callbacks.onItemSelected(position);
                        }
                    }, 500);
                }
            }

            return oldCursor;
        }
    }
}
