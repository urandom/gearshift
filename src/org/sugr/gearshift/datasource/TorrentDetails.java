package org.sugr.gearshift.datasource;

import android.database.Cursor;

public class TorrentDetails {
    public Cursor torrentCursor;
    public Cursor filesCursor;
    public Cursor trackersCursor;

    public TorrentDetails(Cursor torrent, Cursor files, Cursor trackers) {
        torrentCursor = torrent;
        filesCursor = files;
        trackersCursor = trackers;
    }
}
