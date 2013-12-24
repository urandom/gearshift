package org.sugr.gearshift;

import android.database.Cursor;

public interface TorrentListNotification {
    public void notifyTorrentListChanged(
        Cursor cursor, int error, boolean added, boolean removed,
        boolean statusChanged, boolean metadataNeeded);
}
