package org.sugr.gearshift;

import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;

import org.sugr.gearshift.datasource.DataSource;

import java.util.Set;

public class TorrentTrafficLoader
    extends AsyncTaskLoader<TorrentTrafficLoader.TorrentTrafficOutputData> {

    public static class TorrentTrafficOutputData {
        public long downloadSpeed = -1;
        public long uploadSpeed = -1;
        public Set<String> directories = null;
        public Set<String> trackers = null;
    }

    private TorrentTrafficOutputData result;
    private boolean queryTraffic;
    private boolean queryDirectories;
    private boolean queryTrackers;

    public TorrentTrafficLoader(Context context, boolean queryTraffic,
                                boolean queryDirectories, boolean queryTrackers) {
        super(context);

        this.queryTraffic = queryTraffic;
        this.queryDirectories = queryDirectories;
        this.queryTrackers = queryTrackers;
    }

    @Override public TorrentTrafficOutputData loadInBackground() {
        DataSource dataSource = new DataSource(getContext());
        TorrentTrafficOutputData output = new TorrentTrafficOutputData();

        try {
            dataSource.open();

            if (queryTraffic) {
                long[] speed = dataSource.getTrafficSpeed();
                output.downloadSpeed = speed[0];
                output.uploadSpeed = speed[1];
            }

            if (queryTrackers) {
                output.trackers = dataSource.getTrackerAnnounceURLs();
            }
            if (queryDirectories) {
                output.directories = dataSource.getDownloadDirectories();
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
    }
}
