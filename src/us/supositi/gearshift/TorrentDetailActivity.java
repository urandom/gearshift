package us.supositi.gearshift;

import us.supositi.gearshift.dummy.DummyContent;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.support.v4.view.ViewPager;
import android.view.MenuItem;

import us.supositi.gearshift.TorrentDetailPagerAdapter;


/**
 * An activity representing a single Torrent detail screen. This
 * activity is only used on handset devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a {@link TorrentListActivity}.
 * <p>
 * This activity is mostly just a 'shell' activity containing nothing
 * more than a {@link TorrentDetailFragment}.
 */
public class TorrentDetailActivity extends FragmentActivity {

	private ViewPager mPager;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_torrent_detail);

        // Show the Up button in the action bar.
        getActionBar().setDisplayHomeAsUpEnabled(true);

        // savedInstanceState is non-null when there is fragment state
        // saved from previous configurations of this activity
        // (e.g. when rotating the screen from portrait to landscape).
        // In this case, the fragment will automatically be re-added
        // to its container so we don't need to manually add it.
        // For more information, see the Fragments API guide at:
        //
        // http://developer.android.com/guide/components/fragments.html
        //
        if (savedInstanceState == null) {
        	mPager = (ViewPager) findViewById(R.id.torrent_detail_pager);
        	mPager.setAdapter(new TorrentDetailPagerAdapter(getSupportFragmentManager()));
        	
        	mPager.setCurrentItem(DummyContent.ITEMS.indexOf(
        			DummyContent.ITEM_MAP.get(getIntent().getStringExtra(TorrentDetailFragment.ARG_ITEM_ID))));        	
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // This ID represents the Home or Up button. In the case of this
                // activity, the Up button is shown. Use NavUtils to allow users
                // to navigate up one level in the application structure. For
                // more details, see the Navigation pattern on Android Design:
                //
                // http://developer.android.com/design/patterns/navigation.html#up-vs-back
                //
                NavUtils.navigateUpTo(this, new Intent(this, TorrentListActivity.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onBackPressed() {
    	if (mPager.getCurrentItem() == 0) {
    		super.onBackPressed();
    	} else {
    		mPager.setCurrentItem(mPager.getCurrentItem() - 1);
    	}
    }
}
