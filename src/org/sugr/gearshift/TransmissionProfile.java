package org.sugr.gearshift;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TransmissionProfile implements Parcelable, Comparable<TransmissionProfile> {
    private final String mId;
    private String mName = "";
    private String mHost = "";
    private int mPort = 9091;
    private String mPath = "/transmission/rpc";
    private String mUsername = "";
    private String mPassword = "";
    private String mLastDirectory;

    private boolean mUseSSL = false;

    private int mTimeout = 40;
    private int mRetries = 3;

    private Set<String> mDirectories = new HashSet<String>();

    private Context mContext;

    public static TransmissionProfile[] readProfiles(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> profile_ids = prefs.getStringSet(G.PREF_PROFILES, new HashSet<String>());
        TransmissionProfile[] profiles = new TransmissionProfile[profile_ids.size()];
        int index = 0;

        for (String id : profile_ids)
            profiles[index++] = new TransmissionProfile(id, context);

        Arrays.sort(profiles);
        return profiles;
    }

    public static String getCurrentProfileId(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(G.PREF_CURRENT_PROFILE, null);
    }

    public static void setCurrentProfile(TransmissionProfile profile, Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Editor e = prefs.edit();

        e.putString(G.PREF_CURRENT_PROFILE, profile.getId());
        e.commit();
    }

    public TransmissionProfile(Context context) {
        mId = UUID.randomUUID().toString();

        mContext = context;
    }

    public TransmissionProfile(String id, Context context) {
        mId = id;

        mContext = context;
        load();
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
    public Set<String> getDirectories() {
        return mDirectories;
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
    public void setDirectories(Set<String> directories) {
        mDirectories = directories;
    }

    public SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(
                G.PREF_PREFIX + mId, Activity.MODE_PRIVATE);
    }

    public void load() {
        load(getPreferences(mContext));
    }

    public void load(SharedPreferences pref) {
        mName = pref.getString(G.PREF_NAME, "").trim();
        mHost = pref.getString(G.PREF_HOST, "").trim();
        try {
            mPort = Integer.parseInt(pref.getString(G.PREF_PORT, "9091"));
            if (mPort < 1) mPort = 1;
            else if (mPort > 65535) mPort = 65535;
        } catch (NumberFormatException e) {
            mPort = 65535;
        }
        mPath = pref.getString(G.PREF_PATH, "").trim();
        mUsername = pref.getString(G.PREF_USER, "").trim();
        mPassword = pref.getString(G.PREF_PASS, "").trim();
        mUseSSL = pref.getBoolean(G.PREF_SSL, false);
        try {
            mTimeout = Integer.parseInt(pref.getString(G.PREF_TIMEOUT, "-1"));
        } catch (NumberFormatException e) {
            mTimeout = Integer.MAX_VALUE;
        }
        try {
            mRetries = Integer.parseInt(pref.getString(G.PREF_RETRIES, "-1"));
        } catch (NumberFormatException e) {
            mRetries = Integer.MAX_VALUE;
        }
        mDirectories = pref.getStringSet(G.PREF_DIRECTORIES, new HashSet<String>());

        mLastDirectory = pref.getString(G.PREF_LAST_DIRECTORY, "");

        G.logD("Loading profile from prefs: id %s, name %s, host %s, port %d   ",
            new Object[] {mId, mName, mHost, mPort});
    }

    public void save() {
        SharedPreferences pref = getPreferences(mContext);
        Editor e = pref.edit();

        e.putString(G.PREF_NAME, mName);
        e.putString(G.PREF_HOST, mHost);
        e.putString(G.PREF_PORT, Integer.toString(mPort));
        e.putString(G.PREF_PATH, mPath);
        e.putString(G.PREF_USER, mUsername);
        e.putString(G.PREF_PASS, mPassword);
        e.putBoolean(G.PREF_SSL, mUseSSL);
        e.putString(G.PREF_TIMEOUT, Integer.toString(mTimeout));
        e.putString(G.PREF_RETRIES, Integer.toString(mRetries));
        e.putStringSet(G.PREF_DIRECTORIES, mDirectories);

        e.commit();

        G.logD("Saving profile to prefs: id %s, name %s, host %s, port %d",
            new Object[] {mId, mName, mHost, mPort});

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        e = prefs.edit();

        Set<String> ids = prefs.getStringSet(G.PREF_PROFILES, new HashSet<String>());

        if (ids.add(mId)) {
            e.remove(G.PREF_PROFILES);
            e.commit();

            if (ids.size() == 1 && !prefs.getString(G.PREF_CURRENT_PROFILE, "").equals(mId)) {
                e.putString(G.PREF_CURRENT_PROFILE, mId);
            }
            e.putStringSet(G.PREF_PROFILES, ids);
            e.commit();

            G.logD("Adding the profile %s to the set of profiles",
                    new Object[] {mId});
        }
    }

    public void delete() {
        SharedPreferences pref = getPreferences(mContext);
        Editor e = pref.edit();

        e.clear();
        e.commit();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        e = prefs.edit();

        Set<String> ids = prefs.getStringSet(G.PREF_PROFILES, new HashSet<String>());

        if (ids.remove(mId)) {
            e.remove(G.PREF_PROFILES);
            e.commit();
            e.putStringSet(G.PREF_PROFILES, ids);
            e.commit();

            G.logD("Removing the profile %s from the set of profiles",
                    new Object[] {mId});
        }

        G.logD("Deleting profile from prefs: id %s, name %s, host %s, port %d",
                new Object[] {mId, mName, mHost, mPort});
    }

    public String getLastDownloadDirectory() {
        return mLastDirectory;
    }

    public void setLastDownloadDirectory(String directory) {
        SharedPreferences pref = getPreferences(mContext);
        Editor e = pref.edit();

        mLastDirectory = directory;

        e.putString(G.PREF_LAST_DIRECTORY, directory);
        e.commit();
    }

    public Context getContext() {
        return mContext;
    }

    public void setContext(Context context) {
        mContext = context;
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
        in.writeStringList(new ArrayList<String>(mDirectories));
        in.writeString(mLastDirectory);
    }

    public static final Parcelable.Creator<TransmissionProfile> CREATOR
            = new Parcelable.Creator<TransmissionProfile>() {
        @Override
        public TransmissionProfile createFromParcel(Parcel in) {
            return new TransmissionProfile(in);
        }

        @Override
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

        ArrayList<String> directories = new ArrayList<String>();
        in.readStringList(directories);
        mDirectories = new HashSet<String>(directories);
        mLastDirectory = in.readString();
    }
}
