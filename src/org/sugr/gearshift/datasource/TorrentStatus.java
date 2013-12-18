package org.sugr.gearshift.datasource;

public class TorrentStatus {
    public boolean hasAdded;
    public boolean hasRemoved;
    public boolean hasStatusChanged;
    public boolean hasIncompleteMetadata;

    public TorrentStatus() { }

    public TorrentStatus(boolean added, boolean removed,
                         boolean statusChanged, boolean incompleteMetadata) {
        this();

        hasAdded = added;
        hasRemoved = removed;
        hasStatusChanged = statusChanged;
        hasIncompleteMetadata = incompleteMetadata;
    }
}
