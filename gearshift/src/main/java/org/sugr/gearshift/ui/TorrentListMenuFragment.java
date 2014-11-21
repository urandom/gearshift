package org.sugr.gearshift.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.SparseBooleanArray;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import org.sugr.gearshift.G;
import org.sugr.gearshift.G.FilterBy;
import org.sugr.gearshift.G.SortBy;
import org.sugr.gearshift.G.SortOrder;
import org.sugr.gearshift.R;
import org.sugr.gearshift.core.TransmissionProfile;
import org.sugr.gearshift.core.TransmissionSession;
import org.sugr.gearshift.service.DataService;
import org.sugr.gearshift.ui.loader.TorrentTrafficLoader;
import org.sugr.gearshift.ui.loader.TransmissionProfileSupportLoader;
import org.sugr.gearshift.ui.settings.SettingsActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

public class TorrentListMenuFragment extends Fragment implements TorrentListNotificationInterface {
    private RecyclerView filterList;
    private FilterAdapter filterAdapter;

    private enum Type {
        PROFILE_SELECTOR, PROFILE, FILTER, DIRECTORY, TRACKER,
        HEADER, OPTION_HEADER, OPTION
    }

    private static final String PROFILE_SELECTOR_KEY = "profile_selector";
    private static final String FILTERS_HEADER_KEY = "filters_header";
    private static final String DIRECTORIES_HEADER_KEY = "directories_header";
    private static final String TRACKERS_HEADER_KEY = "trackers_header";
    private static final String OPTIONS_HEADER_KEY = "options_header";

    private static final String SESSION_SETTINGS_VALUE = "session_settings";
    private static final String SETTINGS_VALUE = "settings";
    private static final String ABOUT_VALUE = "about";

    private TreeSet<String> directories = new TreeSet<>(G.SIMPLE_STRING_COMPARATOR);
    private TreeSet<String> trackers = new TreeSet<>(G.SIMPLE_STRING_COMPARATOR);

    private HashMap<String, ListItem> listItemMap = new HashMap<>();

    private SharedPreferences sharedPrefs;
    private List<TransmissionProfile> profiles = new ArrayList<>();

    private BroadcastReceiver sessionReceiver;

    private Handler closeHandler = new Handler();
    private Runnable closeRunnable = new Runnable() {
        @Override public void run() {
            if (getActivity() == null) {
                return;
            }

            DrawerLayout drawer = ((DrawerLayout) getActivity().findViewById(R.id.drawer_layout));
            drawer.closeDrawer(getActivity().findViewById(R.id.sliding_menu_frame));
        }
    };

    private boolean filtersChanged;
    private boolean initialProfilesLoaded = false;

