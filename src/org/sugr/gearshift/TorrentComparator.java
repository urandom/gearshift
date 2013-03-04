package org.sugr.gearshift;

import java.util.Comparator;

public class TorrentComparator implements Comparator<Torrent> {
    public static enum SortBy {
        NAME, SIZE, STATUS, ACTIVITY, AGE, PROGRESS, RATIO, LOCATION,
        PEERS, RATE_DOWNLOAD, RATE_UPLOAD, QUEUE
    };

    public static enum SortOrder {
        ASCENDING, DESCENDING
    };

    private SortBy mSortBy = SortBy.STATUS;
    private SortOrder mSortOrder = SortOrder.ASCENDING;

    public void setSortingMethod(SortBy by, SortOrder order) {
        mSortBy = by;
        mSortOrder = order;
    }

    public SortBy getSortBy() {
        return mSortBy;
    }

    public SortOrder getSortOrder() {
        return mSortOrder;
    }

    @Override
    public int compare(Torrent a, Torrent b) {
        /* FIXME: use addedDate instead of name as the base order */
        String aName = a.getName(), bName = b.getName();
        if (aName == null && bName == null) {
            return 0;
        } else if (aName == null) {
            return -1;
        } else if (bName == null) {
            return 1;
        }

        int nameComp = aName.compareToIgnoreCase(bName);
        /* Artificially inflate the download status to appear above
         * the seeding */
        int statusComp = (b.getStatus() == Torrent.Status.DOWNLOADING
            ? b.getStatus() + 10 : b.getStatus()
        ) - (a.getStatus() == Torrent.Status.DOWNLOADING
            ? a.getStatus() + 10 : a.getStatus());
        int ret = 0;
        float delta;

        if (nameComp == 0)
            nameComp = a.getId() - b.getId();
        if (statusComp == 0)
            statusComp = nameComp;

        switch(mSortBy) {
            case NAME:
                ret = nameComp;
                break;
            case SIZE:
                ret = (int) (a.getTotalSize() - b.getTotalSize());
                break;
            case STATUS:
                ret = statusComp;
                break;
            case ACTIVITY:
                ret = (int) ((b.getRateDownload() + b.getRateUpload()) - (a.getRateDownload() + a.getRateUpload()));
                break;
            case AGE:
                ret = (int) (b.getAddedDate() - a.getAddedDate());
                break;
            case LOCATION:
                ret = a.getDownloadDir().compareToIgnoreCase(b.getDownloadDir());
                break;
            case PEERS:
                ret = a.getPeersConnected() - b.getPeersConnected();
                break;
            case PROGRESS:
                delta = a.getPercentDone() - b.getPercentDone();
                ret = delta < 0 ? -1 : delta > 0 ? 1 : 0;
                break;
            case QUEUE:
                ret = a.getQueuePosition() - b.getQueuePosition();
                break;
            case RATE_DOWNLOAD:
                ret = (int) (a.getRateDownload() - b.getRateDownload());
                break;
            case RATE_UPLOAD:
                ret = (int) (a.getRateUpload() - b.getRateUpload());
                break;
            case RATIO:
                delta = b.getUploadRatio() - a.getUploadRatio();
                ret = delta < 0 ? -1 : delta > 0 ? 1 : 0;
                break;
            default:
                break;
        }
        if (mSortBy != SortBy.NAME && mSortBy != SortBy.STATUS
                && ret == 0) {
            ret = statusComp;
        }

        return mSortOrder == SortOrder.ASCENDING ? ret : -ret;
    }
}
