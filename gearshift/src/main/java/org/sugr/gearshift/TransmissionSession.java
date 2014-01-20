package org.sugr.gearshift;

import android.os.Parcel;
import android.os.Parcelable;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.NONE,
    creatorVisibility = JsonAutoDetect.Visibility.NONE,
    getterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
    isGetterVisibility = JsonAutoDetect.Visibility.PUBLIC_ONLY,
    setterVisibility = JsonAutoDetect.Visibility.NONE)
public class TransmissionSession implements Parcelable {
    /* TODO: add "idle-seeding-limit" and "idle-seeding-limit-enabled" */
    public static final class SetterFields {
        public static final String ALT_SPEED_LIMIT_ENABLED = "alt-speed-enabled";
        public static final String ALT_DOWNLOAD_SPEED_LIMIT = "alt-speed-down";
        public static final String ALT_UPLOAD_SPEED_LIMIT = "alt-speed-up";
        public static final String ALT_SPEED_LIMIT_TIME_ENABLED = "alt-speed-time-enabled";
        public static final String ALT_SPEED_LIMIT_TIME_BEGIN = "alt-speed-time-begin";
        public static final String ALT_SPEED_LIMIT_TIME_END = "alt-speed-time-end";
        public static final String ALT_SPEED_LIMIT_TIME_DAY = "alt-speed-time-day";
        public static final String BLOCKLIST_ENABLED = "blocklist-enabled";
        public static final String BLOCKLIST_URL = "blocklist-url";
        public static final String CACHE_SIZE = "cache-size-mb";
        public static final String DHT = "dht-enabled";
        public static final String DOWNLOAD_DIR = "download-dir";
        public static final String DOWNLOAD_QUEUE_SIZE = "download-queue-size";
        public static final String DOWNLOAD_QUEUE_ENABLED = "download-queue-enabled";
        public static final String DOWNLOAD_SPEED_LIMIT = "speed-limit-down";
        public static final String DOWNLOAD_SPEED_LIMIT_ENABLED = "speed-limit-down-enabled";
        public static final String ENCRYPTION = "encryption";
        public static final String GLOBAL_PEER_LIMIT = "peer-limit-global";
        public static final String INCOMPLETE_DIR = "incomplete-dir";
        public static final String INCOMPLETE_DIR_ENABLED = "incomplete-dir-enabled";
        public static final String LOCAL_DISCOVERY = "lpd-enabled";
        public static final String UTP = "utp-enabled";
        public static final String PEER_EXCHANGE = "pex-enabled";
        public static final String PEER_PORT = "peer-port";
        public static final String PORT_FORWARDING = "port-forwarding-enabled";
        public static final String RANDOM_PORT = "peer-port-random-on-start";
        public static final String RENAME_PARTIAL = "rename-partial-files";
        public static final String DONE_SCRIPT = "script-torrent-done-filename";
        public static final String DONE_SCRIPT_ENABLED = "script-torrent-done-enabled";
        public static final String SEED_QUEUE_SIZE = "seed-queue-size";
        public static final String SEED_QUEUE_ENABLED = "seed-queue-enabled";
        public static final String SEED_RATIO_LIMIT = "seedRatioLimit";
        public static final String SEED_RATIO_LIMIT_ENABLED = "seedRatioLimited";
        public static final String STALLED_QUEUE_SIZE = "queue-stalled-minutes";
        public static final String STALLED_QUEUE_ENABLED = "queue-stalled-enabled";
        public static final String START_ADDED = "start-added-torrents";
        public static final String TORRENT_PEER_LIMIT = "peer-limit-per-torrent";
        public static final String TRASH_ORIGINAL = "trash-original-torrent-files";
        public static final String UPLOAD_SPEED_LIMIT = "speed-limit-up";
        public static final String UPLOAD_SPEED_LIMIT_ENABLED = "speed-limit-up-enabled";
    }

    public static final int FREE_SPACE_METHOD_RPC_VERSION = 15;

    private boolean mAltSpeedEnabled;
    private long mAltSpeedDown;
    private long mAltSpeedUp;

    private boolean mAltSpeedTimeEnabled;
    private int mAltSpeedTimeBegin;
    private int mAltSpeedTimeEnd;
    private int mAltSpeedTimeDay;

    private boolean mBlocklistEnabled;
    private long mBlocklistSize;
    private String mBlocklistURL;

