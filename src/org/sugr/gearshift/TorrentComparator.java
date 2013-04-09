package org.sugr.gearshift;

import java.util.Comparator;

import org.sugr.gearshift.G.SortBy;
import org.sugr.gearshift.G.SortOrder;

public class TorrentComparator implements Comparator<Torrent> {
    private SortBy mSortBy = SortBy.STATUS;
    private SortOrder mSortOrder = SortOrder.ASCENDING;
    private SortBy mBaseSort = SortBy.AGE;

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

    public void setBaseSort(SortBy by) {
        mBaseSort = by;
    }

    public SortBy getBaseSort() {
        return mBaseSort;
    }

    @Override
    public int compare(Torrent a, Torrent b) {
        int ret = compare(a, b, mSortBy, mSortOrder);
        if (ret == 0 && mBaseSort != mSortBy) {
            ret = compare(a, b, mBaseSort, mSortOrder);
        }

        return ret;
    }

    private int compare(Torrent a, Torrent b, SortBy sort, SortOrder order) {
        int ret = 0;
        float delta;

        switch(sort) {
            case NAME:
                ret = a.getName() == null && b.getName() == null
                    ? 0 : a.getName() == null
                    ? -1 : a.getName().compareToIgnoreCase(b.getName());
                break;
            case SIZE:
                ret = (int) (a.getTotalSize() - b.getTotalSize());
                break;
            case STATUS:
                ret = (a.getStatus() == Torrent.Status.STOPPED
                        ? a.getStatus() + 100 : a.getStatus() == Torrent.Status.CHECK_WAITING
                        ? a.getStatus() + 10  : a.getStatus() == Torrent.Status.DOWNLOAD_WAITING
                        ? a.getStatus() + 20  : a.getStatus() == Torrent.Status.SEED_WAITING
                        ? a.getStatus() + 30  : a.getStatus())
                    - (b.getStatus() == Torrent.Status.STOPPED
                        ? b.getStatus() + 100 : b.getStatus() == Torrent.Status.CHECK_WAITING
                        ? b.getStatus() + 10  : b.getStatus() == Torrent.Status.DOWNLOAD_WAITING
                        ? b.getStatus() + 20  : b.getStatus() == Torrent.Status.SEED_WAITING
                        ? b.getStatus() + 30  : b.getStatus());
                break;
            case ACTIVITY:
                ret = (int) ((b.getRateDownload() + b.getRateUpload()) - (a.getRateDownload() + a.getRateUpload()));
                break;
            case AGE:
                ret = (int) (b.getAddedDate() - a.getAddedDate());
                break;
            case LOCATION:
                ret = a.getDownloadDir() == null && b.getDownloadDir() == null
                    ?  0 : a.getDownloadDir() == null
                    ? -1 : a.getDownloadDir().compareToIgnoreCase(b.getDownloadDir());
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
                ret = (int) (b.getRateDownload() - a.getRateDownload());
                break;
            case RATE_UPLOAD:
                ret = (int) (b.getRateUpload() - a.getRateUpload());
                break;
            case RATIO:
                delta = b.getUploadRatio() - a.getUploadRatio();
                ret = delta < 0 ? -1 : delta > 0 ? 1 : 0;
                break;
            default:
                break;
        }

        return order == SortOrder.ASCENDING ? ret : -ret;
    }
}
