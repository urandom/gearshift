package us.supositi.gearshift;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;

public class TorrentProfile implements Parcelable {
    private String mName;
    private String mHost;
    private int mPort = 9091;
    private String mPath = "/transmission/rpc";
    private String mUsername;
    private String mPassword;
    
    private boolean mUseSSL = false;
    
    private int mTimeout = 40;
    private int mRetries = 3;
    
    public static TorrentProfile[] readProfiles(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String jsonProfiles = prefs.getString("profiles", null);
        
        if (jsonProfiles == null) {
            TorrentProfile profile = new TorrentProfile();
            profile = new TorrentProfile();
            profile.setName("Test Profile");
            profile.setUsername("User");
            profile.setPassword("testP4ssw0rd");
            profile.setHost("example.com");
            profile.setPort(2901);
            return new TorrentProfile[] {profile};
        }
        
        Gson gson = new GsonBuilder().create();
        
        TorrentProfile[] profiles = gson.fromJson(jsonProfiles, TorrentProfile[].class);
        
        return profiles;
    }
    
    public static void writeProfiles(Context context,TorrentProfile[] profiles) {
        Gson gson = new GsonBuilder().create();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Editor e = prefs.edit();
        
        e.putString("profiles", gson.toJson(profiles));
        e.commit();
    }
    
    public TorrentProfile() {
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
    
    @Override
    public String toString() {
        return getName() + "://" + getUsername() + '@' + getHost() + ':' + getPort();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel in, int flags) {
        in.writeString(mName);
        in.writeString(mHost);
        in.writeInt(mPort);
        in.writeString(mUsername);
        in.writeString(mPassword);
        in.writeInt(mUseSSL ? 1 : 0);
        in.writeInt(mTimeout);
        in.writeInt(mRetries);
    }
    
    public static final Parcelable.Creator<TorrentProfile> CREATOR
        = new Parcelable.Creator<TorrentProfile>() {
      public TorrentProfile createFromParcel(Parcel in) {
          return new TorrentProfile(in);
      }
      
      public TorrentProfile[] newArray(int size) {
          return new TorrentProfile[size];
      }
    };
    
    private TorrentProfile(Parcel in) {
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
