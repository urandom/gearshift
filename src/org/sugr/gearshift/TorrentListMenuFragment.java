package org.sugr.gearshift;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;

import org.sugr.gearshift.G.FilterBy;
import org.sugr.gearshift.G.SortBy;
import org.sugr.gearshift.G.SortOrder;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.Html;
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

import com.slidingmenu.lib.app.SlidingFragmentActivity;

public class TorrentListMenuFragment extends Fragment {
    private ListView mFilterList;
    private FilterAdapter mFilterAdapter;
    private int mFilterPosition = ListView.INVALID_POSITION;
    private int mSortPosition = ListView.INVALID_POSITION;
    private int mOrderPosition = ListView.INVALID_POSITION;
    private int mDirectoryPosition = ListView.INVALID_POSITION;

    private enum Type {
        FILTER, DIRECTORY, SORT_BY, SORT_ORDER, HEADER
    };

    private static final String FILTERS_HEADER_KEY = "filters_header";
    private static final String DIRECTORIES_HEADER_KEY = "directories_header";
    private static final String SORT_BY_HEADER_KEY = "sort_by_header";
    private static final String SORT_ORDER_HEADER_KEY = "sort_order_header";

    private TreeSet<String> mDirectories = new TreeSet<String>(G.SIMPLE_STRING_COMPARATOR);

    private HashMap<String, ListItem> mListItemMap
        = new HashMap<String, ListItem>();

    private SharedPreferences mSharedPrefs;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        Context context = new ContextThemeWrapper(getActivity(), android.R.style.Theme_Holo);
        LayoutInflater localInflater = inflater.cloneInContext(context);

        View root = localInflater.inflate(R.layout.fragment_torrent_list_menu, container, false);

        mFilterList = (ListView) root.findViewById(R.id.filter_list);

        View footer = localInflater.inflate(R.layout.menu_list_footer, mFilterList, false);
        mFilterList.addFooterView(footer, null, false);

        /* TODO: The list items should have a count that indicates
         *  how many torrents are matched by the filter */
        mFilterAdapter = new FilterAdapter(context);
        mFilterList.setAdapter(mFilterAdapter);