    private long mCacheSize;
    private String mConfigDir;

    private boolean mDHTEnabled;

    private String mDownloadDir;
    private long mDownloadDirFreeSpace;

    private int mDownloadQueueSize;
    private boolean mDownloadQueueEnabled;

    private long mSpeedLimitDown;
    private boolean mSpeedLimitDownEnabled;

    private String mEncryption;

    private String mIncompleteDir;
    private boolean mIncompleteDirEnabled;

    private boolean mLPDEnabled;
    private boolean mUTPEnabled;

    private int mGlobalPeerLimit;
    private int mTorrentPeerLimit;

    private boolean mPEXEnabled;

    private int mPeerPort;
    private boolean mPortForwardingEnabled;
    private boolean mPeerPortRandomOnStart;

    private boolean mRenamePartial;

    private int mRPCVersion;
    private int mRPCVersionMin;

    private String mDoneScript;
    private boolean mDoneScriptEnabled;

    private int mSeedQueueSize;
    private boolean mSeedQueueEnabled;

    private float mSeedRatioLimit;
    private boolean mSeedRatioLimited;

    private long mSpeedLimitUp;
    private boolean mSpeedLimitUpEnabled;

    private int mStalledQueueSize;
    private boolean mStalledQueueEnabled;

    private boolean mStartAdded;
    private boolean mTrashOriginal;

    private String mVersion;

    // https://trac.transmissionbt.com/browser/trunk/libtransmission/transmission.h - tr_sched_day
    public static class AltSpeedDay {
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

    public static class Encryption {
        public static final String REQUIRED = "required";
        public static final String PREFERRED = "preferred";
        public static final String TOLERATED = "tolerated";
    }

    private Set<String> mDownloadDirectories;

    public TransmissionSession() {
        mDownloadDirectories = new HashSet<String>();
    }

    @JsonProperty(SetterFields.ALT_SPEED_LIMIT_ENABLED) public boolean isAltSpeedLimitEnabled() {
        return mAltSpeedEnabled;
    }

    @JsonProperty(SetterFields.ALT_DOWNLOAD_SPEED_LIMIT) public long getAltDownloadSpeedLimit() {
        return mAltSpeedDown;
    }

    @JsonProperty(SetterFields.ALT_UPLOAD_SPEED_LIMIT) public long getAltUploadSpeedLimit() {
        return mAltSpeedUp;
    }

    @JsonProperty(SetterFields.ALT_SPEED_LIMIT_TIME_ENABLED) public boolean isAltSpeedLimitTimeEnabled() {
        return mAltSpeedTimeEnabled;
    }

    @JsonProperty(SetterFields.ALT_SPEED_LIMIT_TIME_BEGIN) public int getAltSpeedTimeBegin() {
        return mAltSpeedTimeBegin;
    }

    @JsonProperty(SetterFields.ALT_SPEED_LIMIT_TIME_END) public int getAltSpeedTimeEnd() {
        return mAltSpeedTimeEnd;
    }

    @JsonProperty(SetterFields.ALT_SPEED_LIMIT_TIME_DAY) public int getAltSpeedTimeDay() {
        return mAltSpeedTimeDay;
    }

    @JsonProperty(SetterFields.BLOCKLIST_ENABLED) public boolean isBlocklistEnabled() {
        return mBlocklistEnabled;
    }

    @JsonProperty("blocklist-size") public long getBlocklistSize() {
        return mBlocklistSize;
    }

    @JsonProperty(SetterFields.BLOCKLIST_URL) public String getBlocklistURL() {
        return mBlocklistURL;
    }

    @JsonProperty(SetterFields.DHT) public boolean isDHTEnabled() {
        return mDHTEnabled;
    }

    @JsonProperty(SetterFields.ENCRYPTION) public String getEncryption() {
        return mEncryption;
    }

    @JsonProperty(SetterFields.CACHE_SIZE) public long getCacheSize() {
        return mCacheSize;
    }

    @JsonProperty("config-dir") public String getConfigDir() {
        return mConfigDir;
    }

    @JsonProperty(SetterFields.DOWNLOAD_DIR) public String getDownloadDir() {
        return mDownloadDir;
    }

    @JsonProperty("download-dir-free-space") public long getDownloadDirFreeSpace() {
        return mDownloadDirFreeSpace;
    }

