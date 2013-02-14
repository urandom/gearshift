package org.sugr.gearshift;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

public class TorrentDetailFragment extends Fragment {
    public static final String TAG = "detail_fragment";
    public static final String ARG_PAGE_POSITION = "page_position";
    public interface PagerCallbacks {
        public void onPageSelected(int position);
    }

    private PagerCallbacks mCallbacks = sDummyCallbacks;
    private ViewPager mPager;
    private int mCurrentTorrentId = -1;
    private int mCurrentPosition = -1;

    private boolean mExpectingPause = false;
    private boolean mExpectingResume = false;

    private static PagerCallbacks sDummyCallbacks = new PagerCallbacks() {
        @Override
        public void onPageSelected(int position) { }
    };
    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public TorrentDetailFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null)
            setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.fragment_torrent_detail, container, false);

        mPager = (ViewPager) root.findViewById(R.id.torrent_detail_pager);
        mPager.setAdapter(new TorrentDetailPagerAdapter(getActivity()));
        mPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mCurrentPosition = position;
                ArrayList<Torrent> torrents = ((TransmissionSessionInterface) getActivity()).getTorrents();
                mCurrentTorrentId = torrents.size() > position
                    ? torrents.get(position).getId()
                    : -1;

                mCallbacks.onPageSelected(position);
                getActivity().invalidateOptionsMenu();
            }
        });

        if (getArguments().containsKey(ARG_PAGE_POSITION)) {
            mCurrentPosition = getArguments().getInt(ARG_PAGE_POSITION);
            mPager.setCurrentItem(mCurrentPosition);
            ArrayList<Torrent> torrents = ((TransmissionSessionInterface) getActivity()).getTorrents();
            mCurrentTorrentId = torrents.size() > mCurrentPosition
                ? torrents.get(mCurrentPosition).getId()
                : -1;
        }


        return root;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Activities containing this fragment must implement its callbacks.
        if (!(activity instanceof PagerCallbacks)) {
            throw new IllegalStateException("Activity must implement fragment's callbacks.");
        }

        mCallbacks = (PagerCallbacks) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();

        // Reset the active callbacks interface to the dummy implementation.
        mCallbacks = sDummyCallbacks;
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.torrent_detail_fragment, menu);

        ArrayList<Torrent> torrents = ((TransmissionSessionInterface) getActivity()).getTorrents();
        Torrent torrent = mCurrentPosition < torrents.size()
            ? torrents.get(mCurrentPosition)
            : null;

        boolean state = torrent != null && torrent.getStatus() == Torrent.Status.STOPPED;
        MenuItem item = menu.findItem(R.id.resume);
        item.setVisible(state).setEnabled(state);

        state = torrent != null && torrent.getStatus() != Torrent.Status.STOPPED;
        item = menu.findItem(R.id.pause);
        item.setVisible(state).setEnabled(state);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final Loader<TransmissionSessionData> loader = getActivity().getSupportLoaderManager()
            .getLoader(TorrentListActivity.SESSION_LOADER_ID);

        ArrayList<Torrent> torrents = ((TransmissionSessionInterface) getActivity()).getTorrents();
        Torrent torrent = torrents.size() > mCurrentPosition
            ? torrents.get(mCurrentPosition) : null;

        if (loader == null || torrent == null)
            return super.onOptionsItemSelected(item);

        final int[] ids = new int[] {mCurrentTorrentId};

        switch (item.getItemId()) {
            case R.id.remove:
            case R.id.delete:
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setCancelable(false)
                    .setNegativeButton(android.R.string.no, null);

                builder.setPositiveButton(android.R.string.yes,
                    new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        ((TransmissionSessionLoader) loader).setTorrentsRemove(ids, item.getItemId() == R.id.delete);
                    }
                })
                    .setMessage(String.format(getString(
                                    item.getItemId() == R.id.delete
                            ? R.string.delete_current_confirmation
                            : R.string.remove_current_confirmation),
                                torrent.getName()))
                .show();
                break;
            case R.id.resume:
                mExpectingResume = true;
                ((TransmissionSessionLoader) loader).setTorrentsAction("torrent-start", ids);
                break;
            case R.id.pause:
                mExpectingPause = true;
                ((TransmissionSessionLoader) loader).setTorrentsAction("torrent-stop", ids);
                break;
            default:
                return true;
        }

        /* TODO: use the action progress bar if present
        mRefreshing = true;
        */

        return true;
    }

    public void setCurrentTorrent(int position) {
        mPager.setCurrentItem(position);
    }

    public void notifyTorrentListChanged(boolean removed, boolean added) {
        ArrayList<Torrent> torrents = ((TransmissionSessionInterface) getActivity()).getTorrents();
        Torrent torrent = null;
        if (removed || added) {
            int index = 0;
            for (Torrent t : torrents) {
                if (t.getId() == mCurrentTorrentId) {
                    torrent = t;
                    break;
                }
                index++;
            }
            mPager.setAdapter(new TorrentDetailPagerAdapter(getActivity()));
            if (torrent != null) {
                mPager.setCurrentItem(index);
            } else {
                mPager.setCurrentItem(0);
            }
        }
        if (mExpectingPause || mExpectingResume) {
            if (torrent == null) {
                torrent = torrents.size() > mCurrentPosition
                    ? torrents.get(mCurrentPosition)
                    : null;
            }

            if (torrent != null) {
                if (mExpectingPause && torrent.getStatus() == Torrent.Status.STOPPED) {
                    mExpectingPause = false;
                    getActivity().invalidateOptionsMenu();
                }
                if (mExpectingResume && torrent.getStatus() != Torrent.Status.STOPPED) {
                    mExpectingResume = false;
                    getActivity().invalidateOptionsMenu();
                }
            }
        }
        Torrent[] currentTorrents = ((TransmissionSessionInterface) getActivity()).getCurrentTorrents();
        List<TorrentDetailPageFragment> pages = ((TorrentDetailPagerAdapter) mPager.getAdapter()).getFragments();
        for (Torrent t : currentTorrents) {
            int index = torrents.indexOf(t);
            TorrentDetailPageFragment page = pages.size() > index
                ? pages.get(index) : null;
            if (page == null) continue;
            page.notifyTorrentUpdate(t);
        }
    }
}