        mFilterList.setDivider(null);
        mFilterList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        mFilterList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                setActivatedPosition(position);
            }

        });

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

        fillMenuItems();
        checkSelectedItems();

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();

        setStatus(null, null);
    }

    public void notifyTorrentListUpdate(ArrayList<Torrent> torrents, TransmissionSession session) {
        long down = 0, up = 0;
        HashSet<String> directories = new HashSet<String>();
        boolean equalDirectories = true;
        boolean currentDirectoryTorrents = false;

        if (torrents != null) {
            String dir = mSharedPrefs.getString(G.PREF_LIST_DIRECTORY, "");
            for (Torrent t : torrents) {
                down += t.getRateDownload();
                up += t.getRateUpload();
                if (t.getDownloadDir() != null) {
                    directories.add(t.getDownloadDir());
                    if (!currentDirectoryTorrents && t.getDownloadDir().equals(dir)) {
                        currentDirectoryTorrents = true;
                    }
                }
            }
        }

        if (directories.size() != mDirectories.size()) {
            equalDirectories = false;
        } else {
            for (String d : directories) {
                if (!mDirectories.contains(d)) {
                    equalDirectories = false;
                    break;
                }
            }
        }
        if (!equalDirectories) {
            mDirectories.clear();
            mDirectories.addAll(directories);

            mFilterAdapter.setNotifyOnChange(false);
            ListItem item = mListItemMap.get(DIRECTORIES_HEADER_KEY);
            int position = mFilterAdapter.getPosition(item);

            if (position != -1) {
                ArrayList<ListItem> removal = new ArrayList<ListItem>();
                removal.add(item);
                for (int i = 0; i < mFilterAdapter.getCount(); i++) {
                    item = mFilterAdapter.getItem(i);
                    if (item.getType() == Type.DIRECTORY) {
                        removal.add(item);
                    }
                }

                for (ListItem i : removal) {
                    mFilterAdapter.remove(i);
                }
            }

            boolean updateFilter = false;
            if (mDirectories.size() > 1) {
                ListItem pivot = mListItemMap.get(SORT_BY_HEADER_KEY);
                position = mFilterAdapter.getPosition(pivot);

                if (position == -1) {
                    pivot = mListItemMap.get(SORT_ORDER_HEADER_KEY);
                    position = mFilterAdapter.getPosition(pivot);
                }

                ListItem header = mListItemMap.get(DIRECTORIES_HEADER_KEY);
                if (position == -1) {
                    mFilterAdapter.add(header);
                    for (String d : mDirectories) {
                        mFilterAdapter.add(getDirectoryItem(d));
                    }
                } else {
                    mFilterAdapter.insert(header, position++);
                    for (String d : mDirectories) {
                        mFilterAdapter.insert(getDirectoryItem(d), position++);
                    }
                }
                updateFilter = !currentDirectoryTorrents;
            } else {
                updateFilter = true;
            }

            if (updateFilter) {
                TorrentListFragment fragment =
                        ((TorrentListFragment) getFragmentManager().findFragmentById(R.id.torrent_list));
                fragment.setListFilter((String) null);
                ((SlidingFragmentActivity) getActivity()).showContent();
            }
            mFilterAdapter.notifyDataSetChanged();
            checkSelectedItems();
        }

        Object[] speed = {
            G.readableFileSize(down), "",
            G.readableFileSize(up), ""
        };

        if (session == null) {
            setStatus(speed, null);
            return;
        }


        if (session.isSpeedLimitDownEnabled() || session.isAltSpeedEnabled()) {
            speed[1] = " (" + G.readableFileSize((
                session.isAltSpeedEnabled()
                    ? session.getAltSpeedDown()
                    : session.getSpeedLimitDown()) * 1024) + "/s)";
        }
        if (session.isSpeedLimitUpEnabled() || session.isAltSpeedEnabled()) {
            speed[3] = " (" + G.readableFileSize((
                session.isAltSpeedEnabled()
                    ? session.getAltSpeedUp()
                    : session.getSpeedLimitUp()) * 1024) + "/s)";
        }

        setStatus(speed,
            session.getDownloadDirFreeSpace() > 0
                ? G.readableFileSize(session.getDownloadDirFreeSpace())
                : null
        );
    }

    private void setStatus(Object[] speeds, String freeSpace) {
        TextView speed = (TextView) getView().findViewById(R.id.status_speed);
        TextView space = (TextView) getView().findViewById(R.id.status_free_space);

        if (speeds == null)
            speeds = new Object[] {"0 KB", "", "0 KB", ""};

        speed.setText(Html.fromHtml(String.format(getString(
                            R.string.speed_format), speeds)));

        space.setText(Html.fromHtml(String.format(getString(
                            R.string.free_space_format),
                            freeSpace == null ? getString(R.string.unknown) : freeSpace)));
    }

    private void setActivatedPosition(int position) {
        if (position == mFilterPosition || position == mSortPosition) {
            mFilterList.setItemChecked(position, true);
            return;
        }
        if (position == ListView.INVALID_POSITION) {
            SparseBooleanArray checked = mFilterList.getCheckedItemPositions();
            for (int i = 0; i < checked.size(); i++) {
                int pos = checked.keyAt(i);
                if (checked.get(pos)) {
                    mFilterList.setItemChecked(pos, false);
                }
            }

            mFilterPosition = ListView.INVALID_POSITION;
            mSortPosition = ListView.INVALID_POSITION;
            mOrderPosition = ListView.INVALID_POSITION;
            mDirectoryPosition = ListView.INVALID_POSITION;
        } else {
            ListItem item = mFilterAdapter.getItem(position);
            TorrentListFragment fragment =
                    ((TorrentListFragment) getFragmentManager().findFragmentById(R.id.torrent_list));

            switch(item.getType()) {
                case FILTER:
                    mFilterList.setItemChecked(mFilterPosition, false);
                    mFilterList.setItemChecked(position, true);
                    mFilterPosition = position;
                    fragment.setListFilter((FilterBy) item.getValue());
                    break;
                case DIRECTORY:
                    mFilterList.setItemChecked(mDirectoryPosition, false);
                    if (mDirectoryPosition == position) {
                        mDirectoryPosition = ListView.INVALID_POSITION;
                        fragment.setListFilter((String) null);
                    } else {
                        mFilterList.setItemChecked(position, true);
                        mDirectoryPosition = position;
                        fragment.setListFilter(item.getValueString());
                    }
                    break;
                case SORT_BY:
                    mFilterList.setItemChecked(mSortPosition, false);
                    mFilterList.setItemChecked(position, true);
                    mSortPosition = position;
                    fragment.setListFilter((SortBy) item.getValue());
                    break;
                case SORT_ORDER:
                    if (mOrderPosition == position) {
                        mOrderPosition = ListView.INVALID_POSITION;
                        mFilterList.setItemChecked(position, false);
                        fragment.setListFilter(SortOrder.ASCENDING);
                    } else {
                        mOrderPosition = position;
                        mFilterList.setItemChecked(position, true);
                        fragment.setListFilter((SortOrder) item.getValue());
                    }
                    break;
                default:
                    return;
            }
        }

        ((SlidingFragmentActivity) getActivity()).showContent();
    }

    private void fillMenuItems() {
        ArrayList<ListItem> list = new ArrayList<ListItem>();

        mFilterAdapter.setNotifyOnChange(false);
        mFilterAdapter.clear();

        for (FilterBy filter : FilterBy.values()) {
            ListItem item;
            if (mListItemMap.containsKey(filter.name())) {
                item = mListItemMap.get(filter.name());
            } else {
                int string = -1;
                String pref = null;
                switch(filter) {
                    case ALL:
                        string = R.string.menu_filters_all;
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
                }
                item = new ListItem(Type.FILTER, filter, string, pref);
            }
            if (mSharedPrefs.getBoolean(item.getPreferenceKey(), true)) {
                list.add(item);
            }
        }
        ListItem header;
        if (mListItemMap.containsKey(FILTERS_HEADER_KEY)) {
            header = mListItemMap.get(FILTERS_HEADER_KEY);
        } else {
            header = new ListItem(Type.HEADER, FILTERS_HEADER_KEY, R.string.menu_filters_header);
        }

        if (list.size() > 1) {
            mFilterAdapter.add(header);
            mFilterAdapter.addAll(list);
        }
        list.clear();

        if (!mListItemMap.containsKey(DIRECTORIES_HEADER_KEY)) {
            header = new ListItem(Type.HEADER, DIRECTORIES_HEADER_KEY, R.string.menu_directories_header);
        }

        int index = 0;
        for (SortBy sort : SortBy.values()) {
            ListItem item;
            if (mListItemMap.containsKey(sort.name())) {
                item = mListItemMap.get(sort.name());
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
            if (mSharedPrefs.getBoolean(item.getPreferenceKey(), true) && (index == 0 || index % 3 != 0)) {
                list.add(item);
            }
            index++;
        }
        if (mListItemMap.containsKey(SORT_BY_HEADER_KEY)) {
            header = mListItemMap.get(SORT_BY_HEADER_KEY);
        } else {
            header = new ListItem(Type.HEADER, SORT_BY_HEADER_KEY, R.string.menu_sort_header);
        }

        if (list.size() > 1) {
            mFilterAdapter.add(header);
            mFilterAdapter.addAll(list);

            if (mListItemMap.containsKey(SORT_ORDER_HEADER_KEY)) {
                header = mListItemMap.get(SORT_ORDER_HEADER_KEY);
            } else {
                header = new ListItem(Type.HEADER, SORT_ORDER_HEADER_KEY, R.string.menu_order_header);
            }
            mFilterAdapter.add(header);
            ListItem item;
            if (mListItemMap.containsKey(SortOrder.DESCENDING.name())) {
                item = mListItemMap.get(SortOrder.DESCENDING.name());
            } else {
                item = new ListItem(Type.SORT_ORDER, SortOrder.DESCENDING, R.string.menu_order_descending, null);
            }
            mFilterAdapter.add(item);
        }
        list.clear();

        mFilterAdapter.notifyDataSetChanged();
    }

    private void checkSelectedItems() {
        SparseBooleanArray checked = mFilterList.getCheckedItemPositions();
        for (int i = 0; i < checked.size(); i++) {
            int position = checked.keyAt(i);
            if (checked.get(position)) {
                mFilterList.setItemChecked(position, false);
            }
        }

        FilterBy selectedFilter = FilterBy.ALL;
        if (mSharedPrefs.contains(G.PREF_LIST_FILTER)) {
            try {
                selectedFilter = FilterBy.valueOf(
                    mSharedPrefs.getString(G.PREF_LIST_FILTER, "")
                );
            } catch (Exception e) { }
        }
        mFilterPosition = mFilterAdapter.getPosition(
                mListItemMap.get(selectedFilter.name()));
        mFilterList.setItemChecked(mFilterPosition, true);

        SortBy selectedSort = SortBy.STATUS;
        if (mSharedPrefs.contains(G.PREF_LIST_SORT_BY)) {
            try {
                selectedSort = SortBy.valueOf(
                    mSharedPrefs.getString(G.PREF_LIST_SORT_BY, "")
                );
            } catch (Exception e) { }
        }
        mSortPosition = mFilterAdapter.getPosition(
                mListItemMap.get(selectedSort.name()));
        mFilterList.setItemChecked(mSortPosition, true);

        SortOrder selectedOrder = SortOrder.DESCENDING;
        if (mSharedPrefs.contains(G.PREF_LIST_SORT_ORDER)) {
            try {
                selectedOrder = SortOrder.valueOf(
                    mSharedPrefs.getString(G.PREF_LIST_SORT_ORDER, "")
                );
            } catch (Exception e) { }
        }
        if (selectedOrder == SortOrder.DESCENDING) {
            mOrderPosition = mFilterAdapter.getPosition(
                    mListItemMap.get(selectedOrder.name()));
            mFilterList.setItemChecked(mOrderPosition, true);
        } else {
            mOrderPosition = ListView.INVALID_POSITION;
        }

        String selectedDirectory = null;
        if (mSharedPrefs.contains(G.PREF_LIST_DIRECTORY)) {
            selectedDirectory = mSharedPrefs.getString(G.PREF_LIST_DIRECTORY, null);
        }
        if (selectedDirectory != null) {
            mDirectoryPosition = mFilterAdapter.getPosition(
                    mListItemMap.get(selectedDirectory));
            mFilterList.setItemChecked(mDirectoryPosition, true);
        }
    }

    private ListItem getDirectoryItem(String directory) {
        ListItem item;
        if (mListItemMap.containsKey(directory)) {
            item = mListItemMap.get(directory);
        } else {
            String name = directory;
            int lastSlash = directory.lastIndexOf('/');
            if (lastSlash > -1) {
                name = directory.substring(lastSlash + 1);
            }
            item = new ListItem(Type.DIRECTORY, directory, name, G.PREF_FILTER_DIRECTORIES);
        }

        return item;
    }

    private static class FilterAdapter extends ArrayAdapter<ListItem> {
        public static final int ITEM_TYPE_HEADER = 0;
        public static final int ITEM_TYPE_NORMAL = 1;

        private static final int ITEM_TYPE_COUNT = 2;

        private LayoutInflater mInflater;

        private final static int mHeaderLayout = R.layout.filter_list_header;
        private final static int mItemLayout = R.layout.filter_list_item;
        private final static int mViewId = android.R.id.text1;

        public FilterAdapter(Context context) {
            super(context, mViewId);
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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

            TextView text = null;

            if (convertView == null) {
                switch (item.getType()) {
                    case HEADER:
                        convertView = mInflater.inflate(mHeaderLayout, parent, false);
                        break;

                    case FILTER:
                    case DIRECTORY:
                    case SORT_BY:
                    case SORT_ORDER:
                        convertView = mInflater.inflate(mItemLayout, parent, false);
                        break;
                }
            }

            text = (TextView) convertView.findViewById(mViewId);
            text.setText(item.getLabel());

            return convertView;
        }
    }

    private class ListItem {
        private Type mType;
        private Enum<?> mValue;
        private String mValueString;
        private String mLabel;
        private String mPref;

        public ListItem(Type type, Enum<?> value, int stringId,
                String pref) {
            mType = type;
            mValue = value;
            mLabel = getString(stringId);
            mPref = pref;

            mListItemMap.put(value.name(), this);
        }

        public ListItem(Type type, String value, String label,
                String pref) {
            mType = type;
            mLabel = label;
            mValueString = value;
            mPref = pref;

            mListItemMap.put(value, this);
        }

        public ListItem(Type type, String value, int stringId) {
            mType = type;
            mLabel = getString(stringId);
            mValueString = value;

            mListItemMap.put(value, this);
        }

        public Type getType() {
            return mType;
        }

        public Enum<?> getValue() {
            return mValue;
        }

        public String getValueString() {
            return mValue == null ? mValueString : mValue.name();
        }

        public String getLabel() {
            return mLabel;
        }

        public String getPreferenceKey() {
            return mPref;
        }
    }
}
