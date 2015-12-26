package org.sugr.gearshift.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.Html;
import android.text.Spanned;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.sugr.gearshift.G;
import org.sugr.gearshift.GearShiftApplication;
import org.sugr.gearshift.R;
import org.sugr.gearshift.core.TransmissionProfile;
import org.sugr.gearshift.core.TransmissionSession;
import org.sugr.gearshift.datasource.DataSource;
import org.sugr.gearshift.service.DataService;
import org.sugr.gearshift.service.DataServiceManager;
import org.sugr.gearshift.service.DataServiceManagerInterface;
import org.sugr.gearshift.ui.loader.TransmissionProfileSupportLoader;
import org.sugr.gearshift.ui.util.LocationDialogHelper;
import org.sugr.gearshift.ui.util.LocationDialogHelperInterface;
import org.sugr.gearshift.ui.util.QueueManagementDialogHelper;
import org.sugr.gearshift.ui.util.QueueManagementDialogHelperInterface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public abstract class BaseTorrentActivity extends ColorizedToolbarActivity
    implements TransmissionSessionInterface, DataServiceManagerInterface,
    LocationDialogHelperInterface, QueueManagementDialogHelperInterface,
    TransmissionProfileInterface, TorrentDetailFragment.PagerCallbacks {

    protected TransmissionProfile profile;
    protected TransmissionSession session;
    protected DataServiceManager manager;

    protected boolean refreshing = false;
    protected String refreshType;

    protected Menu menu;

    protected BroadcastReceiver serviceReceiver;

    protected LocationDialogHelper locationDialogHelper;
    protected QueueManagementDialogHelper queueManagementDialogHelper;

    protected boolean hasFatalError = false;
    protected long lastServerActivity;
    protected List<TransmissionProfile> profiles = new ArrayList<>();

    private static final String STATE_LAST_SERVER_ACTIVITY = "last_server_activity";
    private static final String STATE_FATAL_ERROR = "fatal_error";
    private static final String STATE_ERROR_TYPE = "error_type";
    private static final String STATE_ERROR_CODE = "error_Code";
    private static final String STATE_ERROR_STRING = "error_string";
    private static final String STATE_REFRESHING = "refreshing";

    private int errorType;
    private int errorCode;
    private String errorString;

    private LoaderManager.LoaderCallbacks<TransmissionProfile[]> profileLoaderCallbacks = new LoaderManager.LoaderCallbacks<TransmissionProfile[]>() {
        @Override
        public android.support.v4.content.Loader<TransmissionProfile[]> onCreateLoader(
            int id, Bundle args) {
            return new TransmissionProfileSupportLoader(BaseTorrentActivity.this);
        }

        @Override
        public void onLoadFinished(
            android.support.v4.content.Loader<TransmissionProfile[]> loader,
            TransmissionProfile[] profiles) {

            setProfiles(Arrays.asList(profiles));
        }

        @Override public void onLoaderReset(
            android.support.v4.content.Loader<TransmissionProfile[]> loader) { }

    };

    /* The callback will get garbage collected if its a mere anon class */
    private SharedPreferences.OnSharedPreferenceChangeListener profileChangeListener =
        new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if (profile == null) return;

                if (!key.endsWith(profile.getId())) return;

                profile.load();

                TransmissionProfile.setCurrentProfile(profile,
                    PreferenceManager.getDefaultSharedPreferences(BaseTorrentActivity.this));
                setProfile(profile);
            }
        };

    @Override protected void onCreate(Bundle savedInstanceState) {
        locationDialogHelper = new LocationDialogHelper(this);
        queueManagementDialogHelper = new QueueManagementDialogHelper(this);
        serviceReceiver = new ServiceReceiver();

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(STATE_LAST_SERVER_ACTIVITY)) {
                lastServerActivity = savedInstanceState.getLong(STATE_LAST_SERVER_ACTIVITY, 0);
            }
            if (savedInstanceState.containsKey(STATE_FATAL_ERROR)) {
                hasFatalError = savedInstanceState.getBoolean(STATE_FATAL_ERROR, false);
                if (hasFatalError) {
                    errorType = savedInstanceState.getInt(STATE_ERROR_TYPE, 0);
                    errorCode = savedInstanceState.getInt(STATE_ERROR_CODE, 0);
                    errorString = savedInstanceState.getString(STATE_ERROR_STRING);
                }
            }
            if (savedInstanceState.containsKey(STATE_REFRESHING)) {
                setRefreshing(true, null);
            }
        }

        getSupportLoaderManager().initLoader(G.PROFILES_LOADER_ID, null, profileLoaderCallbacks);

        super.onCreate(savedInstanceState);
    }

    @Override protected void onResume() {
        super.onResume();

        if (profile != null) {
            colorize(profile);
            if (manager == null) {
                manager = new DataServiceManager(this, profile).startUpdating();
            }
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            serviceReceiver, new IntentFilter(G.INTENT_SERVICE_ACTION_COMPLETE));

        GearShiftApplication.setActivityVisible(true);

        long now = new Date().getTime();
        if (manager != null && now - lastServerActivity >= 60000) {
            setRefreshing(true, DataService.Requests.GET_SESSION);
            manager.getSession();
        }

        if (hasFatalError) {
            showErrorMessage(errorType, errorCode, errorString);
        }
    }

    @Override protected void onPause() {
        super.onPause();

        if (manager != null) {
            manager.reset();
            manager = null;
        }

        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceReceiver);

        GearShiftApplication.setActivityVisible(false);

        locationDialogHelper.reset();
        queueManagementDialogHelper.reset();
    }

    @Override public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_FATAL_ERROR, hasFatalError);
        if (hasFatalError) {
            outState.putInt(STATE_ERROR_TYPE, errorType);
            outState.putInt(STATE_ERROR_CODE, errorCode);
            outState.putString(STATE_ERROR_STRING, errorString);
        }
        outState.putLong(STATE_LAST_SERVER_ACTIVITY, lastServerActivity);
        outState.putBoolean(STATE_REFRESHING, refreshing);
        if (manager != null) {
            manager.onSaveInstanceState(outState);
        }
    }

    @Override public TransmissionSession getSession() {
        return session;
    }

    @Override public void setSession(TransmissionSession session) {
        this.session = session;
    }

    @Override public void setRefreshing(boolean refreshing, String type) {
        if (!refreshing && refreshType != null && !refreshType.equals(type)) {
            return;
        }

        this.refreshing = refreshing;
        refreshType = refreshing ? type : null;
        if (menu == null) {
            return;
        }

        MenuItem item = menu.findItem(R.id.menu_refresh);
        SwipeRefreshLayout swipeRefresh = (SwipeRefreshLayout) findViewById(R.id.swipe_container);

        /* The swipe indicator is underneath the error layer */
        if (refreshing && hasFatalError) {
            swipeRefresh = null;
        }

        if (this.refreshing) {
            if (swipeRefresh != null) {
                swipeRefresh.setRefreshing(true);
                swipeRefresh.setEnabled(false);
                item.setEnabled(false);
            } else {
                View actionView = getLayoutInflater().inflate(R.layout.action_progress_bar, null);
                MenuItemCompat.setActionView(item, actionView);
            }
        } else {
            if (swipeRefresh != null) {
                swipeRefresh.setRefreshing(false);
                swipeRefresh.setEnabled(true);
            }
            item.setEnabled(true);
            MenuItemCompat.setActionView(item, null);
        }
    }

    @Override public DataServiceManager getDataServiceManager() {
        return manager;
    }

    @Override public LocationDialogHelper getLocationDialogHelper() {
        return locationDialogHelper;
    }

    @Override public QueueManagementDialogHelper getQueueManagementDialogHelper() {
        return queueManagementDialogHelper;
    }

    @Override public TransmissionProfile getProfile() {
        return profile;
    }

    @Override public void setProfile(TransmissionProfile profile) {
        boolean newProfile = this.profile == null;

        if (this.profile != null && this.profile.equals(profile) || this.profile == profile) {
            return;
        }

        this.profile = profile;

        if (manager != null) {
            manager.reset();
        }

        if (menu != null) {
            MenuItem item = menu.findItem(R.id.menu_refresh);
            item.setVisible(profile != null);

            if (findViewById(R.id.swipe_container) != null) {
                findViewById(R.id.swipe_container).setEnabled(false);
            }
        }

        if (profile == null) {
            manager = null;
        } else {
            manager = new DataServiceManager(this, profile).startUpdating();
            new SessionTask(this, SessionTask.Flags.START_TORRENT_TASK).execute();

            colorize(profile);
        }

        SharedPreferences prefs = getSharedPreferences(
            TransmissionProfile.getPreferencesName(),
            Activity.MODE_PRIVATE);

        if (prefs != null && newProfile) {
            prefs.registerOnSharedPreferenceChangeListener(profileChangeListener);
        }
    }

    @Override public List<TransmissionProfile> getProfiles() {
        return new ArrayList<>(profiles);
    }

    protected void showErrorMessage(int error, int code, String string) {
        findViewById(R.id.fatal_error_layer).setVisibility(View.VISIBLE);
        TextView text = (TextView) findViewById(R.id.transmission_error);

        text.setText(getErrorMessage(error, code, string));

        setSession(null);
    }

    protected void hideErrorMessage() {
        findViewById(R.id.fatal_error_layer).setVisibility(View.GONE);
    }

    protected abstract void onSessionTaskPostExecute(TransmissionSession session);
    protected abstract void onTorrentTaskPostExecute(Cursor cursor, boolean added,
                                                     boolean removed, boolean statusChanged,
                                                     boolean incompleteMetadata, boolean connected);

    protected abstract boolean handleSuccessServiceBroadcast(String type, Intent intent);
    protected abstract boolean handleErrorServiceBroadcast(String type, int error, Intent intent);

    protected void setProfiles(List<TransmissionProfile> profiles) {
        this.profiles.clear();

        if (profiles.isEmpty()) {
            TransmissionProfile.setCurrentProfile(null,
                PreferenceManager.getDefaultSharedPreferences(this));

            setProfile(null);
            return;
        }

        this.profiles.addAll(profiles);

        String currentId = TransmissionProfile.getCurrentProfileId(
            PreferenceManager.getDefaultSharedPreferences(this));

        boolean isProfileSet = false;
        for (TransmissionProfile prof : profiles) {
            if (prof.getId().equals(currentId)) {
                setProfile(prof);
                isProfileSet = true;
                break;
            }
        }

        if (!isProfileSet) {
            if (this.profiles.size() > 0) {
                setProfile(profiles.get(0));
            } else {
                setProfile(null);
            }

            TransmissionProfile.setCurrentProfile(getProfile(),
                PreferenceManager.getDefaultSharedPreferences(this));
        }
    }

    private Spanned getErrorMessage(int error, int code, String string) {
        if (error == DataService.Errors.NO_CONNECTIVITY) {
            return Html.fromHtml(getString(R.string.no_connectivity_empty_list));
        } else if (error == DataService.Errors.ACCESS_DENIED) {
            return Html.fromHtml(getString(R.string.access_denied_empty_list));
        } else if (error == DataService.Errors.NO_JSON) {
            return Html.fromHtml(getString(R.string.no_json_empty_list));
        } else if (error == DataService.Errors.NO_CONNECTION) {
            return Html.fromHtml(getString(R.string.no_connection_empty_list));
        } else if (error == DataService.Errors.THREAD_ERROR) {
            return Html.fromHtml(getString(R.string.thread_error_empty_list));
        } else if (error == DataService.Errors.RESPONSE_ERROR) {
            return Html.fromHtml(getString(R.string.response_error_empty_list));
        } else if (error == DataService.Errors.TIMEOUT) {
            return Html.fromHtml(getString(R.string.timeout_empty_list));
        } else if (error == DataService.Errors.OUT_OF_MEMORY) {
            return Html.fromHtml(getString(R.string.out_of_memory_empty_list));
        } else if (error == DataService.Errors.JSON_PARSE_ERROR) {
            return Html.fromHtml(getString(R.string.json_parse_empty_list));
        } else if (error == DataService.Errors.GENERIC_HTTP) {
            return Html.fromHtml(String.format(getString(R.string.generic_http_empty_list),
                code, string));
        }

        return null;
    }

    protected class SessionTask extends AsyncTask<Void, Void, TransmissionSession> {
        DataSource readSource;
        boolean startTorrentTask;

        public class Flags {
            public static final int START_TORRENT_TASK = 1;
        }

        public SessionTask(Context context, int flags) {
            super();

            readSource = new DataSource(context);
            if ((flags & Flags.START_TORRENT_TASK) == Flags.START_TORRENT_TASK) {
                startTorrentTask = true;
            }
        }

        @Override protected TransmissionSession doInBackground(Void... ignored) {
            try {
                if (profile == null) {
                    return null;
                }

                readSource.open();

                TransmissionSession session = readSource.getSession(profile.getId());
                session.setDownloadDirectories(profile,
                    readSource.getDownloadDirectories(profile.getId()));

                return session;
            } finally {
                if (readSource.isOpen()) {
                    readSource.close();
                }
            }
        }

        @Override protected void onPostExecute(TransmissionSession session) {
            if (session == null) {
                return;
            }

            setSession(session);

            if (session.getRPCVersion() >= TransmissionSession.FREE_SPACE_METHOD_RPC_VERSION
                    && manager != null) {
                manager.getFreeSpace(session.getDownloadDir());
            }

            if (startTorrentTask) {
                new TorrentTask(BaseTorrentActivity.this, 0).execute();
            }

            onSessionTaskPostExecute(session);
        }
    }

    protected class TorrentTask extends AsyncTask<Void, Void, Cursor> {
        DataSource readSource;
        boolean added, removed, statusChanged, incompleteMetadata, update, connected;

        public class Flags {
            public static final int HAS_ADDED = 1;
            public static final int HAS_REMOVED = 1 << 1;
            public static final int HAS_STATUS_CHANGED = 1 << 2;
            public static final int HAS_INCOMPLETE_METADATA = 1 << 3;
            public static final int UPDATE = 1 << 4;
            public static final int CONNECTED = 1 << 5;
        }

        public TorrentTask(Context context, int flags) {
            super();

            readSource = new DataSource(context);
            if ((flags & Flags.HAS_ADDED) == Flags.HAS_ADDED) {
                added = true;
            }
            if ((flags & Flags.HAS_REMOVED) == Flags.HAS_REMOVED) {
                removed = true;
            }
            if ((flags & Flags.HAS_STATUS_CHANGED) == Flags.HAS_STATUS_CHANGED) {
                statusChanged = true;
            }
            if ((flags & Flags.HAS_INCOMPLETE_METADATA) == Flags.HAS_INCOMPLETE_METADATA) {
                incompleteMetadata = true;
            }
            if ((flags & Flags.UPDATE) == Flags.UPDATE) {
                update = true;
            }
            if ((flags & Flags.CONNECTED) == Flags.CONNECTED) {
                connected = true;
            }
        }

        @Override protected Cursor doInBackground(Void... unused) {
            try {
                if (profile == null) {
                    return null;
                }

                readSource.open();

                return readSource.getTorrentCursor(profile.getId(),
                    PreferenceManager.getDefaultSharedPreferences(readSource.getContext()));
            } finally {
                if (readSource.isOpen()) {
                    readSource.close();
                }
            }
        }

        @Override protected void onPostExecute(Cursor cursor) {
            if (connected) {
                setRefreshing(false, DataService.Requests.GET_TORRENTS);
            }

            if (cursor == null) {
                return;
            }

            if (update && manager != null) {
                update = false;
                manager.update();
            }

            onTorrentTaskPostExecute(cursor, added, removed, statusChanged,
                incompleteMetadata, connected);
        }
    }
    private class ServiceReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            int error = intent.getIntExtra(G.ARG_ERROR, 0);
            String profileId = intent.getStringExtra(G.ARG_PROFILE_ID);
            String type = intent.getStringExtra(G.ARG_REQUEST_TYPE);

            if (profile == null || profileId == null) {
                return;
            }

            hasFatalError = false;

            switch (type) {
                case DataService.Requests.GET_SESSION:
                case DataService.Requests.SET_SESSION:
                case DataService.Requests.GET_TORRENTS:
                case DataService.Requests.ADD_TORRENT:
                case DataService.Requests.REMOVE_TORRENT:
                case DataService.Requests.SET_TORRENT:
                case DataService.Requests.SET_TORRENT_ACTION:
                case DataService.Requests.SET_TORRENT_LOCATION:
                case DataService.Requests.GET_FREE_SPACE:
                    if (!type.equals(DataService.Requests.GET_TORRENTS)) {
                        setRefreshing(false, type);
                    }

                    lastServerActivity = new Date().getTime();

                    if (!profileId.equals(profile.getId())) {
                        /* Only torrent additions are handled out-of-profile, all errors are ignored */
                        if (type.equals(DataService.Requests.ADD_TORRENT)) {
                            if (error == 0) {
                                Toast.makeText(BaseTorrentActivity.this,
                                        R.string.torrent_added_different_profile, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(BaseTorrentActivity.this,
                                        getErrorMessage(error, intent.getIntExtra(G.ARG_ERROR_CODE, 0),
                                                intent.getStringExtra(G.ARG_ERROR_STRING)),
                                        Toast.LENGTH_LONG).show();
                            }
                        }

                        return;
                    }


                    if (error == 0) {
                        if (!handleSuccessServiceBroadcast(type, intent)) {
                            return;
                        }
                        hideErrorMessage();
                    } else {
                        if (!handleErrorServiceBroadcast(type, error, intent)) {
                            return;
                        }
                        if (error == DataService.Errors.DUPLICATE_TORRENT) {
                            Toast.makeText(BaseTorrentActivity.this,
                                R.string.duplicate_torrent, Toast.LENGTH_SHORT).show();
                        } else if (error == DataService.Errors.INVALID_TORRENT) {
                            Toast.makeText(BaseTorrentActivity.this,
                                R.string.invalid_torrent, Toast.LENGTH_SHORT).show();
                        } else {
                            hasFatalError = true;
                            errorType = error;
                            errorCode = intent.getIntExtra(G.ARG_ERROR_CODE, 0);
                            errorString = intent.getStringExtra(G.ARG_ERROR_STRING);
                            setRefreshing(false, refreshType);

                            showErrorMessage(errorType, errorCode, errorString);
                        }
                    }

                    break;
            }
        }
    }
}
