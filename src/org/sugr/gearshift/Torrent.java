package org.sugr.gearshift;

import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Html;
import android.text.Spanned;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.sugr.gearshift.datasource.Constants;

@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.NONE,
    creatorVisibility = JsonAutoDetect.Visibility.NONE,
    getterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
    isGetterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
    setterVisibility = JsonAutoDetect.Visibility.NONE)
public class Torrent implements Parcelable {
    public static final class SetterFields {
        public static final String DOWNLOAD_LIMIT = "downloadLimit";
        public static final String DOWNLOAD_LIMITED = "downloadLimited";
        public static final String PEER_LIMIT = "peer-limit";
        public static final String QUEUE_POSITION = "queuePosition";
        public static final String SEED_RATIO_LIMIT = "seedRatioLimit";
        public static final String SEED_RATIO_MODE = "seedRatioMode";
        public static final String SESSION_LIMITS = "honorsSessionLimits";
        public static final String TORRENT_PRIORITY = "bandwidthPriority";
        public static final String UPLOAD_LIMIT = "uploadLimit";
        public static final String UPLOAD_LIMITED = "uploadLimited";

        public static final String FILES_WANTED = "files-wanted";
        public static final String FILES_UNWANTED = "files-unwanted";
        public static final String FILES_HIGH = "priority-high";
        public static final String FILES_NORMAL = "priority-normal";
        public static final String FILES_LOW = "priority-low";

        public static final String TRACKER_ADD = "trackerAdd";
        public static final String TRACKER_REMOVE = "trackerRemove";
        public static final String TRACKER_REPLACE = "trackerReplace";

    }

    public static final class AddFields {
        public static final String URI = "filename";
        public static final String META = "metainfo";
        public static final String LOCATION = "download-dir";
        public static final String PAUSED = "paused";
    }

    private int mId;
    private int mStatus = Status.STOPPED;

    private String mName = "";

    private int mError;
    private String mErrorString;

    private float mMetadataPercentComplete = 0;
    /* User selected */
    private float mPercentDone = 0;

    private long mEta;

    private boolean mFinished = false;
    private boolean mStalled = true;

    private int mPeersConnected = 0;
    private int mPeersGettingFromUs = 0;
    private int mPeersSendingToUs = 0;

    /* In bytes */
    private long mLeftUntilDone;
    /* 0 .. leftUntilDone */
     private long mDesiredAvailable;

    private long mTotalSize;
    private long mSizeWhenDone;

    private long mRateDownload;
    private long mRateUpload;

    private int mQueuePosition;

    private float mRecheckProgress;

    private int mSeedRatioMode;
    private float mSeedRatioLimit;

    private long mUploadedEver;
    private float mUploadRatio;

    private long mAddedDate = 0;
    private long mDoneDate;
    private long mStartDate;
    private long mActivityDate;

    private long mCorruptEver;

    private String mDownloadDir;
    private long mDownloadedEver;

    private long mHaveUnchecked;
    private long mHaveValid;

    private Tracker[] mTrackers;

    private String mComment;
    private String mCreator;
    private long mDateCreated;
    private File[] mFiles;
    private String mHashString;
    private boolean mPrivate;
    private int mPieceCount;
    private long mPieceSize;

    private int mTorrentPriority;
    private long mDownloadLimit;
    private boolean mDownloadLimited;
    private boolean mHonorsSessionLimits;
    private long mUploadLimit;
    private boolean mUploadLimited;
    private int mWebseedsSendingToUs;
    private Peer[] mPeers;
    private int mPeerLimit;

    private Spanned mFilteredName;
    private Spanned mTrafficText;
    private Spanned mStatusText;

    // https://github.com/killemov/Shift/blob/master/shift.js#L864
    public static class Status {
        public final static int ALL = -1;
        public final static int STOPPED = 0;
        public final static int CHECK_WAITING = 1;
        public final static int CHECKING = 2;
        public final static int DOWNLOAD_WAITING = 3;
        public final static int DOWNLOADING = 4;
        public final static int SEED_WAITING = 5;
        public final static int SEEDING = 6;
    }

