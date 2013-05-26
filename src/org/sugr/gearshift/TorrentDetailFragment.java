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
import android.widget.CheckBox;
import android.widget.Spinner;

public class TorrentDetailFragment extends Fragment {
    public static final String TAG = "detail_fragment";
    public static final String ARG_PAGE_POSITION = "page_position";
    public static final String ARG_SHOW_PAGER = "show_pager";
    public interface PagerCallbacks {
        public void onPageSelected(int position);
    }

    private PagerCallbacks mCallbacks = sDummyCallbacks;
    private ViewPager mPager;
    private int mCurrentTorrentId = -1;
    private int mCurrentPosition = 0;

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

        if (getArguments().containsKey(ARG_PAGE_POSITION)) {
            mCurrentPosition = getArguments().getInt(ARG_PAGE_POSITION);
            if (mCurrentPosition < 0) {
                mCurrentPosition = 0;
            }
            ArrayList<Torrent> torrents = ((TransmissionSessionInterface) getActivity()).getTorrents();
            mCurrentTorrentId = torrents.size() > mCurrentPosition
                ? torrents.get(mCurrentPosition).getId()
                : -1;
        }

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
                List<TorrentDetailPageFragment> pages = ((TorrentDetailPagerAdapter) mPager.getAdapter())
                        .getFragments();

                if (mCurrentPosition != -1 && pages.size() > mCurrentPosition) {
                    pages.get(mCurrentPosition).onPageUnselected();
                }
                mCurrentPosition = position;
                ArrayList<Torrent> torrents = ((TransmissionSessionInterface) getActivity()).getTorrents();
                mCurrentTorrentId = torrents.size() > position
                    ? torrents.get(position).getId()
                    : -1;

                mCallbacks.onPageSelected(position);
                getActivity().invalidateOptionsMenu();
            }
        });

        mPager.setCurrentItem(mCurrentPosition);

        if (getArguments().containsKey(ARG_SHOW_PAGER)) {
            if (getArguments().getBoolean(ARG_SHOW_PAGER)) {
                mPager.setVisibility(View.VISIBLE);
                mPager.setAlpha(1);
            }
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
        super.onCreateOptionsMenu(menu, inflater);

        if (!(getActivity() instanceof TorrentListActivity)
                || ((TorrentListActivity) getActivity()).isDetailPanelShown()) {
            inflater.inflate(R.menu.torrent_detail_fragment, menu);

            ArrayList<Torrent> torrents = ((TransmissionSessionInterface) getActivity()).getTorrents();
            Torrent torrent = mCurrentPosition < torrents.size()
                ? torrents.get(mCurrentPosition)
                : null;

            boolean resumeState = false;
            boolean pauseState = false;
            if (torrent != null) {
                switch (torrent.getStatus()) {
                    case Torrent.Status.STOPPED:
                    case Torrent.Status.DOWNLOAD_WAITING:
                    case Torrent.Status.SEED_WAITING:
                        resumeState = true;
                        pauseState = false;
                        break;
                    default:
                        resumeState = false;
                        pauseState = true;
                }
            }

            MenuItem item = menu.findItem(R.id.resume);
            item.setVisible(resumeState).setEnabled(resumeState);

            item = menu.findItem(R.id.pause);
            item.setVisible(pauseState).setEnabled(pauseState);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final Loader<TransmissionData> loader = getActivity().getSupportLoaderManager()
            .getLoader(G.SESSION_LOADER_ID);

        final TransmissionSessionInterface context = ((TransmissionSessionInterface) getActivity());
        ArrayList<Torrent> torrents = context.getTorrents();
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
                        ((TransmissionDataLoader) loader).setTorrentsRemove(ids, item.getItemId() == R.id.delete);
                        context.setRefreshing(true);
                    }
                })
                    .setMessage(String.format(getString(
                                    item.getItemId() == R.id.delete
                            ? R.string.delete_current_confirmation
                            : R.string.remove_current_confirmation),
                                torrent.getName()))
                .show();
                return true;
            case R.id.resume:
                String action;
                switch(torrent.getStatus()) {
                    case Torrent.Status.DOWNLOAD_WAITING:
                    case Torrent.Status.SEED_WAITING:
                        action = "torrent-start-now";
                        break;
                    default:
                        action = "torrent-start";
                        break;
                }
                ((TransmissionDataLoader) loader).setTorrentsAction(action, ids);
                break;
            case R.id.pause:
                ((TransmissionDataLoader) loader).setTorrentsAction("torrent-stop", ids);
                break;
            case R.id.move:
                LayoutInflater inflater = getActivity().getLayoutInflater();

                builder = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.set_location)
                    .setCancelable(false)
                    .setNegativeButton(android.R.string.no, null)
                    .setPositiveButton(android.R.string.yes,
                    new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        Spinner location = (Spinner) ((AlertDialog) dialog).findViewById(R.id.location_choice);
                        CheckBox move = (CheckBox) ((AlertDialog) dialog).findViewById(R.id.move);

                        String dir = (String) location.getSelectedItem();
                        ((TransmissionDataLoader) loader).setTorrentsLocation(
                                ids, dir, move.isChecked());

                        context.setRefreshing(true);
                    }
                }).setView(inflater.inflate(R.layout.torrent_location_dialog, null));

                AlertDialog dialog = builder.create();
                dialog.show();

                Spinner location;
                TransmissionProfileDirectoryAdapter adapter =
                        new TransmissionProfileDirectoryAdapter(
                        getActivity(), android.R.layout.simple_spinner_item);

                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                adapter.addAll(((TransmissionSessionInterface) getActivity()).getSession().getDownloadDirectories());
                adapter.sort();

                location = (Spinner) dialog.findViewById(R.id.location_choice);
                location.setAdapter(adapter);

                return true;
            case R.id.verify:
                ((TransmissionDataLoader) loader).setTorrentsAction("torrent-verify", ids);
                break;
            case R.id.reannounce:
                ((TransmissionDataLoader) loader).setTorrentsAction("torrent-reannounce", ids);
                break;
            default:
                return true;
        }

        context.setRefreshing(true);

        return true;
    }

    public void setCurrentTorrent(int position) {
        mPager.setCurrentItem(position);
    }

    public void notifyTorrentListChanged(boolean removed, boolean added, boolean status) {
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
                mCallbacks.onPageSelected(mPager.getCurrentItem());
            }
        }

        if (status) {
            getActivity().invalidateOptionsMenu();
        }

        Torrent[] currentTorrents = ((TransmissionSessionInterface) getActivity()).getCurrentTorrents();
        List<TorrentDetailPageFragment> pages = ((TorrentDetailPagerAdapter) mPager.getAdapter()).getFragments();
        for (Torrent t : currentTorrents) {
            int index = torrents.indexOf(t);
            if (index == -1) continue;
            TorrentDetailPageFragment page = pages.size() > index
                ? pages.get(index) : null;
            if (page == null) continue;
            page.notifyTorrentUpdate(t);
        }
    }
}
