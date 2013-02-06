package us.supositi.gearshift;

import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentStatePagerAdapter;

public class TorrentDetailPagerAdapter extends FragmentStatePagerAdapter {
    private ArrayList<TorrentDetailFragment> mFragments = new ArrayList<TorrentDetailFragment>();
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
		TorrentDetailFragment fragment = new TorrentDetailFragment();
		Bundle arguments = new Bundle();
		ArrayList<Torrent> torrents = mContext.getTorrents();
        arguments.putInt(TorrentDetailActivity.ARG_TORRENT_ID, torrents.get(position).getId());
        fragment.setArguments(arguments);
        
        while (mFragments.size() <= position)
            mFragments.add(null);
        mFragments.set(position, fragment);
        
		return fragment;
	}
	
	public List<TorrentDetailFragment> getFragments() {
	    return mFragments;
	}
}