    public static class OldStatus {
        public final static int CHECK_WAITING = 1;
        public final static int CHECKING = 2;
        public final static int DOWNLOADING = 4;
        public final static int SEEDING = 8;
        public final static int STOPPED = 16;
    }

    // http://packages.python.org/transmissionrpc/reference/transmissionrpc.html
    public static class SeedRatioMode {
        public final static int GLOBAL_LIMIT = 0;
        public final static int TORRENT_LIMIT = 1;
        public final static int NO_LIMIT = 2;
    }

    public static class Error {
        public static final int OK = 0;
        public static final int TRACKER_WARNING = 1;
        public static final int TRACKER_ERROR = 2;
        public static final int LOCAL_ERROR = 3;
    }

    public static class Priority {
        public static final int LOW = -1;
        public static final int NORMAL = 0;
        public static final int HIGH = 1;
    }

    public static class Fields {
        /*
         * commonly used fields which only need to be loaded once, either on
         * startup or when a magnet finishes downloading its metadata finishes
         * downloading its metadata
         * */
        public static final String[] METADATA = { "addedDate", "name", "totalSize" };

        // commonly used fields which need to be periodically refreshed
        public static final String[] STATS = {
            "id", "error", "errorString", "eta", "isFinished", "isStalled",
            "leftUntilDone", "metadataPercentComplete", "peersConnected",
            "peersGettingFromUs", "peersSendingToUs", "percentDone",
            SetterFields.QUEUE_POSITION, "rateDownload", "rateUpload",
            "recheckProgress", SetterFields.SEED_RATIO_MODE, SetterFields.SEED_RATIO_LIMIT,
            "sizeWhenDone", "status", "trackers", "uploadedEver",
            "uploadRatio", "downloadDir"
        };

        // fields used by the inspector which only need to be loaded once
        public static final String[] INFO_EXTRA = {
            "comment", "creator", "dateCreated", "files", "hashString",
            "isPrivate", "pieceCount", "pieceSize"
        };

        // fields used in the inspector which need to be periodically refreshed
        public static final String[] STATS_EXTRA = {
            "activityDate", SetterFields.TORRENT_PRIORITY, "corruptEver",
            "desiredAvailable", "downloadedEver", SetterFields.DOWNLOAD_LIMIT,
            SetterFields.DOWNLOAD_LIMITED, "fileStats", "haveUnchecked",
            "haveValid", SetterFields.SESSION_LIMITS, SetterFields.PEER_LIMIT, "peers",
            "startDate", "trackerStats", SetterFields.UPLOAD_LIMIT,
            SetterFields.UPLOAD_LIMITED, "webseedsSendingToUs"
        };
    }

    private static final int SEED = 0x21;

    public static class Tracker {
        private int mId;
        private String mAnnounce;
        private String mScrape;
        private int mTier;

        private boolean mAnnounced;
        private long mLastAnnounceTime;
        private boolean mLastAnnounceSucceeded;
        private int mLastAnnouncePeerCount;
        private String mLastAnnounceResult;

        private boolean mScraped;
        private long mLastScrapeTime;
        private boolean mLastScrapeSucceeded;
        private String mLastScrapeResult;

        private int mSeederCount;
        private int mLeecherCount;

        @JsonProperty("id") public int getId() {
            return mId;
        }

        @JsonProperty("announce") public String getAnnounce() {
            return mAnnounce;
        }

        @JsonProperty("scrape") public String getScrape() {
            return mScrape;
        }

        @JsonProperty("tier") public int getTier() {
            return mTier;
        }

        @JsonProperty("hasAnnounced") public boolean hasAnnounced() {
            return mAnnounced;
        }
        @JsonProperty("lastAnnounceTime") public long getLastAnnounceTime() {
            return mLastAnnounceTime;
        }

        @JsonProperty("lastAnnounceSucceeded") public boolean hasLastAnnounceSucceeded() {
            return mLastAnnounceSucceeded;
        }