    private OnSharedPreferenceChangeListener sharedPrefListener = new OnSharedPreferenceChangeListener() {
        @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if (key.matches(G.PREF_FILTER_MATCH_TEST)) {
                filtersChanged = true;
            }
        }
    };

    private LoaderManager.LoaderCallbacks<TransmissionProfile[]> profileLoaderCallbacks = new LoaderManager.LoaderCallbacks<TransmissionProfile[]>() {
        @Override
        public android.support.v4.content.Loader<TransmissionProfile[]> onCreateLoader(
            int id, Bundle args) {
            return new TransmissionProfileSupportLoader(TorrentListMenuFragment.this.getActivity());
        }

        @Override
        public void onLoadFinished(
            android.support.v4.content.Loader<TransmissionProfile[]> loader,
            TransmissionProfile[] profiles) {

            int start = removeProfileSelector();
            int[] range = removeProfiles();

            filterAdapter.notifyItemRangeRemoved(start, range[1] + 1);

            TorrentListActivity context = (TorrentListActivity) TorrentListMenuFragment.this.getActivity();

            TorrentListMenuFragment.this.profiles = Arrays.asList(profiles);

            if (profiles.length > 0) {
                context.setRefreshing(false, DataService.Requests.GET_TORRENTS);
            } else {
                TransmissionProfile.setCurrentProfile(null,
                    PreferenceManager.getDefaultSharedPreferences(context));
            }

            String currentId = TransmissionProfile.getCurrentProfileId(
                PreferenceManager.getDefaultSharedPreferences(context));

            boolean isProfileSet = false;
            for (TransmissionProfile prof : profiles) {
                if (prof.getId().equals(currentId)) {
                    context.setProfile(prof);
                    isProfileSet = true;
                    break;
                }
            }

            if (!isProfileSet) {
                if (profiles.length > 0) {
                    context.setProfile(profiles[0]);
                } else {
                    context.setProfile(null);
                    /* TODO: should display the message that the user hasn't created a profile yet */
                }
                TransmissionProfile.setCurrentProfile(context.getProfile(),
                    PreferenceManager.getDefaultSharedPreferences(context));
            }

            if (profiles.length > 1) {
                start = fillProfileSelector();
                filterAdapter.notifyItemInserted(start);

                if (!initialProfilesLoaded) {
                    filterList.scrollToPosition(0);
                    initialProfilesLoaded = true;
                }
            }
        }

        @Override
        public void onLoaderReset(
            android.support.v4.content.Loader<TransmissionProfile[]> loader) {

            int start = removeProfileSelector();
            int[] range = removeProfiles();
            filterAdapter.notifyItemRangeRemoved(start, range[1] + 1);
        }

    };

    private LoaderManager.LoaderCallbacks<TorrentTrafficLoader.TorrentTrafficOutputData> torrentTrafficLoaderCallbacks
        = new LoaderManager.LoaderCallbacks<TorrentTrafficLoader.TorrentTrafficOutputData>() {

        @Override public Loader<TorrentTrafficLoader.TorrentTrafficOutputData> onCreateLoader(int id, Bundle bundle) {
            if (id == G.TORRENT_MENU_TRAFFIC_LOADER_ID) {
                TransmissionProfileInterface context = (TransmissionProfileInterface) getActivity();
                if (context == null) {
                    return null;
                }

                boolean showStatus = sharedPrefs.getBoolean(G.PREF_SHOW_STATUS, false);
                boolean directoriesEnabled = sharedPrefs.getBoolean(G.PREF_FILTER_DIRECTORIES, true);
                boolean trackersEnabled = sharedPrefs.getBoolean(G.PREF_FILTER_TRACKERS, false);

                return new TorrentTrafficLoader(getActivity(), context.getProfile().getId(),
                    !showStatus, directoriesEnabled, trackersEnabled);
            }
            return null;
        }

        @Override public void onLoadFinished(Loader<TorrentTrafficLoader.TorrentTrafficOutputData> loader, TorrentTrafficLoader.TorrentTrafficOutputData data) {
            boolean updateDirectoryFilter = false;
            boolean updateTrackerFilter = false;
            boolean checkSelected = false;

            TransmissionSessionInterface context = (TransmissionSessionInterface) getActivity();

            if (context == null) {
                return;
            }

            if (context.getSession() == null) {
                int[][] insertRanges = new int[][]{new int[]{-1, 1}, new int[]{-1, 1}};
                Iterator<ListItem> iter = filterAdapter.itemData.iterator();

                directories.clear();
                trackers.clear();

                int index = 0;
                while (iter.hasNext()) {
                    ListItem item = iter.next();

                    if (DIRECTORIES_HEADER_KEY.equals(item.getValueString())
                        || TRACKERS_HEADER_KEY.equals(item.getValueString())
                        || item.getType() == Type.DIRECTORY
                        || item.getType() == Type.TRACKER) {

                        if (insertRanges[0][0] == -1 && DIRECTORIES_HEADER_KEY.equals(item.getValueString())) {
                            insertRanges[0][0] = index;
                            insertRanges[0][1]++;
                        }

                        if (insertRanges[1][0] == -1 && TRACKERS_HEADER_KEY.equals(item.getValueString())) {
                            insertRanges[1][0] = index;
                            insertRanges[1][1]++;
                        }

                        if (item.getType() == Type.DIRECTORY) {
                            insertRanges[0][1]++;
                        }

                        if (item.getType() == Type.TRACKER) {
                            insertRanges[1][1]++;
                        }

                        iter.remove();
                    }

                    index++;
                }

                /*
                if (insertRanges[0][0] != -1) {
                    filterAdapter.notifyItemRangeInserted(insertRanges[0][0], insertRanges[0][1]);
                }
                if (insertRanges[1][0] != -1) {
                    filterAdapter.notifyItemRangeInserted(insertRanges[1][0], insertRanges[1][1]);
                }
                */
                if (insertRanges[0][0] != -1 || insertRanges[1][0] != -1) {
                    checkSelectedItems();
                    filterAdapter.notifyDataSetChanged();
                }

                return;
            }

            int[][] insertRanges = new int[][]{new int[]{-1, 1}, new int[]{-1, 1}};

            if (data.directories != null) {
                String dir = sharedPrefs.getString(G.PREF_LIST_DIRECTORY, "");
                boolean equalDirectories = true;

                List<String> normalizedDirs = new ArrayList<>();
                for (String d : data.directories) {
                    if (d.length() == 0) {
                        continue;
                    }

                    while (d.charAt(d.length() - 1) == '/') {
                        if (d.length() == 1) {
                            break;
                        }
                        d = d.substring(0, d.length() - 1);
                    }
                    if (!normalizedDirs.contains(d)) {
                        normalizedDirs.add(d);
                    }
                }

                boolean currentDirectoryTorrents = dir.equals("") || normalizedDirs.contains(dir);
                if (normalizedDirs.size() != directories.size()) {
                    equalDirectories = false;
                } else {
                    for (String d : normalizedDirs) {
                        if (!directories.contains(d)) {
                            equalDirectories = false;
                            break;
                        }
                    }
                }
                if (!equalDirectories) {
                    directories.clear();
                    directories.addAll(normalizedDirs);

                    int[] range = removeDirectoriesFilters();
                    // FIXME:RecyclerView 21.0.2 throws an expeption with two position notifications
                    // filterAdapter.notifyItemRangeRemoved(range[0], range[1]);

                    if (directories.size() > 1) {
                        ListItem pivot = listItemMap.get(OPTIONS_HEADER_KEY);
                        int position = filterAdapter.itemData.indexOf(pivot);

                        ListItem header = listItemMap.get(DIRECTORIES_HEADER_KEY);
                        insertRanges[0][0] = position;

                        filterAdapter.itemData.add(position++, header);
                        for (String d : directories) {
                            ListItem di = getDirectoryItem(d);
                            if (di != null) {
                                filterAdapter.itemData.add(position++, di);
                                insertRanges[0][1]++;
                            }
                        }
                        updateDirectoryFilter = !currentDirectoryTorrents;
                    } else {
                        updateDirectoryFilter = true;
                    }

                    checkSelected = true;
                }
            } else {
                int[] range = removeDirectoriesFilters();
                // filterAdapter.notifyItemRangeRemoved(range[0], range[1]);

                if (!sharedPrefs.getString(G.PREF_LIST_DIRECTORY, "").equals("")) {
                    updateDirectoryFilter = true;
                }
            }

            if (data.trackers != null) {
                String track = sharedPrefs.getString(G.PREF_LIST_TRACKER, "");
                boolean equalTrackers = true;
                boolean currentTrackerTorrents = track.equals("") || data.trackers.contains(track)
                    || G.FILTER_UNTRACKED.equals(track);

                if (data.trackers.size() != trackers.size()) {
                    equalTrackers = false;
                } else {
                    for (String t : data.trackers) {
                        if (!trackers.contains(t)) {
                            equalTrackers = false;
                            break;
                        }
                    }
                }
                if (!equalTrackers) {
                    trackers.clear();
                    trackers.addAll(data.trackers);

                    int[] range = removeTrackersFilters();
                    // filterAdapter.notifyItemRangeRemoved(range[0], range[1]);

                    if (trackers.size() > 0 && sharedPrefs.getBoolean(G.PREF_FILTER_UNTRACKED, false)
                        || trackers.size() > 1) {

                        ListItem pivot = listItemMap.get(OPTIONS_HEADER_KEY);
                        int position = filterAdapter.itemData.indexOf(pivot);

                        ListItem header = listItemMap.get(TRACKERS_HEADER_KEY);
                        insertRanges[1][0] = position;

                        filterAdapter.itemData.add(position++, header);
                        for (String t : trackers) {
                            filterAdapter.itemData.add(position++, getTrackerItem(t));
                            insertRanges[1][1]++;
                        }

                        if (sharedPrefs.getBoolean(G.PREF_FILTER_UNTRACKED, false)) {
                            ListItem untracked = new ListItem(Type.TRACKER, G.FILTER_UNTRACKED,
                                getString(R.string.menu_filters_untracked), G.PREF_FILTER_UNTRACKED);

                            filterAdapter.itemData.add(position++, untracked);
                            insertRanges[1][1]++;
                        }
                        updateTrackerFilter = !currentTrackerTorrents;
                    } else {
                        updateTrackerFilter = false;
                    }

                    checkSelected = true;
                }
            } else {
                int[] range = removeTrackersFilters();
                // filterAdapter.notifyItemRangeRemoved(range[0], range[1]);

                if (!sharedPrefs.getString(G.PREF_LIST_TRACKER, "").equals("")) {
                    updateTrackerFilter = true;
                }
            }

            if (checkSelected) {
                checkSelectedItems();
            }

            if (updateDirectoryFilter || updateTrackerFilter) {
                TorrentListFragment fragment =
                    ((TorrentListFragment) getFragmentManager().findFragmentById(R.id.torrent_list));
                if (updateDirectoryFilter) {
                    fragment.setListDirectoryFilter(null);
                }
                if (updateTrackerFilter) {
                    fragment.setListTrackerFilter(null);
                }

                closeHandler.removeCallbacks(closeRunnable);
                closeHandler.post(closeRunnable);
            }

            /*
            if (insertRanges[0][0] != -1) {
                filterAdapter.notifyItemRangeInserted(insertRanges[0][0], insertRanges[0][1]);
            }
            if (insertRanges[1][0] != -1) {
                filterAdapter.notifyItemRangeInserted(insertRanges[1][0], insertRanges[1][1]);
            }
            */
            filterAdapter.notifyDataSetChanged();
        }

        @Override
        public void onLoaderReset(Loader<TorrentTrafficLoader.TorrentTrafficOutputData> loader) {
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        sessionReceiver = new SessionReceiver();

        Context context = new ContextThemeWrapper(getActivity(), android.R.style.Theme_Holo);
        LayoutInflater localInflater = inflater.cloneInContext(context);

        View root = localInflater.inflate(R.layout.fragment_torrent_list_menu, container, false);

        filterList = (RecyclerView) root.findViewById(R.id.filter_list);

        /* TODO: The list items should have a count that indicates
         *  how many torrents are matched by the filter */
        filterAdapter = new FilterAdapter(this);
        filterList.setAdapter(filterAdapter);
        filterList.setLayoutManager(new LinearLayoutManager(getActivity()));

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sharedPrefs.registerOnSharedPreferenceChangeListener(sharedPrefListener);

        fillMenuItems();
        checkSelectedItems();

        getActivity().getSupportLoaderManager().initLoader(G.PROFILES_LOADER_ID, null, profileLoaderCallbacks);

        return root;
    }

    @Override public void onResume() {
        super.onResume();

        if (filtersChanged) {
            filtersChanged = false;
            directories.clear();
            trackers.clear();
            fillMenuItems();
            checkSelectedItems();

            getActivity().getSupportLoaderManager().restartLoader(G.PROFILES_LOADER_ID, null, profileLoaderCallbacks);
        }

        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
            sessionReceiver, new IntentFilter(G.INTENT_SESSION_INVALIDATED));
    }

    @Override public void onPause() {
        super.onPause();

        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(sessionReceiver);
    }

    public void notifyTorrentListChanged(Cursor cursor, int error, boolean added, boolean removed,
                                        boolean statusChanged, boolean metadataNeeded,
                                        boolean connected) {

        getActivity().getSupportLoaderManager().restartLoader(G.TORRENT_MENU_TRAFFIC_LOADER_ID,
            null, torrentTrafficLoaderCallbacks);
    }

    private void setActivatedPosition(ListItem item, int position) {
        if (position == ListView.INVALID_POSITION) {
            filterAdapter.clearSelections();
        } else {
            if (filterAdapter.itemData.size() <= position) {
                return;
            }

            TorrentListFragment fragment =
                    ((TorrentListFragment) getFragmentManager().findFragmentById(R.id.torrent_list));

            final TorrentListActivity context = (TorrentListActivity) getActivity();
            TransmissionProfile profile = context.getProfile();
            boolean allFilterEnabled = sharedPrefs.getBoolean(G.PREF_FILTER_ALL, true);
            ListItem selectedFilter = null;
            ListItem selectedDirectory = null;
            ListItem selectedTracker = null;

            for (ListItem i : filterAdapter.getSelectedItems()) {
                switch (i.getType()) {
                    case FILTER:
                        selectedFilter = i;
                        break;
                    case DIRECTORY:
                        selectedDirectory = i;
                        break;
                    case TRACKER:
                        selectedTracker = i;
                        break;
                }
            }

            boolean overrideTransition = false;
            switch(item.getType()) {
                case PROFILE_SELECTOR:
                    filterAdapter.setProfilesVisible(!filterAdapter.areProfilesVisible());
                    break;
                case PROFILE:
                    if (profile != null && profile.getId().equals(item.getValueString())) {
                        break;
                    }

                    for (TransmissionProfile prof : profiles) {
                        if (prof.getId().equals(item.getValueString())) {
                            TransmissionProfile.setCurrentProfile(prof,
                                PreferenceManager.getDefaultSharedPreferences(context));

                            context.setProfile(prof);

                            context.setRefreshing(true, DataService.Requests.GET_TORRENTS);

                            break;
                        }
                    }

                    filterAdapter.setProfilesVisible(false);

                    int pos = removeProfileSelector();
                    filterAdapter.notifyItemRemoved(pos);

                    pos = fillProfileSelector();
                    filterAdapter.notifyItemInserted(pos);
                    break;
                case FILTER:
                    FilterBy value;
                    if (item.equals(selectedFilter)) {
                        if (allFilterEnabled) {
                            break;
                        }

                        value = FilterBy.ALL;
                        filterAdapter.setItemSelected(position, false);
                    } else {
                        if (selectedFilter != null) {
                            filterAdapter.setItemSelected(
                                filterAdapter.itemData.indexOf(selectedFilter), false);
                        }
                        filterAdapter.setItemSelected(position, true);
                        value = (FilterBy) item.getValue();
                    }

                    fragment.setListFilter(value);
                    break;
                case DIRECTORY:
                    if (selectedDirectory != null) {
                        filterAdapter.setItemSelected(
                            filterAdapter.itemData.indexOf(selectedDirectory), false);
                    }
                    if (item.equals(selectedDirectory)) {
                        fragment.setListDirectoryFilter(null);
                    } else {
                        filterAdapter.setItemSelected(position, true);
                        fragment.setListDirectoryFilter(item.getValueString());
                    }
                    break;
                case TRACKER:
                    if (selectedTracker != null) {
                        filterAdapter.setItemSelected(
                            filterAdapter.itemData.indexOf(selectedTracker), false);
                    }

                    if (item.equals(selectedTracker)) {
                        fragment.setListTrackerFilter(null);
                    } else {
                        filterAdapter.setItemSelected(position, true);
                        fragment.setListTrackerFilter(item.getValueString());
                    }
                    break;
                case OPTION:
                    TransmissionSession session = context.getSession();
                    final Intent intent;

                    switch (item.getValueString()) {
                        case SESSION_SETTINGS_VALUE:
                            if (session == null) {
                                return;
                            }

                            intent = new Intent(context, TransmissionSessionActivity.class);
                            intent.putExtra(G.ARG_PROFILE, profile);
                            intent.putExtra(G.ARG_SESSION, session);
                            overrideTransition = true;
                            break;
                        case SETTINGS_VALUE:
                            intent = new Intent(context, SettingsActivity.class);

                            if (session != null) {
                                ArrayList<String> directories = new ArrayList<>(session.getDownloadDirectories());
                                directories.remove(session.getDownloadDir());
                                intent.putExtra(G.ARG_DIRECTORIES, directories);
                            }
                            if (profile != null) {
                                intent.putExtra(G.ARG_PROFILE_ID, profile.getId());
                            }
                            overrideTransition = true;
                            break;
                        case ABOUT_VALUE:
                            intent = new Intent(context, AboutActivity.class);
                            break;
                        default:
                            return;
                    }

                    closeHandler.removeCallbacks(closeRunnable);
                    closeHandler.post(closeRunnable);
                    if (intent != null) {
                        final boolean oT = overrideTransition;

                        closeHandler.postDelayed(new Runnable() {
                            @Override public void run() {
                                startActivity(intent);
                                if (oT) {
                                    context.overridePendingTransition(
                                        R.anim.slide_in_top, android.R.anim.fade_out);
                                }
                            }
                        }, getResources().getInteger(android.R.integer.config_shortAnimTime) + 100);
                    }
                default:
                    return;
            }

            if (item.getType() != Type.PROFILE_SELECTOR) {
                closeHandler.removeCallbacks(closeRunnable);
                closeHandler.post(closeRunnable);
            }
        }
    }

    private int fillProfileSelector() {
        TransmissionProfileInterface context = (TransmissionProfileInterface) getActivity();
        TransmissionProfile profile = context.getProfile();

        if (profile == null) {
            return -1;
        }

        ListItem item = new ListItem(Type.PROFILE_SELECTOR,
            profile.getId(), profile.getName(), null);
        item.setSublabel((profile.getUsername().length() > 0 ? profile.getUsername() + "@" : "")
            + profile.getHost() + ":" + profile.getPort());
        filterAdapter.itemData.add(0, item);

        listItemMap.put(PROFILE_SELECTOR_KEY, item);

        return 0;
    }

    private int[] fillProfiles() {
        TransmissionProfileInterface context = (TransmissionProfileInterface) getActivity();
        TransmissionProfile currentProfile = context.getProfile();
        ListItem selector = listItemMap.get(PROFILE_SELECTOR_KEY);

        int index = filterAdapter.itemData.indexOf(selector);

        if (currentProfile == null || index == -1) {
            return new int[]{-1, 0};
        }

        int start = ++index;
        int count = 0;
        for (TransmissionProfile profile : profiles) {
            if (profile.getId().equals(currentProfile.getId())) {
                continue;
            }

            ListItem item = new ListItem(Type.PROFILE,
                profile.getId(), profile.getName(), null);
            item.setSublabel((profile.getUsername().length() > 0 ? profile.getUsername() + "@" : "")
                + profile.getHost() + ":" + profile.getPort());
            filterAdapter.itemData.add(index++, item);
            count++;
        }

        return new int[]{start, count};
    }

    private int[] fillSessionItems() {
        int[] range = new int[]{-1, 0};
        TransmissionSessionInterface context = (TransmissionSessionInterface) getActivity();
        TransmissionSession session = context.getSession();

        if (session == null) {
            return range;
        }

        ArrayList<ListItem> list = new ArrayList<>();

        for (FilterBy filter : FilterBy.values()) {
            ListItem item;
            if (listItemMap.containsKey(filter.name())) {
                item = listItemMap.get(filter.name());
            } else {
                int string = -1;
                String pref = null;
                switch(filter) {
                    case ALL:
                        string = R.string.menu_filters_all;
                        pref = G.PREF_FILTER_ALL;
                        break;
                    case DOWNLOADING:
                        string = R.string.menu_filters_downloading;
                        pref = G.PREF_FILTER_DOWNLOADING;
                        break;
                    case SEEDING:
                        string = R.string.menu_filters_seeding;
                        pref = G.PREF_FILTER_SEEDING;
                        break;
                    case PAUSED:
                        string = R.string.menu_filters_paused;
                        pref = G.PREF_FILTER_PAUSED;
                        break;
                    case COMPLETE:
                        string = R.string.menu_filters_complete;
                        pref = G.PREF_FILTER_COMPLETE;
                        break;
                    case INCOMPLETE:
                        string = R.string.menu_filters_incomplete;
                        pref = G.PREF_FILTER_INCOMPLETE;
                        break;
                    case ACTIVE:
                        string = R.string.menu_filters_active;
                        pref = G.PREF_FILTER_ACTIVE;
                        break;
                    case CHECKING:
                        string = R.string.menu_filters_checking;
                        pref = G.PREF_FILTER_CHECKING;
                        break;
                    case ERRORS:
                        string = R.string.menu_filters_errors;
                        pref = G.PREF_FILTER_ERRORS;
                        break;
                }
                item = new ListItem(Type.FILTER, filter, string, pref);
            }
            if (sharedPrefs.getBoolean(item.getPreferenceKey(), true)) {
                list.add(item);
            }
        }
        ListItem header;
        if (listItemMap.containsKey(FILTERS_HEADER_KEY)) {
            header = listItemMap.get(FILTERS_HEADER_KEY);
        } else {
            header = new ListItem(Type.HEADER, FILTERS_HEADER_KEY, R.string.menu_filters_header);
        }

        if (sharedPrefs.getBoolean(G.PREF_FILTER_ALL, true) && list.size() > 1 || list.size() > 0) {
            ListItem pivot = listItemMap.get(OPTIONS_HEADER_KEY);
            int position = filterAdapter.itemData.indexOf(pivot);

            range[0] = position;

            filterAdapter.itemData.add(position++, header);
            for (ListItem item : list) {
                filterAdapter.itemData.add(position++, item);
            }

            range[1] = position;
        }
        list.clear();

        if (!listItemMap.containsKey(DIRECTORIES_HEADER_KEY)) {
            new ListItem(Type.HEADER, DIRECTORIES_HEADER_KEY, R.string.menu_directories_header);
        }

        if (!listItemMap.containsKey(TRACKERS_HEADER_KEY)) {
            new ListItem(Type.HEADER, TRACKERS_HEADER_KEY, R.string.menu_trackers_header);
        }

        return range;
    }

    private void fillMenuItems() {
        ArrayList<ListItem> list = new ArrayList<>();

        filterAdapter.itemData.clear();

        fillProfileSelector();

        fillSessionItems();

        ListItem item;
        if (listItemMap.containsKey(OPTIONS_HEADER_KEY)) {
            item = listItemMap.get(OPTIONS_HEADER_KEY);
        } else {
            item = new ListItem(Type.OPTION_HEADER, null, null, null);
        }
        listItemMap.put(OPTIONS_HEADER_KEY, item);
        filterAdapter.itemData.add(item);

        if (((TransmissionSessionInterface) getActivity()).getSession() != null) {
            item = new ListItem(Type.OPTION, SESSION_SETTINGS_VALUE, R.string.session_settings_item,
                R.drawable.ic_settings_remote_black_18dp);
            filterAdapter.itemData.add(item);
        }

        item = new ListItem(Type.OPTION, SETTINGS_VALUE, R.string.settings,
            R.drawable.ic_settings_black_18dp);
        filterAdapter.itemData.add(item);
        item = new ListItem(Type.OPTION, ABOUT_VALUE, R.string.about_item,
            R.drawable.ic_info_black_18dp);
        filterAdapter.itemData.add(item);

        checkSelectedItems();
        filterAdapter.notifyDataSetChanged();
        filterList.scrollToPosition(0);
    }

    private void checkSelectedItems() {
        filterAdapter.clearSelections();

        FilterBy selectedFilter = FilterBy.ALL;
        if (sharedPrefs.contains(G.PREF_LIST_FILTER)) {
            try {
                selectedFilter = FilterBy.valueOf(
                    sharedPrefs.getString(G.PREF_LIST_FILTER, "")
                );
            } catch (Exception ignored) { }
        }
        int position = filterAdapter.itemData.indexOf(
            listItemMap.get(selectedFilter.name()));
        if (position > -1) {
            filterAdapter.setItemSelected(position, true);
        } else if (selectedFilter != FilterBy.ALL) {
            position = filterAdapter.itemData.indexOf(
                    listItemMap.get(selectedFilter.name()));
            if (position > -1) {
                filterAdapter.setItemSelected(position, true);
                TorrentListFragment fragment =
                        ((TorrentListFragment) getFragmentManager().findFragmentById(R.id.torrent_list));
                fragment.setListFilter(FilterBy.ALL);
            }
        }

        SortBy selectedSort = SortBy.STATUS;
        if (sharedPrefs.contains(G.PREF_LIST_SORT_BY)) {
            try {
                selectedSort = SortBy.valueOf(
                    sharedPrefs.getString(G.PREF_LIST_SORT_BY, "")
                );
            } catch (Exception ignored) { }
        }
        position = filterAdapter.itemData.indexOf(
                listItemMap.get(selectedSort.name()));
        filterAdapter.setItemSelected(position, true);

        SortOrder selectedOrder = SortOrder.DESCENDING;
        if (sharedPrefs.contains(G.PREF_LIST_SORT_ORDER)) {
            try {
                selectedOrder = SortOrder.valueOf(
                    sharedPrefs.getString(G.PREF_LIST_SORT_ORDER, "")
                );
            } catch (Exception ignored) { }
        }
        if (selectedOrder == SortOrder.DESCENDING) {
            position = filterAdapter.itemData.indexOf(
                listItemMap.get(selectedOrder.name()));
            filterAdapter.setItemSelected(position, true);
        }

        if (sharedPrefs.getBoolean(G.PREF_FILTER_DIRECTORIES, true)) {
            String selectedDirectory = null;
            if (sharedPrefs.contains(G.PREF_LIST_DIRECTORY)) {
                selectedDirectory = sharedPrefs.getString(G.PREF_LIST_DIRECTORY, null);
            }
            if (selectedDirectory != null) {
                position = filterAdapter.itemData.indexOf(
                    listItemMap.get(selectedDirectory));
                filterAdapter.setItemSelected(position, true);
            }
        }

        if (sharedPrefs.getBoolean(G.PREF_FILTER_TRACKERS, false)) {
            String selectedTracker = null;
            if (sharedPrefs.contains(G.PREF_LIST_TRACKER)) {
                selectedTracker = sharedPrefs.getString(G.PREF_LIST_TRACKER, null);
            }
            if (!sharedPrefs.getBoolean(G.PREF_FILTER_UNTRACKED, false)
                && selectedFilter.equals(G.FILTER_UNTRACKED)) {

                selectedTracker = null;
            }
            if (selectedTracker != null) {
                position = filterAdapter.itemData.indexOf(
                    listItemMap.get(selectedTracker));
                filterAdapter.setItemSelected(position, true);
            }
        }
    }

    private int removeProfileSelector() {
        ListItem selector = listItemMap.get(PROFILE_SELECTOR_KEY);
        int position = filterAdapter.itemData.indexOf(selector);
        filterAdapter.itemData.remove(selector);

        return position;
    }

    private int[] removeProfiles() {
        Iterator<ListItem> iter = filterAdapter.itemData.iterator();

        int start = -1;
        int count = 0;
        int index = 0;
        while (iter.hasNext()) {
            ListItem item = iter.next();
            if (item.getType() == Type.PROFILE) {
                iter.remove();
                if (start == -1) {
                    start = index;
                }
                count++;
            }
            ++index;
        }

        return new int[]{start, count};
    }

    private int[] removeSessionItems() {
        Iterator<ListItem> iter = filterAdapter.itemData.iterator();

        int start = -1;
        int count = 0;
        int index = 0;
        while (iter.hasNext()) {
            ListItem item = iter.next();
            switch (item.getType()) {
                case HEADER:
                case FILTER:
                case DIRECTORY:
                case TRACKER:
                    iter.remove();
                    if (start == -1) {
                        start = index;
                    }
                    count++;
                    break;
            }
            ++index;
        }

        return new int[]{start, count};
    }

    private ListItem getDirectoryItem(String directory) {
        ListItem item;
        if (listItemMap.containsKey(directory)) {
            item = listItemMap.get(directory);
        } else {
            String name = directory;
            int lastSlash = name.lastIndexOf('/');
            if (lastSlash > -1) {
                name = name.substring(lastSlash + 1);
            }
            if (TextUtils.isEmpty(name)) {
                return null;
            }
            item = new ListItem(Type.DIRECTORY, directory, name, G.PREF_FILTER_DIRECTORIES);
        }

        return item;
    }

    private int[] removeDirectoriesFilters() {
        ListItem item = listItemMap.get(DIRECTORIES_HEADER_KEY);
        int start = filterAdapter.itemData.indexOf(item);
        int count = 0;

        filterAdapter.itemData.remove(item);
        Iterator<ListItem> iter = filterAdapter.itemData.iterator();

        while (iter.hasNext()) {
            item = iter.next();
            if (item.getType() == Type.DIRECTORY) {
                iter.remove();
                count++;
            }
        }

        return new int[]{start, count};
    }

    private ListItem getTrackerItem(String tracker) {
        ListItem item;
        if (listItemMap.containsKey(tracker)) {
            item = listItemMap.get(tracker);
        } else {
            item = new ListItem(Type.TRACKER, tracker, tracker, G.PREF_FILTER_TRACKERS);
        }

        return item;
    }

    private int[] removeTrackersFilters() {
        ListItem item = listItemMap.get(TRACKERS_HEADER_KEY);
        int start = filterAdapter.itemData.indexOf(item);
        int count = 0;

        filterAdapter.itemData.remove(item);
        Iterator<ListItem> iter = filterAdapter.itemData.iterator();

        while (iter.hasNext()) {
            item = iter.next();
            if (item.getType() == Type.TRACKER) {
                iter.remove();
                count++;
            }
        }

        return new int[]{start, count};
    }

    private static class FilterAdapter extends SelectableRecyclerViewAdapter<FilterAdapter.ViewHolder, ListItem> {
        private TorrentListMenuFragment context;
        private boolean profilesVisible;

        public FilterAdapter(TorrentListMenuFragment context) {
            super();
            this.context = context;
        }

        public boolean areProfilesVisible() {
            return profilesVisible;
        }

        public void setProfilesVisible(boolean visible) {
            this.profilesVisible = visible;
            if (visible) {
                int[] range = context.fillProfiles();
                notifyItemRangeInserted(range[0], range[1]);
                setItemSelected(range[0] - 1, true);
            } else {
                int[] range = context.removeProfiles();
                notifyItemRangeRemoved(range[0], range[1]);
                setItemSelected(range[0] - 1, false);
            }
        }

        @Override public long getItemId(int position) {
            return this.itemData.get(position).hashCode();
        }

        @Override public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemLayoutView = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);

            return new ViewHolder(itemLayoutView, viewType);
        }

        @Override public boolean isItemSelectable(int position) {
            if (itemData.size() <= position) {
                return false;
            }

            ListItem item = itemData.get(position);
            switch (item.getType()) {
                case HEADER:
                case OPTION_HEADER:
                case OPTION:
                    return false;
            }

            return true;
        }

        @Override public void onBindViewHolder(ViewHolder holder, final int position) {
            super.onBindViewHolder(holder, position);

            final ListItem item = itemData.get(position);

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    context.setActivatedPosition(item, position);
                }
            });

            if (holder.label != null) {
                holder.label.setText(item.getLabel());
            }
            if (holder.sublabel != null) {
                holder.sublabel.setText(item.getSublabel());
            }
            if (holder.icon != null) {
                holder.icon.setBackgroundResource(item.getIconId());
            }
        }

        @Override public int getItemViewType(int position) {
            ListItem item = itemData.get(position);
            switch (item.getType()) {
                case HEADER:
                    return R.layout.filter_list_header;
                case PROFILE:
                case PROFILE_SELECTOR:
                    return R.layout.filter_list_profile_item;
                case OPTION_HEADER:
                    return R.layout.filter_list_option_header;
                case OPTION:
                    return R.layout.filter_list_option_item;
                default:
                    return R.layout.filter_list_item;
            }
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            public TextView label;
            public TextView sublabel;
            public View icon;

            public ViewHolder(View itemView, int type) {
                super(itemView);

                label = (TextView) itemView.findViewById(android.R.id.text1);
                sublabel = (TextView) itemView.findViewById(android.R.id.text2);
                icon = itemView.findViewById(android.R.id.icon);
            }
        }
    }

    private class ListItem {
        private Type type;
        private Enum<?> value;
        private int iconId;
        private String valueString;
        private String label;
        private String sublabel;
        private String pref;

        public ListItem(Type type, Enum<?> value, int stringId,
                String pref) {
            this.type = type;
            this.value = value;
            label = getString(stringId);
            this.pref = pref;

            listItemMap.put(value.name(), this);
        }

        public ListItem(Type type, String value, String label,
                String pref) {
            this.type = type;
            this.label = label;
            valueString = value;
            this.pref = pref;

            listItemMap.put(value, this);
        }

        public ListItem(Type type, String value, int stringId) {
            this.type = type;
            label = getString(stringId);
            valueString = value;

            listItemMap.put(value, this);
        }

        public ListItem(Type type, String value, int stringId, int iconId) {
            this.type = type;
            this.iconId = iconId;
            label = getString(stringId);
            valueString = value;

            listItemMap.put(value, this);
        }

        public Type getType() {
            return type;
        }

        public Enum<?> getValue() {
            return value;
        }

        public String getValueString() {
            return value == null ? valueString : value.name();
        }

        public String getLabel() {
            return label;
        }

        public int getIconId() {
            return iconId;
        }

        public String getSublabel() {
            return sublabel;
        }

        public void setSublabel(String sublabel) {
            this.sublabel = sublabel;
        }

        public String getPreferenceKey() {
            return pref;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ListItem listItem = (ListItem) o;

            if (iconId != listItem.iconId) return false;
            if (label != null ? !label.equals(listItem.label) : listItem.label != null)
                return false;
            if (pref != null ? !pref.equals(listItem.pref) : listItem.pref != null) return false;
            if (sublabel != null ? !sublabel.equals(listItem.sublabel) : listItem.sublabel != null)
                return false;
            if (type != listItem.type) return false;
            if (value != null ? !value.equals(listItem.value) : listItem.value != null)
                return false;
            if (valueString != null ? !valueString.equals(listItem.valueString) : listItem.valueString != null)
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + (value != null ? value.hashCode() : 0);
            result = 31 * result + iconId;
            result = 31 * result + (valueString != null ? valueString.hashCode() : 0);
            result = 31 * result + (label != null ? label.hashCode() : 0);
            result = 31 * result + (sublabel != null ? sublabel.hashCode() : 0);
            result = 31 * result + (pref != null ? pref.hashCode() : 0);
            return result;
        }
    }

    private class SessionReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent.getBooleanExtra(G.ARG_SESSION_VALID, false)) {
                int[] range = fillSessionItems();
                if (range[0] != -1) {
                    // FIXME:RecyclerView 21.0.2 throws an expeption with two position notifications
                    // filterAdapter.notifyItemRangeInserted(range[0], range[1]);
                }

                ListItem item = new ListItem(Type.OPTION, SESSION_SETTINGS_VALUE, R.string.session_settings_item,
                    R.drawable.ic_settings_remote_black_18dp);
                ListItem pivot = listItemMap.get(OPTIONS_HEADER_KEY);
                int position = filterAdapter.itemData.indexOf(pivot);

                filterAdapter.itemData.add(++position, item);

                // filterAdapter.notifyItemInserted(position);
                checkSelectedItems();
                filterAdapter.notifyDataSetChanged();
            } else {
                ListItem item = listItemMap.get(SESSION_SETTINGS_VALUE);
                if (item != null) {
                    int position = filterAdapter.itemData.indexOf(item);
                    if (position != -1) {
                        filterAdapter.itemData.remove(position);
                        // filterAdapter.notifyItemRemoved(position);
                    }
                }

                int[] range = removeSessionItems();
                if (range[0] != -1) {
                    // filterAdapter.notifyItemRangeRemoved(range[0], range[1]);
                }
                checkSelectedItems();
                filterAdapter.notifyDataSetChanged();
            }
        }
    }
}
