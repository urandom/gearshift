package org.sugr.gearshift.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Html;
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

import java.util.Date;

public abstract class BaseTorrentActivity extends FragmentActivity
    implements TransmissionSessionInterface, DataServiceManagerInterface,
    LocationDialogHelperInterface, TransmissionProfileInterface,
    TorrentDetailFragment.PagerCallbacks {

    protected TransmissionProfile profile;
    protected TransmissionSession session;
    protected DataServiceManager manager;

    protected boolean refreshing = false;
    protected String refreshType;

    protected Menu menu;

    protected BroadcastReceiver serviceReceiver;

    protected LocationDialogHelper locationDialogHelper;

    protected boolean hasFatalError = false;
    protected long lastServerActivity;

    private static final String STATE_LAST_SERVER_ACTIVITY = "last_server_activity";
    private static final String STATE_FATAL_ERROR = "fatal_error";
    private static final String STATE_ERROR_TYPE = "error_type";
    private static final String STATE_ERROR_CODE = "error_Code";
    private static final String STATE_ERROR_STRING = "error_string";
    private static final String STATE_REFRESHING = "refreshing";

    private int errorType;
    private int errorCode;
    private String errorString;

    @Override protected void onCreate(Bundle savedInstanceState) {
        locationDialogHelper = new LocationDialogHelper(this);
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

        super.onCreate(savedInstanceState);
    }

    @Override protected void onResume() {
        super.onResume();

        if (profile != null && manager == null) {
            manager = new DataServiceManager(this, profile.getId()).startUpdating();
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
        if (this.refreshing)
            item.setActionView(R.layout.action_progress_bar);
        else
            item.setActionView(null);
    }

    @Override public DataServiceManager getDataServiceManager() {
        return manager;
    }

    @Override public LocationDialogHelper getLocationDialogHelper() {
        return locationDialogHelper;
    }

    @Override public TransmissionProfile getProfile() {
        return profile;
    }

    protected void showErrorMessage(int error, int code, String string) {
        findViewById(R.id.fatal_error_layer).setVisibility(View.VISIBLE);
        TextView text = (TextView) findViewById(R.id.transmission_error);

        if (error == DataService.Errors.NO_CONNECTIVITY) {
            text.setText(Html.fromHtml(getString(R.string.no_connectivity_empty_list)));
        } else if (error == DataService.Errors.ACCESS_DENIED) {
            text.setText(Html.fromHtml(getString(R.string.access_denied_empty_list)));
        } else if (error == DataService.Errors.NO_JSON) {
            text.setText(Html.fromHtml(getString(R.string.no_json_empty_list)));
        } else if (error == DataService.Errors.NO_CONNECTION) {
            text.setText(Html.fromHtml(getString(R.string.no_connection_empty_list)));
        } else if (error == DataService.Errors.THREAD_ERROR) {
            text.setText(Html.fromHtml(getString(R.string.thread_error_empty_list)));
        } else if (error == DataService.Errors.RESPONSE_ERROR) {
            text.setText(Html.fromHtml(getString(R.string.response_error_empty_list)));
        } else if (error == DataService.Errors.TIMEOUT) {
            text.setText(Html.fromHtml(getString(R.string.timeout_empty_list)));
        } else if (error == DataService.Errors.OUT_OF_MEMORY) {
            text.setText(Html.fromHtml(getString(R.string.out_of_memory_empty_list)));
        } else if (error == DataService.Errors.JSON_PARSE_ERROR) {
            text.setText(Html.fromHtml(getString(R.string.json_parse_empty_list)));
        } else if (error == DataService.Errors.GENERIC_HTTP) {
            text.setText(Html.fromHtml(String.format(getString(R.string.generic_http_empty_list),
                code, string)));
        }
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
                readSource.open();

                return readSource.getTorrentCursor(profile.getId());
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

            if (profileId == null || !profileId.equals(profile.getId())) {
                return;
            }

            hasFatalError = false;

            String type = intent.getStringExtra(G.ARG_REQUEST_TYPE);
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
