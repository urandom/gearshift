package org.sugr.gearshift;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;

import org.sugr.gearshift.datasource.DataSource;

public class DataService extends IntentService {
    public static final String EXTRA_PROFILE_ID = ".profile_id";
    public static final String EXTRA_REQUEST_TYPE = ".request_type";
    public static final String EXTRA_REQUEST_ARGS = ".request_args";

    public static final class Requests {
        public static final String ALL_TORRENTS = "all_torrents";
        public static final String CURRENT_TORRENTS = "current_torrents";
        public static final String CLEAR_TORRENTS_FOR_PROFILE = "clear_torrents_for_profile";
    }

    public static final class Args {
    }

    private DataSource dataSource;
    private TransmissionSessionManager manager;

    public DataService() {
        super("DataService");

        dataSource = new DataSource(this);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String packageName = getPackageName();
        String profileId = intent.getStringExtra(packageName + EXTRA_PROFILE_ID);
        String requestType = intent.getStringExtra(packageName + EXTRA_REQUEST_TYPE);
        Bundle args = intent.getBundleExtra(packageName + EXTRA_REQUEST_ARGS);

        dataSource.open();
        if (manager == null) {
            TransmissionProfile profile = new TransmissionProfile(profileId, this);
            manager = new TransmissionSessionManager(this, profile, dataSource);
        }

        try {
            /* TODO: check whether requestType or profile are null */

            if (requestType.equals(Requests.ALL_TORRENTS)) {

            } else if (requestType.equals(Requests.CURRENT_TORRENTS)) {

            } else if (requestType.equals(Requests.CLEAR_TORRENTS_FOR_PROFILE)) {
                manager.clearTorrents();
            }
        } finally {
            dataSource.close();
        }
    }
}
