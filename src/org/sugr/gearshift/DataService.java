package org.sugr.gearshift;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;

public class DataService extends IntentService {
    public static final String EXTRA_PROFILE_ID = ".profile_id";
    public static final String EXTRA_REQUEST_TYPE = ".request_type";
    public static final String EXTRA_REQUEST_ARGS = ".request_args";

    public static final String REQUEST_TYPE_ALL_TORRENTS = "all_torrents";
    public static final String REQUEST_TYPE_CURRENT_TORRENTS = "current_torrents";

    public DataService() {
        super("DataService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String packageName = getPackageName();
        String profileId = intent.getStringExtra(packageName + EXTRA_PROFILE_ID);
        String requestType = intent.getStringExtra(packageName + EXTRA_REQUEST_TYPE);
        Bundle args = intent.getBundleExtra(packageName + EXTRA_REQUEST_ARGS);
    }
}
