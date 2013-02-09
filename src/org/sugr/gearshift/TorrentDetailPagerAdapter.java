package org.sugr.gearshift;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentStatePagerAdapter;

public class TorrentDetailPagerAdapter extends FragmentStatePagerAdapter {
    private TransmissionSessionInterface mContext;

    public TorrentDetailPagerAdapter(FragmentActivity activity) {
        super(activity.getSupportFragmentManager());

        if (activity instanceof TransmissionSessionInterface) {
            mContext = (TransmissionSessionInterface) activity;
        }
    }

    @Override
    public int getCount() {
        if (mContext == null) {
            return 0;
        } else {
            return mContext.getTorrents().size();
        }
    }

    @Override
    public Fragment getItem(int position) {
        TorrentDetailPageFragment fragment = new TorrentDetailPageFragment();
        Bundle arguments = new Bundle();
        arguments.putInt(TorrentDetailFragment.ARG_PAGE_POSITION, position);
        fragment.setArguments(arguments);

        return fragment;
    }
}
