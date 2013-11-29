package org.sugr.gearshift;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.NONE,
    creatorVisibility = JsonAutoDetect.Visibility.NONE,
    getterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
    isGetterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
    setterVisibility = JsonAutoDetect.Visibility.NONE)
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
