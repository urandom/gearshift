package org.sugr.gearshift;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

public class TorrentDetailFragment extends Fragment {
    public static final String ARG_PAGE_POSITION = "page_position";
    public interface Callbacks {
        public void onPageSelected(int position);
    }

    private Callbacks mCallbacks = sDummyCallbacks;
    private ViewPager mPager;

    private static Callbacks sDummyCallbacks = new Callbacks() {
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
            public void onPageSelected(int position) {
                mCallbacks.onPageSelected(position);
            }
        });

        if (getArguments().containsKey(ARG_PAGE_POSITION)) {
            int position = getArguments().getInt(ARG_PAGE_POSITION);
            mPager.setCurrentItem(position);
        }


        return root;
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
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.torrent_detail_fragment, menu);

        /* FIXME: Set these states depending on the torrent state */
        MenuItem item = menu.findItem(R.id.resume);
        item.setVisible(false).setEnabled(false);

        item = menu.findItem(R.id.pause);
        item.setVisible(true).setEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.remove:
                return true;
            case R.id.delete:
                return true;
            case R.id.resume:
                return true;
            case R.id.pause:
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
