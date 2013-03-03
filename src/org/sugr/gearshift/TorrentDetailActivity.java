package org.sugr.gearshift;

import java.util.ArrayList;
import java.util.Arrays;

import org.sugr.gearshift.TransmissionSessionManager.TransmissionExclusionStrategy;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
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
public class TorrentDetailActivity extends FragmentActivity implements TransmissionSessionInterface,
       TorrentDetailFragment.PagerCallbacks {
    public static final String ARG_PROFILE = "profile";
    public static final String ARG_JSON_TORRENTS = "json_torrents";
    public static final String ARG_JSON_SESSION = "json_session";

    private ArrayList<Torrent> mTorrents = new ArrayList<Torrent>();
    private int mCurrentTorrent = 0;

    private TransmissionProfile mProfile;
    private TransmissionSession mSession;

    /* TODO: create transmissionsessionloader and callback */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent in = getIntent();

        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        mTorrents = new ArrayList<Torrent>(Arrays.asList(
                gson.fromJson(in.getStringExtra(ARG_JSON_TORRENTS), Torrent[].class)));
        mProfile = in.getParcelableExtra(ARG_PROFILE);
        mSession = gson.fromJson(in.getStringExtra(ARG_JSON_SESSION), TransmissionSession.class);


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
            mCurrentTorrent = in.getIntExtra(TorrentDetailFragment.ARG_PAGE_POSITION, 0);
            Bundle arguments = new Bundle();
            arguments.putInt(TorrentDetailFragment.ARG_PAGE_POSITION,
                    mCurrentTorrent);
            TorrentDetailFragment fragment = new TorrentDetailFragment();
            fragment.setArguments(arguments);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.torrent_detail_container, fragment)
                    .commit();
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
        mTorrents.clear();
        if (torrents != null) {
            mTorrents.addAll(torrents);
        }
    }

    @Override
    public ArrayList<Torrent> getTorrents() {
        return mTorrents;
    }

    @Override
    public Torrent[] getCurrentTorrents() {
        int current = mCurrentTorrent;
        int offscreen = 1;
        int count = offscreen * 2 + 1;
        if (current == mTorrents.size() - 1) {
            count--;
        }

        Torrent torrents[] = new Torrent[count];

        for (int i = (current == 0 ? 1 : 0); i < count; i++) {
            int position = current + i - offscreen;
            Torrent t = mTorrents.get(position);

            torrents[i] = t;
        }

        return torrents;
    }

    @Override
    public void onPageSelected(int position) {
        mCurrentTorrent = position;
    }

    @Override
    public void setProfile(TransmissionProfile profile) {
        mProfile = profile;
    }

    @Override
    public TransmissionProfile getProfile() {
        return mProfile;
    }

    @Override
    public void setSession(TransmissionSession session) {
        mSession = session;
    }

    @Override
    public TransmissionSession getSession() {
        return mSession;
    }
}
