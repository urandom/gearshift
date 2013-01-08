package us.supositi.gearshift;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

public class TorrentProfile {
    private String mName;
    private String mHost;
    private int mPort;
    private String mUsername;
    private String mPassword;
    
    private boolean mUseSSL = false;
    
    private int mTimeout = 40;
    private int mRetries = 3;
    
    public static TorrentProfile[] readProfiles(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String jsonProfiles = prefs.getString("profiles", null);
        
        if (jsonProfiles == null)
            return new TorrentProfile[0];
        
        Gson gson = new GsonBuilder().create();
        
        TorrentProfile[] profiles = gson.fromJson(jsonProfiles, TorrentProfile[].class);
        
        return profiles;
    }
    
    public static void writeProfiles(Context context, TorrentProfile[] profiles) {
        Gson gson = new GsonBuilder().create();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Editor e = prefs.edit();
        
        e.putString("profiles", gson.toJson(profiles));
        e.commit();
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
    public void setName(String mName) {
        this.mName = mName;
    }
    public void setHost(String mHost) {
        this.mHost = mHost;
    }
    public void setPort(int mPort) {
        this.mPort = mPort;
    }
    public void setUsername(String mUsername) {
        this.mUsername = mUsername;
    }
    public void setPassword(String mPassword) {
        this.mPassword = mPassword;
    }
    public void setUseSSL(boolean mUseSSL) {
        this.mUseSSL = mUseSSL;
    }
    public void setTimeout(int mTimeout) {
        this.mTimeout = mTimeout;
    }
    public void setRetries(int mRetries) {
        this.mRetries = mRetries;
    }
}
