package org.sugr.gearshift;

import java.util.ArrayList;
import java.util.Arrays;

import org.sugr.gearshift.TransmissionSessionManager.TransmissionExclusionStrategy;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.support.v4.view.ViewPager;
import android.view.MenuItem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


/**
 * An activity representing a single Torrent detail screen. This
 * activity is only used on handset devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a {@link TorrentListActivity}.
 * <p>
 * This activity is mostly just a 'shell' activity containing nothing
 * more than a {@link TorrentDetailFragment}.
 */
public class TorrentDetailActivity extends FragmentActivity implements TransmissionSessionInterface {
    public static final String ARG_TORRENT_ID = "torrent_id";
    public static final String ARG_JSON_TORRENTS = "json_torrents";

	private ViewPager mPager;
	
    private ArrayList<Torrent> mTorrents = new ArrayList<Torrent>();
    
    /* TODO: create transmissionsessionloader and callback */
		
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_torrent_detail);

        // Show the Up button in the action bar.
        getActionBar().setDisplayHomeAsUpEnabled(true);
        
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        mTorrents = new ArrayList<Torrent>(Arrays.asList(
                gson.fromJson(getIntent().getStringExtra(ARG_JSON_TORRENTS), Torrent[].class)));
        
        mPager = (ViewPager) findViewById(R.id.torrent_detail_pager);
        mPager.setAdapter(new TorrentDetailPagerAdapter(this));

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
            int id = getIntent().getIntExtra(ARG_TORRENT_ID, 0);
            for (int i = 0; i < mTorrents.size(); ++i) {
                Torrent t = mTorrents.get(i);
                if (t.getId() == id) {
                    mPager.setCurrentItem(i);
                    break;
                }
            }
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
    
    @Override
    public Torrent[] getCurrentTorrents() {        
        int current = mPager.getCurrentItem();
        int offscreen = mPager.getOffscreenPageLimit(); 
        int count = offscreen * 2 + 1;
        Torrent torrents[] = new Torrent[count];
        
        for (int i = 0; i < count; i++) {
            int position = current + i - offscreen;
            Torrent t = mTorrents.get(position);
            
            torrents[i] = t;
        }

        return torrents;
    }
}
