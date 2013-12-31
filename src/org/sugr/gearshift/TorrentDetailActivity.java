package org.sugr.gearshift;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.app.NavUtils;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.Html;
import android.text.Spanned;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.sugr.gearshift.datasource.DataSource;

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
    private boolean mRefreshing = false;

    private int currentTorrentPosition = 0;

    private TransmissionProfile mProfile;
    private TransmissionSession mSession;

    private Menu menu;

    private LoaderCallbacks<TransmissionData> torrentLoaderCallbacks = new LoaderCallbacks<TransmissionData>() {

        @Override
        public android.support.v4.content.Loader<TransmissionData> onCreateLoader(
                int id, Bundle args) {
            G.logD("Starting the torrents loader with profile " + mProfile);
            if (mProfile == null) return null;

            return new TransmissionDataLoader(
                TorrentDetailActivity.this, mProfile, mSession, true, getCurrentTorrentIds());
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

            if (mRefreshing) {
                setRefreshing(false);
            }
        }

        @Override
        public void onLoaderReset(
                android.support.v4.content.Loader<TransmissionData> loader) {
        }

    };

    private LoaderCallbacks<Cursor> initialTorrentsCursorLoaderCallbacks = new LoaderCallbacks<Cursor>() {
        @Override public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
            if (id == G.TORRENTS_CURSOR_LOADER_ID) {
                return new TorrentsCursorLoader(TorrentDetailActivity.this);
            }
            return null;
        }

        @Override public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            getSupportFragmentManager().executePendingTransactions();
            TorrentDetailFragment fragment =
                (TorrentDetailFragment) getSupportFragmentManager().findFragmentByTag(G.DETAIL_FRAGMENT_TAG);

            if (fragment != null)
                fragment.changeCursor(cursor);
        }

        @Override public void onLoaderReset(Loader<Cursor> loader) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent in = getIntent();

        mProfile = in.getParcelableExtra(G.ARG_PROFILE);
        mProfile.setContext(this);
        mSession = in.getParcelableExtra(G.ARG_SESSION);
        setSession(mSession);

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

        getSupportLoaderManager().initLoader(G.TORRENTS_CURSOR_LOADER_ID,
            null, initialTorrentsCursorLoaderCallbacks);

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
                    setRefreshing(!mRefreshing);
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

    public int[] getCurrentTorrentIds() {
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

        List<Integer> idList = new ArrayList<Integer>();

        for (int i = 0; i < count; i++) {
            int position = current + i - (current == 0 ? 0 : offscreen);
            int id = fragment.getTorrentId(position);
            if (id != -1) {
                idList.add(id);
            }
        }

        int[] ids = new int[idList.size()];
        int index = 0;
        for (int id : idList) {
            ids[index++] = id;
        }

        return ids;
    }

    @Override
    public void onPageSelected(int position) {
        currentTorrentPosition = position;

        Loader<TransmissionData> loader = getSupportLoaderManager()
            .getLoader(G.TORRENTS_LOADER_ID);

        ((TransmissionDataLoader) loader).setUpdateIds(getCurrentTorrentIds());
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

class TorrentsCursorLoader extends AsyncTaskLoader<Cursor> {
    private DataSource readSource;

    public TorrentsCursorLoader(Context context) {
        super(context);

        readSource = new DataSource(context);
    }
    @Override public Cursor loadInBackground() {
        readSource.open();

        Cursor cursor = readSource.getTorrentCursor();

        /* Fill the window */
        cursor.getCount();

        readSource.close();

        return cursor;
    }

    @Override protected void onStartLoading() {
        forceLoad();
    }
}