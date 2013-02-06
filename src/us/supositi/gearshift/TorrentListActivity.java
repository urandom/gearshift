package us.supositi.gearshift;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import us.supositi.gearshift.TransmissionSessionManager.TransmissionExclusionStrategy;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.ListView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.slidingmenu.lib.SlidingMenu;
import com.slidingmenu.lib.app.SlidingFragmentActivity;

/**
 * An activity representing a list of Torrents. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link TorrentDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 * <p>
 * The activity makes heavy use of fragments. The list of items is a
 * {@link TorrentListFragment} and the item details
 * (if present) is a {@link TorrentDetailFragment}.
 * <p>
 * This activity also implements the required
 * {@link TorrentListFragment.Callbacks} interface
 * to listen for item selections.
 */
public class TorrentListActivity extends SlidingFragmentActivity
        implements TransmissionSessionInterface, TorrentListFragment.Callbacks {
    
    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;

    private ViewPager mPager;
    
    private static final boolean DEBUG = true;
    private static final String LogTag = "GearShift";
    
    private ArrayList<Torrent> mTorrents = new ArrayList<Torrent>();
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_torrent_list);
        
        PreferenceManager.setDefaultValues(this, R.xml.general_preferences, false);
        
        if (findViewById(R.id.torrent_detail_pager) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-large and
            // res/values-sw600dp). If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;

        	mPager = (ViewPager) findViewById(R.id.torrent_detail_pager);
        	mPager.setAdapter(new TorrentDetailPagerAdapter(this));
        	mPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
        		public void onPageSelected(int position) {
        			((TorrentListFragment) getSupportFragmentManager()
        					.findFragmentById(R.id.torrent_list))
        				.getListView().setItemChecked(position, true);
        		}
        	});

            // In two-pane mode, list items should be given the
            // 'activated' state when touched.
            ((TorrentListFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.torrent_list))
                    .setActivateOnItemClick(true);

            toggleRightPane(false);
        }
        
        setBehindContentView(R.layout.sliding_menu_frame);
        
        SlidingMenu sm = getSlidingMenu();
        sm.setMode(SlidingMenu.LEFT);
        sm.setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);
        sm.setBehindWidthRes(R.dimen.sliding_menu_offset);
        sm.setShadowWidthRes(R.dimen.shadow_width);
        sm.setShadowDrawable(R.drawable.shadow);

        setSlidingActionBarEnabled(false);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        // TODO: If exposing deep links into your app, handle intents here.
    }

    /**
     * Callback method from {@link TorrentListFragment.Callbacks}
     * indicating that the item with the given ID was selected.
     */
    @Override
    public void onItemSelected(Torrent torrent) {
        if (mTwoPane) {
            toggleRightPane(true);
            mPager.setCurrentItem(mTorrents.indexOf(torrent));
        } else {
            // In single-pane mode, simply start the detail activity
            // for the selected item ID.
            Intent detailIntent = new Intent(this, TorrentDetailActivity.class);
            detailIntent.putExtra(TorrentDetailActivity.ARG_TORRENT_ID, torrent.getId());
            Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
            detailIntent.putExtra(TorrentDetailActivity.ARG_JSON_TORRENTS,
                    gson.toJson(mTorrents.toArray(new Torrent[mTorrents.size()])));
            startActivity(detailIntent);
        }
    }
    
    @Override
    public void onBackPressed() {
    	TorrentListFragment fragment = ((TorrentListFragment) getSupportFragmentManager()
				.findFragmentById(R.id.torrent_list));
    	
    	int position = fragment.getListView().getCheckedItemPosition();
    	if (position == ListView.INVALID_POSITION) {
    		super.onBackPressed();
    	} else {
        	toggleRightPane(false);
    		fragment.getListView().setItemChecked(position, false);
    	}
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case android.R.id.home:
            if (!mTwoPane || getSlidingMenu().isMenuShowing()) {
                toggle();
                return true;
            }
            
            TorrentListFragment fragment = ((TorrentListFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.torrent_list));
            
            int position = fragment.getListView().getCheckedItemPosition();
            if (position == ListView.INVALID_POSITION) {
                toggle();
                return true;
            } else {
                toggleRightPane(false);
                fragment.getListView().setItemChecked(position, false);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }
    
    public boolean isDetailsPanelShown() {
        return mTwoPane && findViewById(R.id.torrent_detail_panel).getVisibility() == View.VISIBLE;
    }
    
    private void toggleRightPane(boolean show) {
        if (!mTwoPane) return;
        
        ViewGroup panel = (ViewGroup) findViewById(R.id.torrent_detail_panel);
        List<TorrentDetailFragment> fragments = ((TorrentDetailPagerAdapter) mPager.getAdapter()).getFragments();
        
        if (show) {
            if (panel.getVisibility() != View.VISIBLE) {
                panel.setVisibility(View.VISIBLE);
                LayoutAnimationController controller = AnimationUtils.loadLayoutAnimation(
                        this, R.anim.layout_slide_right);
                panel.setLayoutAnimation(controller);
                getSlidingMenu().setTouchModeAbove(SlidingMenu.TOUCHMODE_NONE);
                for (TorrentDetailFragment fragment : fragments) {
                    if (fragment != null)
                        fragment.setHasOptionsMenu(true);
                }
            }
        } else {
            if (panel.getVisibility() != View.GONE) {
                panel.setVisibility(View.GONE);
                getSlidingMenu().setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);
                for (TorrentDetailFragment fragment : fragments) {
                    if (fragment != null)
                        fragment.setHasOptionsMenu(false);
                }
            }
        }
    }
    
    public static void logE(String message, Object[] args, Exception e) {
        Log.e(LogTag, String.format(message, args), e);
    }
    
    public static void logE(String message, Exception e) {
        Log.e(LogTag, message, e);
    }
    
    public static void logD(String message, Object[] args) {
        if (!DEBUG) return;
        
        Log.d(LogTag, String.format(message, args));
    }
    
    public static void logD(String message) {
        if (!DEBUG) return;
        
        Log.d(LogTag, message);
    }
    
    public static void logDTrace() {
        if (!DEBUG) return;
        
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        
        Throwable t = new Throwable();
        
        t.printStackTrace(pw);
        Log.d(LogTag, sw.toString());
    }

    @Override
    public void setTorrents(ArrayList<Torrent> torrents) {
        if (torrents == null) {
            mTorrents.clear();
        } else {
            mTorrents.addAll(torrents);
        }
    }

    @Override
    public ArrayList<Torrent> getTorrents() {
        return mTorrents;
    }
}
