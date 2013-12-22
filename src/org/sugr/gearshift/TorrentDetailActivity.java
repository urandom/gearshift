package org.sugr.gearshift;

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

import java.util.ArrayList;


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
    public static final String ARG_TORRENTS = "torrents";

    private boolean mRefreshing = false;

    private ArrayList<Torrent> mTorrents = new ArrayList<Torrent>();
    private int mCurrentTorrent = 0;

    private TransmissionProfile mProfile;
    private TransmissionSession mSession;

    private Menu menu;

    private LoaderCallbacks<TransmissionData> mTorrentLoaderCallbacks = new LoaderCallbacks<TransmissionData>() {

        @Override
        public android.support.v4.content.Loader<TransmissionData> onCreateLoader(
                int id, Bundle args) {
            G.logD("Starting the torrents loader with profile " + mProfile);
            if (mProfile == null) return null;

            return new TransmissionDataLoader(
                TorrentDetailActivity.this, mProfile, mSession, mTorrents, getCurrentTorrents());
        }

        @Override
        public void onLoadFinished(
                android.support.v4.content.Loader<TransmissionData> loader,
                TransmissionData data) {

            setSession(data.session);

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
                    } else if (data.error == TransmissionData.Errors.GENERIC_HTTP) {
                        text.setText(Html.fromHtml(String.format(
                            getString(R.string.generic_http_empty_list), data.errorCode)));
                    } else if (data.error == TransmissionData.Errors.THREAD_ERROR) {
                        text.setText(Html.fromHtml(getString(R.string.thread_error_empty_list)));
                    } else if (data.error == TransmissionData.Errors.RESPONSE_ERROR) {
                        text.setText(Html.fromHtml(getString(R.string.response_error_empty_list)));
                    } else if (data.error == TransmissionData.Errors.TIMEOUT) {
                        text.setText(Html.fromHtml(getString(R.string.timeout_empty_list)));
                    } else if (data.error == TransmissionData.Errors.OUT_OF_MEMORY) {
                        text.setText(Html.fromHtml(getString(R.string.out_of_memory_empty_list)));
                    } else if (data.error == TransmissionData.Errors.JSON_PARSE_ERROR) {
                        text.setText(Html.fromHtml(getString(R.string.json_parse_empty_list)));
                    }
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
                    }
                } else {
                    Toast.makeText(TorrentDetailActivity.this,
                            Html.fromHtml(getString(R.string.no_torrents_empty_list)),
                            Toast.LENGTH_SHORT).show();
                }
            }

            FragmentManager manager = getSupportFragmentManager();
            TorrentDetailFragment detail = (TorrentDetailFragment) manager.findFragmentByTag(
                    G.DETAIL_FRAGMENT_TAG);
            if (detail != null) {
                detail.notifyTorrentListChanged(data.torrents, data.error, false, data.hasRemoved,
                    data.hasStatusChanged, data.hasMetadataNeeded);
            }

            if (mRefreshing) {
                setRefreshing(false);
            }
        }

        @Override
        public void onLoaderReset(
                android.support.v4.content.Loader<TransmissionData> loader) {
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent in = getIntent();

        mTorrents = in.getParcelableArrayListExtra(ARG_TORRENTS);
        mProfile = in.getParcelableExtra(G.ARG_PROFILE);
        mProfile.setContext(this);
        mSession = in.getParcelableExtra(G.ARG_SESSION);
        setSession(mSession);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_torrent_detail);

        // Show the Up button in the action bar.
        getActionBar().setDisplayHomeAsUpEnabled(true);

        mCurrentTorrent = in.getIntExtra(G.ARG_PAGE_POSITION, 0);
        if (mCurrentTorrent < 0) {
            mCurrentTorrent = 0;
        }
        if (savedInstanceState == null) {
            Bundle arguments = new Bundle();
            arguments.putInt(G.ARG_PAGE_POSITION, mCurrentTorrent);
            arguments.putBoolean(TorrentDetailFragment.ARG_SHOW_PAGER,
                    true);
            TorrentDetailFragment fragment = new TorrentDetailFragment();
            fragment.setArguments(arguments);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.torrent_detail_container, fragment, G.DETAIL_FRAGMENT_TAG)
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

        this.menu = menu;

        getMenuInflater().inflate(R.menu.torrent_detail_activity, menu);

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
                    setRefreshing(!mRefreshing);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTrimMemory(int level) {
        switch (level) {
            case TRIM_MEMORY_RUNNING_LOW:
            case TRIM_MEMORY_RUNNING_CRITICAL:
            case TRIM_MEMORY_COMPLETE:
                break;
            default:
                return;
        }
        if (!isFinishing()) {
            finish();
            Toast.makeText(this, "Low memory", Toast.LENGTH_SHORT).show();
        }
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

        MenuItem item = menu.findItem(R.id.menu_refresh);
        if (mRefreshing)
            item.setActionView(R.layout.action_progress_bar);
        else
            item.setActionView(null);
    }
}