        @JsonProperty("lastAnnouncePeerCount") public int getLastAnnouncePeerCount() {
            return mLastAnnouncePeerCount;
        }

        @JsonProperty("lastAnnounceResult") public String getLastAnnounceResult() {
            return mLastAnnounceResult;
        }

        @JsonProperty("hasScraped") public boolean hasScraped() {
            return mScraped;
        }

        @JsonProperty("lastScrapeTime") public long getLastScrapeTime() {
            return mLastScrapeTime;
        }

        @JsonProperty("lastScrapeSucceeded") public boolean hasLastScrapeSucceeded() {
            return mLastScrapeSucceeded;
        }

        @JsonProperty("lastScrapeResult") public String getLastScrapeResult() {
            return mLastScrapeResult;
        }

        @JsonProperty("seederCount") public int getSeederCount() {
            return mSeederCount;
        }

        @JsonProperty("leecherCount") public int getLeecherCount() {
            return mLeecherCount;
        }

        public void setId(int id) {
            mId = id;
        }

        public void setAnnounce(String announce) {
            mAnnounce = announce;
        }

        public void setScrape(String scrape) {
            mScrape = scrape;
        }

        public void setTier(int tier) {
            mTier = tier;
        }

        public void setAnnounced(boolean announced) {
            mAnnounced = announced;
        }

        public void setLastAnnounceTime(long time) {
            mLastAnnounceTime = time;
        }

        public void setLastAnnounceSucceeded(boolean succeeded) {
            mLastAnnounceSucceeded = succeeded;
        }

        public void setLastAnnouncePeerCount(int count) {
            mLastAnnouncePeerCount = count;
        }

        public void setLastAnnounceResult(String result) {
            mLastAnnounceResult = result;
        }

        public void setScraped(boolean scraped) {
            mScraped = scraped;
        }

        public void setLastScrapeTime(long time) {
            mLastScrapeTime = time;
        }

        public void setLastScrapeSucceeded(boolean succeeded) {
            mLastScrapeSucceeded = succeeded;
        }

        public void setLastScrapeResult(String result) {
            mLastScrapeResult = result;
        }

        public void setSeederCount(int count) {
            mSeederCount = count;
        }

        public void setLeecherCount(int count) {
            mLeecherCount = count;
        }
    }

    public static class File {
        private long mBytesCompleted;
        private long mLength;
        private String mName;
        private boolean mWanted;
        private int mPriority = Priority.NORMAL;

        @JsonProperty("bytesCompleted") public long getBytesCompleted() {
            return mBytesCompleted;
        }

        @JsonProperty("length") public long getLength() {
            return mLength;
        }

        @JsonProperty("name") public String getName() {
            return mName;
        }

        @JsonProperty("priority") public int getPriority() {
            return mPriority;
        }

        @JsonProperty("wanted") public boolean isWanted() {
            return mWanted;
        }

        public void setBytesCompleted(long bytes) {
            mBytesCompleted = bytes;
        }

        public void setLength(long length) {
            mLength = length;
        }

        public void setName(String name) {
            mName = name;
        }

        public void setPriority(int priority) {
            mPriority = priority;
        }

        public void setWanted(boolean wanted) {
            mWanted = wanted;
        }
    }

    public static class Peer {
        private String mAddress;
        private String mClientName;
        private boolean mClientChoked;
        private boolean mClientInterested;
        private boolean mDownloadingFrom;
        private boolean mEncrypted;
        private boolean mIncoming;
        private boolean mUploadingTo;
        private boolean mPeerChoked;
        private boolean mPeerInterested;
        private int mPort;
        private float mProgress;
        private long mRateToClient;
        private long mRateToPeer;

