package org.sugr.gearshift;

import java.util.List;

public interface TorrentListNotification {
    public void notifyTorrentListChanged(
        List<Torrent> torrents, int error, boolean added, boolean removed,
        boolean statusChanged, boolean metadataNeeded);
}
