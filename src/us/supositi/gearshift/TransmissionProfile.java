package us.supositi.gearshift;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;

public class TransmissionProfile implements Parcelable, Comparable<TransmissionProfile> {
    public static final String PREF_PROFILES = "profiles";
    public static final String PREF_CURRENT_PROFILE = "default_profiles";
    
    public static final String PREF_NAME = "profile_name";
    public static final String PREF_HOST = "profile_host";
    public static final String PREF_PORT = "profile_port";
    public static final String PREF_PATH = "profile_path";
    public static final String PREF_USER = "profile_username";
    public static final String PREF_PASS = "profile_password";
    public static final String PREF_SSL = "profile_use_ssl";
    public static final String PREF_TIMEOUT = "profile_timeout";
    public static final String PREF_RETRIES = "profile_retries";
    public static final String PREF_PREFIX = "profile_";

    
    private final String mId;
    private String mName = "";
    private String mHost = "";
    private int mPort = 9091;
    private String mPath = "/transmission/rpc";
    private String mUsername = "";
    private String mPassword = "";
    
    private boolean mUseSSL = false;
    
    private int mTimeout = 40;
    private int mRetries = 3;

    public static TransmissionProfile[] readProfiles(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> profile_ids = prefs.getStringSet(PREF_PROFILES, new HashSet<String>());
        TransmissionProfile[] profiles = new TransmissionProfile[profile_ids.size()];
        int index = 0;
        
        for (String id : profile_ids)
            profiles[index++] = new TransmissionProfile(id, context);
        
        Arrays.sort(profiles);
        return profiles;
    }
    
    public static String getCurrentProfileId(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(PREF_CURRENT_PROFILE, null);
    }    

    public static void setCurrentProfile(TransmissionProfile profile, Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Editor e = prefs.edit();
        
        e.putString(PREF_CURRENT_PROFILE, profile.getId());
        e.commit();
    }
    
    public TransmissionProfile() {
        mId = UUID.randomUUID().toString();
    }    
    
    public TransmissionProfile(String id, Context context) {
        mId = id;
        
        load(context);
    }
    
    public String getId() {
        return mId;        
    }
    
    public String getName() {
        return mName;
    }
    public String getHost() {
        return mHost;
    }
    public int getPort() {
        return mPort;
    }
    public String getPath() {
        return mPath;
    }
    public String getUsername() {
        return mUsername;
    }
    public String getPassword() {
        return mPassword;
    }
    public boolean isUseSSL() {
        return mUseSSL;
    }
    public int getTimeout() {
        return mTimeout;
    }
    public int getRetries() {
        return mRetries;
    }
    public void setName(String name) {
        mName = name;
    }
    public void setHost(String host) {
        mHost = host;
    }
    public void setPort(int port) {
        this.mPort = port;
    }
    public void setPath(String path) {
        mPath = path;
    }
    public void setUsername(String username) {
        mUsername = username;
    }
    public void setPassword(String password) {
        mPassword = password;
    }
    public void setUseSSL(boolean useSSL) {
        mUseSSL = useSSL;
    }
    public void setTimeout(int timeout) {
        mTimeout = timeout;
    }
    public void setRetries(int retries) {
        mRetries = retries;
    }
    
    public void load(Context context) {
        SharedPreferences pref = context.getSharedPreferences(
                PREF_PREFIX + mId, Activity.MODE_PRIVATE);
        
        load(pref);
    }
    
    public void load(SharedPreferences pref) {
        mName = pref.getString(PREF_NAME, "").trim();
        mHost = pref.getString(PREF_HOST, "").trim();
        mPort = Integer.parseInt(pref.getString(PREF_PORT, "-1"));
        mPath = pref.getString(PREF_PATH, "").trim();
        mUsername = pref.getString(PREF_USER, "").trim();
        mPassword = pref.getString(PREF_PASS, "").trim();
        mUseSSL = pref.getBoolean(PREF_SSL, false);
        mTimeout = Integer.parseInt(pref.getString(PREF_TIMEOUT, "-1"));
        mRetries = Integer.parseInt(pref.getString(PREF_RETRIES, "-1"));

        TorrentListActivity.logD(
            "Loading profile from prefs: id {0}, name {1}, host {2}, port {3}   ",
            new Object[] {mId, mName, mHost, mPort});
    }
    
