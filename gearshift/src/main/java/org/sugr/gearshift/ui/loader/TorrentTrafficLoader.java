package org.sugr.gearshift.ui.loader;

import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;

import org.sugr.gearshift.datasource.DataSource;

import java.util.List;

public class TorrentTrafficLoader
    extends AsyncTaskLoader<TorrentTrafficLoader.TorrentTrafficOutputData> {

    public static class TorrentTrafficOutputData {
        public long downloadSpeed = -1;
        public long uploadSpeed = -1;
        public List<String> directories = null;
        public List<String> trackers = null;
    }

    private String profile;
    private TorrentTrafficOutputData result;
    private boolean queryTraffic;
    private boolean queryDirectories;
    private boolean queryTrackers;

    public TorrentTrafficLoader(Context context, String profile, boolean queryTraffic,
                                boolean queryDirectories, boolean queryTrackers) {
        super(context);

        this.profile = profile;
        this.queryTraffic = queryTraffic;
        this.queryDirectories = queryDirectories;
        this.queryTrackers = queryTrackers;
    }

    @Override public TorrentTrafficOutputData loadInBackground() {
        DataSource dataSource = new DataSource(getContext());
        TorrentTrafficOutputData output = new TorrentTrafficOutputData();

        dataSource.open();
        try {
            if (queryTraffic) {
                long[] speed = dataSource.getTrafficSpeed(profile);
                output.downloadSpeed = speed[0];
                output.uploadSpeed = speed[1];
            }

            if (queryTrackers) {
                output.trackers = dataSource.getTrackerAnnounceURLs(profile);
            }

            if (queryDirectories) {
                output.directories = dataSource.getDownloadDirectories(profile);
            }
        } catch (Exception ignored) {
            output = null;
        } finally {
            dataSource.close();
        }

        return output;
    }

    @Override protected void onStartLoading() {
        if (result != null) {
            deliverResult(result);
        }

        if (takeContentChanged() || result == null) {
            forceLoad();
        }
    }

    @Override protected void onReset() {
        result = null;
    }

    @Override public void deliverResult(TorrentTrafficOutputData result) {
        this.result = result;

        if (isStarted()) {
            super.deliverResult(result);
        }
    }
}
