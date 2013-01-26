package us.supositi.gearshift;

import java.text.DecimalFormat;

public class Torrent {
    
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
    };
    
    // http://packages.python.org/transmissionrpc/reference/transmissionrpc.html
    public static class SeedRatioMode {
        public final static int GLOBAL_LIMIT = 0;
        public final static int TORRENT_LIMIT = 1;
        public final static int NO_LIMIT = 2;
    }
    
    private int mId;
    private String mName;
    
    private int mError;
    private String mErrorString;
    
    private float mMetadataPercentComplete = 0;
    private float mPercentDone = 0;
    
    private long mEta;
    
    private int mStatus = Status.STOPPED;
    
    private boolean mFinished = false;
    private boolean mStalled = true;
    
    private int mPeersConnected = 0;
    private int mPeersGettingFromUs = 0;
    private int mPeersSendingToUs = 0;
    
    private long mLeftUntilDone;
    private long mAddedDate;
    
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
    
    /* Trackers, Files, FileStats */
    
    public Torrent(int id, String name) {
        mId = id;
        mName = name;
    }
    
    public int getId() {
        return mId;
    }
    public String getName() {
        return mName;
    }
    public int getError() {
        return mError;
    }
    public String getErrorString() {
        return mErrorString;
    }
    public float getMetadataPercentComplete() {
        return mMetadataPercentComplete;
    }
    public float getPercentDone() {
        return mPercentDone;
    }
    public long getEta() {
        return mEta;
    }
    public int getStatus() {
        return mStatus;
    }
    public boolean isFinished() {
        return mFinished;
    }
    public boolean isStalled() {
        return mStalled;
    }
    public int getPeersConnected() {
        return mPeersConnected;
    }
    public int getPeersGettingFromUs() {
        return mPeersGettingFromUs;
    }
    public int getPeersSendingToUs() {
        return mPeersSendingToUs;
    }
    public long getLeftUntilDone() {
        return mLeftUntilDone;
    }
    public long getAddedDate() {
        return mAddedDate;
    }
    public long getTotalSize() {
        return mTotalSize;
    }
    public long getSizeWhenDone() {
        return mSizeWhenDone;
    }
    public long getRateDownload() {
        return mRateDownload;
    }
    public long getRateUpload() {
        return mRateUpload;
    }
    public int getQueuePosition() {
        return mQueuePosition;
    }
    public float getRecheckProgress() {
        return mRecheckProgress;
    }
    public int getSeedRatioMode() {
        return mSeedRatioMode;
    }
    public float getSeedRatioLimit() {
        return mSeedRatioLimit;
    }
    public long getUploadedEver() {
        return mUploadedEver;
    }
    public float getUploadRatio() {
        return mUploadRatio;
    }
    public void setId(int mId) {
        this.mId = mId;
    }
    public void setName(String mName) {
        this.mName = mName;
    }
    public void setError(int mError) {
        this.mError = mError;
    }
    public void setErrorString(String mErrorString) {
        this.mErrorString = mErrorString;
    }
    public void setMetadataPercentComplete(float mMetadataPercentComplete) {
        this.mMetadataPercentComplete = mMetadataPercentComplete;
    }
    public void setPercentDone(float mPercentDone) {
        this.mPercentDone = mPercentDone;
    }
    public void setEta(long mEta) {
        this.mEta = mEta;
    }
    public void setStatus(int mStatus) {
        this.mStatus = mStatus;
    }
    public void setFinished(boolean mFinished) {
        this.mFinished = mFinished;
    }
    public void setStalled(boolean mStalled) {
        this.mStalled = mStalled;
    }
    public void setPeersConnected(int mPeersConnected) {
        this.mPeersConnected = mPeersConnected;
    }
    public void setPeersGettingFromUs(int mPeersGettingFromUs) {
        this.mPeersGettingFromUs = mPeersGettingFromUs;
    }
    public void setPeersSendingToUs(int mPeersSendingToUs) {
        this.mPeersSendingToUs = mPeersSendingToUs;
    }
    public void setLeftUntilDone(long mLeftUntilDone) {
        this.mLeftUntilDone = mLeftUntilDone;
    }
    public void setAddedDate(long mAddedDate) {
        this.mAddedDate = mAddedDate;
    }
    public void setTotalSize(long mTotalSize) {
        this.mTotalSize = mTotalSize;
    }
    public void setSizeWhenDone(long mSizeWhenDone) {
        this.mSizeWhenDone = mSizeWhenDone;
    }
    public void setRateDownload(long mRateDownload) {
        this.mRateDownload = mRateDownload;
    }
    public void setRateUpload(long mRateUpload) {
        this.mRateUpload = mRateUpload;
    }
    public void setQueuePosition(int mQueuePosition) {
        this.mQueuePosition = mQueuePosition;
    }
    public void setRecheckProgress(float mRecheckProgress) {
        this.mRecheckProgress = mRecheckProgress;
    }
    public void setSeedRatioMode(int mSeedRatioMode) {
        this.mSeedRatioMode = mSeedRatioMode;
    }
    public void setSeedRatioLimit(float mSeedRatioLimit) {
        this.mSeedRatioLimit = mSeedRatioLimit;
    }
    public void setUploadedEver(long mUploadedEver) {
        this.mUploadedEver = mUploadedEver;
    }
    public void setUploadRatio(float mUploadRatio) {
        this.mUploadRatio = mUploadRatio;
    }
    
    public static String readableFileSize(long size) {
        if(size <= 0) return "0";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