    public void save(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF_PREFIX + mId, Activity.MODE_PRIVATE);
        Editor e = pref.edit();
        
        e.putString(PREF_NAME, mName);
        e.putString(PREF_HOST, mHost);
        e.putString(PREF_PORT, Integer.toString(mPort));
        e.putString(PREF_PATH, mPath);
        e.putString(PREF_USER, mUsername);
        e.putString(PREF_PASS, mPassword);
        e.putBoolean(PREF_SSL, mUseSSL);
        e.putString(PREF_TIMEOUT, Integer.toString(mTimeout));
        e.putString(PREF_RETRIES, Integer.toString(mRetries));
        
        e.commit();
        
        TorrentListActivity.logD(
            "Saving profile to prefs: id {0}, name {1}, host {2}, port {3}",
            new Object[] {mId, mName, mHost, mPort});
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        e = prefs.edit();
        
        Set<String> ids = prefs.getStringSet(PREF_PROFILES, new HashSet<String>());
        
        if (ids.add(mId)) {
            e.remove(PREF_PROFILES);
            e.commit();
            
            if (ids.size() == 1 && !prefs.getString(PREF_CURRENT_PROFILE, "").equals(mId)) {
                e.putString(PREF_CURRENT_PROFILE, mId);
            }
            e.putStringSet(PREF_PROFILES, ids);
            e.commit();
            
            TorrentListActivity.logD(
                    "Adding the profile {0} to the set of profiles",
                    new Object[] {mId});
        }
    }
    
    public void delete(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF_PREFIX + mId, Activity.MODE_PRIVATE);
        Editor e = pref.edit();
        
        e.clear();
        e.commit();
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        e = prefs.edit();

        Set<String> ids = prefs.getStringSet(PREF_PROFILES, new HashSet<String>());
        
        if (ids.remove(mId)) {
            e.remove(PREF_PROFILES);
            e.commit();
            e.putStringSet(PREF_PROFILES, ids);
            e.commit();
            
            TorrentListActivity.logD(
                    "Removing the profile {0} from the set of profiles",
                    new Object[] {mId});
        }
        
        TorrentListActivity.logD(
                "Deleting profile from prefs: id {0}, name {1}, host {2}, port {3}",
                new Object[] {mId, mName, mHost, mPort});
    }

    @Override
    public int compareTo(TransmissionProfile another) {
        return mName.compareToIgnoreCase(another.getName());
    }
    
    @Override
    public String toString() {
        return mName + "://" + mUsername + '@' + mHost + ':' + mPort;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel in, int flags) {
        in.writeString(mId);
        in.writeString(mName);
        in.writeString(mHost);
        in.writeInt(mPort);
        in.writeString(mUsername);
        in.writeString(mPassword);
        in.writeInt(mUseSSL ? 1 : 0);
        in.writeInt(mTimeout);
        in.writeInt(mRetries);
    }
    
    public static final Parcelable.Creator<TransmissionProfile> CREATOR
        = new Parcelable.Creator<TransmissionProfile>() {
      public TransmissionProfile createFromParcel(Parcel in) {
          return new TransmissionProfile(in);
      }
      
      public TransmissionProfile[] newArray(int size) {
          return new TransmissionProfile[size];
      }
    };
    
    private TransmissionProfile(Parcel in) {
        mId = in.readString();
        mName = in.readString();
        mHost = in.readString();
        mPort = in.readInt();
        mUsername = in.readString();
        mPassword = in.readString();
        mUseSSL = (in.readInt() == 1 ? true : false);
        mTimeout = in.readInt();
        mRetries = in.readInt();
    }
}