        @JsonProperty("address") public String getAddress() {
            return mAddress;
        }
        @JsonProperty("clientName") public String getClientName() {
            return mClientName;
        }
        @JsonProperty("clientIsChoked") public boolean isClientChoked() {
            return mClientChoked;
        }
        @JsonProperty("clientIsInterested") public boolean isClientInterested() {
            return mClientInterested;
        }
        @JsonProperty("isDownloadingFrom") public boolean isDownloadingFrom() {
            return mDownloadingFrom;
        }
        @JsonProperty("isEncrypted") public boolean isEncrypted() {
            return mEncrypted;
        }
        @JsonProperty("isIncoming") public boolean isIncoming() {
            return mIncoming;
        }
        @JsonProperty("isUploadingTo") public boolean isUploadingTo() {
            return mUploadingTo;
        }
        @JsonProperty("peerIsChoked") public boolean isPeerChoked() {
            return mPeerChoked;
        }
        @JsonProperty("peerIsInterested") public boolean isPeerInterested() {
            return mPeerInterested;
        }
        @JsonProperty("port") public int getPort() {
            return mPort;
        }
        @JsonProperty("progress") public float getProgress() {
            return mProgress;
        }
        @JsonProperty("rateToClient") public long getRateToClient() {
            return mRateToClient;
        }
        @JsonProperty("rateToPeer") public long getRateToPeer() {
            return mRateToPeer;
        }
        public void setAddress(String address) {
            mAddress = address;
        }
        public void setClientName(String clientName) {
            mClientName = clientName;
        }
        public void setClientChoked(boolean clientChoked) {
            mClientChoked = clientChoked;
        }
        public void setClientInterested(boolean clientInterested) {
            mClientInterested = clientInterested;
        }
        public void setDownloadingFrom(boolean downloadingFrom) {
            mDownloadingFrom = downloadingFrom;
        }
        public void setEncrypted(boolean encrypted) {
            mEncrypted = encrypted;
        }
        public void setIncoming(boolean incoming) {
            mIncoming = incoming;
        }
        public void setUploadingTo(boolean uploadingTo) {
            mUploadingTo = uploadingTo;
        }
        public void setPeerChoked(boolean peerChoked) {
            mPeerChoked = peerChoked;
        }
        public void setPeerInterested(boolean peerInterested) {
            mPeerInterested = peerInterested;
        }
        public void setPort(int port) {
            mPort = port;
        }
        public void setProgress(float progress) {
            mProgress = progress;
        }
        public void setRateToClient(long rateToClient) {
            mRateToClient = rateToClient;
        }
        public void setRateToPeer(long rateToPeer) {
            mRateToPeer = rateToPeer;
        }
    }

    public Torrent() {}

    public Torrent(int id, String name) {
        mId = id;
        mName = name;
    }

    @JsonProperty("id")
    public int getId() {
        return mId;
    }

    @JsonProperty("status")
    public int getStatus() {
        return mStatus;
    }

    public int getStatus(boolean skipConversion) {
        if (skipConversion) {
            return mStatus;
        } else {
            return getStatus();
        }
    }

    @JsonProperty("name")
    public String getName() {
        return mName;
    }

    @JsonProperty("error")
    public int getError() {
        return mError;
    }

    @JsonProperty("errorString")
    public String getErrorString() {
        return mErrorString;
    }

    @JsonProperty("metadataPercentComplete")
    public float getMetadataPercentComplete() {
        return mMetadataPercentComplete;
    }

    @JsonProperty("percentDone")
    public float getPercentDone() {
        return mPercentDone;
    }

    @JsonProperty("eta")
    public long getEta() {
        return mEta;
    }

    @JsonProperty("isFinished")
    public boolean isFinished() {
        return mFinished;
    }

    @JsonProperty("isStalled")
    public boolean isStalled() {
        return mStalled;
    }

    @JsonProperty("peersConnected")
    public int getPeersConnected() {
        return mPeersConnected;
    }

    @JsonProperty("peersGettingFromUs")
    public int getPeersGettingFromUs() {
        return mPeersGettingFromUs;
    }

    @JsonProperty("peersSendingToUs")
    public int getPeersSendingToUs() {
        return mPeersSendingToUs;
    }

    @JsonProperty("leftUntilDone")
    public long getLeftUntilDone() {
        return mLeftUntilDone;
    }

