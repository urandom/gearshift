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
    private int mActivatedPosition = ListView.INVALID_POSITION;
    private static final String STATE_ACTIVATED_POSITION = "filter_activated_position";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        Context context = new ContextThemeWrapper(getActivity(), android.R.style.Theme_Holo);
        LayoutInflater localInflater = inflater.cloneInContext(context);

        View root = localInflater.inflate(R.layout.fragment_torrent_list_menu, container, false);

        mFilterList = (ListView) root.findViewById(R.id.filter_list);

        View header = localInflater.inflate(R.layout.menu_list_header, mFilterList, false);
        mFilterList.addHeaderView(header);

        View footer = localInflater.inflate(R.layout.menu_list_footer, mFilterList, false);
        mFilterList.addFooterView(footer);

        /* TODO: The list items should have a count that indicates
         *  how many torrents are matched by the filter */
        mFilterList.setAdapter(new ArrayAdapter<String>(context,
                R.layout.filter_list_item,
                android.R.id.text1,
                getResources().getStringArray(R.array.filter_list_entries)));

        mFilterList.setDivider(null);
        mFilterList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        mFilterList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                setActivatedPosition(position);
            }

        });

        if (savedInstanceState != null
                && savedInstanceState.containsKey(STATE_ACTIVATED_POSITION)) {
            setActivatedPosition(savedInstanceState.getInt(STATE_ACTIVATED_POSITION));
        } else {
            mActivatedPosition = mFilterList.getHeaderViewsCount();
            mFilterList.setItemChecked(mActivatedPosition, true);
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
        if (mActivatedPosition != ListView.INVALID_POSITION) {
            // Serialize and persist the activated item position.
            outState.putInt(STATE_ACTIVATED_POSITION, mActivatedPosition);
        }
    }

    public void notifyTorrentListUpdate(ArrayList<Torrent> torrents, TransmissionSession session) {
        long down = 0, up = 0;

        for (Torrent t : torrents) {
            down += t.getRateDownload();
            up += t.getRateUpload();
        }

        Object[] speed = {
            Torrent.readableFileSize(down), "",
            Torrent.readableFileSize(up), ""
        };
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
        if (position == ListView.INVALID_POSITION) {
            mFilterList.setItemChecked(mActivatedPosition, false);
        } else {
            mFilterList.setItemChecked(position, true);
            String value = getResources().getStringArray(R.array.filter_list_entry_values)
                    [position - mFilterList.getHeaderViewsCount()];
            ((TorrentListFragment) getFragmentManager().findFragmentById(R.id.torrent_list)).setListFilter(value);
        }

        mActivatedPosition = position;
        ((SlidingFragmentActivity) getActivity()).showContent();
    }

}
