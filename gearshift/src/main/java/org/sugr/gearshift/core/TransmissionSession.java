package org.sugr.gearshift.core;

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
        public static final String IDLE_SEEDING_LIMIT = "idle-seeding-limit";
        public static final String IDLE_SEEDING_LIMIT_ENABLED = "idle-seeding-limit-enabled";
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

    private boolean altSpeedEnabled;
    private long altSpeedDown;
    private long altSpeedUp;

    private boolean altSpeedTimeEnabled;
    private int altSpeedTimeBegin;
    private int altSpeedTimeEnd;
    private int altSpeedTimeDay;

    private boolean blocklistEnabled;
    private long blocklistSize;
    private String blocklistURL;

    private long cacheSize;
    private String configDir;

    private boolean dhtEnabled;

    private String downloadDir;
    private long downloadDirFreeSpace;

    private int downloadQueueSize;
    private boolean downloadQueueEnabled;

    private long speedLimitDown;
    private boolean speedLimitDownEnabled;

    private String encryption;

    private long idleSeedingLimig;
    private boolean idleSeedingLimitEnabled;

    private String incompleteDir;
    private boolean incompleteDirEnabled;

    private boolean lpdEnabled;
    private boolean utpEnabled;

    private int globalPeerLimit;
    private int torrentPeerLimit;

    private boolean pexEnabled;

    private int peerPort;
    private boolean portForwardingEnabled;
    private boolean peerPortRandomOnStart;

    private boolean renamePartial;

    private int rpcVersion;
    private int rpcVersionMin;

    private String doneScript;
    private boolean doneScriptEnabled;

    private int seedQueueSize;
    private boolean seedQueueEnabled;

    private float seedRatioLimit;
    private boolean seedRatioLimited;

    private long speedLimitUp;
    private boolean speedLimitUpEnabled;

    private int stalledQueueSize;
    private boolean stalledQueueEnabled;

    private boolean startAdded;
    private boolean trashOriginal;

    private String version;

    // https://trac.transmissionbt.com/browser/trunk/libtransmission/transmission.h - tr_sched_day
    public static class AltSpeedDay {
        public static final int SUN = (1);
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

    private Set<String> downloadDirectories;

    public TransmissionSession() {
        downloadDirectories = new HashSet<>();
    }

    @JsonProperty(SetterFields.ALT_SPEED_LIMIT_ENABLED) public boolean isAltSpeedLimitEnabled() {
        return altSpeedEnabled;
    }

    @JsonProperty(SetterFields.ALT_DOWNLOAD_SPEED_LIMIT) public long getAltDownloadSpeedLimit() {
        return altSpeedDown;
    }

    @JsonProperty(SetterFields.ALT_UPLOAD_SPEED_LIMIT) public long getAltUploadSpeedLimit() {
        return altSpeedUp;
    }

    @JsonProperty(SetterFields.ALT_SPEED_LIMIT_TIME_ENABLED) public boolean isAltSpeedLimitTimeEnabled() {
        return altSpeedTimeEnabled;
    }

    @JsonProperty(SetterFields.ALT_SPEED_LIMIT_TIME_BEGIN) public int getAltSpeedTimeBegin() {
        return altSpeedTimeBegin;
    }

    @JsonProperty(SetterFields.ALT_SPEED_LIMIT_TIME_END) public int getAltSpeedTimeEnd() {
        return altSpeedTimeEnd;
    }

    @JsonProperty(SetterFields.ALT_SPEED_LIMIT_TIME_DAY) public int getAltSpeedTimeDay() {
        return altSpeedTimeDay;
    }

    @JsonProperty(SetterFields.BLOCKLIST_ENABLED) public boolean isBlocklistEnabled() {
        return blocklistEnabled;
    }

    @JsonProperty("blocklist-size") public long getBlocklistSize() {
        return blocklistSize;
    }

    @JsonProperty(SetterFields.BLOCKLIST_URL) public String getBlocklistURL() {
        return blocklistURL;
    }

    @JsonProperty(SetterFields.DHT) public boolean isDhtEnabled() {
        return dhtEnabled;
    }

    @JsonProperty(SetterFields.ENCRYPTION) public String getEncryption() {
        return encryption;
    }

    @JsonProperty(SetterFields.CACHE_SIZE) public long getCacheSize() {
        return cacheSize;
    }

    @JsonProperty("config-dir") public String getConfigDir() {
        return configDir;
    }

    @JsonProperty(SetterFields.DOWNLOAD_DIR) public String getDownloadDir() {
        return downloadDir;
    }

    @JsonProperty("download-dir-free-space") public long getDownloadDirFreeSpace() {
        return downloadDirFreeSpace;
    }

    @JsonProperty(SetterFields.DOWNLOAD_QUEUE_SIZE) public int getDownloadQueueSize() {
        return downloadQueueSize;
    }

    @JsonProperty(SetterFields.DOWNLOAD_QUEUE_ENABLED) public boolean isDownloadQueueEnabled() {
        return downloadQueueEnabled;
    }

    @JsonProperty(SetterFields.IDLE_SEEDING_LIMIT) public long getIdleSeedingLimig() {
        return idleSeedingLimig;
    }

    @JsonProperty(SetterFields.IDLE_SEEDING_LIMIT_ENABLED) public boolean isIdleSeedingLimitEnabled() {
        return idleSeedingLimitEnabled;
    }
    @JsonProperty(SetterFields.INCOMPLETE_DIR) public String getIncompleteDir() {
        return incompleteDir;
    }

    @JsonProperty(SetterFields.INCOMPLETE_DIR_ENABLED) public boolean isIncompleteDirEnabled() {
        return incompleteDirEnabled;
    }

    @JsonProperty(SetterFields.LOCAL_DISCOVERY) public boolean isLocalDiscoveryEnabled() {
        return lpdEnabled;
    }

    @JsonProperty(SetterFields.UTP) public boolean isUtpEnabled() {
        return utpEnabled;
    }

    @JsonProperty(SetterFields.GLOBAL_PEER_LIMIT) public int getGlobalPeerLimit() {
        return globalPeerLimit;
    }

    @JsonProperty(SetterFields.TORRENT_PEER_LIMIT) public int getTorrentPeerLimit() {
        return torrentPeerLimit;
    }

    @JsonProperty(SetterFields.PEER_EXCHANGE) public boolean isPeerExchangeEnabled() {
        return pexEnabled;
    }

    @JsonProperty(SetterFields.PEER_PORT) public int getPeerPort() {
        return peerPort;
    }

    @JsonProperty(SetterFields.RANDOM_PORT) public boolean isPeerPortRandomOnStart() {
        return peerPortRandomOnStart;
    }

    @JsonProperty(SetterFields.PORT_FORWARDING) public boolean isPortForwardingEnabled() {
        return portForwardingEnabled;
    }

    @JsonProperty(SetterFields.RENAME_PARTIAL) public boolean isRenamePartialFilesEnabled() {
        return renamePartial;
    }

    @JsonProperty("rpc-version") public int getRPCVersion() {
        return rpcVersion;
    }

    @JsonProperty("rpc-version-minimum") public int getRPCVersionMin() {
        return rpcVersionMin;
    }

    @JsonProperty(SetterFields.DONE_SCRIPT) public String getDoneScript() {
        return doneScript;
    }

    @JsonProperty(SetterFields.DONE_SCRIPT_ENABLED) public boolean isDoneScriptEnabled() {
        return doneScriptEnabled;
    }

    @JsonProperty(SetterFields.SEED_QUEUE_SIZE) public int getSeedQueueSize() {
        return seedQueueSize;
    }

    @JsonProperty(SetterFields.SEED_QUEUE_ENABLED) public boolean isSeedQueueEnabled() {
        return seedQueueEnabled;
    }

    @JsonProperty(SetterFields.SEED_RATIO_LIMIT) public float getSeedRatioLimit() {
        return seedRatioLimit;
    }

    @JsonProperty(SetterFields.SEED_RATIO_LIMIT_ENABLED) public boolean isSeedRatioLimitEnabled() {
        return seedRatioLimited;
    }

    @JsonProperty(SetterFields.DOWNLOAD_SPEED_LIMIT) public long getDownloadSpeedLimit() {
        return speedLimitDown;
    }

    @JsonProperty(SetterFields.DOWNLOAD_SPEED_LIMIT_ENABLED) public boolean isDownloadSpeedLimitEnabled() {
        return speedLimitDownEnabled;
    }

    @JsonProperty(SetterFields.UPLOAD_SPEED_LIMIT) public long getUploadSpeedLimit() {
        return speedLimitUp;
    }

    @JsonProperty(SetterFields.UPLOAD_SPEED_LIMIT_ENABLED) public boolean isUploadSpeedLimitEnabled() {
        return speedLimitUpEnabled;
    }

    @JsonProperty(SetterFields.STALLED_QUEUE_SIZE) public int getStalledQueueSize() {
        return stalledQueueSize;
    }

    @JsonProperty(SetterFields.STALLED_QUEUE_ENABLED) public boolean isStalledQueueEnabled() {
        return stalledQueueEnabled;
    }

    @JsonProperty(SetterFields.START_ADDED) public boolean isStartAddedTorrentsEnabled() {
        return startAdded;
    }

    @JsonProperty(SetterFields.TRASH_ORIGINAL) public boolean isTrashOriginalTorrentFilesEnabled() {
        return trashOriginal;
    }

    @JsonProperty("version") public String getVersion() {
        return version;
    }

    public void setAltSpeedLimitEnabled(boolean altSpeedEnabled) {
        this.altSpeedEnabled = altSpeedEnabled;
    }

    public void setAltDownloadSpeedLimit(long altSpeedDown) {
        this.altSpeedDown = altSpeedDown;
    }

    public void setAltUploadSpeedLimit(long altSpeedUp) {
        this.altSpeedUp = altSpeedUp;
    }

    public void setAltSpeedLimitTimeEnabled(boolean altSpeedTimeEnabled) {
        this.altSpeedTimeEnabled = altSpeedTimeEnabled;
    }

    public void setAltSpeedTimeBegin(int altSpeedTimeBegin) {
        this.altSpeedTimeBegin = altSpeedTimeBegin;
    }

    public void setAltSpeedTimeEnd(int altSpeedTimeEnd) {
        this.altSpeedTimeEnd = altSpeedTimeEnd;
    }

    public void setAltSpeedTimeDay(int days) {
        altSpeedTimeDay = days;
    }

    public void setBlocklistEnabled(boolean blocklistEnabled) {
        this.blocklistEnabled = blocklistEnabled;
    }

    public void setBlocklistSize(long blocklistSize) {
        this.blocklistSize = blocklistSize;
    }

    public void setBlocklistURL(String url) {
        blocklistURL = url;
    }

    public void setDhtEnabled(boolean enable) {
        dhtEnabled = enable;
    }

    public void setEncryption(String encryption) {
        this.encryption = encryption;
    }

    public void setCacheSize(long size) {
        cacheSize = size;
    }

    public void setConfigDir(String dir) {
        configDir = dir;
    }

    public void setDownloadDir(String downloadDir) {
        this.downloadDir = downloadDir;
    }

    public void setDownloadDirFreeSpace(long freeSpace) {
        downloadDirFreeSpace = freeSpace;
    }

    public void setDownloadQueueSize(int size) {
        downloadQueueSize = size;
    }

    public void setDownloadQueueEnabled(boolean enable) {
        downloadQueueEnabled = enable;
    }

    public void setIdleSeedingLimig(long limit) {
        idleSeedingLimig = limit;
    }

    public void setIdleSeedingLimitEnabled(boolean enable) {
        idleSeedingLimitEnabled = enable;
    }
    public void setIncompleteDir(String dir) {
        incompleteDir = dir;
    }

    public void setIncompleteDirEnabled(boolean enable) {
        incompleteDirEnabled = enable;
    }

    public void setLocalDiscoveryEnabled(boolean enable) {
        lpdEnabled = enable;
    }

    public void setUtpEnabled(boolean enable) {
        utpEnabled = enable;
    }

    public void setGlobalPeerLimit(int limit) {
        globalPeerLimit = limit;
    }

    public void setTorrentPeerLimit(int limit) {
        torrentPeerLimit = limit;
    }

    public void setPeerExchangeEnabled(boolean enable) {
        pexEnabled = enable;
    }

    public void setPeerPort(int peerPort) {
        this.peerPort = peerPort;
    }

    public void setPeerPortRandomOnStart(boolean peerPortRandomOnStart) {
        this.peerPortRandomOnStart = peerPortRandomOnStart;
    }

    public void setPortForwardingEnabled(boolean portForwardingEnabled) {
        this.portForwardingEnabled = portForwardingEnabled;
    }

    public void setRenamePartialFilesEnabled(boolean rename) {
        renamePartial = rename;
    }

    public void setRPCVersion(int rPCVersion) {
        rpcVersion = rPCVersion;
    }

    public void setRPCVersionMin(int rPCVersionMin) {
        rpcVersionMin = rPCVersionMin;
    }

    public void setDoneScript(String script) {
        doneScript = script;
    }

    public void setDoneScriptEnabled(boolean enabled) {
        doneScriptEnabled = enabled;
    }

    public void setSeedQueueSize(int size) {
        seedQueueSize = size;
    }

    public void setSeedQueueEnabled(boolean enable) {
        seedQueueEnabled = enable;
    }

    public void setSeedRatioLimit(float seedRatioLimit) {
        this.seedRatioLimit = seedRatioLimit;
    }

    public void setSeedRatioLimitEnabled(boolean seedRatioLimited) {
        this.seedRatioLimited = seedRatioLimited;
    }

    public void setDownloadSpeedLimit(long speedLimitDown) {
        this.speedLimitDown = speedLimitDown;
    }

    public void setDownloadSpeedLimitEnabled(boolean speedLimitDownEnabled) {
        this.speedLimitDownEnabled = speedLimitDownEnabled;
    }

    public void setUploadSpeedLimit(long speedLimitUp) {
        this.speedLimitUp = speedLimitUp;
    }

    public void setUploadSpeedLimitEnabled(boolean speedLimitUpEnabled) {
        this.speedLimitUpEnabled = speedLimitUpEnabled;
    }

    public void setStalledQueueSize(int size) {
        stalledQueueSize = size;
    }

    public void setStalledQueueEnabled(boolean enable) {
        stalledQueueEnabled = enable;
    }

    public void setStartAddedTorrentsEnabled(boolean enable) {
        startAdded = enable;
    }

    public void setTrashOriginalTorrentFilesEnabled(boolean enable) {
        trashOriginal = enable;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setDownloadDirectories(TransmissionProfile profile, Set<String> directories) {
        downloadDirectories.clear();
        downloadDirectories.add(downloadDir);
        downloadDirectories.addAll(profile.getDirectories());
        downloadDirectories.addAll(directories);

        downloadDirectories.remove(null);
    }

    public void setDownloadDirectories(Collection<String> directories) {
        downloadDirectories.addAll(directories);
    }

    @JsonIgnore public Set<String> getDownloadDirectories() {
        return downloadDirectories;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel in, int flags) {
        in.writeByte((byte) (altSpeedEnabled ? 1 : 0));
        in.writeLong(altSpeedDown);
        in.writeLong(altSpeedUp);
        in.writeByte((byte) (altSpeedTimeEnabled ? 1 : 0));
        in.writeInt(altSpeedTimeBegin);
        in.writeInt(altSpeedTimeEnd);
        in.writeByte((byte) (blocklistEnabled ? 1 : 0));
        in.writeLong(blocklistSize);
        in.writeString(blocklistURL);
        in.writeByte((byte) (dhtEnabled ? 1 : 0));
        in.writeString(encryption);
        in.writeLong(cacheSize);
        in.writeString(downloadDir);
        in.writeLong(downloadDirFreeSpace);
        in.writeInt(downloadQueueSize);
        in.writeByte((byte) (downloadQueueEnabled ? 1 : 0));
        in.writeLong(idleSeedingLimig);
        in.writeByte((byte) (idleSeedingLimitEnabled ? 1 : 0));
        in.writeString(incompleteDir);
        in.writeByte((byte) (incompleteDirEnabled ? 1 : 0));
        in.writeByte((byte) (lpdEnabled ? 1 : 0));
        in.writeByte((byte) (utpEnabled ? 1 : 0));
        in.writeInt(globalPeerLimit);
        in.writeInt(torrentPeerLimit);
        in.writeByte((byte) (pexEnabled ? 1 : 0));
        in.writeInt(peerPort);
        in.writeByte((byte) (peerPortRandomOnStart ? 1 : 0));
        in.writeByte((byte) (portForwardingEnabled ? 1 : 0));
        in.writeByte((byte) (renamePartial ? 1 : 0));
        in.writeInt(rpcVersion);
        in.writeInt(rpcVersionMin);
        in.writeString(doneScript);
        in.writeByte((byte) (doneScriptEnabled ? 1 : 0));
        in.writeInt(seedQueueSize);
        in.writeByte((byte) (seedQueueEnabled ? 1 : 0));
        in.writeFloat(seedRatioLimit);
        in.writeByte((byte) (seedRatioLimited ? 1 : 0));
        in.writeLong(speedLimitDown);
        in.writeByte((byte) (speedLimitDownEnabled ? 1 : 0));
        in.writeLong(speedLimitUp);
        in.writeByte((byte) (speedLimitUpEnabled ? 1 : 0));
        in.writeInt(stalledQueueSize);
        in.writeByte((byte) (stalledQueueEnabled ? 1 : 0));
        in.writeByte((byte) (startAdded ? 1 : 0));
        in.writeByte((byte) (trashOriginal ? 1 : 0));
        in.writeString(version);
        in.writeStringList(new ArrayList<>(downloadDirectories));
    }

    private TransmissionSession(Parcel in) {
        altSpeedEnabled = in.readByte() == 1;
        altSpeedDown = in.readLong();
        altSpeedUp = in.readLong();
        altSpeedTimeEnabled = in.readByte() == 1;
        altSpeedTimeBegin = in.readInt();
        altSpeedTimeEnd = in.readInt();
        blocklistEnabled = in.readByte() == 1;
        blocklistSize = in.readLong();
        blocklistURL = in.readString();
        dhtEnabled = in.readByte() == 1;
        encryption = in.readString();
        cacheSize = in.readLong();
        downloadDir = in.readString();
        downloadDirFreeSpace = in.readLong();
        downloadQueueSize = in.readInt();
        downloadQueueEnabled = in.readByte() == 1;
        idleSeedingLimig = in.readLong();
        idleSeedingLimitEnabled = in.readByte() == 1;
        incompleteDir = in.readString();
        incompleteDirEnabled = in.readByte() == 1;
        lpdEnabled = in.readByte() == 1;
        utpEnabled = in.readByte() == 1;
        globalPeerLimit = in.readInt();
        torrentPeerLimit = in.readInt();
        pexEnabled = in.readByte() == 1;
        peerPort = in.readInt();
        peerPortRandomOnStart = in.readByte() == 1;
        portForwardingEnabled = in.readByte() == 1;
        renamePartial = in.readByte() == 1;
        rpcVersion = in.readInt();
        rpcVersionMin = in.readInt();
        doneScript = in.readString();
        doneScriptEnabled = in.readByte() == 1;
        seedQueueSize = in.readInt();
        seedQueueEnabled = in.readByte() == 1;
        seedRatioLimit = in.readFloat();
        seedRatioLimited = in.readByte() == 1;
        speedLimitDown = in.readLong();
        speedLimitDownEnabled = in.readByte() == 1;
        speedLimitUp = in.readLong();
        speedLimitUpEnabled = in.readByte() == 1;
        stalledQueueSize = in.readInt();
        stalledQueueEnabled = in.readByte() == 1;
        startAdded = in.readByte() == 1;
        trashOriginal = in.readByte() == 1;
        version = in.readString();
        downloadDirectories = new HashSet<String>(in.createStringArrayList());
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