    @JsonProperty("desiredAvailable")
    public long getDesiredAvailable() {
        return mDesiredAvailable;
    }

    @JsonProperty("totalSize")
    public long getTotalSize() {
        return mTotalSize;
    }

    @JsonProperty("sizeWhenDone")
    public long getSizeWhenDone() {
        return mSizeWhenDone;
    }

    @JsonProperty("rateDownload")
    public long getRateDownload() {
        return mRateDownload;
    }

    @JsonProperty("rateUpload")
    public long getRateUpload() {
        return mRateUpload;
    }

    @JsonProperty(SetterFields.QUEUE_POSITION)
    public int getQueuePosition() {
        return mQueuePosition;
    }

    @JsonProperty("recheckProgress")
    public float getRecheckProgress() {
        return mRecheckProgress;
    }

    @JsonProperty(SetterFields.SEED_RATIO_MODE)
    public int getSeedRatioMode() {
        return mSeedRatioMode;
    }

    @JsonProperty(SetterFields.SEED_RATIO_LIMIT)
    public float getSeedRatioLimit() {
        return mSeedRatioLimit;
    }

    @JsonProperty("uploadedEver")
    public long getUploadedEver() {
        return mUploadedEver;
    }

    @JsonProperty("uploadRatio")
    public float getUploadRatio() {
        return mUploadRatio;
    }

    @JsonProperty("addedDate")
    public long getAddedDate() {
        return mAddedDate;
    }

    @JsonProperty("doneDate")
    public long getDoneDate() {
        return mDoneDate;
    }

    @JsonProperty("startDate")
    public long getStartDate() {
        return mStartDate;
    }

    @JsonProperty("activityDate")
    public long getActivityDate() {
        return mActivityDate;
    }

    @JsonProperty("corruptEver")
    public long getCorruptEver() {
        return mCorruptEver;
    }

    @JsonProperty("downloadDir")
    public String getDownloadDir() {
        return mDownloadDir;
    }

    @JsonProperty("downloadedEver")
    public long getDownloadedEver() {
        return mDownloadedEver;
    }

    @JsonProperty("haveUnchecked")
    public long getHaveUnchecked() {
        return mHaveUnchecked;
    }

    @JsonProperty("haveValid")
    public long getHaveValid() {
        return mHaveValid;
    }

    @JsonProperty("trackers")
    public Tracker[] getTrackers() {
        return mTrackers;
    }

    @JsonProperty(SetterFields.TORRENT_PRIORITY)
    public int getTorrentPriority() {
        return mTorrentPriority;
    }

    @JsonProperty("comment")
    public String getComment() {
        return mComment;
    }

    @JsonProperty("creator")
    public String getCreator() {
        return mCreator;
    }

    @JsonProperty("dateCreated")
    public long getDateCreated() {
        return mDateCreated;
    }

    @JsonProperty(SetterFields.DOWNLOAD_LIMIT)
    public long getDownloadLimit() {
        return mDownloadLimit;
    }

    @JsonProperty(SetterFields.DOWNLOAD_LIMITED)
    public boolean isDownloadLimited() {
        return mDownloadLimited;
    }

    @JsonProperty("files")
    public File[] getFiles() {
        return mFiles;
    }

    @JsonProperty("hashString")
    public String getHashString() {
        return mHashString;
    }

    @JsonProperty(SetterFields.SESSION_LIMITS)
    public boolean areSessionLimitsHonored() {
        return mHonorsSessionLimits;
    }

    @JsonProperty("isPrivate")
    public boolean isPrivate() {
        return mPrivate;
    }

    @JsonProperty("pieceCount")
    public int getPieceCount() {
        return mPieceCount;
    }

    @JsonProperty("pieceSize")
    public long getPieceSize() {
        return mPieceSize;
    }

    @JsonProperty(SetterFields.UPLOAD_LIMIT)
    public long getUploadLimit() {
        return mUploadLimit;
    }

