package org.sugr.gearshift;

import android.app.ActionBar;
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
import android.support.v4.app.LoaderManager.LoaderCallbacks;
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
import android.widget.Toast;

import org.sugr.gearshift.G.FilterBy;
import org.sugr.gearshift.G.SortBy;
import org.sugr.gearshift.G.SortOrder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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
public class TorrentListFragment extends ListFragment {
    /**
     * The serialization (saved instance state) Bundle key representing the
     * activated item position. Only used on tablets.
     */
    private static final String STATE_ACTIVATED_POSITION = "activated_position";
    private static final String STATE_FIND_SHOWN = "find_shown";
    private static final String STATE_FIND_QUERY = "find_query";
    private static final String STATE_CURRENT_PROFILE = "current_profile";
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

    private boolean mAltSpeed = false;

    private boolean mRefreshing = true;

    private ActionMode mActionMode;

    private int mChoiceMode = ListView.CHOICE_MODE_NONE;

    private TransmissionProfileListAdapter mProfileAdapter;
    private TorrentListAdapter mTorrentListAdapter;

    private TransmissionProfile mProfile;
    private TransmissionSession mSession;
//    private TransmissionSessionStats mSessionStats;

    private boolean mScrollToTop = false;

    private boolean mFindShown = false;
    private String mFindQuery;

    private SharedPreferences mSharedPrefs;

    private boolean mPreventRefreshIndicator;

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

    private LoaderCallbacks<TransmissionProfile[]> mProfileLoaderCallbacks = new LoaderCallbacks<TransmissionProfile[]>() {
        @Override
        public android.support.v4.content.Loader<TransmissionProfile[]> onCreateLoader(
                int id, Bundle args) {
            return new TransmissionProfileSupportLoader(getActivity());
        }

        @Override
        public void onLoadFinished(
                android.support.v4.content.Loader<TransmissionProfile[]> loader,
                TransmissionProfile[] profiles) {

            TransmissionProfile oldProfile = mProfile;

            mProfile = null;
            mProfileAdapter.clear();
            if (profiles.length > 0) {
                mProfileAdapter.addAll(profiles);
            } else {
                mProfileAdapter.add(TransmissionProfileListAdapter.EMPTY_PROFILE);
                setEmptyText(R.string.no_profiles_empty_list);
                mRefreshing = false;
                getActivity().invalidateOptionsMenu();
            }

            String currentId = TransmissionProfile.getCurrentProfileId(getActivity());
            int index = 0;
            for (TransmissionProfile prof : profiles) {
                if (prof.getId().equals(currentId)) {
                    ActionBar actionBar = getActivity().getActionBar();
                    if (actionBar != null)
                        actionBar.setSelectedNavigationItem(index);
                    mProfile = prof;
                    break;
                }
                index++;
            }

            if (mProfile == null && profiles.length > 0) {
                mProfile = profiles[0];
            }

            ((TransmissionSessionInterface) getActivity()).setProfile(mProfile);
            if (mProfile == null) {
                getActivity().getSupportLoaderManager().destroyLoader(G.TORRENTS_LOADER_ID);
            } else {
                /* The torrents might be loaded before the navigation
                 * callback fires, which will cause the refresh indicator to
                 * appear until the next server request */
                mPreventRefreshIndicator = true;

                if (oldProfile != null && oldProfile.getId().equals(mProfile.getId())) {
                    getActivity().getSupportLoaderManager().initLoader(
                        G.TORRENTS_LOADER_ID,
                        null, mTorrentLoaderCallbacks);
                } else {
                    getActivity().getSupportLoaderManager().restartLoader(
                        G.TORRENTS_LOADER_ID,
                        null, mTorrentLoaderCallbacks);
                }
            }
        }

        @Override
        public void onLoaderReset(
                android.support.v4.content.Loader<TransmissionProfile[]> loader) {
            mProfileAdapter.clear();
        }

    };