    @JsonProperty(SetterFields.DOWNLOAD_QUEUE_SIZE) public int getDownloadQueueSize() {
        return mDownloadQueueSize;
    }

    @JsonProperty(SetterFields.DOWNLOAD_QUEUE_ENABLED) public boolean isDownloadQueueEnabled() {
        return mDownloadQueueEnabled;
    }

    @JsonProperty(SetterFields.INCOMPLETE_DIR) public String getIncompleteDir() {
        return mIncompleteDir;
    }

    @JsonProperty(SetterFields.INCOMPLETE_DIR_ENABLED) public boolean isIncompleteDirEnabled() {
        return mIncompleteDirEnabled;
    }

    @JsonProperty(SetterFields.LOCAL_DISCOVERY) public boolean isLocalDiscoveryEnabled() {
        return mLPDEnabled;
    }

    @JsonProperty(SetterFields.UTP) public boolean isUTPEnabled() {
        return mUTPEnabled;
    }

    @JsonProperty(SetterFields.GLOBAL_PEER_LIMIT) public int getGlobalPeerLimit() {
        return mGlobalPeerLimit;
    }

    @JsonProperty(SetterFields.TORRENT_PEER_LIMIT) public int getTorrentPeerLimit() {
        return mTorrentPeerLimit;
    }

    @JsonProperty(SetterFields.PEER_EXCHANGE) public boolean isPeerExchangeEnabled() {
        return mPEXEnabled;
    }

    @JsonProperty(SetterFields.PEER_PORT) public int getPeerPort() {
        return mPeerPort;
    }

    @JsonProperty(SetterFields.RANDOM_PORT) public boolean isPeerPortRandomOnStart() {
        return mPeerPortRandomOnStart;
    }

    @JsonProperty(SetterFields.PORT_FORWARDING) public boolean isPortForwardingEnabled() {
        return mPortForwardingEnabled;
    }

    @JsonProperty(SetterFields.RENAME_PARTIAL) public boolean isRenamePartialFilesEnabled() {
        return mRenamePartial;
    }

    @JsonProperty("rpc-version") public int getRPCVersion() {
        return mRPCVersion;
    }

    @JsonProperty("rpc-version-minimum") public int getRPCVersionMin() {
        return mRPCVersionMin;
    }

    @JsonProperty(SetterFields.DONE_SCRIPT) public String getDoneScript() {
        return mDoneScript;
    }

    @JsonProperty(SetterFields.DONE_SCRIPT_ENABLED) public boolean isDoneScriptEnabled() {
        return mDoneScriptEnabled;
    }

    @JsonProperty(SetterFields.SEED_QUEUE_SIZE) public int getSeedQueueSize() {
        return mSeedQueueSize;
    }

    @JsonProperty(SetterFields.SEED_QUEUE_ENABLED) public boolean isSeedQueueEnabled() {
        return mSeedQueueEnabled;
    }

    @JsonProperty(SetterFields.SEED_RATIO_LIMIT) public float getSeedRatioLimit() {
        return mSeedRatioLimit;
    }

    @JsonProperty(SetterFields.SEED_RATIO_LIMIT_ENABLED) public boolean isSeedRatioLimitEnabled() {
        return mSeedRatioLimited;
    }

    @JsonProperty(SetterFields.DOWNLOAD_SPEED_LIMIT) public long getDownloadSpeedLimit() {
        return mSpeedLimitDown;
    }

    @JsonProperty(SetterFields.DOWNLOAD_SPEED_LIMIT_ENABLED) public boolean isDownloadSpeedLimitEnabled() {
        return mSpeedLimitDownEnabled;
    }

    @JsonProperty(SetterFields.UPLOAD_SPEED_LIMIT) public long getUploadSpeedLimit() {
        return mSpeedLimitUp;
    }

    @JsonProperty(SetterFields.UPLOAD_SPEED_LIMIT_ENABLED) public boolean isUploadSpeedLimitEnabled() {
        return mSpeedLimitUpEnabled;
    }

    @JsonProperty(SetterFields.STALLED_QUEUE_SIZE) public int getStalledQueueSize() {
        return mStalledQueueSize;
    }

    @JsonProperty(SetterFields.STALLED_QUEUE_ENABLED) public boolean isStalledQueueEnabled() {
        return mStalledQueueEnabled;
    }

