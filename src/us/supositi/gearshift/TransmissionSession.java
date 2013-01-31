package us.supositi.gearshift;

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
}
