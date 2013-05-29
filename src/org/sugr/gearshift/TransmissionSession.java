package org.sugr.gearshift;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sugr.gearshift.TransmissionSessionManager.Exclude;

import com.google.gson.annotations.SerializedName;

public class TransmissionSession {
    @Exclude public static final class SetterFields {
        public static final String BLOCKLIST_ENABLED = "blocklist-enabled";
        public static final String BLOCKLIST_URL = "blocklist-url";
        public static final String CACHE_SIZE = "cache-size-mb";
        public static final String DHT = "dht-enabled";
        public static final String DOWNLOAD_DIR = "download-dir";
        public static final String ENCRYPTION = "encryption";
        public static final String INCOMPLETE_DIR = "incomplete-dir";
        public static final String INCOMPLETE_DIR_ENABLED = "incomplete-dir-enabled";
        public static final String LOCAL_DISCOVERY = "lpd-enabled";
        public static final String PEER_EXCHANGE = "pex-enabled";
        public static final String PEER_PORT = "peer-port";
        public static final String PORT_FORWARDING = "port-forwarding-enabled";
        public static final String RANDOM_PORT = "peer-port-random-on-start";
        public static final String RENAME_PARTIAL = "rename-partial-files";
        public static final String DONE_SCRIPT = "script-torrent-done-filename";
        public static final String DONE_SCRIPT_ENABLED = "script-torrent-done-enabled";
        public static final String TRASH_ORIGINAL = "trash-original-torrent-files";
        public static final String START_ADDED = "start-added-torrents";
    }

    @SerializedName("alt-speed-enabled") private boolean mAltSpeedEnabled;
    @SerializedName("alt-speed-down") private long mAltSpeedDown;
    @SerializedName("alt-speed-up") private long mAltSpeedUp;

    @SerializedName("alt-speed-time-enabled") private boolean mAltSpeedTimeEnabled;
    @SerializedName("alt-speed-time-begin") private int mAltSpeedTimeBegin;
    @SerializedName("alt-speed-time-end") private int mAltSpeedTimeEnd;

    @SerializedName(SetterFields.BLOCKLIST_ENABLED) private boolean mBlocklistEnabled;
    @SerializedName("blocklist-size") private long mBlocklistSize;
    @SerializedName(SetterFields.BLOCKLIST_URL) private String mBlocklistURL;

    @SerializedName(SetterFields.CACHE_SIZE) private long mCacheSize;

    @SerializedName(SetterFields.DHT) private boolean mDHTEnabled;

    @SerializedName(SetterFields.DOWNLOAD_DIR) private String mDownloadDir;
    @SerializedName("download-dir-free-space") private long mDownloadDirFreeSpace;

    @SerializedName("download-queue-size") private int mDownloadQueueSize;
    @SerializedName("download-queue-enabled") private boolean mDownloadQueueEnabled;

    @SerializedName(SetterFields.ENCRYPTION) private String mEncryption;

    @SerializedName(SetterFields.INCOMPLETE_DIR) private String mIncompleteDir;
    @SerializedName(SetterFields.INCOMPLETE_DIR_ENABLED) private boolean mIncompleteDirEnabled;

    @SerializedName(SetterFields.LOCAL_DISCOVERY) private boolean mLPDEnabled;

    @SerializedName("peer-limit-global") private int mGlobalPeerLimit;
    @SerializedName("peer-limit-per-torrent") private int mTorrentPeerLimit;

    @SerializedName(SetterFields.PEER_EXCHANGE) private boolean mPEXEnabled;

    @SerializedName(SetterFields.PEER_PORT) private int mPeerPort;
    @SerializedName(SetterFields.PORT_FORWARDING) private boolean mPortForwardingEnabled;
    @SerializedName(SetterFields.RANDOM_PORT) private boolean mPeerPortRandomOnStart;

    @SerializedName(SetterFields.RENAME_PARTIAL) private boolean mRenamePartial;

    @SerializedName("rpc-version") private int mRPCVersion;
    @SerializedName("rpc-version-minimum") private int mRPCVersionMin;

    @SerializedName(SetterFields.DONE_SCRIPT) private String mDoneScript;
    @SerializedName(SetterFields.DONE_SCRIPT_ENABLED) private boolean mDoneScriptEnabled;

    @SerializedName("seed-queue-size") private int mSeedQueueSize;
    @SerializedName("seed-queue-enabled") private boolean mSeedQueueEnabled;

    @SerializedName("seedRatioLimit") private float mSeedRatioLimit;
    @SerializedName("seedRatioLimited") private boolean mSeedRatioLimited;

    @SerializedName("speed-limit-down") private long mSpeedLimitDown;
    @SerializedName("speed-limit-down-enabled") private boolean mSpeedLimitDownEnabled;
    @SerializedName("speed-limit-up") private long mSpeedLimitUp;
    @SerializedName("speed-limit-up-enabled") private boolean mSpeedLimitUpEnabled;

    @SerializedName("queue-stalled-minutes") private int mStalledQueueSize;
    @SerializedName("queue-stalled-enabled") private boolean mStalledQueueEnabled;

    @SerializedName(SetterFields.START_ADDED) private boolean mStartAdded;
    @SerializedName(SetterFields.TRASH_ORIGINAL) private boolean mTrashOriginal;

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

    @Exclude private Set<String> mDownloadDirectories = new HashSet<String>();

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

    public long getBlocklistSize() {
        return mBlocklistSize;
    }

    public String getBlocklistURL() {
        return mBlocklistURL;
    }

    public boolean isDHTEnabled() {
        return mDHTEnabled;
    }