    @JsonProperty(SetterFields.START_ADDED) public boolean isStartAddedTorrentsEnabled() {
        return mStartAdded;
    }

    @JsonProperty(SetterFields.TRASH_ORIGINAL) public boolean isTrashOriginalTorrentFilesEnabled() {
        return mTrashOriginal;
    }

    @JsonProperty("version") public String getVersion() {
        return mVersion;
    }

    public void setAltSpeedLimitEnabled(boolean altSpeedEnabled) {
        mAltSpeedEnabled = altSpeedEnabled;
    }

    public void setAltDownloadSpeedLimit(long altSpeedDown) {
        mAltSpeedDown = altSpeedDown;
    }

    public void setAltUploadSpeedLimit(long altSpeedUp) {
        mAltSpeedUp = altSpeedUp;
    }

    public void setAltSpeedLimitTimeEnabled(boolean altSpeedTimeEnabled) {
        mAltSpeedTimeEnabled = altSpeedTimeEnabled;
    }

    public void setAltSpeedTimeBegin(int altSpeedTimeBegin) {
        mAltSpeedTimeBegin = altSpeedTimeBegin;
    }

    public void setAltSpeedTimeEnd(int altSpeedTimeEnd) {
        mAltSpeedTimeEnd = altSpeedTimeEnd;
    }

