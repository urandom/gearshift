package org.sugr.gearshift;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;

public class DataServiceManager {
    private Context context;
    private String profileId;

    private int iteration;
    private boolean details;
    private boolean isStopped;
    private boolean isLastErrorFatal;

    private String[] torrentsToUpdate;
    private ResponceReceiver responceReceiver;

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

    public DataServiceManager(Context context, String profileId) {
        this.context = context;
        this.profileId = profileId;

        responceReceiver = new ResponceReceiver();
        context.registerReceiver(responceReceiver,
            new IntentFilter(G.INTENT_SERVICE_ACTION_COMPLETE));
    }

    public void reset() {
        stopUpdating();
        context.unregisterReceiver(responceReceiver);
    }

    public void setDetails(boolean details) {
        this.details = details;
    }

    public void setTorrentsToUpdate(String[] hashStrings) {
        torrentsToUpdate = hashStrings;
        update();
    }

    public DataServiceManager startUpdating() {
        synchronized (this) {
            isStopped = false;
        }
        iteration = 0;
        isLastErrorFatal = false;

        update();

        return this;
    }

    public DataServiceManager stopUpdating() {
        synchronized (this) {
            isStopped = true;
        }

        return this;
    }

    public DataServiceManager clearTorrents() {
        Intent intent = createIntent(DataService.Requests.CLEAR_TORRENTS_FOR_PROFILE, null);

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

    private void update() {
        updateHandler.removeCallbacks(updateRunnable);

        SharedPreferences prefs = getPreferences();
        boolean active = prefs.getBoolean(G.PREF_UPDATE_ACTIVE, false);
        int fullUpdate = Integer.parseInt(prefs.getString(G.PREF_FULL_UPDATE, "2"));

        String requestType;
        if (active && !details && iteration % fullUpdate != 0) {
            requestType = DataService.Requests.GET_ACTIVE_TORRENTS;
        } else {
            requestType = DataService.Requests.GET_ALL_TORRENTS;
        }

        Bundle args = new Bundle();
        if (details) {
            args.putBoolean(DataService.Args.DETAIL_FIELDS, details);
        }

        if (torrentsToUpdate != null) {
            args.putStringArray(DataService.Args.TORRENTS_TO_UPDATE, torrentsToUpdate);
        }

        if (iteration == 0 || isLastErrorFatal) {
            args.putBoolean(DataService.Args.REMOVE_OBSOLETE, true);
        }

        if (iteration % 3 == 0) {
            context.startService(createIntent(DataService.Requests.GET_SESSION, null));
        }

        Intent intent = createIntent(requestType, args);

        context.startService(intent);

        iteration++;
        isLastErrorFatal = false;
    }

    private void repeatLoading() {
        SharedPreferences prefs = getPreferences();
        int update = Integer.parseInt(prefs.getString(G.PREF_UPDATE_INTERVAL, "-1"));
        if (update >= 0 && !isStopped) {
            updateHandler.postDelayed(updateRunnable, update * 1000);
        }
    }

    private class ResponceReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            int error = intent.getIntExtra(G.ARG_ERROR, 0);

            if (error == 0 || error == TransmissionData.Errors.DUPLICATE_TORRENT
                    || error == TransmissionData.Errors.INVALID_TORRENT) {
                repeatLoading();
            } else {
                isLastErrorFatal = true;
            }
        }
    }
}
