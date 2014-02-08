package org.sugr.gearshift.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;

import org.sugr.gearshift.G;
import org.sugr.gearshift.Torrent;
import org.sugr.gearshift.TransmissionData;
import org.sugr.gearshift.TransmissionSession;

import java.util.ArrayList;

public class DataServiceManager {
    private Context context;
    private String profileId;

    private int iteration;
    private boolean details;
    private boolean isStopped;
    private boolean isLastErrorFatal;
    private boolean sessionOnly;

    private String[] torrentsToUpdate;
    private ServiceReceiver serviceReceiver;

    private Handler updateHandler = new Handler();
    private Runnable updateRunnable = new Runnable() {
        @Override public void run() {
            synchronized (DataServiceManager.this) {
                if (!isStopped) {
                    update();
                }
            }
        }
    };

    private static final String STATE_ITERATION = "data_service_iteration";

    public DataServiceManager(Context context, String profileId) {
        this.context = context;
        this.profileId = profileId;

        iteration = 0;
        serviceReceiver = new ServiceReceiver();
    }

    public DataServiceManager onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putInt(STATE_ITERATION, iteration);

        return this;
    }

    public DataServiceManager onRestoreInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(STATE_ITERATION)) {
                iteration = savedInstanceState.getInt(STATE_ITERATION);
            }
        }

        return this;
    }

    public DataServiceManager reset() {
        iteration = 0;
        stopUpdating();

        return this;
    }

    public DataServiceManager setDetails(boolean details) {
        this.details = details;

        return this;
    }

    public DataServiceManager setSessionOnly(boolean sessionOnly) {
        this.sessionOnly = sessionOnly;

        return this;
    }

    public DataServiceManager setTorrentsToUpdate(String[] hashStrings) {
        torrentsToUpdate = hashStrings;
        update();

        return this;
    }

    public DataServiceManager startUpdating() {
        synchronized (this) {
            isStopped = false;
            LocalBroadcastManager.getInstance(context).registerReceiver(serviceReceiver,
                new IntentFilter(G.INTENT_SERVICE_ACTION_COMPLETE));
        }
        isLastErrorFatal = false;

        update();

        return this;
    }

    public DataServiceManager stopUpdating() {
        synchronized (this) {
            isStopped = true;
            LocalBroadcastManager.getInstance(context).unregisterReceiver(serviceReceiver);
        }

        return this;
    }

    public void update() {
        updateHandler.removeCallbacks(updateRunnable);

        if (sessionOnly) {
            getSession();
        } else {
            SharedPreferences prefs = getPreferences();
            boolean active = prefs.getBoolean(G.PREF_UPDATE_ACTIVE, false);
            int fullUpdate = Integer.parseInt(prefs.getString(G.PREF_FULL_UPDATE, "2"));

            String requestType = DataService.Requests.GET_TORRENTS;
            Bundle args = new Bundle();

            if (active && !details && iteration % fullUpdate != 0) {
                args.putBoolean(DataService.Args.UPDATE_ACTIVE, true);
            }

            if (details || iteration == 1) {
                args.putBoolean(DataService.Args.DETAIL_FIELDS, true);
            }

            if (torrentsToUpdate != null) {
                args.putStringArray(DataService.Args.TORRENTS_TO_UPDATE, torrentsToUpdate);
            }

            if (iteration == 0 || isLastErrorFatal) {
                args.putBoolean(DataService.Args.REMOVE_OBSOLETE, true);
            }

            if (iteration % 3 == 0) {
                getSession();
            }

            Intent intent = createIntent(requestType, args);

            context.startService(intent);
        }

        iteration++;
        isLastErrorFatal = false;
    }

    public DataServiceManager getSession() {
        context.startService(createIntent(DataService.Requests.GET_SESSION, null));

        return this;
    }

    public DataServiceManager setSession(TransmissionSession session, String... fields) {
        Bundle args = new Bundle();

        args.putParcelable(DataService.Args.SESSION, session);
        args.putStringArray(DataService.Args.SESSION_FIELDS, fields);
        Intent intent = createIntent(DataService.Requests.SET_SESSION, args);

        context.startService(intent);

        return this;
    }

    public DataServiceManager addTorrent(String magnet, String data, String location,
                                         boolean addPaused, String temporaryFile, Uri documentUri) {
        Bundle args = new Bundle();

        args.putString(DataService.Args.MAGNET_URI, magnet);
        args.putString(DataService.Args.TORRENT_DATA, data);
        args.putString(DataService.Args.LOCATION, location);
        args.putBoolean(DataService.Args.ADD_PAUSED, addPaused);
        args.putString(DataService.Args.TEMPORARY_FILE, temporaryFile);
        args.putParcelable(DataService.Args.DOCUMENT_URI, documentUri);
        Intent intent = createIntent(DataService.Requests.ADD_TORRENT, args);

        context.startService(intent);

        return this;
    }

    public DataServiceManager removeTorrent(String[] hashStrings, boolean deleteData) {
        Bundle args = new Bundle();

        args.putStringArray(DataService.Args.HASH_STRINGS, hashStrings);
        args.putBoolean(DataService.Args.DELETE_DATA, deleteData);
        Intent intent = createIntent(DataService.Requests.REMOVE_TORRENT, args);

        context.startService(intent);

        return this;
    }

    public DataServiceManager setTorrent(String[] hashStrings, String field, int value) {
        Bundle args = new Bundle();

        args.putInt(DataService.Args.TORRENT_FIELD_VALUE, value);
        return setTorrent(hashStrings, field, args);
    }

    public DataServiceManager setTorrent(String[] hashStrings, String field, long value) {
        Bundle args = new Bundle();

        args.putLong(DataService.Args.TORRENT_FIELD_VALUE, value);
        return setTorrent(hashStrings, field, args);
    }

    public DataServiceManager setTorrent(String[] hashStrings, String field, boolean value) {
        Bundle args = new Bundle();

        args.putBoolean(DataService.Args.TORRENT_FIELD_VALUE, value);
        return setTorrent(hashStrings, field, args);
    }

    public DataServiceManager setTorrent(String[] hashStrings, String field, float value) {
        Bundle args = new Bundle();

        args.putFloat(DataService.Args.TORRENT_FIELD_VALUE, value);
        return setTorrent(hashStrings, field, args);
    }

    @SuppressWarnings("unchecked")
    public DataServiceManager setTorrent(String[] hashStrings, String field, ArrayList<?> value) {
        Bundle args = new Bundle();

        switch (field) {
            case Torrent.SetterFields.FILES_WANTED:
            case Torrent.SetterFields.FILES_UNWANTED:
            case Torrent.SetterFields.FILES_LOW:
            case Torrent.SetterFields.FILES_NORMAL:
            case Torrent.SetterFields.FILES_HIGH:
            case Torrent.SetterFields.TRACKER_REMOVE:
                args.putIntegerArrayList(DataService.Args.TORRENT_FIELD_VALUE, (ArrayList<Integer>) value);
                break;
            case Torrent.SetterFields.TRACKER_ADD:
            case Torrent.SetterFields.TRACKER_REPLACE:
                args.putStringArrayList(DataService.Args.TORRENT_FIELD_VALUE, (ArrayList<String>) value);
                break;
        }
        return setTorrent(hashStrings, field, args);
    }

    public DataServiceManager setTorrentLocation(String[] hashStrings, String location, boolean move) {
        Bundle args = new Bundle();

        args.putStringArray(DataService.Args.HASH_STRINGS, hashStrings);
        args.putString(DataService.Args.LOCATION, location);
        args.putBoolean(DataService.Args.MOVE_DATA, move);
        Intent intent = createIntent(DataService.Requests.SET_TORRENT_LOCATION, args);

        context.startService(intent);

        return this;
    }

    public DataServiceManager setTorrentAction(String[] hashStrings, String action) {
        Bundle args = new Bundle();

        args.putStringArray(DataService.Args.HASH_STRINGS, hashStrings);
        args.putString(DataService.Args.TORRENT_ACTION, action);
        Intent intent = createIntent(DataService.Requests.SET_TORRENT_ACTION, args);

        context.startService(intent);

        return this;
    }

    public DataServiceManager removeProfile() {
        Intent intent = createIntent(DataService.Requests.REMOVE_PROFILE, null);

        context.startService(intent);

        return this;
    }

    public DataServiceManager getFreeSpace(String location) {
        Bundle args = new Bundle();

        args.putString(DataService.Args.LOCATION, location);
        Intent intent = createIntent(DataService.Requests.GET_FREE_SPACE, args);

        context.startService(intent);

        return this;
    }

    public DataServiceManager testPort() {
        Intent intent = createIntent(DataService.Requests.TEST_PORT, null);

        context.startService(intent);

        return this;
    }

    public DataServiceManager updateBlocklist() {
        Intent intent = createIntent(DataService.Requests.UPDATE_BLOCKLIST, null);

        context.startService(intent);

        return this;
    }

    private SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private Intent createIntent(String requestType, Bundle args) {
        Intent intent = new Intent(context, DataService.class);
        intent.putExtra(G.ARG_PROFILE_ID, profileId);
        intent.putExtra(G.ARG_REQUEST_TYPE, requestType);
        intent.putExtra(G.ARG_REQUEST_ARGS, args == null ? new Bundle() : args);

        return intent;
    }

    private void repeatLoading() {
        SharedPreferences prefs = getPreferences();
        int update = Integer.parseInt(prefs.getString(G.PREF_UPDATE_INTERVAL, "-1"));
        if (update >= 0 && !isStopped) {
            if (sessionOnly && update < 10) {
                update = 10;
            }
            updateHandler.postDelayed(updateRunnable, update * 1000);
        }
    }

    private DataServiceManager setTorrent(String[] hashStrings, String field, Bundle args) {
        args.putStringArray(DataService.Args.HASH_STRINGS, hashStrings);
        args.putString(DataService.Args.TORRENT_FIELD, field);
        Intent intent = createIntent(DataService.Requests.SET_TORRENT, args);

        context.startService(intent);

        return this;
    }

    private class ServiceReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            int error = intent.getIntExtra(G.ARG_ERROR, 0);

            if (error == 0 || error == TransmissionData.Errors.DUPLICATE_TORRENT
                || error == TransmissionData.Errors.INVALID_TORRENT) {

                String type = intent.getStringExtra(G.ARG_REQUEST_TYPE);
                switch (type) {
                    case DataService.Requests.GET_TORRENTS:
                        repeatLoading();
                        break;
                    case DataService.Requests.ADD_TORRENT:
                        update();
                        break;
                }
            } else {
                isLastErrorFatal = true;
            }
        }
    }

}
