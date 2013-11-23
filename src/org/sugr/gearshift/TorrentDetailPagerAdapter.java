package org.sugr.gearshift;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;

public class TorrentDetailPagerAdapter extends FragmentStatePagerAdapter {
    private TransmissionSessionInterface mContext;

	public TorrentDetailPagerAdapter(FragmentActivity activity) {
	    super(activity.getSupportFragmentManager());

	    mContext = (TransmissionSessionInterface) activity;
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
        arguments.putInt(G.ARG_PAGE_POSITION, position);
        fragment.setArguments(arguments);

		return fragment;
	}

    public TorrentDetailPageFragment getFragment(ViewPager container, int position) {
        String name = makeFragmentName(container.getId(), position);
        FragmentManager manager = ((FragmentActivity) mContext).getSupportFragmentManager();

        if (manager == null) {
            return null;
        } else {
            return (TorrentDetailPageFragment) manager.findFragmentByTag(name);
        }
    }

    private static String makeFragmentName(int viewId, int index) {
        return "android:switcher:" + viewId + ":" + index;
    }
}
