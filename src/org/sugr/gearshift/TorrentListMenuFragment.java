package org.sugr.gearshift;


import java.util.ArrayList;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
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
    private int mFilterPosition = ListView.INVALID_POSITION;
    private int mSortPosition = ListView.INVALID_POSITION;
    private boolean mOrderDescending = false;
    private static final String STATE_FILTER_POSITION = "filter_activated_position";
    private static final String STATE_SORT_POSITION = "sort_activated_position";
    private static final String STATE_ORDER_DESCENDING = "sort_order_descending";

    private int mFilterNameStart;
    private int mFilterNameLength;
    private int mFilterSortStart;
    private int mFilterSortLength;
    private int mFilterSortOrder;

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
        mFilterList.setAdapter(new FilterAdapter(context));

        mFilterList.setDivider(null);
        mFilterList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        mFilterList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                setActivatedPosition(position);
            }

        });

        mFilterNameStart = getResources().getInteger(R.integer.filter_name_start);
        mFilterNameLength = getResources().getInteger(R.integer.filter_name_length);
        mFilterSortStart = getResources().getInteger(R.integer.filter_sort_start);
        mFilterSortLength = getResources().getInteger(R.integer.filter_sort_length);
        mFilterSortOrder = getResources().getInteger(R.integer.filter_sort_order);

        if (savedInstanceState != null
                && savedInstanceState.containsKey(STATE_FILTER_POSITION)) {
            setActivatedPosition(savedInstanceState.getInt(STATE_FILTER_POSITION));
        } else {
            mFilterPosition = 1;
            mFilterList.setItemChecked(mFilterPosition, true);
        }
        if (savedInstanceState != null
                && savedInstanceState.containsKey(STATE_SORT_POSITION)) {
            setActivatedPosition(savedInstanceState.getInt(STATE_SORT_POSITION));
        } else {
            mSortPosition = mFilterSortStart + 2;
            mFilterList.setItemChecked(mSortPosition, true);
        }
        if (savedInstanceState != null
                && savedInstanceState.containsKey(STATE_ORDER_DESCENDING)
                && savedInstanceState.getBoolean(STATE_ORDER_DESCENDING)) {
            setActivatedPosition(mFilterSortOrder);
        }


        return root;
    }

    @Override
    public void onResume() {
        super.onResume();

        setStatus(null, null);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mFilterPosition != ListView.INVALID_POSITION) {
            // Serialize and persist the activated item position.
            outState.putInt(STATE_FILTER_POSITION, mFilterPosition);
        }
        if (mSortPosition != ListView.INVALID_POSITION) {
            // Serialize and persist the activated item position.
            outState.putInt(STATE_SORT_POSITION, mSortPosition);
        }
        outState.putBoolean(STATE_ORDER_DESCENDING, mOrderDescending);
    }

    public void notifyTorrentListUpdate(ArrayList<Torrent> torrents, TransmissionSession session) {
        long down = 0, up = 0;

        if (torrents != null) {
            for (Torrent t : torrents) {
                down += t.getRateDownload();
                up += t.getRateUpload();
            }
        }

        Object[] speed = {
            Torrent.readableFileSize(down), "",
            Torrent.readableFileSize(up), ""
        };

        if (session == null) {
            setStatus(speed, getString(R.string.unknown));
            return;
        }


        if (session.isSpeedLimitDownEnabled() || session.isAltSpeedEnabled()) {
            speed[1] = " (" + Torrent.readableFileSize((
                session.isAltSpeedEnabled()
                    ? session.getAltSpeedDown()
                    : session.getSpeedLimitDown()) * 1024) + "/s)";
        }
        if (session.isSpeedLimitUpEnabled() || session.isAltSpeedEnabled()) {
            speed[3] = " (" + Torrent.readableFileSize((
                session.isAltSpeedEnabled()
                    ? session.getAltSpeedUp()
                    : session.getSpeedLimitUp()) * 1024) + "/s)";
        }

        setStatus(speed,
            session.getDownloadDirFreeSpace() > 0
                ? Torrent.readableFileSize(session.getDownloadDirFreeSpace())
                : getString(R.string.unknown)
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
                            R.string.free_space_format), freeSpace)));
    }

    private void setActivatedPosition(int position) {
        if (position == mFilterPosition || position == mSortPosition) {
            mFilterList.setItemChecked(position, true);
            return;
        }
        if (position == ListView.INVALID_POSITION) {
            mFilterList.setItemChecked(mFilterPosition, false);
            mFilterList.setItemChecked(mSortPosition, false);
            mFilterList.setItemChecked(mFilterSortOrder, false);

            mFilterPosition = ListView.INVALID_POSITION;
            mSortPosition = ListView.INVALID_POSITION;
            mOrderDescending = false;
        } else {
            String value = getResources().getStringArray(R.array.filter_list_entry_values)
                    [position];

            if (position >= mFilterNameStart && position < mFilterNameStart + mFilterNameLength) {
                mFilterList.setItemChecked(mFilterPosition, false);
                mFilterList.setItemChecked(position, true);
                mFilterPosition = position;
            } else if (position >= mFilterSortStart && position < mFilterSortStart + mFilterSortLength) {
                mFilterList.setItemChecked(mSortPosition, false);
                mFilterList.setItemChecked(position, true);
                mSortPosition = position;
            } else if (position == mFilterSortOrder) {
                if (mOrderDescending) {
                    mFilterList.setItemChecked(position, false);
                    value = "sortorder:ascending";
                } else {
                    mFilterList.setItemChecked(position, true);
                }
                mOrderDescending = !mOrderDescending;
            }

            ((TorrentListFragment) getFragmentManager().findFragmentById(R.id.torrent_list)).setListFilter(value);
        }

        ((SlidingFragmentActivity) getActivity()).showContent();
    }

    private static class FilterAdapter extends ArrayAdapter<String> {
        public static final int ITEM_TYPE_HEADER = 0;
        public static final int ITEM_TYPE_NORMAL = 1;

        private static final int ITEM_TYPE_COUNT = 2;

        private LayoutInflater mInflater;

        private final static int mHeaderLayout = R.layout.filter_list_header;
        private final static int mItemLayout = R.layout.filter_list_item;
        private final static int mViewId = android.R.id.text1;
        private String[] mNames;
        private String[] mValues;

        public FilterAdapter(Context context) {
            super(context, mViewId, context.getResources().getStringArray(R.array.filter_list_entries));
            mNames = context.getResources().getStringArray(R.array.filter_list_entries);
            mValues = context.getResources().getStringArray(R.array.filter_list_entry_values);
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public int getItemViewType(int position) {
            if (mValues.length > position) {
                if (mValues[position].equals("")) {
                    return ITEM_TYPE_HEADER;
                } else {
                    return ITEM_TYPE_NORMAL;
                }
            } else {
                return -1;
            }
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return position < mNames.length
                ? getItemViewType(position) != ITEM_TYPE_HEADER
                : false;
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
            if (position >= mValues.length) {
                return convertView;
            }

            String name = mNames[position];
            int itemType = getItemViewType(position);
            TextView text = null;

            if (convertView == null) {
                switch (itemType) {
                    case ITEM_TYPE_HEADER:
                        convertView = mInflater.inflate(mHeaderLayout, parent, false);
                        break;


                    case ITEM_TYPE_NORMAL:
                        convertView = mInflater.inflate(mItemLayout, parent, false);
                        break;
                }
            }
            text = (TextView) convertView.findViewById(mViewId);
            text.setText(name);

            return convertView;
        }
    }
}
