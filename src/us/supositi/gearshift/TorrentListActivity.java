package us.supositi.gearshift;

import us.supositi.gearshift.dummy.DummyContent;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

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
        implements TorrentListFragment.Callbacks {

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;

    private ViewPager mPager;
    

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_torrent_list);

        if (findViewById(R.id.torrent_detail_pager) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-large and
            // res/values-sw600dp). If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;
            
        	mPager = (ViewPager) findViewById(R.id.torrent_detail_pager);
        	mPager.setAdapter(new TorrentDetailPagerAdapter(getSupportFragmentManager()));
        	mPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
        		public void onPageSelected(int position) {
        			((TorrentListFragment) getSupportFragmentManager()
        					.findFragmentById(R.id.torrent_list))
        				.getListView().setItemChecked(position, true);
        		}
        	});
        	mPager.setVisibility(View.GONE);

            // In two-pane mode, list items should be given the
            // 'activated' state when touched.
            ((TorrentListFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.torrent_list))
                    .setActivateOnItemClick(true);
            
            setBehindContentView(R.layout.sliding_menu_frame);
            /* TODO: put a proper fragment in the layout */
            FragmentTransaction t = getSupportFragmentManager().beginTransaction();
            t.replace(R.id.sliding_menu_frame, new TorrentListFragment());
            t.commit();
            
            SlidingMenu sm = getSlidingMenu();
            sm.setMode(SlidingMenu.LEFT);
            sm.setTouchModeAbove(SlidingMenu.TOUCHMODE_MARGIN);
            sm.setBehindWidthRes(R.dimen.sliding_menu_offset);
            sm.setShadowWidthRes(R.dimen.shadow_width);
            sm.setShadowDrawable(R.drawable.shadow);

            setSlidingActionBarEnabled(false);
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // TODO: If exposing deep links into your app, handle intents here.
    }

    /**
     * Callback method from {@link TorrentListFragment.Callbacks}
     * indicating that the item with the given ID was selected.
     */
    @Override
    public void onItemSelected(String id) {
        if (mTwoPane) {
        	mPager.setVisibility(View.VISIBLE);
        	mPager.setCurrentItem(DummyContent.ITEMS.indexOf(
        			DummyContent.ITEM_MAP.get(id)));

        } else {
            // In single-pane mode, simply start the detail activity
            // for the selected item ID.
            Intent detailIntent = new Intent(this, TorrentDetailActivity.class);
            detailIntent.putExtra(TorrentDetailFragment.ARG_ITEM_ID, id);
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
        	mPager.setVisibility(View.GONE);
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
                mPager.setVisibility(View.GONE);
                fragment.getListView().setItemChecked(position, false);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }
}
