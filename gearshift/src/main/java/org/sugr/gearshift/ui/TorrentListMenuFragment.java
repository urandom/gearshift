package org.sugr.gearshift.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.DrawerLayout;
import android.text.Html;
import android.text.TextUtils;
import android.util.SparseBooleanArray;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.sugr.gearshift.G;
import org.sugr.gearshift.G.FilterBy;
import org.sugr.gearshift.G.SortBy;
import org.sugr.gearshift.G.SortOrder;
import org.sugr.gearshift.R;
import org.sugr.gearshift.core.TransmissionSession;
import org.sugr.gearshift.ui.loader.TorrentTrafficLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class TorrentListMenuFragment extends Fragment implements TorrentListNotificationInterface {
    private ListView filterList;
    private View footer;
    private FilterAdapter filterAdapter;
    private int filterPosition = ListView.INVALID_POSITION;
    private int sortPosition = ListView.INVALID_POSITION;
    private int orderPosition = ListView.INVALID_POSITION;
    private int directoryPosition = ListView.INVALID_POSITION;
    private int trackerPosition = ListView.INVALID_POSITION;

    private enum Type {
        FIND, FILTER, DIRECTORY, TRACKER, SORT_BY, SORT_ORDER, HEADER
    }

    private static final String FILTERS_HEADER_KEY = "filters_header";
    private static final String DIRECTORIES_HEADER_KEY = "directories_header";
    private static final String TRACKERS_HEADER_KEY = "trackers_header";
    private static final String SORT_BY_HEADER_KEY = "sort_by_header";
    private static final String SORT_ORDER_HEADER_KEY = "sort_order_header";

    private TreeSet<String> directories = new TreeSet<>(G.SIMPLE_STRING_COMPARATOR);
    private TreeSet<String> trackers = new TreeSet<>(G.SIMPLE_STRING_COMPARATOR);

    private HashMap<String, ListItem> listItemMap = new HashMap<>();

    private SharedPreferences sharedPrefs;

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

    private OnSharedPreferenceChangeListener sharedPrefListener = new OnSharedPreferenceChangeListener() {
        @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if (key.matches(G.PREF_FILTER_MATCH_TEST)) {
                filtersChanged = true;
            } else if (key.matches(G.PREF_SHOW_STATUS) && filterList != null) {
                if (prefs.getBoolean(G.PREF_SHOW_STATUS, false)) {
                    filterList.removeFooterView(footer);
                } else {
                    filterList.addFooterView(footer);
                }
            }
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
            boolean dataChanged = false;

            TransmissionSessionInterface context = (TransmissionSessionInterface) getActivity();

            if (context == null) {
                return;
            }

            filterAdapter.setNotifyOnChange(false);

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

                    removeDirectoriesFilters();

                    if (directories.size() > 1) {
                        ListItem pivot = listItemMap.get(SORT_BY_HEADER_KEY);
                        int position = filterAdapter.getPosition(pivot);

                        if (position == -1) {
                            pivot = listItemMap.get(SORT_ORDER_HEADER_KEY);
                            position = filterAdapter.getPosition(pivot);
                        }

                        ListItem header = listItemMap.get(DIRECTORIES_HEADER_KEY);
                        if (position == -1) {
                            filterAdapter.add(header);
                            for (String d : directories) {
                                ListItem di = getDirectoryItem(d);
                                if (di != null) {
                                    filterAdapter.add(di);
                                }
                            }
                        } else {
                            filterAdapter.insert(header, position++);
                            for (String d : directories) {
                                ListItem di = getDirectoryItem(d);
                                if (di != null) {
                                    filterAdapter.insert(di, position++);
                                }
                            }
                        }
                        updateDirectoryFilter = !currentDirectoryTorrents;
                    } else {
                        updateDirectoryFilter = true;
                    }

                    dataChanged = true;
                    checkSelected = true;
                }
            } else {
                removeDirectoriesFilters();
                dataChanged = true;

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

                    removeTrackersFilters();

                    if (trackers.size() > 0 && sharedPrefs.getBoolean(G.PREF_FILTER_UNTRACKED, false)
                        || trackers.size() > 1) {

                        ListItem pivot = listItemMap.get(SORT_BY_HEADER_KEY);
                        int position = filterAdapter.getPosition(pivot);

                        if (position == -1) {
                            pivot = listItemMap.get(SORT_ORDER_HEADER_KEY);
                            position = filterAdapter.getPosition(pivot);
                        }

                        ListItem header = listItemMap.get(TRACKERS_HEADER_KEY);
                        if (position == -1) {
                            filterAdapter.add(header);
                            for (String t : trackers) {
                                filterAdapter.add(getTrackerItem(t));
                            }
                        } else {
                            filterAdapter.insert(header, position++);
                            for (String t : trackers) {
                                filterAdapter.insert(getTrackerItem(t), position++);
                            }
                        }
                        if (sharedPrefs.getBoolean(G.PREF_FILTER_UNTRACKED, false)) {
                            ListItem untracked = new ListItem(Type.TRACKER, G.FILTER_UNTRACKED,
                                getString(R.string.menu_filters_untracked), G.PREF_FILTER_UNTRACKED);

                            if (position == -1) {
                                filterAdapter.add(untracked);
                            } else {
                                filterAdapter.insert(untracked, position++);
                            }
                        }
                        updateTrackerFilter = !currentTrackerTorrents;
                    } else {
                        updateTrackerFilter = false;
                    }

                    dataChanged = true;
                    checkSelected = true;
                }
            } else {
                removeTrackersFilters();
                dataChanged = true;

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
                    directoryPosition = ListView.INVALID_POSITION;
                    fragment.setListDirectoryFilter(null);
                }
                if (updateTrackerFilter) {
                    trackerPosition = ListView.INVALID_POSITION;
                    fragment.setListTrackerFilter(null);
                }

                closeHandler.removeCallbacks(closeRunnable);
                closeHandler.post(closeRunnable);
            }

            if (data.downloadSpeed != -1 && data.uploadSpeed != -1) {
                TransmissionSession session = context.getSession();
                Object[] speed = {
                    G.readableFileSize(data.downloadSpeed), "",
                    G.readableFileSize(data.uploadSpeed), ""
                };

                if (session == null) {
                    setStatus(speed, null);
                } else {
                    if (session.isDownloadSpeedLimitEnabled() || session.isAltSpeedLimitEnabled()) {
                        speed[1] = " (" + G.readableFileSize((
                            session.isAltSpeedLimitEnabled()
                                ? session.getAltDownloadSpeedLimit()
                                : session.getDownloadSpeedLimit()) * 1024) + "/s)";
                    }
                    if (session.isUploadSpeedLimitEnabled() || session.isAltSpeedLimitEnabled()) {
                        speed[3] = " (" + G.readableFileSize((
                            session.isAltSpeedLimitEnabled()
                                ? session.getAltUploadSpeedLimit()
                                : session.getUploadSpeedLimit()) * 1024) + "/s)";
                    }

                    setStatus(speed,
                        session.getDownloadDirFreeSpace() > 0
                            ? G.readableFileSize(session.getDownloadDirFreeSpace())
                            : null
                    );
                }
            }

            if (dataChanged) {
                filterAdapter.notifyDataSetChanged();
            } else {
                filterAdapter.setNotifyOnChange(true);
            }
        }

        @Override
        public void onLoaderReset(Loader<TorrentTrafficLoader.TorrentTrafficOutputData> loader) {
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        Context context = new ContextThemeWrapper(getActivity(), android.R.style.Theme_Holo);
        LayoutInflater localInflater = inflater.cloneInContext(context);

        View root = localInflater.inflate(R.layout.fragment_torrent_list_menu, container, false);

        filterList = (ListView) root.findViewById(R.id.filter_list);

        footer = localInflater.inflate(R.layout.menu_list_footer, filterList, false);
        filterList.addFooterView(footer, null, false);

        /* TODO: The list items should have a count that indicates
         *  how many torrents are matched by the filter */
        filterAdapter = new FilterAdapter(context);
        filterList.setAdapter(filterAdapter);

        filterList.setDivider(null);
        filterList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        filterList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                setActivatedPosition(position);
            }

        });

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sharedPrefs.registerOnSharedPreferenceChangeListener(sharedPrefListener);

        if (sharedPrefs.getBoolean(G.PREF_SHOW_STATUS, false)) {
            filterList.removeFooterView(footer);
        }

        fillMenuItems();
        checkSelectedItems();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (filtersChanged) {
            filtersChanged = false;
            directories.clear();
            trackers.clear();
            fillMenuItems();
            checkSelectedItems();
        }
        setStatus(null, null);
    }

    public void notifyTorrentListChanged(Cursor cursor, int error, boolean added, boolean removed,
                                        boolean statusChanged, boolean metadataNeeded,
                                        boolean connected) {

        getActivity().getSupportLoaderManager().restartLoader(G.TORRENT_MENU_TRAFFIC_LOADER_ID,
            null, torrentTrafficLoaderCallbacks);
    }

    private void setStatus(Object[] speeds, String freeSpace) {
        TextView speed = (TextView) getView().findViewById(R.id.status_speed);
        TextView space = (TextView) getView().findViewById(R.id.status_free_space);

        if (speed == null || space == null) {
            return;
        }

        if (speeds == null) {
            speeds = new Object[] {"0 KB", "", "0 KB", ""};
        }

        speed.setText(Html.fromHtml(String.format(getString(
                            R.string.speed_format), speeds)));

        space.setText(Html.fromHtml(String.format(getString(
                            R.string.free_space_format),
                            freeSpace == null ? getString(R.string.unknown) : freeSpace)));
    }

    private void setActivatedPosition(int position) {
        if (sharedPrefs.getBoolean(G.PREF_FILTER_ALL, true) && position == filterPosition
                || position == sortPosition) {
            filterList.setItemChecked(position, true);
            return;
        }
        if (position == ListView.INVALID_POSITION) {
            SparseBooleanArray checked = filterList.getCheckedItemPositions();
            for (int i = 0; i < checked.size(); i++) {
                int pos = checked.keyAt(i);
                if (checked.get(pos)) {
                    filterList.setItemChecked(pos, false);
                }
            }

            filterPosition = ListView.INVALID_POSITION;
            sortPosition = ListView.INVALID_POSITION;
            orderPosition = ListView.INVALID_POSITION;
            directoryPosition = ListView.INVALID_POSITION;
            trackerPosition = ListView.INVALID_POSITION;
        } else {
            if (filterAdapter.getCount() <= position) {
                return;
            }

            ListItem item = filterAdapter.getItem(position);
            TorrentListFragment fragment =
                    ((TorrentListFragment) getFragmentManager().findFragmentById(R.id.torrent_list));

            switch(item.getType()) {
                case FIND:
                    if (!fragment.isFindShown()) {
                        fragment.showFind();
                    }
                    filterList.setItemChecked(position, false);
                    break;
                case FILTER:
                    FilterBy value;
                    if (position == filterPosition) {
                        value = FilterBy.ALL;
                        filterList.setItemChecked(filterPosition, false);
                        filterPosition = ListView.INVALID_POSITION;
                    } else {
                        filterList.setItemChecked(filterPosition, false);
                        filterList.setItemChecked(position, true);
                        filterPosition = position;
                        value = (FilterBy) item.getValue();
                    }

                    fragment.setListFilter(value);
                    break;
                case DIRECTORY:
                    filterList.setItemChecked(directoryPosition, false);
                    if (directoryPosition == position) {
                        directoryPosition = ListView.INVALID_POSITION;
                        fragment.setListDirectoryFilter(null);
                    } else {
                        filterList.setItemChecked(position, true);
                        directoryPosition = position;
                        fragment.setListDirectoryFilter(item.getValueString());
                    }
                    break;
                case TRACKER:
                    filterList.setItemChecked(trackerPosition, false);
                    if (trackerPosition == position) {
                        trackerPosition = ListView.INVALID_POSITION;
                        fragment.setListTrackerFilter(null);
                    } else {
                        filterList.setItemChecked(position, true);
                        trackerPosition = position;
                        fragment.setListTrackerFilter(item.getValueString());
                    }
                    break;
                case SORT_BY:
                    filterList.setItemChecked(sortPosition, false);
                    filterList.setItemChecked(position, true);
                    sortPosition = position;
                    fragment.setListFilter((SortBy) item.getValue());
                    break;
                case SORT_ORDER:
                    if (orderPosition == position) {
                        orderPosition = ListView.INVALID_POSITION;
                        filterList.setItemChecked(position, false);
                        fragment.setListFilter(SortOrder.ASCENDING);
                    } else {
                        orderPosition = position;
                        filterList.setItemChecked(position, true);
                        fragment.setListFilter((SortOrder) item.getValue());
                    }
                    break;
                default:
                    return;
            }
        }

        closeHandler.removeCallbacks(closeRunnable);
        closeHandler.post(closeRunnable);
    }

    private void fillMenuItems() {
        ArrayList<ListItem> list = new ArrayList<>();

        filterAdapter.setNotifyOnChange(false);
        filterAdapter.clear();

        filterAdapter.add(new ListItem(Type.FIND, "", R.string.find));

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
            filterAdapter.add(header);
            filterAdapter.addAll(list);
        }
        list.clear();

        if (!listItemMap.containsKey(DIRECTORIES_HEADER_KEY)) {
            new ListItem(Type.HEADER, DIRECTORIES_HEADER_KEY, R.string.menu_directories_header);
        }

        if (!listItemMap.containsKey(TRACKERS_HEADER_KEY)) {
            new ListItem(Type.HEADER, TRACKERS_HEADER_KEY, R.string.menu_trackers_header);
        }

        for (SortBy sort : SortBy.values()) {
            ListItem item;
            if (listItemMap.containsKey(sort.name())) {
                item = listItemMap.get(sort.name());
            } else {
                int string = -1;
                String pref = null;
                switch(sort) {
                    case NAME:
                        string = R.string.menu_sort_name;
                        pref = G.PREF_SORT_NAME;
                        break;
                    case SIZE:
                        string = R.string.menu_sort_size;
                        pref = G.PREF_SORT_SIZE;
                        break;
                    case STATUS:
                        string = R.string.menu_sort_status;
                        pref = G.PREF_SORT_STATUS;
                        break;
                    case ACTIVITY:
                        string = R.string.menu_sort_activity;
                        pref = G.PREF_SORT_ACTIVITY;
                        break;
                    case AGE:
                        string = R.string.menu_sort_age;
                        pref = G.PREF_SORT_AGE;
                        break;
                    case PROGRESS:
                        string = R.string.menu_sort_progress;
                        pref = G.PREF_SORT_PROGRESS;
                        break;
                    case RATIO:
                        string = R.string.menu_sort_ratio;
                        pref = G.PREF_SORT_RATIO;
                        break;
                    case LOCATION:
                        string = R.string.menu_sort_location;
                        pref = G.PREF_SORT_LOCATION;
                        break;
                    case PEERS:
                        string = R.string.menu_sort_peers;
                        pref = G.PREF_SORT_PEERS;
                        break;
                    case RATE_DOWNLOAD:
                        string = R.string.menu_sort_download_speed;
                        pref = G.PREF_SORT_RATE_DOWNLOAD;
                        break;
                    case RATE_UPLOAD:
                        string = R.string.menu_sort_upload_speed;
                        pref = G.PREF_SORT_RATE_UPLOAD;
                        break;
                    case QUEUE:
                        string = R.string.menu_sort_queue;
                        pref = G.PREF_SORT_QUEUE;
                        break;
                }
                item = new ListItem(Type.SORT_BY, sort, string, pref);
            }
            if (sharedPrefs.getBoolean(item.getPreferenceKey(), true)) {
                list.add(item);
            }
        }
        if (listItemMap.containsKey(SORT_BY_HEADER_KEY)) {
            header = listItemMap.get(SORT_BY_HEADER_KEY);
        } else {
            header = new ListItem(Type.HEADER, SORT_BY_HEADER_KEY, R.string.menu_sort_header);
        }

        if (list.size() > 1) {
            filterAdapter.add(header);
            filterAdapter.addAll(list);

            if (listItemMap.containsKey(SORT_ORDER_HEADER_KEY)) {
                header = listItemMap.get(SORT_ORDER_HEADER_KEY);
            } else {
                header = new ListItem(Type.HEADER, SORT_ORDER_HEADER_KEY, R.string.menu_order_header);
            }
            filterAdapter.add(header);
            ListItem item;
            if (listItemMap.containsKey(SortOrder.DESCENDING.name())) {
                item = listItemMap.get(SortOrder.DESCENDING.name());
            } else {
                item = new ListItem(Type.SORT_ORDER, SortOrder.DESCENDING, R.string.menu_order_descending, null);
            }
            filterAdapter.add(item);
        }
        list.clear();

        filterAdapter.notifyDataSetChanged();
    }

    private void checkSelectedItems() {
        SparseBooleanArray checked = filterList.getCheckedItemPositions();
        for (int i = 0; i < checked.size(); i++) {
            int position = checked.keyAt(i);
            if (checked.get(position)) {
                filterList.setItemChecked(position, false);
            }
        }

        FilterBy selectedFilter = FilterBy.ALL;
        if (sharedPrefs.contains(G.PREF_LIST_FILTER)) {
            try {
                selectedFilter = FilterBy.valueOf(
                    sharedPrefs.getString(G.PREF_LIST_FILTER, "")
                );
            } catch (Exception ignored) { }
        }
        filterPosition = filterAdapter.getPosition(
            listItemMap.get(selectedFilter.name()));
        if (filterPosition > -1) {
            filterList.setItemChecked(filterPosition, true);
        } else if (selectedFilter != FilterBy.ALL) {
            filterPosition = filterAdapter.getPosition(
                    listItemMap.get(selectedFilter.name()));
            if (filterPosition > -1) {
                filterList.setItemChecked(0, true);
                TorrentListFragment fragment =
                        ((TorrentListFragment) getFragmentManager().findFragmentById(R.id.torrent_list));
                filterPosition = ListView.INVALID_POSITION;
                fragment.setListFilter(FilterBy.ALL);
            } else {
                filterPosition = ListView.INVALID_POSITION;
            }
        } else {
            filterPosition = ListView.INVALID_POSITION;
        }

        SortBy selectedSort = SortBy.STATUS;
        if (sharedPrefs.contains(G.PREF_LIST_SORT_BY)) {
            try {
                selectedSort = SortBy.valueOf(
                    sharedPrefs.getString(G.PREF_LIST_SORT_BY, "")
                );
            } catch (Exception ignored) { }
        }
        sortPosition = filterAdapter.getPosition(
                listItemMap.get(selectedSort.name()));
        filterList.setItemChecked(sortPosition, true);

        SortOrder selectedOrder = SortOrder.DESCENDING;
        if (sharedPrefs.contains(G.PREF_LIST_SORT_ORDER)) {
            try {
                selectedOrder = SortOrder.valueOf(
                    sharedPrefs.getString(G.PREF_LIST_SORT_ORDER, "")
                );
            } catch (Exception ignored) { }
        }
        if (selectedOrder == SortOrder.DESCENDING) {
            orderPosition = filterAdapter.getPosition(
                listItemMap.get(selectedOrder.name()));
            filterList.setItemChecked(orderPosition, true);
        } else {
            orderPosition = ListView.INVALID_POSITION;
        }

        if (sharedPrefs.getBoolean(G.PREF_FILTER_DIRECTORIES, true)) {
            String selectedDirectory = null;
            if (sharedPrefs.contains(G.PREF_LIST_DIRECTORY)) {
                selectedDirectory = sharedPrefs.getString(G.PREF_LIST_DIRECTORY, null);
            }
            if (selectedDirectory != null) {
                directoryPosition = filterAdapter.getPosition(
                    listItemMap.get(selectedDirectory));
                filterList.setItemChecked(directoryPosition, true);
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
                trackerPosition = filterAdapter.getPosition(
                    listItemMap.get(selectedTracker));
                filterList.setItemChecked(trackerPosition, true);
            }
        }
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

    private int removeDirectoriesFilters() {
        ListItem item = listItemMap.get(DIRECTORIES_HEADER_KEY);
        int position = filterAdapter.getPosition(item);

        if (position != -1) {
            ArrayList<ListItem> removal = new ArrayList<>();
            removal.add(item);
            for (int i = 0; i < filterAdapter.getCount(); i++) {
                item = filterAdapter.getItem(i);
                if (item.getType() == Type.DIRECTORY) {
                    removal.add(item);
                }
            }

            for (ListItem i : removal) {
                filterAdapter.remove(i);
            }
        }

        return position;
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

    private int removeTrackersFilters() {
        ListItem item = listItemMap.get(TRACKERS_HEADER_KEY);
        int position = filterAdapter.getPosition(item);

        if (position != -1) {
            ArrayList<ListItem> removal = new ArrayList<>();
            removal.add(item);
            for (int i = 0; i < filterAdapter.getCount(); i++) {
                item = filterAdapter.getItem(i);
                if (item.getType() == Type.TRACKER) {
                    removal.add(item);
                }
            }

            for (ListItem i : removal) {
                filterAdapter.remove(i);
            }
        }

        return position;
    }

    private static class FilterAdapter extends ArrayAdapter<ListItem> {
        public static final int ITEM_TYPE_HEADER = 0;
        public static final int ITEM_TYPE_NORMAL = 1;

        private static final int ITEM_TYPE_COUNT = 2;

        private LayoutInflater inflater;

        private final static int headerLayout = R.layout.filter_list_header;
        private final static int itemLayout = R.layout.filter_list_item;
        private final static int viewId = android.R.id.text1;

        public FilterAdapter(Context context) {
            super(context, viewId);
            inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getItemViewType(int position) {
            ListItem item = getItem(position);
            if (item.getType() == Type.HEADER) {
                return ITEM_TYPE_HEADER;
            } else {
                return ITEM_TYPE_NORMAL;
            }
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return getItemViewType(position) != ITEM_TYPE_HEADER;
        }

        @Override
        public int getViewTypeCount() {
            return ITEM_TYPE_COUNT;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ListItem item = getItem(position);

            TextView text;

            if (convertView == null) {
                switch (item.getType()) {
                    case HEADER:
                        convertView = inflater.inflate(headerLayout, parent, false);
                        break;

                    case FIND:
                    case FILTER:
                    case DIRECTORY:
                    case TRACKER:
                    case SORT_BY:
                    case SORT_ORDER:
                        convertView = inflater.inflate(itemLayout, parent, false);
                        break;
                }
            }

            text = (TextView) convertView.findViewById(viewId);
            text.setText(item.getLabel());

            return convertView;
        }
    }

    private class ListItem {
        private Type type;
        private Enum<?> value;
        private String valueString;
        private String label;
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

        public String getPreferenceKey() {
            return pref;
        }
    }
}