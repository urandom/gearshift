package org.sugr.gearshift;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.NavUtils;
import android.text.Html;
import android.text.Spanned;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.sugr.gearshift.datasource.DataSource;
import org.sugr.gearshift.service.DataService;
import org.sugr.gearshift.service.DataServiceManager;
import org.sugr.gearshift.service.DataServiceManagerInterface;

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
       TorrentDetailFragment.PagerCallbacks, DataServiceManagerInterface {
    private boolean refreshing = false;

    private int currentTorrentPosition = 0;

    private TransmissionProfile profile;
    private TransmissionSession session;
    private DataServiceManager manager;

    private ServiceReceiver serviceReceiver;
    private SessionTask sessionTask;
    private TorrentTask torrentTask;

    private Menu menu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent in = getIntent();

        profile = in.getParcelableExtra(G.ARG_PROFILE);
        profile.setContext(this);
        session = in.getParcelableExtra(G.ARG_SESSION);
        setSession(session);

        serviceReceiver = new ServiceReceiver();
        sessionTask = new SessionTask(this);
        torrentTask = new TorrentTask(this);
        manager = new DataServiceManager(this, profile.getId())
            .setDetails(true).startUpdating();

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
    }

    @Override protected void onResume() {
        super.onResume();

        registerReceiver(serviceReceiver, new IntentFilter(G.INTENT_SERVICE_ACTION_COMPLETE));
    }

    @Override protected void onPause() {
        super.onPause();

        unregisterReceiver(serviceReceiver);
        manager.reset();
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
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpTo(this, new Intent(this, TorrentListActivity.class));
                return true;
            case R.id.menu_refresh:
                manager.update();
                setRefreshing(!refreshing);
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
    public void onPageSelected(int position) {
        currentTorrentPosition = position;

        manager.setTorrentsToUpdate(getCurrentTorrentHashStrings());
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

    public DataServiceManager getDataServiceManager() {
        return manager;
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

    private class ServiceReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            int error = intent.getIntExtra(G.ARG_ERROR, 0);

            String type = intent.getStringExtra(G.ARG_REQUEST_TYPE);
            switch (type) {
                case DataService.Requests.GET_SESSION:
                case DataService.Requests.SET_SESSION:
                case DataService.Requests.GET_ACTIVE_TORRENTS:
                case DataService.Requests.GET_ALL_TORRENTS:
                case DataService.Requests.ADD_TORRENT:
                case DataService.Requests.REMOVE_TORRENT:
                case DataService.Requests.SET_TORRENT:
                case DataService.Requests.SET_TORRENT_ACTION:
                case DataService.Requests.SET_TORRENT_LOCATION:
                    if (error == 0) {
                        findViewById(R.id.fatal_error_layer).setVisibility(View.GONE);

                        switch (type) {
                            case DataService.Requests.GET_SESSION:
                                sessionTask.execute();
                                break;
                            case DataService.Requests.SET_SESSION:
                                manager.getSession();
                                break;
                            case DataService.Requests.GET_ACTIVE_TORRENTS:
                            case DataService.Requests.GET_ALL_TORRENTS:
                                boolean added = intent.getBooleanExtra(G.ARG_ADDED, false);
                                boolean removed = intent.getBooleanExtra(G.ARG_REMOVED, false);
                                boolean statusChanged = intent.getBooleanExtra(G.ARG_STATUS_CHANGED, false);
                                boolean incomplete = intent.getBooleanExtra(G.ARG_INCOMPLETE_METADATA, false);

                                torrentTask.execute(added, removed, statusChanged, incomplete);
                                break;
                            case DataService.Requests.ADD_TORRENT:
                                manager.update();
                                torrentTask.execute(true, false, false, true);
                                break;
                            case DataService.Requests.REMOVE_TORRENT:
                                manager.update();
                                torrentTask.execute(false, true, false, false);
                                break;
                            case DataService.Requests.SET_TORRENT_LOCATION:
                                manager.update();
                                torrentTask.execute(true, true, false, false);
                                break;
                            case DataService.Requests.SET_TORRENT:
                            case DataService.Requests.SET_TORRENT_ACTION:
                                manager.update();
                                torrentTask.execute(false, false, true, false);
                                break;
                        }
                    } else {
                        if (error == TransmissionData.Errors.DUPLICATE_TORRENT) {
                            Toast.makeText(TorrentDetailActivity.this,
                                R.string.duplicate_torrent, Toast.LENGTH_SHORT).show();
                        } else if (error == TransmissionData.Errors.INVALID_TORRENT) {
                            Toast.makeText(TorrentDetailActivity.this,
                                R.string.invalid_torrent, Toast.LENGTH_SHORT).show();
                        } else {
                            findViewById(R.id.fatal_error_layer).setVisibility(View.VISIBLE);
                            TextView text = (TextView) findViewById(R.id.transmission_error);
                            setRefreshing(false);
                            FragmentManager manager = getSupportFragmentManager();
                            TorrentListFragment fragment = (TorrentListFragment) manager.findFragmentById(R.id.torrent_list);
                            if (fragment != null) {
                                fragment.notifyTorrentListChanged(null, error, false, false, false, false);
                            }

                            if (error == TransmissionData.Errors.NO_CONNECTIVITY) {
                                text.setText(Html.fromHtml(getString(R.string.no_connectivity_empty_list)));
                            } else if (error == TransmissionData.Errors.ACCESS_DENIED) {
                                text.setText(Html.fromHtml(getString(R.string.access_denied_empty_list)));
                            } else if (error == TransmissionData.Errors.NO_JSON) {
                                text.setText(Html.fromHtml(getString(R.string.no_json_empty_list)));
                            } else if (error == TransmissionData.Errors.NO_CONNECTION) {
                                text.setText(Html.fromHtml(getString(R.string.no_connection_empty_list)));
                            } else if (error == TransmissionData.Errors.THREAD_ERROR) {
                                text.setText(Html.fromHtml(getString(R.string.thread_error_empty_list)));
                            } else if (error == TransmissionData.Errors.RESPONSE_ERROR) {
                                text.setText(Html.fromHtml(getString(R.string.response_error_empty_list)));
                            } else if (error == TransmissionData.Errors.TIMEOUT) {
                                text.setText(Html.fromHtml(getString(R.string.timeout_empty_list)));
                            } else if (error == TransmissionData.Errors.OUT_OF_MEMORY) {
                                text.setText(Html.fromHtml(getString(R.string.out_of_memory_empty_list)));
                            } else if (error == TransmissionData.Errors.JSON_PARSE_ERROR) {
                                text.setText(Html.fromHtml(getString(R.string.json_parse_empty_list)));
                            }
                        }
                    }
                    break;
            }
        }
    }

    private class SessionTask extends AsyncTask<Boolean, Void, TransmissionSession> {
        DataSource readSource;
        boolean startTorrentTask;

        public SessionTask(Context context) {
            super();

            readSource = new DataSource(context);
        }

        @Override protected TransmissionSession doInBackground(Boolean... startTorrentTask) {
            try {
                readSource.open();

                TransmissionSession session = readSource.getSession();
                session.setDownloadDirectories(profile, readSource.getDownloadDirectories());

                if (startTorrentTask.length == 1 && startTorrentTask[0]) {
                    this.startTorrentTask = true;
                }

                return session;
            } finally {
                if (readSource.isOpen()) {
                    readSource.close();
                }
            }
        }

        @Override protected void onPostExecute(TransmissionSession session) {
            setSession(session);

            if (startTorrentTask) {
                torrentTask.execute();
            }
        }
    }

    private class TorrentTask extends AsyncTask<Boolean, Void, Cursor> {
        DataSource readSource;
        boolean added, removed, statusChanged, incompleteMetadata, update;

        public TorrentTask(Context context) {
            super();

            readSource = new DataSource(context);
        }

        @Override protected Cursor doInBackground(Boolean... flags) {
            try {
                readSource.open();

                Cursor cursor = readSource.getTorrentCursor();

                if (flags.length == 1) {
                    update = flags[0];
                } else if (flags.length == 4) {
                    added = flags[0];
                    removed = flags[1];
                    statusChanged = flags[2];
                    incompleteMetadata = flags[3];
                }

                return cursor;
            } finally {
                if (readSource.isOpen()) {
                    readSource.close();
                }
            }
        }

        @Override protected void onPostExecute(Cursor cursor) {
            if (cursor.getCount() == 0) {
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

            FragmentManager manager = getSupportFragmentManager();
            TorrentDetailFragment detail = (TorrentDetailFragment) manager.findFragmentByTag(
                G.DETAIL_FRAGMENT_TAG);
            if (detail != null) {
                detail.notifyTorrentListChanged(cursor, 0, added, removed,
                    statusChanged, incompleteMetadata);
            }

            if (refreshing && !update) {
                setRefreshing(false);
            }

            if (update) {
                update = false;
                TorrentDetailActivity.this.manager.update();
            }
        }
    }
}