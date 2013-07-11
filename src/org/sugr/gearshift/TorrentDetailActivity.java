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
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

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

    private LoaderCallbacks<TransmissionData> mTorrentLoaderCallbacks = new LoaderCallbacks<TransmissionData>() {

        @Override
        public android.support.v4.content.Loader<TransmissionData> onCreateLoader(
                int id, Bundle args) {
            G.logD("Starting the torrents loader with profile " + mProfile);
            if (mProfile == null) return null;

            TransmissionDataLoader loader = new TransmissionDataLoader(
                    TorrentDetailActivity.this, mProfile, mSession, mTorrents, getCurrentTorrents());

            return loader;
        }

        @Override
        public void onLoadFinished(
                android.support.v4.content.Loader<TransmissionData> loader,
                TransmissionData data) {

            setSession(data.session);
           /* if (data.stats != null)
                mSessionStats = data.stats;*/

            boolean invalidateMenu = false;

            View error = findViewById(R.id.fatal_error_layer);
            error.setVisibility(View.GONE);
            if (data.error > 0) {
                if (data.error == TransmissionData.Errors.DUPLICATE_TORRENT) {
                    Toast.makeText(TorrentDetailActivity.this,
                            R.string.duplicate_torrent, Toast.LENGTH_SHORT).show();
                } else if (data.error == TransmissionData.Errors.INVALID_TORRENT) {
                    Toast.makeText(TorrentDetailActivity.this,
                            R.string.invalid_torrent, Toast.LENGTH_SHORT).show();
                } else {
                    error.setVisibility(View.VISIBLE);
                    TextView text = (TextView) findViewById(R.id.transmission_error);
                    if (data.error == TransmissionData.Errors.NO_CONNECTIVITY) {
                        text.setText(Html.fromHtml(getString(R.string.no_connectivity_empty_list)));
                    } else if (data.error == TransmissionData.Errors.ACCESS_DENIED) {
                        text.setText(Html.fromHtml(getString(R.string.access_denied_empty_list)));
                    } else if (data.error == TransmissionData.Errors.NO_JSON) {
                        text.setText(Html.fromHtml(getString(R.string.no_json_empty_list)));
                    } else if (data.error == TransmissionData.Errors.NO_CONNECTION) {
                        text.setText(Html.fromHtml(getString(R.string.no_connection_empty_list)));
                    } else if (data.error == TransmissionData.Errors.THREAD_ERROR) {
                        text.setText(Html.fromHtml(getString(R.string.thread_error_empty_list)));
                    } else if (data.error == TransmissionData.Errors.RESPONSE_ERROR) {
                        text.setText(Html.fromHtml(getString(R.string.response_error_empty_list)));
                    } else if (data.error == TransmissionData.Errors.TIMEOUT) {
                        text.setText(Html.fromHtml(getString(R.string.timeout_empty_list)));
                    }
                    invalidateOptionsMenu();
                }
            } else {
                if (data.torrents.size() > 0) {
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
                    Toast.makeText(TorrentDetailActivity.this,
                            Html.fromHtml(getString(R.string.no_torrents_empty_list)),
                            Toast.LENGTH_SHORT).show();
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
                android.support.v4.content.Loader<TransmissionData> loader) {
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent in = getIntent();

        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        mTorrents = new ArrayList<Torrent>(Arrays.asList(
                gson.fromJson(in.getStringExtra(ARG_JSON_TORRENTS), Torrent[].class)));
        mProfile = in.getParcelableExtra(ARG_PROFILE);
        mProfile.setContext(this);
        setSession(gson.fromJson(in.getStringExtra(ARG_JSON_SESSION), TransmissionSession.class));
        mSession.setDownloadDirectories(mProfile, mTorrents);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_torrent_detail);

        // Show the Up button in the action bar.
        getActionBar().setDisplayHomeAsUpEnabled(true);

        mCurrentTorrent = in.getIntExtra(TorrentDetailFragment.ARG_PAGE_POSITION, 0);
        if (mCurrentTorrent < 0) {
            mCurrentTorrent = 0;
        }
        if (savedInstanceState == null) {
            Bundle arguments = new Bundle();
            arguments.putInt(TorrentDetailFragment.ARG_PAGE_POSITION,
                    mCurrentTorrent);
            arguments.putBoolean(TorrentDetailFragment.ARG_SHOW_PAGER,
                    true);
            TorrentDetailFragment fragment = new TorrentDetailFragment();
            fragment.setArguments(arguments);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.torrent_detail_container, fragment, TorrentDetailFragment.TAG)
                    .commit();
        }

        getSupportLoaderManager().restartLoader(
                G.TORRENTS_LOADER_ID, null, mTorrentLoaderCallbacks);
    }

    @Override
    public void onDestroy() {
        Loader<TransmissionData> loader = getSupportLoaderManager()
            .getLoader(G.TORRENTS_LOADER_ID);

        ((TransmissionDataLoader) loader).setAllCurrentTorrents(false);

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
        Loader<TransmissionData> loader;

        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpTo(this, new Intent(this, TorrentListActivity.class));
                return true;
            case R.id.menu_refresh:
                loader = getSupportLoaderManager()
                    .getLoader(G.TORRENTS_LOADER_ID);
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

        Loader<TransmissionData> loader = getSupportLoaderManager()
            .getLoader(G.TORRENTS_LOADER_ID);

        ((TransmissionDataLoader) loader).setCurrentTorrents(getCurrentTorrents());
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
