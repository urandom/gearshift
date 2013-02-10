package org.sugr.gearshift;

import org.sugr.gearshift.TransmissionSessionManager.Exclude;

import com.google.gson.annotations.SerializedName;

public class TransmissionSession {
    @SerializedName("alt-speed-enabled") private boolean mAltSpeedEnabled;
    @SerializedName("alt-speed-down") private long mAltSpeedDown;
    @SerializedName("alt-speed-up") private long mAltSpeedUp;

    @SerializedName("alt-speed-time-enabled") private boolean mAltSpeedTimeEnabled;
    @SerializedName("alt-speed-time-begin") private int mAltSpeedTimeBegin;
    @SerializedName("alt-speed-time-end") private int mAltSpeedTimeEnd;

    @SerializedName("blocklist-enabled") private boolean mBlocklistEnabled;
    @SerializedName("blocklist-size") private int mBlocklistSize;

    @SerializedName("dht-enabled") private boolean mDHTEnabled;
    @SerializedName("encryption") private String mEncryption;

    @SerializedName("download-dir") private String mDownloadDir;
    @SerializedName("download-dir-free-space") private long mDownloadDirFreeSpace;

    @SerializedName("peer-limit-global") private int mPeerLimitGlobal;
    @SerializedName("peer-limit-per-torrent") private int mPeerLimitPerTorrent;

    @SerializedName("pex-enabled") private boolean mPEXEnabled;

    @SerializedName("peer-port") private int mPeerPort;
    @SerializedName("peer-port-random-on-start") private boolean mPeerPortRandomOnStart;

    @SerializedName("port-forwarding-enabled") private boolean mPortForwardingEnabled;

    @SerializedName("rpc-version") private int mRPCVersion;
    @SerializedName("rpc-version-minimum") private int mRPCVersionMin;

    @SerializedName("seedRatioLimit") private float mSeedRatioLimit;
    @SerializedName("seedRatioLimited") private boolean mSeedRatioLimited;

    @SerializedName("speed-limit-down") private long mSpeedLimitDown;
    @SerializedName("speed-limit-down-enabled") private boolean mSpeedLimitDownEnabled;
    @SerializedName("speed-limit-up") private long mSpeedLimitUp;
    @SerializedName("speed-limit-up-enabled") private boolean mSpeedLimitUpEnabled;

    @SerializedName("version") private String mVersion;

    // https://trac.transmissionbt.com/browser/trunk/libtransmission/transmission.h - tr_sched_day
    @Exclude public static class AltSpeedDay {
        public static final int SUN = (1<<0);
        public static final int MON = (1<<1);
        public static final int TUE = (1<<2);
        public static final int WED = (1<<3);
        public static final int THU = (1<<4);
        public static final int FRI = (1<<5);
        public static final int SAT = (1<<6);
        public static final int WEEKDAY = (MON|TUE|WED|THU|FRI);
        public static final int WEEKEND = (SUN|SAT);
        public static final int ALL = (WEEKDAY|WEEKEND);
    }

    @Exclude public static class Encryption {
        public static final String REQUIRED = "required";
        public static final String PREFERRED = "preferred";
        public static final String TOLERATED = "tolerated";
    }

    public boolean isAltSpeedEnabled() {
        return mAltSpeedEnabled;
    }

    public long getAltSpeedDown() {
        return mAltSpeedDown;
    }

    public long getAltSpeedUp() {
        return mAltSpeedUp;
    }

    public boolean isAltSpeedTimeEnabled() {
        return mAltSpeedTimeEnabled;
    }

    public int getAltSpeedTimeBegin() {
        return mAltSpeedTimeBegin;
    }

    public int getAltSpeedTimeEnd() {
        return mAltSpeedTimeEnd;
    }

    public boolean isBlocklistEnabled() {
        return mBlocklistEnabled;
    }

    public int getBlocklistSize() {
        return mBlocklistSize;
    }

    public boolean isDHTEnabled() {
        return mDHTEnabled;
    }

    public String getEncryption() {
        return mEncryption;
    }

    public String getDownloadDir() {
        return mDownloadDir;
    }

    public long getDownloadDirFreeSpace() {
        return mDownloadDirFreeSpace;
    }

    public int getPeerLimitGlobal() {
        return mPeerLimitGlobal;
    }

    public int getPeerLimitPerTorrent() {
        return mPeerLimitPerTorrent;
    }

    public boolean isPEXEnabled() {
        return mPEXEnabled;
    }

