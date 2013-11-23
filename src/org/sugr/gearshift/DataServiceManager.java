package org.sugr.gearshift;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

public class DataServiceManager {
    private Context context;
    private AlarmManager alarmManager;
    private String profileId;

    public DataServiceManager(Context context, String profileId) {
        this.context = context;
        this.profileId = profileId;

        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    public DataServiceManager start() {
        return start(DataService.REQUEST_TYPE_ALL_TORRENTS, null);
    }

    public DataServiceManager start(String requestType, Bundle args) {
        SharedPreferences prefs = getPreferences();
        int update = Integer.parseInt(prefs.getString(G.PREF_UPDATE_INTERVAL, "-1"));

        Intent intent = createIntent(requestType, args);

        PendingIntent pendingIntent = PendingIntent.getService(context,
            0,  intent, PendingIntent.FLAG_UPDATE_CURRENT);

        alarmManager.setInexactRepeating(AlarmManager.RTC,
            update * 1000, update * 1000, pendingIntent);

        context.startService(intent);

        return this;
    }

    public DataServiceManager stop() {
        PendingIntent pendingIntent = PendingIntent.getService(context,
                0,  new Intent(context, DataService.class), 0);

        alarmManager.cancel(pendingIntent);

        return this;
    }

    private SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private Intent createIntent(String requestType, Bundle args) {
        Intent intent = new Intent(context, DataService.class);
        intent.putExtra(context.getPackageName() + DataService.EXTRA_PROFILE_ID, profileId);
        intent.putExtra(context.getPackageName() + DataService.EXTRA_REQUEST_TYPE, requestType);
        intent.putExtra(context.getPackageName() + DataService.EXTRA_REQUEST_ARGS, args);

        return intent;
    }
}
