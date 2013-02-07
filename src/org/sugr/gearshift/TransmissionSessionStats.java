package org.sugr.gearshift;

import com.google.gson.annotations.SerializedName;

public class TransmissionSessionStats {
    @SerializedName("activeTorrentCount") private int mActiveTorrentCount;
    @SerializedName("downloadSpeed") private long mDownloadSpeed;
    @SerializedName("pausedTorrentCount") private int mPausedTorrentCount;
    @SerializedName("torrentCount") private int mTorrentCount;
    @SerializedName("uploadSpeed") private long mUploadSpeed;

    @SerializedName("cumulative-stats") private Stats mCumulativeStats;
    @SerializedName("current-stats") private Stats mCurrentStats;

    public static class Stats {
        @SerializedName("uploadedBytes") private long mUploadedBytes;
        @SerializedName("downloadedBytes") private long mDownloadedBytes;
        @SerializedName("filesAdded") private int mFilesAdded;
        @SerializedName("sessionCount") private int mSessionCount;
        @SerializedName("secondsActive") private long mSecondsActive;
        
        public long getUploadedBytes() {
            return mUploadedBytes;
        }
        public long getDownloadedBytes() {
            return mDownloadedBytes;
        }
        public int getFilesAdded() {
            return mFilesAdded;
        }
        public int getSessionCount() {
            return mSessionCount;
        }
        public long getSecondsActive() {
            return mSecondsActive;
        }
        public void setUploadedBytes(long uploadedBytes) {
            this.mUploadedBytes = uploadedBytes;
        }
        public void setDownloadedBytes(long downloadedBytes) {
            this.mDownloadedBytes = downloadedBytes;
        }
        public void setFilesAdded(int filesAdded) {
            this.mFilesAdded = filesAdded;
        }
        public void setSessionCount(int sessionCount) {
            this.mSessionCount = sessionCount;
        }
        public void setSecondsActive(long secondsActive) {
            this.mSecondsActive = secondsActive;
        }
    }

    public int getActiveTorrentCount() {
        return mActiveTorrentCount;
    }

    public long getDownloadSpeed() {
        return mDownloadSpeed;
    }

    public int getPausedTorrentCount() {
        return mPausedTorrentCount;
    }

    public int getTorrentCount() {
        return mTorrentCount;
    }

    public long getUploadSpeed() {
        return mUploadSpeed;
    }

    public void setActiveTorrentCount(int activeTorrentCount) {
        this.mActiveTorrentCount = activeTorrentCount;
    }

    public void setDownloadSpeed(long downloadSpeed) {
        this.mDownloadSpeed = downloadSpeed;
    }

    public void setPausedTorrentCount(int pausedTorrentCount) {
        this.mPausedTorrentCount = pausedTorrentCount;
    }

    public void setTorrentCount(int torrentCount) {
        this.mTorrentCount = torrentCount;
    }

    public void setUploadSpeed(long uploadSpeed) {
        this.mUploadSpeed = uploadSpeed;
    }
}
