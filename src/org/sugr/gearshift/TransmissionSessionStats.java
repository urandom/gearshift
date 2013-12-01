package org.sugr.gearshift;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.NONE,
    creatorVisibility = JsonAutoDetect.Visibility.NONE,
    getterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
    isGetterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
    setterVisibility = JsonAutoDetect.Visibility.NONE)
public class TransmissionSessionStats {
    private int mActiveTorrentCount;
    private long mDownloadSpeed;
    private int mPausedTorrentCount;
    private int mTorrentCount;
    private long mUploadSpeed;

    private Stats mCumulativeStats;
    private Stats mCurrentStats;

    public static class Stats {
        private long mUploadedBytes;
        private long mDownloadedBytes;
        private int mFilesAdded;
        private int mSessionCount;
        private long mSecondsActive;

        @JsonProperty("uploadedBytes") public long getUploadedBytes() {
            return mUploadedBytes;
        }
        @JsonProperty("downloadedBytes") public long getDownloadedBytes() {
            return mDownloadedBytes;
        }
        @JsonProperty("filesAdded") public int getFilesAdded() {
            return mFilesAdded;
        }
        @JsonProperty("sessionCount") public int getSessionCount() {
            return mSessionCount;
        }
        @JsonProperty("secondsActive") public long getSecondsActive() {
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

    @JsonProperty("activeTorrentCount") public int getActiveTorrentCount() {
        return mActiveTorrentCount;
    }

    @JsonProperty("downloadSpeed") public long getDownloadSpeed() {
        return mDownloadSpeed;
    }

    @JsonProperty("pausedTorrentCount") public int getPausedTorrentCount() {
        return mPausedTorrentCount;
    }

    @JsonProperty("torrentCount") public int getTorrentCount() {
        return mTorrentCount;
    }

    @JsonProperty("uploadSpeed") public long getUploadSpeed() {
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