    @JsonProperty(SetterFields.UPLOAD_LIMITED)
    public boolean isUploadLimited() {
        return mUploadLimited;
    }

    @JsonProperty("webseedsSendingToUs")
    public int getWebseedsSendingToUs() {
        return mWebseedsSendingToUs;
    }

    @JsonProperty("peers")
    public Peer[] getPeers() {
        return mPeers;
    }

    @JsonProperty(SetterFields.PEER_LIMIT)
    public int getPeerLimit() {
        return mPeerLimit;
    }

    public void setId(int id) {
        mId = id;
    }

    public void setStatus(int status) {
        mStatus = status;
    }

    public void setName(String name) {
        mName = name;
    }

    public void setError(int error) {
        mError = error;
    }

    public void setErrorString(String errorString) {
        mErrorString = errorString;
    }

    public void setMetadataPercentComplete(float metadataPercentComplete) {
        mMetadataPercentComplete = metadataPercentComplete;
    }

    public void setPercentDone(float percentDone) {
        mPercentDone = percentDone;
    }

    public void setEta(long eta) {
        mEta = eta;
    }

    public void setFinished(boolean finished) {
        mFinished = finished;
    }

    public void setStalled(boolean stalled) {
        mStalled = stalled;
    }

    public void setPeersConnected(int peersConnected) {
        mPeersConnected = peersConnected;
    }

    public void setPeersGettingFromUs(int peersGettingFromUs) {
        mPeersGettingFromUs = peersGettingFromUs;
    }

    public void setPeersSendingToUs(int peersSendingToUs) {
        mPeersSendingToUs = peersSendingToUs;
    }

    public void setLeftUntilDone(long leftUntilDone) {
        mLeftUntilDone = leftUntilDone;
    }

    public void setDesiredAvailable(long desiredAvailable) {
        mDesiredAvailable = desiredAvailable;
    }

    public void setTotalSize(long totalSize) {
        mTotalSize = totalSize;
    }

    public void setSizeWhenDone(long sizeWhenDone) {
        mSizeWhenDone = sizeWhenDone;
    }

    public void setRateDownload(long rateDownload) {
        mRateDownload = rateDownload;
    }

    public void setRateUpload(long rateUpload) {
        mRateUpload = rateUpload;
    }

    public void setQueuePosition(int queuePosition) {
        mQueuePosition = queuePosition;
    }

    public void setRecheckProgress(float recheckProgress) {
        mRecheckProgress = recheckProgress;
    }

    public void setSeedRatioMode(int seedRatioMode) {
        mSeedRatioMode = seedRatioMode;
    }

    public void setSeedRatioLimit(float seedRatioLimit) {
        mSeedRatioLimit = seedRatioLimit;
    }

    public void setUploadedEver(long uploadedEver) {
        mUploadedEver = uploadedEver;
    }

    public void setUploadRatio(float uploadRatio) {
        mUploadRatio = uploadRatio;
    }

    public void setAddedDate(long addedDate) {
        mAddedDate = addedDate;
    }

    public void setDoneDate(long doneDate) {
        mDoneDate = doneDate;
    }

    public void setStartDate(long startDate) {
        mStartDate = startDate;
    }

    public void setActivityDate(long activityDate) {
        mActivityDate = activityDate;
    }

    public void setCorruptEver(long corruptEver) {
        mCorruptEver = corruptEver;
    }

    public void setDownloadDir(String downloadDir) {
        mDownloadDir = downloadDir;
    }

    public void setDownloadedEver(long downloadedEver) {
        mDownloadedEver = downloadedEver;
    }

    public void setHaveUnchecked(long haveUnchecked) {
        mHaveUnchecked = haveUnchecked;
    }

    public void setHaveValid(long haveValid) {
        mHaveValid = haveValid;
    }

    public void setTrackers(Tracker[] trackers) {
        mTrackers = trackers;
    }

    public void setTorrentPriority(int priority) {
        mTorrentPriority = priority;
    }

    public void setComment(String comment) {
        mComment = comment;
    }

