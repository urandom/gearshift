package us.supositi.gearshift;

public class Torrent {
    private int mId;
    private String mName;
    
    private int mError;
    private String mErrorString;
    
    private float mMetadataPercentComplete;
    private float mPercentDone;
    
    private long mEta;
    
    private int mStatus;
    
    private boolean mFinished;
    private boolean mStalled;
    
    private int mPeersConnected;
    private int mPeersGettingFromUs;
    private int mPeersSendingToUs;
    
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
    public boolean ismFinished() {
        return mFinished;
    }
    public boolean ismStalled() {
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
}
