package us.supositi.gearshift;

import us.supositi.gearshift.dummy.DummyContent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

public class TorrentDetailPagerAdapter extends FragmentStatePagerAdapter {
	
	public TorrentDetailPagerAdapter(FragmentManager fm) {
		super(fm);
	}
	
	@Override
	public int getCount() {
		return DummyContent.ITEMS.size();
	}
	
	@Override
	public Fragment getItem(int position) {
		TorrentDetailFragment fragment = new TorrentDetailFragment();
		Bundle arguments = new Bundle();
        arguments.putString(TorrentDetailFragment.ARG_ITEM_ID, DummyContent.ITEMS.get(position).id);
        fragment.setArguments(arguments);
        
		return fragment;
	}
}