    public void setCreator(String creator) {
        mCreator = creator;
    }

    public void setDateCreated(long dateCreated) {
        mDateCreated = dateCreated;
    }

    public void setDownloadLimit(long limit) {
        mDownloadLimit = limit;
    }

    public void setDownloadLimited(boolean limited) {
        mDownloadLimited = limited;
    }

    public void setFiles(File[] files) {
        mFiles = files;
    }

    public void setHashString(String hashString) {
        mHashString = hashString;
    }

    public void setHonorsSessionLimits(boolean limits) {
        mHonorsSessionLimits = limits;
    }

    public void setPrivate(boolean priv) {
        mPrivate = priv;
    }

    public void setPieceCount(int pieceCount) {
        mPieceCount = pieceCount;
    }

    public void setPieceSize(long pieceSize) {
        mPieceSize = pieceSize;
    }

    public void setUploadLimit(long limit) {
        mUploadLimit = limit;
    }

    public void setUploadLimited(boolean limited) {
        mUploadLimited = limited;
    }

    public void setWebseedsSendingToUs(int webseedsSendingToUs) {
        mWebseedsSendingToUs = webseedsSendingToUs;
    }

    public void setPeers(Peer[] peers) {
        mPeers = peers;
    }

    public void setPeerLimit(int peers) {
        mPeerLimit = peers;
    }

    @JsonIgnore
    public boolean isPaused() {
        return getStatus() == Status.STOPPED && mUploadRatio < getActiveSeedRatioLimit();
    }

    @JsonIgnore
    public boolean isSeeding() {
        return getStatus() == Status.SEEDING;
    }

    @JsonIgnore
    public boolean isActive() {
        switch(getStatus()) {
            case Status.STOPPED:
            case Status.CHECK_WAITING:
            case Status.DOWNLOAD_WAITING:
            case Status.SEED_WAITING:
                return false;
            default:
                return true;
        }
    }

    public void setTrafficText(String text) {
        mTrafficText = Html.fromHtml(text);
    }

    @JsonIgnore
    public Spanned getFilteredName() {
        return mFilteredName;
    }

    public void setFilteredName(Spanned name) {
        mFilteredName = name;
    }

    @JsonIgnore
    public Spanned getTrafficText() {
        return mTrafficText;
    }

    public void setStatusText(String text) {
        mStatusText = Html.fromHtml(text);
    }

    @JsonIgnore
    public Spanned getStatusText() {
        return mStatusText;
    }

    @JsonIgnore
    public float getActiveSeedRatioLimit() {
        switch(mSeedRatioMode) {
            case Torrent.SeedRatioMode.GLOBAL_LIMIT:
            case Torrent.SeedRatioMode.TORRENT_LIMIT:
                return mSeedRatioLimit;
            default:
                return -1;
        }
    }

    @Override
    public boolean equals (Object o) {
        return this == o || o instanceof Torrent && hashCode() == o.hashCode();
    }

    @Override
    public int hashCode() {
        int result = SEED;

        result = SEED * result + mId;
        result = SEED * result + (mHashString == null
                ? 0 : mHashString.hashCode());

        return result;
    }

    @Override
    public String toString() {
        return mId + ": " + mName;
    }

