package org.sugr.gearshift;

public interface TorrentListNotification {
    public void notifyTorrentListChanged(
        int error, boolean added, boolean removed,
        boolean statusChanged, boolean metadataNeeded);
}
