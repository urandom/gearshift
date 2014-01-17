package org.sugr.gearshift;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.app.NavUtils;
import android.support.v4.content.Loader;
import android.text.Html;
import android.text.Spanned;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;


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
    private boolean refreshing = false;

    private int currentTorrentPosition = 0;

    private TransmissionProfile profile;
    private TransmissionSession session;

    private Menu menu;

    private LoaderCallbacks<TransmissionData> torrentLoaderCallbacks = new LoaderCallbacks<TransmissionData>() {

        @Override
        public android.support.v4.content.Loader<TransmissionData> onCreateLoader(
                int id, Bundle args) {
            G.logD("Starting the torrents loader with profile " + profile);
            if (profile == null) return null;

            TransmissionDataLoader loader = new TransmissionDataLoader(
                TorrentDetailActivity.this, profile, session, true, getCurrentTorrentHashStrings());
            loader.setQueryOnly(true);

            return loader;
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
                if (data.cursor.getCount() == 0) {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(TorrentDetailActivity.this);

                    Spanned text;
                    if (prefs.getString(G.PREF_LIST_SEARCH, "").equals("")
                        && prefs.getString(G.PREF_LIST_DIRECTORY, "").equals("")
                        && prefs.getString(G.PREF_LIST_TRACKER, "").equals("")
                        && prefs.getString(G.PREF_LIST_FILTER, G.FilterBy.ALL.name()).equals(G.FilterBy.ALL.name())) {
                        text = Html.fromHtml(getString(R.string.no_torrents_empty_list));
                    } else {
                        text = Html.fromHtml(getString(R.string.no_filtered_torrents_empty_list));
                    }
                    Toast.makeText(TorrentDetailActivity.this, text, Toast.LENGTH_SHORT).show();
                }
            }

            FragmentManager manager = getSupportFragmentManager();
            TorrentDetailFragment detail = (TorrentDetailFragment) manager.findFragmentByTag(
                    G.DETAIL_FRAGMENT_TAG);
            if (detail != null) {
                detail.notifyTorrentListChanged(data.cursor, data.error, false, data.hasRemoved,
                    data.hasStatusChanged, data.hasMetadataNeeded);
            }

            if (refreshing) {
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

        profile = in.getParcelableExtra(G.ARG_PROFILE);
        profile.setContext(this);
        session = in.getParcelableExtra(G.ARG_SESSION);
        setSession(session);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_torrent_detail);

        // Show the Up button in the action bar.
        getActionBar().setDisplayHomeAsUpEnabled(true);

        currentTorrentPosition = in.getIntExtra(G.ARG_PAGE_POSITION, 0);
        if (currentTorrentPosition < 0) {
            currentTorrentPosition = 0;
        }
        if (savedInstanceState == null) {
            Bundle arguments = new Bundle();
            arguments.putInt(G.ARG_PAGE_POSITION, currentTorrentPosition);
            arguments.putBoolean(TorrentDetailFragment.ARG_SHOW_PAGER,
                    true);
            TorrentDetailFragment fragment = new TorrentDetailFragment();
            fragment.setArguments(arguments);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.torrent_detail_container, fragment, G.DETAIL_FRAGMENT_TAG)
                    .commit();
        }

        getSupportLoaderManager().restartLoader(
                G.TORRENTS_LOADER_ID, null, torrentLoaderCallbacks);
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
                    setRefreshing(!refreshing);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        Loader<TransmissionData> loader = getSupportLoaderManager()
            .getLoader(G.TORRENTS_LOADER_ID);

        if (loader != null) {
            ((TransmissionDataLoader) loader).setDetails(false);
        }

        super.onDestroy();
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
    public void onPageSelected(int position) {
        currentTorrentPosition = position;

        Loader<TransmissionData> loader = getSupportLoaderManager()
            .getLoader(G.TORRENTS_LOADER_ID);

        ((TransmissionDataLoader) loader).setUpdateHashStrings(getCurrentTorrentHashStrings());
    }

    @Override
    public TransmissionProfile getProfile() {
        return profile;
    }

    @Override
    public void setSession(TransmissionSession session) {
        this.session = session;
    }

    @Override
    public TransmissionSession getSession() {
        return session;
    }

    @Override
    public void setRefreshing(boolean refreshing) {
        this.refreshing = refreshing;

        MenuItem item = menu.findItem(R.id.menu_refresh);
        if (this.refreshing)
            item.setActionView(R.layout.action_progress_bar);
        else
            item.setActionView(null);
    }

    private String[] getCurrentTorrentHashStrings() {
        TorrentDetailFragment fragment =
            (TorrentDetailFragment) getSupportFragmentManager().findFragmentByTag(G.DETAIL_FRAGMENT_TAG);

        if (fragment == null) {
            return null;
        }

        int current = currentTorrentPosition;
        int offscreen = 1;
        int count = offscreen * 2 + 1;
        if (current == 0) {
            count--;
        }

        List<String> hashList = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            int position = current + i - (current == 0 ? 0 : offscreen);
            String hash = fragment.getTorrentHashString(position);
            if (hash != null) {
                hashList.add(hash);
            }
        }

        return hashList.toArray(new String[hashList.size()]);
    }

}