    public int getPeerPort() {
        return mPeerPort;
    }

    public boolean isPeerPortRandomOnStart() {
        return mPeerPortRandomOnStart;
    }

    public boolean isPortForwardingEnabled() {
        return mPortForwardingEnabled;
    }

    public int getRPCVersion() {
        return mRPCVersion;
    }

    public int getRPCVersionMin() {
        return mRPCVersionMin;
    }

    public float getSeedRatioLimit() {
        return mSeedRatioLimit;
    }

    public boolean isSeedRatioLimited() {
        return mSeedRatioLimited;
    }

    public long getSpeedLimitDown() {
        return mSpeedLimitDown;
    }

    public boolean isSpeedLimitDownEnabled() {
        return mSpeedLimitDownEnabled;
    }

    public long getSpeedLimitUp() {
        return mSpeedLimitUp;
    }

    public boolean isSpeedLimitUpEnabled() {
        return mSpeedLimitUpEnabled;
    }

    public String getVersion() {
        return mVersion;
    }

    public void setAltSpeedEnabled(boolean altSpeedEnabled) {
        mAltSpeedEnabled = altSpeedEnabled;
    }

    public void setAltSpeedDown(long altSpeedDown) {
        mAltSpeedDown = altSpeedDown;
    }

    public void setAltSpeedUp(long altSpeedUp) {
        mAltSpeedUp = altSpeedUp;
    }

    public void setAltSpeedTimeEnabled(boolean altSpeedTimeEnabled) {
        mAltSpeedTimeEnabled = altSpeedTimeEnabled;
    }

    public void setAltSpeedTimeBegin(int altSpeedTimeBegin) {
        mAltSpeedTimeBegin = altSpeedTimeBegin;
    }

    public void setAltSpeedTimeEnd(int altSpeedTimeEnd) {
        mAltSpeedTimeEnd = altSpeedTimeEnd;
    }

    public void setBlocklistEnabled(boolean blocklistEnabled) {
        mBlocklistEnabled = blocklistEnabled;
    }

    public void setBlocklistSize(int blocklistSize) {
        mBlocklistSize = blocklistSize;
    }

    public void setDHTEnabled(boolean dHTEnabled) {
        mDHTEnabled = dHTEnabled;
    }

    public void setEncryption(String encryption) {
        mEncryption = encryption;
    }

    public void setDownloadDir(String downloadDir) {
        mDownloadDir = downloadDir;
    }

    public void setDownloadDirFreeSpace(long freeSpace) {
        mDownloadDirFreeSpace = freeSpace;
    }

    public void setPeerLimitGlobal(int peerLimitGlobal) {
        mPeerLimitGlobal = peerLimitGlobal;
    }

    public void setPeerLimitPerTorrent(int peerLimitPerTorrent) {
        mPeerLimitPerTorrent = peerLimitPerTorrent;
    }

    public void setPEXEnabled(boolean pEXEnabled) {
        mPEXEnabled = pEXEnabled;
    }

    public void setPeerPort(int peerPort) {
        mPeerPort = peerPort;
    }

    public void setPeerPortRandomOnStart(boolean peerPortRandomOnStart) {
        mPeerPortRandomOnStart = peerPortRandomOnStart;
    }

    public void setPortForwardingEnabled(boolean portForwardingEnabled) {
        mPortForwardingEnabled = portForwardingEnabled;
    }

    public void setRPCVersion(int rPCVersion) {
        mRPCVersion = rPCVersion;
    }

    public void setRPCVersionMin(int rPCVersionMin) {
        mRPCVersionMin = rPCVersionMin;
    }

    public void setSeedRatioLimit(float seedRatioLimit) {
        mSeedRatioLimit = seedRatioLimit;
    }

    public void setSeedRatioLimited(boolean seedRatioLimited) {
        mSeedRatioLimited = seedRatioLimited;
    }

    public void setSpeedLimitDown(long speedLimitDown) {
        mSpeedLimitDown = speedLimitDown;
    }

    public void setSpeedLimitDownEnabled(boolean speedLimitDownEnabled) {
        mSpeedLimitDownEnabled = speedLimitDownEnabled;
    }

    public void setSpeedLimitUp(long speedLimitUp) {
        mSpeedLimitUp = speedLimitUp;
    }

    public void setSpeedLimitUpEnabled(boolean speedLimitUpEnabled) {
        mSpeedLimitUpEnabled = speedLimitUpEnabled;
    }

    public void setVersion(String version) {
        mVersion = version;
    }
}
