package org.sugr.gearshift;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.ListFragment;
import android.support.v4.content.Loader;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.SparseBooleanArray;
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
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;

import org.sugr.gearshift.G.FilterBy;
import org.sugr.gearshift.G.SortBy;
import org.sugr.gearshift.G.SortOrder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
    private static final String STATE_TORRENTS = "torrents";

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

    private TorrentListAdapter mTorrentListAdapter;

    private boolean mScrollToTop = false;

    private boolean mFindShown = false;
    private String mFindQuery;

    private SharedPreferences mSharedPrefs;

    /**
     * A callback interface that all activities containing this fragment must
     * implement. This mechanism allows activities to be notified of item
     * selections.
     */
    public interface Callbacks {
        /**
         * Callback for when an item has been selected.
         */
        public void onItemSelected(Torrent torrent);
    }

    /**
     * A dummy implementation of the {@link Callbacks} interface that does
     * nothing. Used only when this fragment is not attached to an activity.
     */
    private static Callbacks sDummyCallbacks = new Callbacks() {
        @Override
        public void onItemSelected(Torrent torrent) {
        }
    };

    private MultiChoiceModeListener mListChoiceListener = new MultiChoiceModeListener() {
        private HashSet<Integer> mSelectedTorrentIds;
        private boolean hasQueued = false;

        @Override
        public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
            final Loader<TransmissionData> loader = getActivity().getSupportLoaderManager()
                    .getLoader(G.TORRENTS_LOADER_ID);

            if (loader == null)
                return false;

            final int[] ids = new int[mSelectedTorrentIds.size()];
            int index = 0;
            for (Integer id : mSelectedTorrentIds)
                ids[index++] = id;

            AlertDialog.Builder builder;
            switch (item.getItemId()) {
                case R.id.select_all:
                    ListView v = getListView();
                    for (int i = 0; i < mTorrentListAdapter.getCount(); i++) {
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

            mSelectedTorrentIds = new HashSet<Integer>();
            mActionMode = mode;
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            G.logD("Destroying context menu");
            mActionMode = null;
            mSelectedTorrentIds = null;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode,
                                              int position, long id, boolean checked) {

            if (checked)
                mSelectedTorrentIds.add(mTorrentListAdapter.getItem(position).getId());
            else
                mSelectedTorrentIds.remove(mTorrentListAdapter.getItem(position).getId());

            ArrayList<Torrent> torrents = ((TransmissionSessionInterface) getActivity()).getTorrents();
            boolean hasPaused = false;
            boolean hasRunning = false;

            hasQueued = false;
            for (Torrent t : torrents) {
                if (mSelectedTorrentIds.contains(t.getId())) {
                    if (t.getStatus() == Torrent.Status.STOPPED) {
                        hasPaused = true;
                    } else if (!t.isActive()) {
                        hasQueued = true;
                    } else {
                        hasRunning = true;
                    }
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
                        mTorrentListAdapter.resetBaseSort();
                    } else if (key.equals(G.PREF_PROFILES)) {
                        ((TransmissionSessionInterface) getActivity()).setRefreshing(true);
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
            G.logD("Search query " + mFindQuery);
            setListFilter(mFindQuery);
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
        hasOptionsMenu();

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
                mFindShown = savedInstanceState.getBoolean(STATE_FIND_SHOWN);
                if (savedInstanceState.containsKey(STATE_FIND_QUERY)) {
                    mFindQuery = savedInstanceState.getString(STATE_FIND_QUERY);
                }
            }

            ((TransmissionSessionInterface) getActivity()).setRefreshing(false);
        }
        if (!mFindShown) {
            Editor e = mSharedPrefs.edit();
            e.putString(G.PREF_LIST_SEARCH, "");
            e.apply();
        }

        mTorrentListAdapter = new TorrentListAdapter(getActivity());
        setListAdapter(mTorrentListAdapter);
        if (savedInstanceState != null &&
                (savedInstanceState.containsKey(STATE_TORRENTS) || savedInstanceState.containsKey(STATE_ACTIVATED_POSITION))) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    if (savedInstanceState.containsKey(STATE_TORRENTS)) {
                        mTorrentListAdapter.setNotifyOnChange(false);
                        ArrayList<Torrent> torrents = savedInstanceState.getParcelableArrayList(STATE_TORRENTS);
                        ((TransmissionSessionInterface) getActivity()).setTorrents(torrents);
                        mTorrentListAdapter.clear();
                        mTorrentListAdapter.addAll(torrents);
                        mTorrentListAdapter.repeatFilter();
                    }
                    if (savedInstanceState.containsKey(STATE_ACTIVATED_POSITION)) {
                        setActivatedPosition(savedInstanceState.getInt(STATE_ACTIVATED_POSITION));
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

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        super.onListItemClick(listView, view, position, id);

        if (mActionMode == null)
            listView.setChoiceMode(mChoiceMode);

        // Notify the active callbacks interface (the activity, if the
        // fragment is attached to one) that an item has been selected.
        mCallbacks.onItemSelected(mTorrentListAdapter.getItem(position));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mActivatedPosition != ListView.INVALID_POSITION) {
            // Serialize and persist the activated item position.
            outState.putInt(STATE_ACTIVATED_POSITION, mActivatedPosition);
        }
        outState.putBoolean(STATE_FIND_SHOWN, mFindShown);
        outState.putString(STATE_FIND_QUERY, mFindQuery);
        outState.putParcelableArrayList(STATE_TORRENTS, mTorrentListAdapter.getUnfilteredItems());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_torrent_list, container, false);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, MenuInflater inflater) {
        if (mFindShown) {
            boolean detailVisible = ((TorrentListActivity) getActivity()).isDetailPanelShown();
            MenuItem item = menu.add(R.string.find);
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);

            mFindQuery = mSharedPrefs.getString(G.PREF_LIST_SEARCH, "");

            if (detailVisible) {
                item.setActionView(R.layout.action_search_query);
                TextView query = ((TextView) item.getActionView().findViewById(R.id.action_search_query));
                query.setText(mFindQuery);
            } else {
                SearchView findView = new SearchView(
                        getActivity().getActionBar().getThemedContext());
                findView.setQueryHint(getActivity().getString(R.string.filter));
                findView.setIconified(false);

                item.setActionView(findView);
            }
            item.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override public boolean onMenuItemActionExpand(MenuItem item) {
                    return true;
                }

                @Override public boolean onMenuItemActionCollapse(MenuItem item) {
                    mFindQuery = null;
                    mFindShown = false;
                    setListFilter((String) null);
                    return true;
                }
            });
            item.expandActionView();

            if (!detailVisible) {
                SearchView findView = (SearchView) item.getActionView();

                findView.setQuery(mSharedPrefs.getString(G.PREF_LIST_SEARCH, ""), false);
                findView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override public boolean onQueryTextSubmit(String query) {
                        return false;
                    }

                    @Override public boolean onQueryTextChange(String newText) {
                        mFindQuery = newText;
                        mFindHandler.removeCallbacks(mFindRunnable);
                        mFindHandler.postDelayed(mFindRunnable, 500);
                        return true;
                    }
                });
            }
        }
    }

    @Override
    public void notifyTorrentListChanged(List<Torrent> torrents, int error, boolean added, boolean removed,
                                         boolean statusChanged, boolean metadataNeeded) {
        if (error == -1) {
            mTorrentListAdapter.clear();
            return;
        }

        boolean filtered = false;

        if (torrents.size() > 0 || error > 0 || mTorrentListAdapter.getUnfilteredCount() > 0) {
             /* The notifyDataSetChanged method sets this to true */
            mTorrentListAdapter.setNotifyOnChange(false);
            boolean notifyChange = true;
            if (error == 0) {
                if (added || removed || statusChanged || metadataNeeded
                    || mTorrentListAdapter.getUnfilteredCount() == 0) {
                    notifyChange = false;
                    mTorrentListAdapter.clear();
                    mTorrentListAdapter.addAll(torrents);
                    mTorrentListAdapter.repeatFilter();
                    filtered = true;
                }
            } else {
                if (error != TransmissionData.Errors.DUPLICATE_TORRENT
                    && error != TransmissionData.Errors.INVALID_TORRENT
                    && mActionMode != null) {

                    mActionMode.finish();
                    mActionMode = null;
                }
            }
            if (torrents.size() > 0) {
                if (notifyChange) {
                    mTorrentListAdapter.notifyDataSetChanged();
                }
            } else {
                mTorrentListAdapter.notifyDataSetInvalidated();
            }

            if (error == 0) {
                updateStatus(torrents, ((TransmissionSessionInterface) getActivity()).getSession());
                FragmentManager manager = getActivity().getSupportFragmentManager();
                TorrentListMenuFragment menu = (TorrentListMenuFragment) manager.findFragmentById(R.id.torrent_list_menu);

                if (menu != null) {
                    menu.notifyTorrentListChanged(torrents, error, added, removed,
                        statusChanged, metadataNeeded);
                }

                if (!filtered) {
                    TorrentDetailFragment detail = (TorrentDetailFragment) manager.findFragmentByTag(
                        G.DETAIL_FRAGMENT_TAG);
                    if (detail != null) {
                        detail.notifyTorrentListChanged(torrents, error, added, removed,
                            statusChanged, metadataNeeded);
                    }
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
        mTorrentListAdapter.filter(query);
        mScrollToTop = true;
    }

    public void setListFilter(FilterBy e) {
        mTorrentListAdapter.filter(e);
        mScrollToTop = true;
    }

    public void setListFilter(SortBy e) {
        mTorrentListAdapter.filter(e);
        mScrollToTop = true;
    }

    public void setListFilter(SortOrder e) {
        mTorrentListAdapter.filter(e);
        mScrollToTop = true;
    }

    public void setListDirectoryFilter(String e) {
        mTorrentListAdapter.filterDirectory(e);
        mScrollToTop = true;
    }

    public void setListTrackerFilter(String e) {
        mTorrentListAdapter.filterTracker(e);
        mScrollToTop = true;
    }

    public void showFind() {
        mFindShown = true;
        mFindQuery = null;
        if (mActionMode != null) {
            mActionMode.finish();
        }
        getActivity().invalidateOptionsMenu();
    }

    public boolean isFindShown() {
        return mFindShown;
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

        return true;
    }

    private void updateStatus(List<Torrent> torrents, TransmissionSession session) {
        boolean showStatus = mSharedPrefs.getBoolean(G.PREF_SHOW_STATUS, false);

        TextView status = (TextView) getView().findViewById(R.id.status_bar_text);
        if (showStatus) {
            long down = 0, up = 0, free = 0;
            String limitDown = "", limitUp = "";

            for (Torrent t : torrents) {
                down += t.getRateDownload();
                up += t.getRateUpload();
            }

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
                    G.readableFileSize(down),
                    limitDown,
                    G.readableFileSize(up),
                    limitUp,
                    free == 0 ? getString(R.string.unknown) : G.readableFileSize(free)
            )));
        }
    }

    private class TorrentListAdapter extends ArrayAdapter<Torrent> {
        private final Object mLock = new Object();
        private ArrayList<Torrent> mObjects = new ArrayList<Torrent>();
        private ArrayList<Torrent> mOriginalValues;
        private TorrentFilter mFilter;
        private CharSequence mCurrentConstraint;
        private TorrentComparator mTorrentComparator = new TorrentComparator();
        private FilterBy mFilterBy = FilterBy.ALL;
        private SortBy mSortBy = mTorrentComparator.getSortBy();
        private SortBy mBaseSort = mTorrentComparator.getBaseSort();
        private SortOrder mSortOrder = mTorrentComparator.getSortOrder();
        private SortOrder mBaseSortOrder = mTorrentComparator.getBaseSortOrder();
        private String mDirectory;
        private String mTracker;
        private SparseBooleanArray mTorrentAdded = new SparseBooleanArray();

        public TorrentListAdapter(Context context) {
            super(context, R.layout.torrent_list_item, R.id.name);

            /*
            if (mSharedPrefs.contains(G.PREF_LIST_SEARCH)) {
                mCurrentConstraint = mSharedPrefs.getString(
                        G.PREF_LIST_SEARCH, null);
            }
            */
            if (mSharedPrefs.contains(G.PREF_LIST_FILTER)) {
                try {
                    mFilterBy = FilterBy.valueOf(
                        mSharedPrefs.getString(G.PREF_LIST_FILTER, "")
                    );
                } catch (Exception e) {
                    mFilterBy = FilterBy.ALL;
                }
            }
            if (mSharedPrefs.contains(G.PREF_LIST_DIRECTORY)) {
                mDirectory = mSharedPrefs.getString(G.PREF_LIST_DIRECTORY, null);
            }
            if (mSharedPrefs.contains(G.PREF_LIST_TRACKER)) {
                mTracker = mSharedPrefs.getString(G.PREF_LIST_TRACKER, null);
            }
            if (mSharedPrefs.contains(G.PREF_LIST_SORT_BY)) {
                try {
                    mSortBy = SortBy.valueOf(
                        mSharedPrefs.getString(G.PREF_LIST_SORT_BY, "")
                    );
                } catch (Exception e) {
                    mSortBy = mTorrentComparator.getSortBy();
                }
            }
            if (mSharedPrefs.contains(G.PREF_LIST_SORT_ORDER)) {
                try {
                    mSortOrder = SortOrder.valueOf(
                        mSharedPrefs.getString(G.PREF_LIST_SORT_ORDER, "")
                    );
                } catch (Exception e) {
                    mSortOrder = mTorrentComparator.getSortOrder();
                }
            }
            mBaseSort = getBaseSort();
            mBaseSortOrder = getBaseSortOrder();

            mTorrentComparator.setSortingMethod(mSortBy, mSortOrder);
            mTorrentComparator.setBaseSort(mBaseSort, mBaseSortOrder);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView;
            Torrent torrent = getItem(position);

            if (rowView == null) {
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                rowView = vi.inflate(R.layout.torrent_list_item, parent, false);
            }

            TextView name = (TextView) rowView.findViewById(R.id.name);

            TextView traffic = (TextView) rowView.findViewById(R.id.traffic);
            ProgressBar progress = (ProgressBar) rowView.findViewById(R.id.progress);
            TextView status = (TextView) rowView.findViewById(R.id.status);
            TextView errorText = (TextView) rowView.findViewById(R.id.error_text);

            if (mFindQuery != null && !mFindQuery.equals("")) {
                if (torrent.getFilteredName() == null) {
                    name.setText(torrent.getName());
                } else {
                    name.setText(torrent.getFilteredName(), TextView.BufferType.SPANNABLE);
                }
            } else {
                name.setText(torrent.getName());
            }

            if (torrent.getMetadataPercentComplete() < 1) {
                progress.setSecondaryProgress((int) (torrent.getMetadataPercentComplete() * 100));
                progress.setProgress(0);
            } else if (torrent.getPercentDone() < 1) {
                progress.setSecondaryProgress((int) (torrent.getPercentDone() * 100));
                progress.setProgress(0);
            } else {
                progress.setSecondaryProgress(100);

                float limit = torrent.getActiveSeedRatioLimit();
                float current = torrent.getUploadRatio();

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

            traffic.setText(torrent.getTrafficText());
            status.setText(torrent.getStatusText());

            boolean enabled = torrent.isActive();
            name.setEnabled(enabled);
            traffic.setEnabled(enabled);
            status.setEnabled(enabled);
            errorText.setEnabled(enabled);

            if (torrent.getError() == Torrent.Error.OK) {
                errorText.setVisibility(View.GONE);
            } else {
                errorText.setVisibility(View.VISIBLE);
                errorText.setText(torrent.getErrorString());
            }



            if (!mTorrentAdded.get(torrent.getId(), false)) {
                rowView.setTranslationY(100);
                rowView.setAlpha((float) 0.3);
                rowView.setRotationX(10);
                rowView.animate().setDuration(300).translationY(0).alpha(1).rotationX(0).start();
                mTorrentAdded.append(torrent.getId(), true);
            }

            return rowView;
        }

        @Override
        public void addAll(Collection<? extends Torrent> collection) {
            synchronized (mLock) {
                if (mOriginalValues != null) {
                    mOriginalValues.addAll(collection);
                } else {
                    mObjects.addAll(collection);
                }
                super.addAll(collection);
            }
        }

        @Override
        public void clear() {
            synchronized (mLock) {
                if (mOriginalValues != null) {
                    mOriginalValues = null;
                }
                if (mObjects != null) {
                    mObjects.clear();
                }
                super.clear();
            }
        }

        @Override
        public int getCount() {
            synchronized(mLock) {
                return mObjects == null ? 0 : mObjects.size();
            }
        }

        public int getUnfilteredCount() {
            synchronized(mLock) {
                if (mOriginalValues != null) {
                    return mOriginalValues.size();
                } else {
                    return mObjects.size();
                }
            }
        }

        public ArrayList<Torrent> getUnfilteredItems() {
            synchronized(mLock) {
                if (mOriginalValues != null) {
                    return mOriginalValues;
                } else {
                    return mObjects;
                }
            }
        }

        @Override
        public Torrent getItem(int position) {
            return mObjects.get(position);
        }

        @Override
        public int getPosition(Torrent item) {
            return mObjects.indexOf(item);
        }

        @Override
        public Filter getFilter() {
            if (mFilter == null)
                mFilter = new TorrentFilter();

            return mFilter;
        }

        public void filter(String query) {
            if (query == null && mCurrentConstraint == null
                    || query != null && mCurrentConstraint == null && query.equals("")
                    || query != null && query.equals(mCurrentConstraint))
                return;

            mCurrentConstraint = query;
            applyFilter(query, G.PREF_LIST_SEARCH, false);
        }

        public void filter(FilterBy by) {
            if (by == mFilterBy)
                return;

            mFilterBy = by;
            applyFilter(by.name(), G.PREF_LIST_FILTER);
        }

        public void filter(SortBy by) {
            if (by == mSortBy)
                return;

            mSortBy = by;
            applyFilter(by.name(), G.PREF_LIST_SORT_BY);
        }

        public void filter(SortOrder order) {
            if (order == mSortOrder)
                return;

            mSortOrder = order;
            mBaseSortOrder = getBaseSortOrder();
            applyFilter(order.name(), G.PREF_LIST_SORT_ORDER);
        }

        public void filterDirectory(String directory) {
            if (directory == null && mDirectory == null
                    || directory != null && directory.equals(mDirectory))
                return;

            mDirectory = directory;
            applyFilter(directory, G.PREF_LIST_DIRECTORY);
        }

        public void filterTracker(String tracker) {
            if (tracker == null && mTracker == null
                    || tracker != null && tracker.equals(mTracker))
                return;

            mTracker = tracker;
            applyFilter(tracker, G.PREF_LIST_TRACKER);
        }

        public void repeatFilter() {
            if (((TransmissionSessionInterface) getActivity()).getProfile() != null) {
                getFilter().filter(mCurrentConstraint, null);
            }
        }

        public void resetBaseSort() {
            mBaseSort = getBaseSort();
            mBaseSortOrder = getBaseSortOrder();
            mTorrentComparator.setBaseSort(mBaseSort, mBaseSortOrder);
            repeatFilter();
        }

        private SortBy getBaseSort() {
            if (mSharedPrefs.contains(G.PREF_BASE_SORT)) {
                try {
                    return SortBy.valueOf(
                            mSharedPrefs.getString(G.PREF_BASE_SORT, "")
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return mTorrentComparator.getBaseSort();
        }

        private SortOrder getBaseSortOrder() {
            if (mSharedPrefs.contains(G.PREF_BASE_SORT_ORDER)) {
                String pref = mSharedPrefs.getString(G.PREF_BASE_SORT_ORDER, null);

                if (pref != null) {
                    if (pref.equals("ASCENDING")) {
                        return SortOrder.ASCENDING;
                    } else if (pref.equals("DESCENDING")) {
                        return SortOrder.DESCENDING;
                    } else if (pref.equals("PRIMARY")) {
                        return mSortOrder;
                    } else if (pref.equals("REVERSE")) {
                        return mSortOrder == SortOrder.ASCENDING
                                ? SortOrder.DESCENDING
                                : SortOrder.ASCENDING;
                    }
                }
            }
            return mTorrentComparator.getBaseSortOrder();
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

            mTorrentComparator.setSortingMethod(mSortBy, mSortOrder);
            mTorrentComparator.setBaseSort(mBaseSort, mBaseSortOrder);
            repeatFilter();
            if (animate) {
                mTorrentAdded = new SparseBooleanArray();
            }
        }

        private void applyFilter(String value, String pref) {
            applyFilter(value, pref, true);
        }

        private class TorrentFilter extends Filter {
            @Override
            protected FilterResults performFiltering(CharSequence prefix) {
                FilterResults results = new FilterResults();
                ArrayList<Torrent> resultList;

                if (mOriginalValues == null) {
                    synchronized (mLock) {
                        mOriginalValues = new ArrayList<Torrent>(mObjects);
                    }
                }

                if (prefix == null) {
                    prefix = "";
                }

                if (prefix.length() == 0 && mFilterBy == FilterBy.ALL
                        && mDirectory == null && mTracker == null) {
                    ArrayList<Torrent> list;
                    synchronized (mLock) {
                        list = new ArrayList<Torrent>(mOriginalValues);
                    }

                    resultList = list;
                } else {
                    ArrayList<Torrent> values;
                    synchronized (mLock) {
                        values = new ArrayList<Torrent>(mOriginalValues);
                    }

                    final ArrayList<Torrent> newValues = new ArrayList<Torrent>();
                    String prefixString = prefix.toString().toLowerCase(Locale.getDefault());
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

                    for (final Torrent torrent : values) {
                        if (mFilterBy == FilterBy.DOWNLOADING) {
                            if (torrent.getStatus() != Torrent.Status.DOWNLOADING)
                                continue;
                        } else if (mFilterBy == FilterBy.SEEDING) {
                            if (torrent.getStatus() != Torrent.Status.SEEDING)
                                continue;
                        } else if (mFilterBy == FilterBy.PAUSED) {
                            if (torrent.getStatus() != Torrent.Status.STOPPED)
                                continue;
                        } else if (mFilterBy == FilterBy.COMPLETE) {
                            if (torrent.getPercentDone() != 1)
                                continue;
                        } else if (mFilterBy == FilterBy.INCOMPLETE) {
                            if (torrent.getPercentDone() >= 1)
                                continue;
                        } else if (mFilterBy == FilterBy.ACTIVE) {
                            if (torrent.isStalled() || torrent.isFinished() || (
                                    torrent.getStatus() != Torrent.Status.DOWNLOADING
                                            && torrent.getStatus() != Torrent.Status.SEEDING
                            ))
                                continue;
                        } else if (mFilterBy == FilterBy.CHECKING) {
                            if (torrent.getStatus() != Torrent.Status.CHECKING)
                                continue;
                        }

                        if (mDirectory != null) {
                            if (torrent.getDownloadDir() == null
                                    || !torrent.getDownloadDir().equals(mDirectory))
                                continue;
                        }

                        if (mTracker != null) {
                            Torrent.Tracker[] trackers = torrent.getTrackers();
                            boolean hasMatch = false;
                            if (trackers != null && trackers.length > 0) {
                                for (Torrent.Tracker t : trackers) {
                                    try {
                                        URI uri = new URI(t.getAnnounce());
                                        if (uri.getHost().equals(mTracker)) {
                                            hasMatch = true;
                                            break;
                                        }
                                    } catch (URISyntaxException ignored) {
                                    }
                                }
                            }
                            if (!hasMatch) {
                                continue;
                            }
                        }

                        if (prefix.length() == 0) {
                            newValues.add(torrent);
                        } else if (prefix.length() > 0) {
                            Matcher m = prefixPattern.matcher(
                                    torrent.getName().toLowerCase(Locale.getDefault()));

                            if (m.find()) {
                                SpannableString spannedName = new SpannableString(torrent.getName());
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
                                torrent.setFilteredName(spannedName);
                                newValues.add(torrent);
                            }
                        }
                    }

                    resultList = newValues;
                }

                Collections.sort(resultList, mTorrentComparator);

                results.values = resultList;
                results.count = resultList.size();

                return results;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                mObjects = (ArrayList<Torrent>) results.values;
                TransmissionSessionInterface context = (TransmissionSessionInterface) getActivity();
                if (context == null) {
                    return;
                }
                if (results.count > 0) {
                    context.setTorrents((ArrayList<Torrent>) results.values);
                    FragmentManager manager = getActivity().getSupportFragmentManager();
                    TorrentDetailFragment detail = (TorrentDetailFragment) manager.findFragmentByTag(
                            G.DETAIL_FRAGMENT_TAG);
                    if (detail != null) {
                        detail.notifyTorrentListChanged(getUnfilteredItems(), 0, true,
                            true, false, false);
                    }
                    notifyDataSetChanged();
                } else {
                    if (mTorrentListAdapter.getUnfilteredCount() == 0) {
                        setEmptyText(R.string.no_torrents_empty_list);
                    } else if (mTorrentListAdapter.getCount() == 0) {
                        ((TransmissionSessionInterface) getActivity())
                            .setTorrents(null);
                        setEmptyText(R.string.no_filtered_torrents_empty_list);
                    }
                    notifyDataSetInvalidated();
                }
                if (mScrollToTop) {
                    mScrollToTop = false;
                    getListView().setSelectionAfterHeaderView();
                }
            }
        }
    }
}