    /* Just enough to regenerate the torrent list */
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel in, int flags) {
        in.writeInt(mId);
        in.writeString(mName);
        in.writeFloat(mMetadataPercentComplete);
        in.writeFloat(mPercentDone);
        in.writeInt(mSeedRatioMode);
        in.writeFloat(mSeedRatioLimit);
        in.writeFloat(mUploadRatio);
        in.writeString(mFilteredName == null ? "" : Html.toHtml(mFilteredName));
        in.writeString(mTrafficText == null ? "" : Html.toHtml(mTrafficText));
        in.writeString(mStatusText == null ? "" : Html.toHtml(mStatusText));
        in.writeInt(getStatus());
        in.writeInt(mError);
        in.writeString(mErrorString);
        in.writeByte((byte) (mStalled ? 1 : 0));
        in.writeByte((byte) (mFinished ? 1 : 0));
        in.writeString(mDownloadDir);

        in.writeLong(mAddedDate);
        in.writeLong(mStartDate);
        in.writeLong(mActivityDate);
        in.writeLong(mEta);

        in.writeLong(mSizeWhenDone);
        in.writeLong(mLeftUntilDone);
        in.writeLong(mUploadedEver);
        in.writeLong(mTotalSize);
        in.writeInt(mQueuePosition);

        in.writeLong(mDownloadedEver);
        in.writeLong(mUploadedEver);
    }

    private Torrent(Parcel in) {
        mId = in.readInt();
        mName = in.readString();
        mMetadataPercentComplete = in.readFloat();
        mPercentDone = in.readFloat();
        mSeedRatioMode = in.readInt();
        mSeedRatioLimit = in.readFloat();
        mUploadRatio = in.readFloat();
        mFilteredName = (Spanned) trimTrailingWhitespace(Html.fromHtml(in.readString()));
        mTrafficText = (Spanned) trimTrailingWhitespace(Html.fromHtml(in.readString()));
        mStatusText = (Spanned) trimTrailingWhitespace(Html.fromHtml(in.readString()));
        mStatus = in.readInt();
        mError = in.readInt();
        mErrorString = in.readString();
        mStalled = in.readByte() == 1;
        mFinished = in.readByte() == 1;
        mDownloadDir = in.readString();

        mAddedDate = in.readLong();
        mStartDate = in.readLong();
        mActivityDate = in.readLong();
        mEta = in.readLong();

        mSizeWhenDone = in.readLong();
        mLeftUntilDone = in.readLong();
        mUploadedEver = in.readLong();
        mTotalSize = in.readLong();
        mQueuePosition = in.readInt();

        mDownloadedEver = in.readLong();
        mUploadedEver = in.readLong();
    }

    public static final Parcelable.Creator<Torrent> CREATOR
            = new Parcelable.Creator<Torrent>() {
        @Override
        public Torrent createFromParcel(Parcel in) {
            return new Torrent(in);
        }

        @Override
        public Torrent[] newArray(int size) {
            return new Torrent[size];
        }
    };

    public static CharSequence trimTrailingWhitespace(CharSequence source) {
        if (source == null)
            return "";

        int i = source.length() - 1;
        while (i >= 0 && Character.isWhitespace(source.charAt(i))) {
            --i;
        }

        return source.subSequence(0, i + 1);
    }

    public static int getId(Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndex(Constants.C_ID));
    }

    public static String getName(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(Constants.C_NAME));
    }

    public static int getError(Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndex(Constants.C_ERROR));
    }

    public static String getErrorString(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(Constants.C_ERROR_STRING));
    }

    public static int getStatus(Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndex(Constants.C_STATUS));
    }

    public static float getMetadataPercentDone(Cursor cursor) {
        return cursor.getFloat(cursor.getColumnIndex(Constants.C_METADATA_PERCENT_COMPLETE));
    }

    public static float getPercentDone(Cursor cursor) {
        return cursor.getFloat(cursor.getColumnIndex(Constants.C_PERCENT_DONE));
    }

    public static float getSeedRatioLimit(Cursor cursor) {
        return cursor.getFloat(cursor.getColumnIndex(Constants.C_SEED_RATIO_LIMIT));
    }

    public static float getUploadRatio(Cursor cursor) {
        return cursor.getFloat(cursor.getColumnIndex(Constants.C_UPLOAD_RATIO));
    }

    public static String getTrafficText(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(Constants.C_TRAFFIC_TEXT));
    }

    public static String getStatusText(Cursor cursor) {
        return cursor.getString(cursor.getColumnIndex(Constants.C_STATUS_TEXT));
    }

    public static boolean isActive(int status) {
        switch(status) {
            case Status.CHECKING:
            case Status.DOWNLOADING:
            case Status.SEEDING:
                return true;
            default:
                return false;
        }
    }
}