    public String getEncryption() {
        return mEncryption;
    }

    public long getCacheSize() {
        return mCacheSize;
    }

    public String getDownloadDir() {
        return mDownloadDir;
    }

    public long getDownloadDirFreeSpace() {
        return mDownloadDirFreeSpace;
    }

    public int getDownloadQueueSize() {
        return mDownloadQueueSize;
    }

    public boolean isDownloadQueueEnabled() {
        return mDownloadQueueEnabled;
    }

    public String getIncompleteDir() {
        return mIncompleteDir;
    }

    public boolean isIncompleteDirEnabled() {
        return mIncompleteDirEnabled;
    }

    public boolean isLocalDiscoveryEnabled() {
        return mLPDEnabled;
    }

    public int getGlobalPeerLimit() {
        return mGlobalPeerLimit;
    }

    public int getTorrentPeerLimit() {
        return mTorrentPeerLimit;
    }

    public boolean isPeerExchangeEnabled() {
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

    public boolean isRenamePartialFilesEnabled() {
        return mRenamePartial;
    }

    public int getRPCVersion() {
        return mRPCVersion;
    }

    public int getRPCVersionMin() {
        return mRPCVersionMin;
    }

    public String getDoneScript() {
        return mDoneScript;
    }

    public boolean isDoneScriptEnabled() {
        return mDoneScriptEnabled;
    }

    public int getSeedQueueSize() {
        return mSeedQueueSize;
    }

    public boolean isSeedQueueEnabled() {
        return mSeedQueueEnabled;
    }

    public float getSeedRatioLimit() {
        return mSeedRatioLimit;
    }

    public boolean isSeedRatioLimited() {
        return mSeedRatioLimited;
    }

    public long getDownloadSpeedLimit() {
        return mSpeedLimitDown;
    }

    public boolean isDownloadSpeedLimited() {
        return mSpeedLimitDownEnabled;
    }

    public long getUploadSpeedLimit() {
        return mSpeedLimitUp;
    }

    public boolean isUploadSpeedLimited() {
        return mSpeedLimitUpEnabled;
    }

    public int getStalledQueueSize() {
        return mStalledQueueSize;
    }

    public boolean isStalledQueueEnabled() {
        return mStalledQueueEnabled;
    }

    public boolean isStartAddedTorrentsEnabled() {
        return mStartAdded;
    }

    public boolean isTrashOriginalTorrentFilesEnabled() {
        return mTrashOriginal;
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

    public void setBlocklistSize(long blocklistSize) {
        mBlocklistSize = blocklistSize;
    }

    public void setBlocklistURL(String url) {
        mBlocklistURL = url;
    }

    public void setDHTEnabled(boolean dHTEnabled) {
        mDHTEnabled = dHTEnabled;
    }

    public void setEncryption(String encryption) {
        mEncryption = encryption;
    }

    public void setCacheSize(long size) {
        mCacheSize = size;
    }

    public void setDownloadDir(String downloadDir) {
        mDownloadDir = downloadDir;
    }

    public void setDownloadDirFreeSpace(long freeSpace) {
        mDownloadDirFreeSpace = freeSpace;
    }

    public void setDownloadQueueSize(int size) {
        mDownloadQueueSize = size;
    }

    public void setDownloadQueueEnabled(boolean enable) {
        mDownloadQueueEnabled = enable;
    }

    public void setIncompleteDir(String dir) {
        mIncompleteDir = dir;
    }

    public void setLocalDiscoveryEnabled(boolean enable) {
        mLPDEnabled = enable;
    }

    public void setIncompleteDirEnabled(boolean enable) {
        mIncompleteDirEnabled = enable;
    }

    public void setGlobalPeerLimit(int limit) {
        mGlobalPeerLimit = limit;
    }

    public void setTorrentPeerLimit(int limit) {
        mTorrentPeerLimit = limit;
    }

    public void setPeerExchangeEnabled(boolean enable) {
        mPEXEnabled = enable;
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

    public void setRenamePartialFilesEnabled(boolean rename) {
        mRenamePartial = rename;
    }

    public void setRPCVersion(int rPCVersion) {
        mRPCVersion = rPCVersion;
    }

    public void setRPCVersionMin(int rPCVersionMin) {
        mRPCVersionMin = rPCVersionMin;
    }

    public void setDoneScript(String script) {
        mDoneScript = script;
    }

    public void setDoneScriptEnabled(boolean enabled) {
        mDoneScriptEnabled = enabled;
    }

    public void setSeedQueueSize(int size) {
        mSeedQueueSize = size;
    }

    public void setSeedQueueEnabled(boolean enable) {
        mSeedQueueEnabled = enable;
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

    public void setStalledQueueSize(int size) {
        mStalledQueueSize = size;
    }

    public void setStalledQueueEnabled(boolean enable) {
        mStalledQueueEnabled = enable;
    }

    public void setStartAddedTorrentsEnabled(boolean enable) {
        mStartAdded = enable;
    }

    public void setTrashOriginalTorrentFilesEnabled(boolean enable) {
        mTrashOriginal = enable;
    }

    public void setVersion(String version) {
        mVersion = version;
    }

    public void setDownloadDirectories(TransmissionProfile profile, List<Torrent> torrents) {
        mDownloadDirectories.clear();
        mDownloadDirectories.add(mDownloadDir);
        mDownloadDirectories.addAll(profile.getDirectories());

        for (Torrent t : torrents) {
            mDownloadDirectories.add(t.getDownloadDir());
        }
    }

    public Set<String> getDownloadDirectories() {
        return mDownloadDirectories;
    }
}