    public void setAltSpeedTimeDay(int days) {
        mAltSpeedTimeDay = days;
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

    public void setDHTEnabled(boolean enable) {
        mDHTEnabled = enable;
    }

    public void setEncryption(String encryption) {
        mEncryption = encryption;
    }

    public void setCacheSize(long size) {
        mCacheSize = size;
    }

    public void setConfigDir(String dir) {
        mConfigDir = dir;
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

    public void setUTPEnabled(boolean enable) {
        mUTPEnabled = enable;
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

    public void setSeedRatioLimitEnabled(boolean seedRatioLimited) {
        mSeedRatioLimited = seedRatioLimited;
    }

    public void setDownloadSpeedLimit(long speedLimitDown) {
        mSpeedLimitDown = speedLimitDown;
    }

    public void setDownloadSpeedLimitEnabled(boolean speedLimitDownEnabled) {
        mSpeedLimitDownEnabled = speedLimitDownEnabled;
    }

    public void setUploadSpeedLimit(long speedLimitUp) {
        mSpeedLimitUp = speedLimitUp;
    }

    public void setUploadSpeedLimitEnabled(boolean speedLimitUpEnabled) {
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

    public void setDownloadDirectories(TransmissionProfile profile, Set<String> directories) {
        mDownloadDirectories.clear();
        mDownloadDirectories.add(mDownloadDir);
        mDownloadDirectories.addAll(profile.getDirectories());
        mDownloadDirectories.addAll(directories);

        mDownloadDirectories.remove(null);
    }

    public void setDownloadDirectories(Collection<String> directories) {
        mDownloadDirectories.addAll(directories);
    }

    @JsonIgnore public Set<String> getDownloadDirectories() {
        return mDownloadDirectories;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel in, int flags) {
        in.writeByte((byte) (mAltSpeedEnabled ? 1 : 0));
        in.writeLong(mAltSpeedDown);
        in.writeLong(mAltSpeedUp);
        in.writeByte((byte) (mAltSpeedTimeEnabled ? 1 : 0));
        in.writeInt(mAltSpeedTimeBegin);
        in.writeInt(mAltSpeedTimeEnd);
        in.writeByte((byte) (mBlocklistEnabled ? 1 : 0));
        in.writeLong(mBlocklistSize);
        in.writeString(mBlocklistURL);
        in.writeByte((byte) (mDHTEnabled ? 1 : 0));
        in.writeString(mEncryption);
        in.writeLong(mCacheSize);
        in.writeString(mDownloadDir);
        in.writeLong(mDownloadDirFreeSpace);
        in.writeInt(mDownloadQueueSize);
        in.writeByte((byte) (mDownloadQueueEnabled ? 1 : 0));
        in.writeString(mIncompleteDir);
        in.writeByte((byte) (mIncompleteDirEnabled ? 1 : 0));
        in.writeByte((byte) (mLPDEnabled ? 1 : 0));
        in.writeByte((byte) (mUTPEnabled ? 1 : 0));
        in.writeInt(mGlobalPeerLimit);
        in.writeInt(mTorrentPeerLimit);
        in.writeByte((byte) (mPEXEnabled ? 1 : 0));
        in.writeInt(mPeerPort);
        in.writeByte((byte) (mPeerPortRandomOnStart ? 1 : 0));
        in.writeByte((byte) (mPortForwardingEnabled ? 1 : 0));
        in.writeByte((byte) (mRenamePartial ? 1 : 0));
        in.writeInt(mRPCVersion);
        in.writeInt(mRPCVersionMin);
        in.writeString(mDoneScript);
        in.writeByte((byte) (mDoneScriptEnabled ? 1 : 0));
        in.writeInt(mSeedQueueSize);
        in.writeByte((byte) (mSeedQueueEnabled ? 1 : 0));
        in.writeFloat(mSeedRatioLimit);
        in.writeByte((byte) (mSeedRatioLimited ? 1 : 0));
        in.writeLong(mSpeedLimitDown);
        in.writeByte((byte) (mSpeedLimitDownEnabled ? 1 : 0));
        in.writeLong(mSpeedLimitUp);
        in.writeByte((byte) (mSpeedLimitUpEnabled ? 1 : 0));
        in.writeInt(mStalledQueueSize);
        in.writeByte((byte) (mStalledQueueEnabled ? 1 : 0));
        in.writeByte((byte) (mStartAdded ? 1 : 0));
        in.writeByte((byte) (mTrashOriginal ? 1 : 0));
        in.writeString(mVersion);
        in.writeStringList(new ArrayList<String>(mDownloadDirectories));
    }

    private TransmissionSession(Parcel in) {
        mAltSpeedEnabled = in.readByte() == 1;
        mAltSpeedDown = in.readLong();
        mAltSpeedUp = in.readLong();
        mAltSpeedTimeEnabled = in.readByte() == 1;
        mAltSpeedTimeBegin = in.readInt();
        mAltSpeedTimeEnd = in.readInt();
        mBlocklistEnabled = in.readByte() == 1;
        mBlocklistSize = in.readLong();
        mBlocklistURL = in.readString();
        mDHTEnabled = in.readByte() == 1;
        mEncryption = in.readString();
        mCacheSize = in.readLong();
        mDownloadDir = in.readString();
        mDownloadDirFreeSpace = in.readLong();
        mDownloadQueueSize = in.readInt();
        mDownloadQueueEnabled = in.readByte() == 1;
        mIncompleteDir = in.readString();
        mIncompleteDirEnabled = in.readByte() == 1;
        mLPDEnabled = in.readByte() == 1;
        mUTPEnabled = in.readByte() == 1;
        mGlobalPeerLimit = in.readInt();
        mTorrentPeerLimit = in.readInt();
        mPEXEnabled = in.readByte() == 1;
        mPeerPort = in.readInt();
        mPeerPortRandomOnStart = in.readByte() == 1;
        mPortForwardingEnabled = in.readByte() == 1;
        mRenamePartial = in.readByte() == 1;
        mRPCVersion = in.readInt();
        mRPCVersionMin = in.readInt();
        mDoneScript = in.readString();
        mDoneScriptEnabled = in.readByte() == 1;
        mSeedQueueSize = in.readInt();
        mSeedQueueEnabled = in.readByte() == 1;
        mSeedRatioLimit = in.readFloat();
        mSeedRatioLimited = in.readByte() == 1;
        mSpeedLimitDown = in.readLong();
        mSpeedLimitDownEnabled = in.readByte() == 1;
        mSpeedLimitUp = in.readLong();
        mSpeedLimitUpEnabled = in.readByte() == 1;
        mStalledQueueSize = in.readInt();
        mStalledQueueEnabled = in.readByte() == 1;
        mStartAdded = in.readByte() == 1;
        mTrashOriginal = in.readByte() == 1;
        mVersion = in.readString();
        mDownloadDirectories = new HashSet<String>(in.createStringArrayList());
    }

    public static final Parcelable.Creator<TransmissionSession> CREATOR
            = new Parcelable.Creator<TransmissionSession>() {
        @Override
        public TransmissionSession createFromParcel(Parcel in) {
            return new TransmissionSession(in);
        }

        @Override
        public TransmissionSession[] newArray(int size) {
            return new TransmissionSession[size];
        }
    };
}