    private LoaderCallbacks<TransmissionData> mTorrentLoaderCallbacks = new LoaderCallbacks<TransmissionData>() {

        @Override
        public android.support.v4.content.Loader<TransmissionData> onCreateLoader(
                int id, Bundle args) {
            G.logD("Starting the torrents loader with profile " + mProfile);
            if (mProfile == null) return null;

            return new TransmissionDataLoader(getActivity(), mProfile);
        }

        @Override
        public void onLoadFinished(
                android.support.v4.content.Loader<TransmissionData> loader,
                TransmissionData data) {

            boolean invalidateMenu = false;

            G.logD("Data loaded: " + data.torrents.size() + " torrents, error: " + data.error + " , removed: " + data.hasRemoved + ", added: " + data.hasAdded + ", changed: " + data.hasStatusChanged + ", metadata: " + data.hasMetadataNeeded);
            mSession = data.session;
            ((TransmissionSessionInterface) getActivity()).setSession(data.session);

           /* if (data.stats != null)
                mSessionStats = data.stats;*/

            if (mSession != null && mAltSpeed != mSession.isAltSpeedLimitEnabled()) {
                mAltSpeed = mSession.isAltSpeedLimitEnabled();
                invalidateMenu = true;
            }

            boolean filtered = false;
            View error = getView().findViewById(R.id.fatal_error_layer);
            if (data.error == 0 && error.getVisibility() != View.GONE) {
                error.setVisibility(View.GONE);
                ((TransmissionSessionInterface) getActivity()).setProfile(mProfile);
            }
            if (data.torrents.size() > 0 || data.error > 0
                    || mTorrentListAdapter.getUnfilteredCount() > 0) {

                /* The notifyDataSetChanged method sets this to true */
                mTorrentListAdapter.setNotifyOnChange(false);
                boolean notifyChange = true;
                if (data.error == 0) {
                    if (data.hasRemoved || data.hasAdded
                            || data.hasStatusChanged || data.hasMetadataNeeded
                            || mTorrentListAdapter.getUnfilteredCount() == 0) {
                        notifyChange = false;
                        if (data.hasRemoved || data.hasAdded) {
                            ((TransmissionSessionInterface) getActivity()).setTorrents(data.torrents);
                        }
                        mTorrentListAdapter.clear();
                        mTorrentListAdapter.addAll(data.torrents);
                        mTorrentListAdapter.repeatFilter();
                        filtered = true;
                    }
                } else {
                    if (data.error == TransmissionData.Errors.DUPLICATE_TORRENT) {
                        Toast.makeText(getActivity(), R.string.duplicate_torrent, Toast.LENGTH_SHORT).show();
                    } else if (data.error == TransmissionData.Errors.INVALID_TORRENT) {
                        Toast.makeText(getActivity(), R.string.invalid_torrent, Toast.LENGTH_SHORT).show();
                    } else {
                        error.setVisibility(View.VISIBLE);
                        TextView text = (TextView) getView().findViewById(R.id.transmission_error);
                        ((TransmissionSessionInterface) getActivity()).setProfile(null);
                        if (mActionMode != null) {
                            mActionMode.finish();
                            mActionMode = null;
                        }

                        if (data.error == TransmissionData.Errors.NO_CONNECTIVITY) {
                            text.setText(Html.fromHtml(getString(R.string.no_connectivity_empty_list)));
                        } else if (data.error == TransmissionData.Errors.ACCESS_DENIED) {
                            text.setText(Html.fromHtml(getString(R.string.access_denied_empty_list)));
                        } else if (data.error == TransmissionData.Errors.NO_JSON) {
                            text.setText(Html.fromHtml(getString(R.string.no_json_empty_list)));
                        } else if (data.error == TransmissionData.Errors.NO_CONNECTION) {
                            text.setText(Html.fromHtml(getString(R.string.no_connection_empty_list)));
                        } else if (data.error == TransmissionData.Errors.GENERIC_HTTP) {
                            text.setText(Html.fromHtml(getString(R.string.generic_http_empty_list)));
                        } else if (data.error == TransmissionData.Errors.THREAD_ERROR) {
                            text.setText(Html.fromHtml(getString(R.string.thread_error_empty_list)));
                        } else if (data.error == TransmissionData.Errors.RESPONSE_ERROR) {
                            text.setText(Html.fromHtml(getString(R.string.response_error_empty_list)));
                        } else if (data.error == TransmissionData.Errors.TIMEOUT) {
                            text.setText(Html.fromHtml(getString(R.string.timeout_empty_list)));
                        }
                    }
                }
                if (data.torrents.size() > 0) {
                    if (notifyChange) {
                        mTorrentListAdapter.notifyDataSetChanged();
                    }
                } else {
                    mTorrentListAdapter.notifyDataSetInvalidated();
                }

                if (data.error == 0) {
                    FragmentManager manager = getActivity().getSupportFragmentManager();
                    TorrentListMenuFragment menu = (TorrentListMenuFragment) manager.findFragmentById(R.id.torrent_list_menu);
                    if (menu != null) {
                        menu.notifyTorrentListUpdate(data.torrents, data.session);
                    }
                    if (!filtered) {
                        TorrentDetailFragment detail = (TorrentDetailFragment) manager.findFragmentByTag(
                                TorrentDetailFragment.TAG);
                        if (detail != null) {
                            detail.notifyTorrentListChanged(data.hasRemoved, data.hasAdded, data.hasStatusChanged);
                            if (data.hasStatusChanged) {
                                invalidateMenu = true;
                            }
                        }
                    }
                }
            }

            if (mRefreshing) {
                mRefreshing = false;
                invalidateMenu = true;
            }

            if (invalidateMenu)
                getActivity().invalidateOptionsMenu();
        }

        @Override
        public void onLoaderReset(
                android.support.v4.content.Loader<TransmissionData> loader) {
            mTorrentListAdapter.clear();
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
                                    mRefreshing = true;
                                    getActivity().invalidateOptionsMenu();

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



            mRefreshing = true;
            getActivity().invalidateOptionsMenu();

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
    private SharedPreferences.OnSharedPreferenceChangeListener mProfileChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    if (getActivity() == null || mProfile == null) return;

                    if (!key.endsWith(mProfile.getId())) return;

                    Loader<TransmissionData> loader = getActivity().getSupportLoaderManager()
                            .getLoader(G.TORRENTS_LOADER_ID);

                    mProfile.load();

                    TransmissionProfile.setCurrentProfile(mProfile, getActivity());
                    ((TransmissionSessionInterface) getActivity()).setProfile(mProfile);
                    ((TransmissionDataLoader) loader).setProfile(mProfile);
                }
            };

