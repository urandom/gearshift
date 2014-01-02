package org.sugr.gearshift;

import android.app.Application;
import android.os.AsyncTask;

import org.sugr.gearshift.datasource.DataSource;

public class GearShiftApplication extends Application {
    @Override public void onCreate() {
        new ClearTorrentsTask().execute();
        super.onCreate();
    }

    private class ClearTorrentsTask extends AsyncTask<Void, Void, Void> {
        @Override protected Void doInBackground(Void... voids) {
            DataSource dataSource = new DataSource(getApplicationContext());
            dataSource.open();

            dataSource.clearTorrents();

            dataSource.close();
            return null;
        }
    }
}
