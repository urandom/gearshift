package org.sugr.gearshift.datasource;

import android.database.Cursor;

public class TorrentDetails {
    public Cursor torrentCursor;
    public Cursor filesCursor;
    public Cursor trackersCursor;

    public TorrentDetails(Cursor torrent, Cursor trackers, Cursor files) {
        torrentCursor = torrent;
        filesCursor = files;
        trackersCursor = trackers;
    }
}
