package org.sugr.gearshift.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import org.sugr.gearshift.G;
import org.sugr.gearshift.G.FilterBy;
import org.sugr.gearshift.G.SortBy;
import org.sugr.gearshift.G.SortOrder;
import org.sugr.gearshift.GearShiftApplication;
import org.sugr.gearshift.R;
import org.sugr.gearshift.core.Torrent;
import org.sugr.gearshift.core.TransmissionProfile;
import org.sugr.gearshift.core.TransmissionSession;
import org.sugr.gearshift.datasource.DataSource;
import org.sugr.gearshift.service.DataService;
import org.sugr.gearshift.service.DataServiceManager;
import org.sugr.gearshift.service.DataServiceManagerInterface;
import org.sugr.gearshift.ui.loader.TorrentTrafficLoader;
import org.sugr.gearshift.ui.settings.SettingsActivity;
import org.sugr.gearshift.ui.util.LocationDialogHelperInterface;
import org.sugr.gearshift.ui.util.UpdateCheckDialog;

import java.util.HashMap;
import java.util.Map;

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

    private SparseBooleanArray checkAnimations = new SparseBooleanArray();

    private BroadcastReceiver sessionReceiver;

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
                    builder = new AlertDialog.Builder(getActivity())
                            .setCancelable(false)
                            .setNegativeButton(android.R.string.no, null);

                    builder.setPositiveButton(android.R.string.yes,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    manager.removeTorrent(hashStrings, false);
                                    ((TransmissionSessionInterface) getActivity()).setRefreshing(true,
                                        DataService.Requests.REMOVE_TORRENT);

                                    mode.finish();
                                }
                            })
                        .setNeutralButton(R.string.remove_with_data,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    manager.removeTorrent(hashStrings, true);
                                    ((TransmissionSessionInterface) getActivity()).setRefreshing(true,
                                        DataService.Requests.REMOVE_TORRENT);

                                    mode.finish();

                                }
                            })
                        .setMessage(R.string.remove_selected_confirmation)
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

            ListView list = getListView();
            int firstVisible = list.getFirstVisiblePosition();
            int virtual = position - firstVisible;
            View child = list.getChildAt(virtual);

            if (child != null) {
                toggleListItemChecked(checked, position, child.findViewById(R.id.type_checked),
                    child.findViewById(R.id.type_directory), child.findViewById(R.id.progress));
            }

        }
    };

    /* The callback will get garbage collected if its a mere anon class */
    private SharedPreferences.OnSharedPreferenceChangeListener settingsChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    if (key.equals(G.PREF_BASE_SORT_ORDER)) {
                        torrentAdapter.getFilter().filter("");
                    } else if (key.equals(G.PREF_PROFILES)) {
                        if (getActivity() != null) {
                            ((TransmissionSessionInterface) getActivity()).setRefreshing(true,
                                DataService.Requests.GET_TORRENTS);
                        }
                    } else if (key.equals(G.PREF_CURRENT_PROFILE)) {
                        if (prefs.getString(key, null) == null && getActivity() != null) {
                            setEmptyMessage(R.string.no_profiles_empty_list);
                        }
                    } else if (key.equals(G.PREF_SHOW_STATUS) && getView() != null) {
                        toggleStatusBar();
                    } else if (key.startsWith(G.PREF_SORT_PREFIX)) {
                        if (getActivity() != null
                            && ((TransmissionSessionInterface) getActivity()).getSession() != null) {

                            int visibleCount = setupSortMenu();
                            menu.findItem(R.id.sort).setVisible(visibleCount > 0);
                        }
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
                TransmissionProfileInterface context = (TransmissionProfileInterface) getActivity();
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
            Toolbar status = (Toolbar) getView().findViewById(R.id.status_bar);
            TextView statusBarText = (TextView) status.findViewById(R.id.status_bar_text);

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

            statusBarText.setText(Html.fromHtml(String.format(
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

        sessionReceiver = new SessionReceiver();

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        getActivity().setProgressBarIndeterminateVisibility(true);

        initialLoading = true;
        if (savedInstanceState == null) {
            sharedPrefs.registerOnSharedPreferenceChangeListener(settingsChangeListener);
        }

        if (!GearShiftApplication.isStartupInitialized() && sharedPrefs.getBoolean(G.PREF_AUTO_UPDATE_CHECK, true)) {
            ((GearShiftApplication) getActivity().getApplication()).checkForUpdates(new GearShiftApplication.OnUpdateCheck() {
                @Override public void onNewRelease(String title, String description, String url, String downloadUrl) {
                    new UpdateCheckDialog(getActivity(),
                        G.trimTrailingWhitespace(Html.fromHtml(String.format(getString(R.string.update_available), title))),
                        url, downloadUrl).show();
                }

                @Override public void onCurrentRelease() {  }
                @Override public void onUpdateCheckError(Exception e) {  }
            });
        }

        GearShiftApplication.setStartupInitialized(true);
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

        final SwipeRefreshLayout swipeRefresh = (SwipeRefreshLayout) view.findViewById(R.id.swipe_container);
        swipeRefresh.setColorSchemeResources(R.color.main_red, R.color.main_gray,
            R.color.main_black, R.color.main_red);
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override public void onRefresh() {
                DataServiceManager manager =
                    ((DataServiceManagerInterface) getActivity()).getDataServiceManager();
                manager.update();
                ((TransmissionSessionInterface) getActivity()).setRefreshing(true,
                    DataService.Requests.GET_TORRENTS);
            }
        });

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
            }
        });

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

    @Override public void onResume() {
        super.onResume();

        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
            sessionReceiver, new IntentFilter(G.INTENT_SESSION_INVALIDATED));
    }

    @Override public void onPause() {
        super.onPause();

        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(sessionReceiver);
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

    @Override public void onCreateOptionsMenu(final Menu menu, MenuInflater inflater) {
        if (getActivity() == null) {
            return;
        }

        super.onCreateOptionsMenu(menu, inflater);

        this.menu = menu;

        inflater.inflate(R.menu.torrent_list_fragment, menu);
        MenuItem item = menu.findItem(R.id.find);

        ContextThemeWrapper wrapper = new ContextThemeWrapper(
            ((ActionBarActivity) getActivity()).getSupportActionBar().getThemedContext(),
            R.style.ToolbarControl);
        SearchView findView = new SearchView(wrapper);
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

        MenuItemCompat.setOnActionExpandListener(item, new MenuItemCompat.OnActionExpandListener() {
            @Override public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override public boolean onMenuItemActionCollapse(MenuItem item) {
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

        if (((TransmissionSessionInterface) getActivity()).getSession() != null) {
            int visibleCount = setupSortMenu();
            item = menu.findItem(R.id.sort);
            item.setVisible(visibleCount > 0);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sort_name:
                if (item.isChecked()) {
                    return false;
                }
                item.setChecked(true);
                setListFilter(SortBy.NAME);
                return true;
            case R.id.sort_size:
                if (item.isChecked()) {
                    return false;
                }
                item.setChecked(true);
                setListFilter(SortBy.SIZE);
                return true;
            case R.id.sort_status:
                if (item.isChecked()) {
                    return false;
                }
                item.setChecked(true);
                setListFilter(SortBy.STATUS);
                return true;
            case R.id.sort_activity:
                if (item.isChecked()) {
                    return false;
                }
                item.setChecked(true);
                setListFilter(SortBy.ACTIVITY);
                return true;
            case R.id.sort_age:
                if (item.isChecked()) {
                    return false;
                }
                item.setChecked(true);
                setListFilter(SortBy.AGE);
                return true;
            case R.id.sort_progress:
                if (item.isChecked()) {
                    return false;
                }
                item.setChecked(true);
                setListFilter(SortBy.PROGRESS);
                return true;
            case R.id.sort_ratio:
                if (item.isChecked()) {
                    return false;
                }
                item.setChecked(true);
                setListFilter(SortBy.RATIO);
                return true;
            case R.id.sort_location:
                if (item.isChecked()) {
                    return false;
                }
                item.setChecked(true);
                setListFilter(SortBy.LOCATION);
                return true;
            case R.id.sort_peers:
                if (item.isChecked()) {
                    return false;
                }
                item.setChecked(true);
                setListFilter(SortBy.PEERS);
                return true;
            case R.id.sort_download_speed:
                if (item.isChecked()) {
                    return false;
                }
                item.setChecked(true);
                setListFilter(SortBy.RATE_DOWNLOAD);
                return true;
            case R.id.sort_upload_speed:
                if (item.isChecked()) {
                    return false;
                }
                item.setChecked(true);
                setListFilter(SortBy.RATE_UPLOAD);
                return true;
            case R.id.sort_queue:
                if (item.isChecked()) {
                    return false;
                }
                item.setChecked(true);
                setListFilter(SortBy.QUEUE);
                return true;
            case R.id.sort_order:
                setListFilter(item.isChecked() ? SortOrder.ASCENDING : SortOrder.DESCENDING);
                item.setChecked(!item.isChecked());
                return true;
            default:
                return false;
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
            if (error != DataService.Errors.DUPLICATE_TORRENT
                && error != DataService.Errors.INVALID_TORRENT
                && actionMode != null) {

                actionMode.finish();
                actionMode = null;
            }
        } else if (cursor != null) {
            if (connected) {
                getActivity().getSupportLoaderManager().restartLoader(G.TORRENT_LIST_TRAFFIC_LOADER_ID,
                    null, torrentTrafficLoaderCallbacks);
            }

            if (removed || added || (statusChanged && (
                   sharedPrefs.getString(G.PREF_BASE_SORT, "").equals(SortBy.STATUS.name())
                || sharedPrefs.getString(G.PREF_LIST_SORT_BY, "").equals(SortBy.STATUS.name())
            ))) {
                final Map<Long, Integer> idTopMap = new HashMap<>();
                final ListView listview = getListView();

                int firstVisible = listview.getFirstVisiblePosition();
                for (int i = 0; i < listview.getChildCount(); ++i) {
                    View child = listview.getChildAt(i);
                    int position = firstVisible + i;
                    long id = torrentAdapter.getItemId(position);
                    idTopMap.put(id, child.getTop());
                }

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
                                child.animate().setDuration(250);
                                ObjectAnimator anim = ObjectAnimator.ofFloat(child, View.TRANSLATION_Y, delta, 0);
                                anim.setDuration(250);
                                anim.start();

                                if (firstAnimation) {
                                    anim.addListener(new AnimatorListenerAdapter() {
                                        @Override public void onAnimationEnd(Animator animation) {
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


    public void setEmptyMessage(int stringId) {
        if (stringId == -1) {
            getView().findViewById(R.id.swipe_container).setVisibility(View.VISIBLE);
            getView().findViewById(R.id.empty_message).setVisibility(View.GONE);
            getView().findViewById(R.id.empty_button).setVisibility(View.GONE);
        } else {
            getView().findViewById(R.id.swipe_container).setVisibility(View.GONE);

            TextView message = (TextView) getView().findViewById(R.id.empty_message);
            message.setVisibility(View.VISIBLE);
            message.setText(Html.fromHtml(getString(stringId)));

            Button button = (Button) getView().findViewById(R.id.empty_button);
            if (stringId == R.string.no_profiles_empty_list) {
                button.setText(R.string.add_profile_option);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Intent intent = new Intent(getActivity(), SettingsActivity.class);

                        intent.putExtra(G.ARG_NEW_PROFILE, true);
                        startActivity(intent);
                        getActivity().overridePendingTransition(
                            R.anim.slide_in_top, android.R.anim.fade_out);
                    }
                });
                button.setVisibility(View.VISIBLE);
            } else {
                button.setVisibility(View.GONE);
            }
        }
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

        TransmissionProfile profile = ((TransmissionProfileInterface) getActivity()).getProfile();
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

            SearchView findView = (SearchView) MenuItemCompat.getActionView(item);

            if (!findQuery.equals("")) {
                findView.setQuery(findQuery, false);
            }

        } else {
            item.collapseActionView();
        }
    }

    private void toggleStatusBar() {
        View status = getView().findViewById(R.id.status_bar);
        toggleStatusBar(status);
    }

    private void toggleStatusBar(View status) {
        if (sharedPrefs.getBoolean(G.PREF_SHOW_STATUS, false)) {
            status.setVisibility(View.VISIBLE);
            status.setTranslationY(100);
            status.animate().alpha(1f).translationY(0).setStartDelay(500);
        } else {
            status.setVisibility(View.GONE);
            status.setAlpha(0.3f);
        }
    }

    private void toggleListItemChecked(boolean checked, final int position, final View typeChecked,
                                       final View typeIndicator, final View progress) {

        if (checked && ((TorrentListActivity) getActivity()).isDetailPanelVisible())  {
            checked = false;
        }

        TimeInterpolator interpolator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (checked) {
                interpolator = new PathInterpolator(0f, 0f, 0.2f, 1f);
            } else {
                interpolator = new PathInterpolator(0.4f, 0f, 1f, 1f);

            }
        } else {
            if (checked) {
                interpolator = new DecelerateInterpolator();
            } else {
                interpolator = new AccelerateInterpolator();
            }
        }

        int duration = getActivity().getResources().getInteger(android.R.integer.config_shortAnimTime);

        typeChecked.animate().cancel();
        checkAnimations.put(position, true);
        if (checked) {
            typeChecked.setScaleX(0f);
            typeChecked.setScaleY(0f);
            typeChecked.setVisibility(View.VISIBLE);
            typeChecked.animate().scaleX(1f).scaleY(1f).setInterpolator(
                interpolator
            ).setDuration(duration).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    checkAnimations.put(position, false);
                    typeIndicator.setVisibility(View.GONE);
                    progress.setVisibility(View.GONE);
                }
            });
        } else {
            typeIndicator.setVisibility(View.VISIBLE);
            progress.setVisibility(View.VISIBLE);
            typeChecked.animate().scaleX(0.3f).scaleY(0.3f).setInterpolator(
                interpolator
            ).setDuration(duration).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    checkAnimations.put(position, false);
                    typeChecked.setVisibility(View.GONE);
                }
            });
        }

    }

    private int setupSortMenu() {
        int visibleOptions = 0;
        SortBy selectedSort = SortBy.STATUS;
        if (sharedPrefs.contains(G.PREF_LIST_SORT_BY)) {
            try {
                selectedSort = SortBy.valueOf(
                    sharedPrefs.getString(G.PREF_LIST_SORT_BY, "")
                );
            } catch (Exception ignored) { }
        }

        for (SortBy sort : SortBy.values()) {
            boolean visible;
            MenuItem item;
            switch (sort) {
                case NAME:
                    visible = sharedPrefs.getBoolean(G.PREF_SORT_NAME, true);
                    item = menu.findItem(R.id.sort_name);
                    item.setVisible(visible);
                    if (sort == selectedSort) {
                        item.setChecked(true);
                    }

                    if (visible) {
                        ++visibleOptions;
                    }
                    break;
                case SIZE:
                    visible = sharedPrefs.getBoolean(G.PREF_SORT_SIZE, true);
                    item = menu.findItem(R.id.sort_size);
                    item.setVisible(visible);
                    if (sort == selectedSort) {
                        item.setChecked(true);
                    }

                    if (visible) {
                        ++visibleOptions;
                    }
                    break;
                case STATUS:
                    visible = sharedPrefs.getBoolean(G.PREF_SORT_STATUS, true);
                    item = menu.findItem(R.id.sort_status);
                    item.setVisible(visible);
                    if (sort == selectedSort) {
                        item.setChecked(true);
                    }

                    if (visible) {
                        ++visibleOptions;
                    }
                    break;
                case ACTIVITY:
                    visible = sharedPrefs.getBoolean(G.PREF_SORT_ACTIVITY, true);
                    item = menu.findItem(R.id.sort_activity);
                    item.setVisible(visible);
                    if (sort == selectedSort) {
                        item.setChecked(true);
                    }

                    if (visible) {
                        ++visibleOptions;
                    }
                    break;
                case AGE:
                    visible = sharedPrefs.getBoolean(G.PREF_SORT_AGE, true);
                    item = menu.findItem(R.id.sort_age);
                    item.setVisible(visible);
                    if (sort == selectedSort) {
                        item.setChecked(true);
                    }

                    if (visible) {
                        ++visibleOptions;
                    }
                    break;
                case PROGRESS:
                    visible = sharedPrefs.getBoolean(G.PREF_SORT_PROGRESS, true);
                    item = menu.findItem(R.id.sort_progress);
                    item.setVisible(visible);
                    if (sort == selectedSort) {
                        item.setChecked(true);
                    }

                    if (visible) {
                        ++visibleOptions;
                    }
                    break;
                case RATIO:
                    visible = sharedPrefs.getBoolean(G.PREF_SORT_RATIO, true);
                    item = menu.findItem(R.id.sort_ratio);
                    item.setVisible(visible);
                    if (sort == selectedSort) {
                        item.setChecked(true);
                    }

                    if (visible) {
                        ++visibleOptions;
                    }
                    break;
                case LOCATION:
                    visible = sharedPrefs.getBoolean(G.PREF_SORT_LOCATION, true);
                    item = menu.findItem(R.id.sort_location);
                    item.setVisible(visible);
                    if (sort == selectedSort) {
                        item.setChecked(true);
                    }

                    if (visible) {
                        ++visibleOptions;
                    }
                    break;
                case PEERS:
                    visible = sharedPrefs.getBoolean(G.PREF_SORT_PEERS, true);
                    item = menu.findItem(R.id.sort_peers);
                    item.setVisible(visible);
                    if (sort == selectedSort) {
                        item.setChecked(true);
                    }

                    if (visible) {
                        ++visibleOptions;
                    }
                    break;
                case RATE_DOWNLOAD:
                    visible = sharedPrefs.getBoolean(G.PREF_SORT_RATE_DOWNLOAD, true);
                    item = menu.findItem(R.id.sort_download_speed);
                    item.setVisible(visible);
                    if (sort == selectedSort) {
                        item.setChecked(true);
                    }

                    if (visible) {
                        ++visibleOptions;
                    }
                    break;
                case RATE_UPLOAD:
                    visible = sharedPrefs.getBoolean(G.PREF_SORT_RATE_UPLOAD, true);
                    item = menu.findItem(R.id.sort_upload_speed);
                    item.setVisible(visible);
                    if (sort == selectedSort) {
                        item.setChecked(true);
                    }

                    if (visible) {
                        ++visibleOptions;
                    }
                    break;
                case QUEUE:
                    visible = sharedPrefs.getBoolean(G.PREF_SORT_QUEUE, true);
                    item = menu.findItem(R.id.sort_queue);
                    item.setVisible(visible);
                    if (sort == selectedSort) {
                        item.setChecked(true);
                    }

                    if (visible) {
                        ++visibleOptions;
                    }
                    break;
            }
        }

        MenuItem order = menu.findItem(R.id.sort_order);
        if (visibleOptions > 0) {
            order.setVisible(true);

            SortOrder selectedOrder = SortOrder.DESCENDING;
            if (sharedPrefs.contains(G.PREF_LIST_SORT_ORDER)) {
                try {
                    selectedOrder = SortOrder.valueOf(
                        sharedPrefs.getString(G.PREF_LIST_SORT_ORDER, "")
                    );
                } catch (Exception ignored) { }
            }

            order.setChecked(selectedOrder == SortOrder.DESCENDING);
        } else {
            order.setVisible(false);
        }

        return visibleOptions;
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
                        TransmissionProfileInterface context =
                            (TransmissionProfileInterface) getActivity();
                        if (context == null || context.getProfile() == null) {
                            return null;
                        }

                        readDataSource.open();

                        return readDataSource.getTorrentCursor(context.getProfile().getId(),
                            PreferenceManager.getDefaultSharedPreferences(getActivity()));
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

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override public void bindView(final View view, Context context, Cursor cursor) {
            final int id = Torrent.getId(cursor);

            TextView name = (TextView) view.findViewById(R.id.name);

            TextView traffic = (TextView) view.findViewById(R.id.traffic);
            ProgressBar progress = (ProgressBar) view.findViewById(R.id.progress);
            TextView status = (TextView) view.findViewById(R.id.status);
            TextView errorText = (TextView) view.findViewById(R.id.error_text);
            View typeDirectory = view.findViewById(R.id.type_directory);
            View typeChecked = view.findViewById(R.id.type_checked);

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

            final int position = cursor.getPosition();
            progress.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (actionMode == null) {
                        if (!((TorrentListActivity) getActivity()).isDetailPanelVisible()) {
                            getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
                            setActivatedPosition(position);
                        } else {
                            onListItemClick(getListView(), view, position, id);
                        }
                    } else {
                        SparseBooleanArray checkedItems = getListView().getCheckedItemPositions();
                        getListView().setItemChecked(position, !checkedItems.get(position));
                    }
                }
            });

            traffic.setText(Html.fromHtml(Torrent.getTrafficText(cursor)));
            status.setText(Html.fromHtml(Torrent.getStatusText(cursor)));

            int torrentStatus = Torrent.getStatus(cursor);
            boolean enabled = Torrent.isActive(torrentStatus);

            name.setEnabled(enabled);
            traffic.setEnabled(enabled);
            status.setEnabled(enabled);
            errorText.setEnabled(enabled);
            progress.setAlpha(enabled ? 1f : 0.5f);
            typeDirectory.setAlpha(enabled ? 0.6f : 0.3f);

            if (Torrent.getError(cursor) == Torrent.Error.OK) {
                errorText.setVisibility(View.GONE);
            } else {
                errorText.setVisibility(View.VISIBLE);
                errorText.setText(Torrent.getErrorString(cursor));
            }

            SparseBooleanArray checkedItems = getListView().getCheckedItemPositions();

            if (checkedItems != null && checkedItems.get(position)) {
                if (!checkAnimations.get(position) && !((TorrentListActivity) getActivity()).isDetailPanelVisible()) {
                    typeDirectory.setVisibility(View.GONE);
                    progress.setVisibility(View.GONE);
                    typeChecked.setVisibility(View.VISIBLE);
                    typeChecked.setScaleX(1f);
                    typeChecked.setScaleY(1f);
                }
            } else {
                if (!checkAnimations.get(position)) {
                    typeDirectory.setVisibility(View.VISIBLE);
                    progress.setVisibility(View.VISIBLE);
                    typeChecked.setVisibility(View.GONE);
                }

                if ("inode/directory".equals(Torrent.getMimeType(cursor))) {
                    typeDirectory.setVisibility(View.VISIBLE);
                } else {
                    typeDirectory.setVisibility(View.GONE);
                }
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

            if (!isAdded()) {
                return oldCursor;
            }

            if (newCursor.getCount() == 0) {
                if (sharedPrefs.getString(G.PREF_LIST_SEARCH, "").equals("")
                    && sharedPrefs.getString(G.PREF_LIST_DIRECTORY, "").equals("")
                    && sharedPrefs.getString(G.PREF_LIST_TRACKER, "").equals("")
                    && sharedPrefs.getString(G.PREF_LIST_FILTER, FilterBy.ALL.name()).equals(FilterBy.ALL.name())) {
                    setEmptyMessage(R.string.no_torrents_empty_list);
                } else {
                    setEmptyMessage(R.string.no_filtered_torrents_empty_list);
                }
            } else {
                setEmptyMessage(-1);

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

    private class SessionReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            if (getActivity() == null || menu == null) {
                return;
            }

            if (intent.getBooleanExtra(G.ARG_SESSION_VALID, false)) {
                menu.findItem(R.id.sort).setVisible(true);
                menu.findItem(R.id.find).setVisible(true);
            } else {
                menu.findItem(R.id.sort).setVisible(false);
                menu.findItem(R.id.find).setVisible(false);
            }
        }
    }
}