    private SharedPreferences.OnSharedPreferenceChangeListener mSettingsChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    if (key.equals(G.PREF_BASE_SORT_ORDER) || key.equals(G.PREF_BASE_SORT_ORDER)) {
                        mTorrentListAdapter.resetBaseSort();
                    } else if (key.equals(G.PREF_PROFILES)) {
                        mRefreshing = true;
                        getActivity().invalidateOptionsMenu();
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
        setHasOptionsMenu(true);
        getActivity().setProgressBarIndeterminateVisibility(true);

        if (savedInstanceState == null) {
            mSharedPrefs.registerOnSharedPreferenceChangeListener(mSettingsChangeListener);
        }

        ActionBar actionBar = getActivity().getActionBar();
        if (actionBar != null) {
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

            mProfileAdapter = new TransmissionProfileListAdapter(getActivity());

            actionBar.setListNavigationCallbacks(mProfileAdapter, new ActionBar.OnNavigationListener() {
                @Override
                public boolean onNavigationItemSelected(int pos, long id) {
                    TransmissionProfile profile = mProfileAdapter.getItem(pos);
                    if (profile != TransmissionProfileListAdapter.EMPTY_PROFILE) {
                        final Loader<TransmissionData> loader = getActivity().getSupportLoaderManager()
                                .getLoader(G.TORRENTS_LOADER_ID);

                        if (mProfile != null) {
                            SharedPreferences prefs = mProfile.getPreferences(getActivity());
                            if (prefs != null)
                                prefs.unregisterOnSharedPreferenceChangeListener(mProfileChangeListener);
                        }

                        mProfile = profile;
                        TransmissionProfile.setCurrentProfile(profile, getActivity());
                        ((TransmissionSessionInterface) getActivity()).setProfile(profile);
                        ((TransmissionDataLoader) loader).setProfile(profile);

                        SharedPreferences prefs = mProfile.getPreferences(getActivity());
                        if (prefs != null)
                            prefs.registerOnSharedPreferenceChangeListener(mProfileChangeListener);

                        if (mPreventRefreshIndicator) {
                            mPreventRefreshIndicator = false;
                        } else {
                            mRefreshing = true;
                            getActivity().invalidateOptionsMenu();
                        }
                    }

                    return false;
                }
            });

            actionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
        }

        if (savedInstanceState != null
                && savedInstanceState.containsKey(STATE_CURRENT_PROFILE)) {
            mProfile = savedInstanceState.getParcelable(STATE_CURRENT_PROFILE);
        }

        getActivity().getSupportLoaderManager().initLoader(G.PROFILES_LOADER_ID, null, mProfileLoaderCallbacks);
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

            mRefreshing = false;
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
                        mTorrentListAdapter.clear();
                        ArrayList<Torrent> torrents = savedInstanceState.getParcelableArrayList(STATE_TORRENTS);
                        mTorrentListAdapter.addAll(torrents);
                        mTorrentListAdapter.repeatFilter();
                    }
                    if (savedInstanceState.containsKey(STATE_ACTIVATED_POSITION)) {
                        setActivatedPosition(savedInstanceState.getInt(STATE_ACTIVATED_POSITION));
                    }
                }
            });

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
        outState.putParcelable(STATE_CURRENT_PROFILE, mProfile);
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
        inflater.inflate(R.menu.torrent_list_options, menu);

        MenuItem item = menu.findItem(R.id.menu_refresh);
        if (mRefreshing)
            item.setActionView(R.layout.action_progress_bar);
        else
            item.setActionView(null);

        item = menu.findItem(R.id.menu_alt_speed);
        if (mSession == null) {
            item.setVisible(false);
        } else {
            item.setVisible(true);
            if (mAltSpeed) {
                item.setIcon(R.drawable.ic_menu_alt_speed_on);
                item.setTitle(R.string.alt_speed_label_off);
            } else {
                item.setIcon(R.drawable.ic_menu_alt_speed_off);
                item.setTitle(R.string.alt_speed_label_on);
            }
        }

        if (mFindShown) {
            boolean detailVisible = ((TorrentListActivity) getActivity()).isDetailPanelShown();
            item = menu.add(R.string.find);
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
    public boolean onOptionsItemSelected(MenuItem item) {
        Loader<TransmissionData> loader;
        switch (item.getItemId()) {
            case R.id.menu_alt_speed:
                loader = getActivity().getSupportLoaderManager()
                    .getLoader(G.TORRENTS_LOADER_ID);
                if (loader != null) {
                    mAltSpeed = !mAltSpeed;
                    mSession.setAltSpeedLimitEnabled(mAltSpeed);
                    ((TransmissionDataLoader) loader).setSession(mSession, "alt-speed-enabled");
                    getActivity().invalidateOptionsMenu();
                }
                return true;
            case R.id.menu_refresh:
                loader = getActivity().getSupportLoaderManager()
                    .getLoader(G.TORRENTS_LOADER_ID);
                if (loader != null) {
                    loader.onContentChanged();
                    mRefreshing = !mRefreshing;
                    getActivity().invalidateOptionsMenu();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
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

    public void setRefreshing(boolean refreshing) {
        mRefreshing = refreshing;
        getActivity().invalidateOptionsMenu();
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
                mRefreshing = true;
                getActivity().invalidateOptionsMenu();

                if (mActionMode != null) {
                    mActionMode.finish();
                }
            }
        }).setView(inflater.inflate(R.layout.torrent_location_dialog, null));

        if (mSession == null) {
            return true;
        }

        AlertDialog dialog = builder.create();
        dialog.show();

        TransmissionProfileDirectoryAdapter adapter =
                new TransmissionProfileDirectoryAdapter(
                getActivity(), android.R.layout.simple_spinner_item);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        adapter.addAll(mSession.getDownloadDirectories());
        adapter.sort();

        Spinner location = (Spinner) dialog.findViewById(R.id.location_choice);
        location.setAdapter(adapter);

        if (mProfile.getLastDownloadDirectory() != null) {
            int position = adapter.getPosition(mProfile.getLastDownloadDirectory());

            if (position > -1) {
                location.setSelection(position);
            }
        }

        return true;
    }

    private static class TransmissionProfileListAdapter extends ArrayAdapter<TransmissionProfile> {
        public static final TransmissionProfile EMPTY_PROFILE = new TransmissionProfile(null);

        public TransmissionProfileListAdapter(Context context) {
            super(context, 0);

            add(EMPTY_PROFILE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView;
            TransmissionProfile profile = getItem(position);

            if (rowView == null) {
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                rowView = vi.inflate(R.layout.torrent_profile_selector, null);
            }

            TextView name = (TextView) rowView.findViewById(R.id.name);
            TextView summary = (TextView) rowView.findViewById(R.id.summary);

            if (profile == EMPTY_PROFILE) {
                name.setText(R.string.no_profiles);
                if (summary != null)
                    summary.setText(R.string.create_profile_in_settings);
            } else {
                name.setText(profile.getName());
                if (summary != null)
                    summary.setText((profile.getUsername().length() > 0 ? profile.getUsername() + "@" : "")
                        + profile.getHost() + ":" + profile.getPort());
            }

            return rowView;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView;

            if (rowView == null) {
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                rowView = vi.inflate(R.layout.torrent_profile_selector_dropdown, null);
            }

            return getView(position, rowView, parent);
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
            if (mProfile != null) {
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
                } catch (Exception e) { }
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
                        String[] split = prefixString.toString().split("");
                        StringBuilder pattern = new StringBuilder();
                        for (int i = 0; i < split.length; i++) {
                            if (split[i].equals("")) {
                                continue;
                            }
                            pattern.append("\\Q" + split[i] + "\\E");
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
                            TorrentDetailFragment.TAG);
                    if (detail != null) {
                        detail.notifyTorrentListChanged(true, true, false);
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
