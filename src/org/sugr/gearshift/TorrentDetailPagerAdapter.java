package org.sugr.gearshift;

import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

public class TorrentDetailPagerAdapter extends FragmentStatePagerAdapter {
    private ArrayList<TorrentDetailPageFragment> mFragments = new ArrayList<TorrentDetailPageFragment>();
    private TransmissionSessionInterface mContext;
    private FragmentManager mFragmentManager;

	public TorrentDetailPagerAdapter(FragmentActivity activity) {
	    super(activity.getSupportFragmentManager());
	    mFragmentManager = activity.getSupportFragmentManager();

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

        while (mFragments.size() <= position)
            mFragments.add(null);
        mFragments.set(position, fragment);

		return fragment;
	}

	@Override
    public void restoreState(Parcelable state, ClassLoader loader) {
        if (state != null) {
            Bundle bundle = (Bundle)state;
            bundle.setClassLoader(loader);
            mFragments.clear();

            Iterable<String> keys = bundle.keySet();
            for (String key: keys) {
                if (key.startsWith("f")) {
                    int index = Integer.parseInt(key.substring(1));
                    Fragment f = mFragmentManager.getFragment(bundle, key);
                    if (f != null) {
                        while (mFragments.size() <= index) {
                            mFragments.add(null);
                        }
                        f.setMenuVisibility(false);
                        mFragments.set(index, (TorrentDetailPageFragment) f);
                    }
                }
            }
        }
        super.restoreState(state, loader);
    }

	public List<TorrentDetailPageFragment> getFragments() {
	    return mFragments;
	}
}
