package org.sugr.gearshift;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.SerializedName;

import org.sugr.gearshift.TransmissionSessionManager.Exclude;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TransmissionSession implements Parcelable {
    @Exclude public static final class SetterFields {
        public static final String ALT_SPEED_LIMIT_ENABLED = "alt-speed-enabled";
        public static final String ALT_DOWNLOAD_SPEED_LIMIT = "alt-speed-down";
        public static final String ALT_UPLOAD_SPEED_LIMIT = "alt-speed-up";
        public static final String ALT_SPEED_LIMIT_TIME_ENABLED = "alt-speed-time-enabled";
        public static final String ALT_SPEED_LIMIT_TIME_BEGIN = "alt-speed-time-begin";
        public static final String ALT_SPEED_LIMIT_TIME_END = "alt-speed-time-end";
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

    @SerializedName(SetterFields.ALT_SPEED_LIMIT_ENABLED) private boolean mAltSpeedEnabled;
    @SerializedName(SetterFields.ALT_DOWNLOAD_SPEED_LIMIT) private long mAltSpeedDown;
    @SerializedName(SetterFields.ALT_UPLOAD_SPEED_LIMIT) private long mAltSpeedUp;

    @SerializedName(SetterFields.ALT_SPEED_LIMIT_TIME_ENABLED) private boolean mAltSpeedTimeEnabled;
    @SerializedName(SetterFields.ALT_SPEED_LIMIT_TIME_BEGIN) private int mAltSpeedTimeBegin;
    @SerializedName(SetterFields.ALT_SPEED_LIMIT_TIME_END) private int mAltSpeedTimeEnd;

    @SerializedName(SetterFields.BLOCKLIST_ENABLED) private boolean mBlocklistEnabled;
    @SerializedName("blocklist-size") private long mBlocklistSize;
    @SerializedName(SetterFields.BLOCKLIST_URL) private String mBlocklistURL;

    @SerializedName(SetterFields.CACHE_SIZE) private long mCacheSize;

    @SerializedName(SetterFields.DHT) private boolean mDHTEnabled;

    @SerializedName(SetterFields.DOWNLOAD_DIR) private String mDownloadDir;
    @SerializedName("download-dir-free-space") private long mDownloadDirFreeSpace;

    @SerializedName(SetterFields.DOWNLOAD_QUEUE_SIZE) private int mDownloadQueueSize;
    @SerializedName(SetterFields.DOWNLOAD_QUEUE_ENABLED) private boolean mDownloadQueueEnabled;

    @SerializedName(SetterFields.DOWNLOAD_SPEED_LIMIT) private long mSpeedLimitDown;
    @SerializedName(SetterFields.DOWNLOAD_SPEED_LIMIT_ENABLED) private boolean mSpeedLimitDownEnabled;

    @SerializedName(SetterFields.ENCRYPTION) private String mEncryption;

    @SerializedName(SetterFields.INCOMPLETE_DIR) private String mIncompleteDir;
    @SerializedName(SetterFields.INCOMPLETE_DIR_ENABLED) private boolean mIncompleteDirEnabled;

    @SerializedName(SetterFields.LOCAL_DISCOVERY) private boolean mLPDEnabled;
    @SerializedName(SetterFields.UTP) private boolean mUTPEnabled;

    @SerializedName(SetterFields.GLOBAL_PEER_LIMIT) private int mGlobalPeerLimit;
    @SerializedName(SetterFields.TORRENT_PEER_LIMIT) private int mTorrentPeerLimit;

    @SerializedName(SetterFields.PEER_EXCHANGE) private boolean mPEXEnabled;

    @SerializedName(SetterFields.PEER_PORT) private int mPeerPort;
    @SerializedName(SetterFields.PORT_FORWARDING) private boolean mPortForwardingEnabled;
    @SerializedName(SetterFields.RANDOM_PORT) private boolean mPeerPortRandomOnStart;

    @SerializedName(SetterFields.RENAME_PARTIAL) private boolean mRenamePartial;

    @SerializedName("rpc-version") private int mRPCVersion;
    @SerializedName("rpc-version-minimum") private int mRPCVersionMin;

    @SerializedName(SetterFields.DONE_SCRIPT) private String mDoneScript;
    @SerializedName(SetterFields.DONE_SCRIPT_ENABLED) private boolean mDoneScriptEnabled;

    @SerializedName(SetterFields.SEED_QUEUE_SIZE) private int mSeedQueueSize;
    @SerializedName(SetterFields.SEED_QUEUE_ENABLED) private boolean mSeedQueueEnabled;

    @SerializedName(SetterFields.SEED_RATIO_LIMIT) private float mSeedRatioLimit;
    @SerializedName(SetterFields.SEED_RATIO_LIMIT_ENABLED) private boolean mSeedRatioLimited;

    @SerializedName(SetterFields.UPLOAD_SPEED_LIMIT) private long mSpeedLimitUp;
    @SerializedName(SetterFields.UPLOAD_SPEED_LIMIT_ENABLED) private boolean mSpeedLimitUpEnabled;

    @SerializedName(SetterFields.STALLED_QUEUE_SIZE) private int mStalledQueueSize;
    @SerializedName(SetterFields.STALLED_QUEUE_ENABLED) private boolean mStalledQueueEnabled;

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

    @Exclude private Set<String> mDownloadDirectories;

    public TransmissionSession() {
        mDownloadDirectories = new HashSet<String>();
    }

    public boolean isAltSpeedLimitEnabled() {
        return mAltSpeedEnabled;
    }

    public long getAltDownloadSpeedLimit() {
        return mAltSpeedDown;
    }

    public long getAltUploadSpeedLimit() {
        return mAltSpeedUp;
    }

    public boolean isAltSpeedLimitTimeEnabled() {
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

    public boolean isUTPEnabled() {
        return mUTPEnabled;
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

    public boolean isSeedRatioLimitEnabled() {
        return mSeedRatioLimited;
    }

    public long getDownloadSpeedLimit() {
        return mSpeedLimitDown;
    }

    public boolean isDownloadSpeedLimitEnabled() {
        return mSpeedLimitDownEnabled;
    }

    public long getUploadSpeedLimit() {
        return mSpeedLimitUp;
    }

    public boolean isUploadSpeedLimitEnabled() {
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

    public void setDownloadDirectories(TransmissionProfile profile, List<Torrent> torrents) {
        mDownloadDirectories.clear();
        mDownloadDirectories.add(mDownloadDir);
        mDownloadDirectories.addAll(profile.getDirectories());

        for (Torrent t : torrents) {
            mDownloadDirectories.add(t.getDownloadDir());
        }

        mDownloadDirectories.remove(null);
    }

    public void setDownloadDirectories(Collection<String> directories) {
        mDownloadDirectories.addAll(directories);
    }

    public Set<String> getDownloadDirectories() {
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
