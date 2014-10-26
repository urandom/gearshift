package org.sugr.gearshift.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.NavUtils;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spanned;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;
import org.sugr.gearshift.core.TransmissionSession;
import org.sugr.gearshift.service.DataService;
import org.sugr.gearshift.service.DataServiceManager;

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
public class TorrentDetailActivity extends BaseTorrentActivity {
    private int currentTorrentPosition = 0;

    @Override protected void onCreate(Bundle savedInstanceState) {
        Intent in = getIntent();

        profile = in.getParcelableExtra(G.ARG_PROFILE);
        profile.setContext(this);
        session = in.getParcelableExtra(G.ARG_SESSION);
        setSession(session);

        lastServerActivity = in.getLongExtra(G.ARG_LAST_SERVER_ACTIVITY, 0);

        if (in.hasExtra(G.ARG_REFRESH_TYPE)) {
            setRefreshing(true, in.getStringExtra(G.ARG_REFRESH_TYPE));
        }

        manager = new DataServiceManager(this, profile.getId())
            .setDetails(true).onRestoreInstanceState(savedInstanceState).startUpdating();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_torrent_detail);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Show the Up button in the action bar.
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

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
        new SessionTask(this, SessionTask.Flags.START_TORRENT_TASK).execute();
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        this.menu = menu;

        getMenuInflater().inflate(R.menu.torrent_detail_activity, menu);

        setRefreshing(refreshing, refreshType);

        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpTo(this, new Intent(this, TorrentListActivity.class));
                return true;
            case R.id.menu_refresh:
                manager.update();
                setRefreshing(true, DataService.Requests.GET_TORRENTS);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override public void onTrimMemory(int level) {
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

    @Override public void onPageSelected(int position) {
        currentTorrentPosition = position;

        if (manager != null) {
            manager.setTorrentsToUpdate(getCurrentTorrentHashStrings());
        }
    }

    @Override protected boolean handleSuccessServiceBroadcast(String type, Intent intent) {
        if (manager == null) {
            return false;
        }

        int flags = TorrentTask.Flags.CONNECTED;
        switch (type) {
            case DataService.Requests.GET_SESSION:
                new SessionTask(TorrentDetailActivity.this, 0).execute();
                break;
            case DataService.Requests.SET_SESSION:
                manager.getSession();
                break;
            case DataService.Requests.GET_TORRENTS:
                boolean added = intent.getBooleanExtra(G.ARG_ADDED, false);
                boolean removed = intent.getBooleanExtra(G.ARG_REMOVED, false);
                boolean statusChanged = intent.getBooleanExtra(G.ARG_STATUS_CHANGED, false);
                boolean incomplete = intent.getBooleanExtra(G.ARG_INCOMPLETE_METADATA, false);

                if (added) {
                    flags |= TorrentTask.Flags.HAS_ADDED;
                }
                if (removed) {
                    flags |= TorrentTask.Flags.HAS_REMOVED;
                }
                if (statusChanged) {
                    flags |= TorrentTask.Flags.HAS_STATUS_CHANGED;
                }
                if (incomplete) {
                    flags |= TorrentTask.Flags.HAS_INCOMPLETE_METADATA;
                }

                new TorrentTask(TorrentDetailActivity.this, flags).execute();
                break;
            case DataService.Requests.ADD_TORRENT:
                manager.update();
                flags |= TorrentTask.Flags.HAS_ADDED | TorrentTask.Flags.HAS_INCOMPLETE_METADATA;
                new TorrentTask(TorrentDetailActivity.this, flags).execute();
                break;
            case DataService.Requests.REMOVE_TORRENT:
                manager.update();
                flags |= TorrentTask.Flags.HAS_REMOVED;
                new TorrentTask(TorrentDetailActivity.this, flags).execute();
                break;
            case DataService.Requests.SET_TORRENT_LOCATION:
                manager.update();
                flags |= TorrentTask.Flags.HAS_ADDED | TorrentTask.Flags.HAS_REMOVED;
                new TorrentTask(TorrentDetailActivity.this, flags).execute();
                break;
            case DataService.Requests.SET_TORRENT:
            case DataService.Requests.SET_TORRENT_ACTION:
                manager.update();
                flags |= TorrentTask.Flags.HAS_STATUS_CHANGED;
                new TorrentTask(TorrentDetailActivity.this, flags).execute();
                break;
        }

        return true;
    }

    @Override protected boolean handleErrorServiceBroadcast(String type, int error, Intent intent) {
        FragmentManager fm = getSupportFragmentManager();
        TorrentDetailFragment fragment =
            (TorrentDetailFragment) fm.findFragmentByTag(G.DETAIL_FRAGMENT_TAG);

        if (fragment != null) {
            fragment.notifyTorrentListChanged(null, error, false, false, false, false, false);
        }

        return true;
    }

    @Override protected void onSessionTaskPostExecute(TransmissionSession session) {
    }

    @Override protected void onTorrentTaskPostExecute(Cursor cursor, boolean added, boolean removed, boolean statusChanged, boolean incompleteMetadata, boolean connected) {
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

        FragmentManager fm = getSupportFragmentManager();
        TorrentDetailFragment detail = (TorrentDetailFragment) fm.findFragmentByTag(
            G.DETAIL_FRAGMENT_TAG);
        if (detail != null) {
            detail.notifyTorrentListChanged(cursor, 0, added, removed,
                statusChanged, incompleteMetadata, connected);
        }

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