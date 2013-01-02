package us.supositi.gearshift;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;

public class TorrentDetailTabListener<T extends Fragment> implements TabListener {
    private Fragment mFragment;
    private final FragmentActivity mActivity;
    private final String mTag;
    private final Class<T> mClass;
    
    public TorrentDetailTabListener(FragmentActivity activity, String tag, Class<T> clz) {
        mActivity = activity;
        mTag = tag;
        mClass = clz;
    }
    
    public static void addTabs(FragmentActivity activity) {
        ActionBar actionBar = activity.getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        
        Tab tab = actionBar.newTab().setText(R.string.torrent_overview)
                .setTabListener(new TorrentDetailTabListener<TorrentDetailOverviewFragment>(
                        activity, "overview", TorrentDetailOverviewFragment.class));
        
        actionBar.addTab(tab);
        
        
        tab = actionBar.newTab().setText(R.string.torrent_limits)
                .setTabListener(new TorrentDetailTabListener<TorrentDetailOverviewFragment>(
                        activity, "limits", TorrentDetailOverviewFragment.class));
        
        actionBar.addTab(tab);
        
        
        tab = actionBar.newTab().setText(R.string.torrent_advanced)
                .setTabListener(new TorrentDetailTabListener<TorrentDetailOverviewFragment>(
                        activity, "advanced", TorrentDetailOverviewFragment.class));
        
        actionBar.addTab(tab);
    }

    @Override
    public void onTabReselected(Tab tab, android.app.FragmentTransaction unused) {
        FragmentTransaction ft = mActivity.getSupportFragmentManager().beginTransaction();
        if (mFragment == null) {
            mFragment = Fragment.instantiate(mActivity, mClass.getName());
            ft.add(R.id.torrent_detail_container, mFragment, mTag);
        } else {
            ft.attach(mFragment);
        }        
    }

    @Override
    public void onTabSelected(Tab tab, android.app.FragmentTransaction unused) {
        FragmentTransaction ft = mActivity.getSupportFragmentManager().beginTransaction();
        if (mFragment != null)
            ft.detach(mFragment);
    }

    @Override
    public void onTabUnselected(Tab tab, android.app.FragmentTransaction unused) {        
    }
}
