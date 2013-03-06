package org.sugr.gearshift;

import java.util.ArrayList;
import java.util.Arrays;

import org.sugr.gearshift.TransmissionSessionManager.TransmissionExclusionStrategy;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.app.NavUtils;
import android.support.v4.content.Loader;
import android.view.Menu;
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

    private boolean mRefreshing = false;

    private ArrayList<Torrent> mTorrents = new ArrayList<Torrent>();
    private int mCurrentTorrent = 0;

    private TransmissionProfile mProfile;
    private TransmissionSession mSession;

    private LoaderCallbacks<TransmissionSessionData> mTorrentLoaderCallbacks = new LoaderCallbacks<TransmissionSessionData>() {

        @Override
        public android.support.v4.content.Loader<TransmissionSessionData> onCreateLoader(
                int id, Bundle args) {
            TorrentListActivity.logD("Starting the torrents loader with profile " + mProfile);
            if (mProfile == null) return null;

            TransmissionSessionLoader loader = new TransmissionSessionLoader(
                    TorrentDetailActivity.this, mProfile, mTorrents, getCurrentTorrents());

            return loader;
        }

        @Override
        public void onLoadFinished(
                android.support.v4.content.Loader<TransmissionSessionData> loader,
                TransmissionSessionData data) {

            if (data.session != null) {
                mSession = data.session;
                setSession(data.session);
            }
           /* if (data.stats != null)
                mSessionStats = data.stats;*/

            boolean invalidateMenu = false;

            if (data.torrents.size() > 0 || data.error > 0) {
                if (data.error == 0) {
                    if (data.hasRemoved) {
                        ArrayList<Torrent> removal = new ArrayList<Torrent>();
                        for (Torrent t : mTorrents) {
                            if (!data.torrents.contains(t)) {
                                removal.add(t);
                            }
                        }

                        for (Torrent t : removal) {
                            mTorrents.remove(t);
                        }

                        if (data.hasStatusChanged) {
                            invalidateMenu = true;
                        }
                    }
                } else {
                    /* FIXME: Handle the errors
                    if (data.error == TransmissionSessionData.Errors.NO_CONNECTIVITY) {
                        setEmptyText(R.string.no_connectivity_empty_list);
                    } else if (data.error == TransmissionSessionData.Errors.ACCESS_DENIED) {
                        setEmptyText(R.string.access_denied_empty_list);
                    } else if (data.error == TransmissionSessionData.Errors.NO_JSON) {
                        setEmptyText(R.string.no_json_empty_list);
                    } else if (data.error == TransmissionSessionData.Errors.NO_CONNECTION) {
                        setEmptyText(R.string.no_connection_empty_list);
                    } else if (data.error == TransmissionSessionData.Errors.THREAD_ERROR) {
                        setEmptyText(R.string.thread_error_empty_list);
                    }
                    */
                }
            }


            FragmentManager manager = getSupportFragmentManager();
            TorrentDetailFragment detail = (TorrentDetailFragment) manager.findFragmentByTag(
                    TorrentDetailFragment.TAG);
            if (detail != null) {
                detail.notifyTorrentListChanged(data.hasRemoved, false, data.hasStatusChanged);
            }

            if (mRefreshing) {
                mRefreshing = false;
                invalidateMenu = true;
            }
            if (invalidateMenu)
                invalidateOptionsMenu();
        }

        @Override
        public void onLoaderReset(
                android.support.v4.content.Loader<TransmissionSessionData> loader) {
        }

    };

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

        mCurrentTorrent = in.getIntExtra(TorrentDetailFragment.ARG_PAGE_POSITION, 0);
        if (savedInstanceState == null) {
            Bundle arguments = new Bundle();
            arguments.putInt(TorrentDetailFragment.ARG_PAGE_POSITION,
                    mCurrentTorrent);
            TorrentDetailFragment fragment = new TorrentDetailFragment();
            fragment.setArguments(arguments);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.torrent_detail_container, fragment, TorrentDetailFragment.TAG)
                    .commit();
        }

        getSupportLoaderManager().restartLoader(
                TorrentListActivity.SESSION_LOADER_ID, null, mTorrentLoaderCallbacks);
    }

    @Override
    public void onDestroy() {
        Loader<TransmissionSessionData> loader = getSupportLoaderManager()
            .getLoader(TorrentListActivity.SESSION_LOADER_ID);

        ((TransmissionSessionLoader) loader).setAllCurrentTorrents(false);

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.torrent_detail_activity, menu);

        MenuItem item = menu.findItem(R.id.menu_refresh);
        if (mRefreshing)
            item.setActionView(R.layout.action_progress_bar);
        else
            item.setActionView(null);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Loader<TransmissionSessionData> loader;

        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpTo(this, new Intent(this, TorrentListActivity.class));
                return true;
            case R.id.menu_refresh:
                loader = getSupportLoaderManager()
                    .getLoader(TorrentListActivity.SESSION_LOADER_ID);
                if (loader != null) {
                    loader.onContentChanged();
                    mRefreshing = !mRefreshing;
                    invalidateOptionsMenu();
                }
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
        if (current == mTorrents.size() - 1 || current == 0) {
            count--;
        }

        if (count > mTorrents.size())
            count = mTorrents.size();

        Torrent torrents[] = new Torrent[count];

        for (int i = 0; i < count; i++) {
            int position = current + i - (current == 0 ? 0 : offscreen);
            Torrent t = mTorrents.get(position);

            torrents[i] = t;
        }

        return torrents;
    }

    @Override
    public void onPageSelected(int position) {
        mCurrentTorrent = position;

        Loader<TransmissionSessionData> loader = getSupportLoaderManager()
            .getLoader(TorrentListActivity.SESSION_LOADER_ID);

        ((TransmissionSessionLoader) loader).setCurrentTorrents(getCurrentTorrents());
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

    @Override
    public void setRefreshing(boolean refreshing) {
        mRefreshing = refreshing;
        invalidateOptionsMenu();
    }
}
