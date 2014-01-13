package org.sugr.gearshift;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentStatePagerAdapter;

public class TorrentDetailPagerAdapter extends FragmentStatePagerAdapter {
    private int count;

	public TorrentDetailPagerAdapter(FragmentActivity activity, int count) {
	    super(activity.getSupportFragmentManager());

	    this.count = count;
	}

	@Override
	public int getCount() {
        return count;
	}

	@Override
	public Fragment getItem(int position) {
		TorrentDetailPageFragment fragment = new TorrentDetailPageFragment();
		Bundle arguments = new Bundle();
        arguments.putInt(G.ARG_PAGE_POSITION, position);
        fragment.setArguments(arguments);

		return fragment;
	}
}
