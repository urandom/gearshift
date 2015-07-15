package org.sugr.gearshift.core;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import org.sugr.gearshift.G;
import org.sugr.gearshift.ui.util.Colorizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TransmissionProfile implements Parcelable, Comparable<TransmissionProfile> {
    private String id;
    private String name = "";
    private String host = "";
    private int port = 9091;
    private String path = "/transmission/rpc";
    private String username = "";
    private String password = "";

    private boolean useSSL = false;

    private int timeout = 40;
    private int retries = 3;

    private String lastDirectory;
    private boolean moveData = true;
    private boolean deleteLocal = false;
    private boolean startPaused = false;

    private Set<String> directories = new HashSet<>();

    private boolean useProxy = false;
    private String proxyHost = "";
    private int proxyPort = 8080;

    private int color;

    private Context context;
    private SharedPreferences defaultPrefs;

    public static TransmissionProfile[] readProfiles(Context context, SharedPreferences prefs) {
        Set<String> profile_ids = prefs.getStringSet(G.PREF_PROFILES, new HashSet<String>());
        TransmissionProfile[] profiles = new TransmissionProfile[profile_ids.size()];
        int index = 0;

        for (String id : profile_ids) {
            profiles[index++] = new TransmissionProfile(id, context, prefs);
        }

        Arrays.sort(profiles);
        return profiles;
    }

    public static String getCurrentProfileId(SharedPreferences prefs) {
        return prefs.getString(G.PREF_CURRENT_PROFILE, null);
    }

    public static void setCurrentProfile(TransmissionProfile profile, SharedPreferences prefs) {
        Editor e = prefs.edit();

        if (profile == null) {
            e.putString(G.PREF_CURRENT_PROFILE, null);
        } else {
            e.putString(G.PREF_CURRENT_PROFILE, profile.getId());
        }
        e.commit();
    }

    public static String generateId() {
        return UUID.randomUUID().toString();
    }

    public static String getPreferencesName() {
        return G.PROFILES_PREF_NAME;
    }

    public static void cleanTemporaryPreferences(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(getPreferencesName(),
            Activity.MODE_PRIVATE);
        Editor e = prefs.edit();

        for (String key : G.UNPREFIXED_PROFILE_PREFERENCE_KEYS) {
            e.remove(key);
        }

        e.apply();
    }

    public TransmissionProfile(Context context, SharedPreferences prefs) {
        id = generateId();

        this.context = context;
        this.defaultPrefs = prefs;
    }

    public TransmissionProfile(String id, Context context, SharedPreferences prefs) {
        this.id = id;

        this.context = context;
        this.defaultPrefs = prefs;
        load();
    }

    public boolean isValid() {
        return !TextUtils.isEmpty(name) && (!TextUtils.isEmpty(host) && !host.equals("example.com")) &&
                (!useProxy || ((!TextUtils.isEmpty(proxyHost) && !proxyHost.equals("example.com")) &&
                        proxyPort > 0 && proxyPort < 65535));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
    public String getHost() {
        return host;
    }
    public int getPort() {
        return port;
    }
    public String getPath() {
        return path;
    }
    public String getUsername() {
        return username;
    }
    public String getPassword() {
        return password;
    }
    public boolean isUseSSL() {
        return useSSL;
    }
    public int getTimeout() {
        return timeout;
    }
    public int getRetries() {
        return retries;
    }
    public Set<String> getDirectories() {
        return directories;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setHost(String host) {
        this.host = host;
    }
    public void setPort(int port) {
        this.port = port;
    }
    public void setPath(String path) {
        this.path = path;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public void setUseSSL(boolean useSSL) {
        this.useSSL = useSSL;
    }
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    public void setRetries(int retries) {
        this.retries = retries;
    }
    public void setDirectories(Set<String> directories) {
        this.directories = directories;
    }

    public boolean isUseProxy() {
        return useProxy;
    }

    public void setUseProxy(boolean useProxy) {
        this.useProxy = useProxy;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String host) {
        this.proxyHost = host;
    }

    public void setProxyPort(int port) {
        this.proxyPort = port;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void load() {
        SharedPreferences pref = getPreferences();

        name = pref.getString(G.PREF_NAME + id, "").trim();
        host = pref.getString(G.PREF_HOST + id, "").trim();
        try {
             port = Integer.parseInt(pref.getString(G.PREF_PORT + id, "9091"));
            if (port < 1) {
                 port = 1;
            } else if ( port > 65535) {
                 port = 65535;
            }
        } catch (NumberFormatException e) {
             port = 65535;
        }
        path = pref.getString(G.PREF_PATH + id, "").trim();
        username = pref.getString(G.PREF_USER + id, "").trim();
        password = pref.getString(G.PREF_PASS + id, "").trim();
        useSSL = pref.getBoolean(G.PREF_SSL + id, false);
        try {
            timeout = Integer.parseInt(pref.getString(G.PREF_TIMEOUT + id, "-1"));
        } catch (NumberFormatException e) {
            timeout = Integer.MAX_VALUE;
        }
        try {
            retries = Integer.parseInt(pref.getString(G.PREF_RETRIES + id, "-1"));
        } catch (NumberFormatException e) {
            retries = Integer.MAX_VALUE;
        }
        directories = pref.getStringSet(G.PREF_DIRECTORIES + id, new HashSet<String>());

        lastDirectory = pref.getString(G.PREF_LAST_DIRECTORY + id, "");
        moveData = pref.getBoolean(G.PREF_MOVE_DATA + id, true);
        deleteLocal = pref.getBoolean(G.PREF_DELETE_LOCAL + id, false);
        startPaused = pref.getBoolean(G.PREF_START_PAUSED + id, false);

        useProxy = pref.getBoolean(G.PREF_PROXY + id, false);
        proxyHost = pref.getString(G.PREF_PROXY_HOST + id, "").trim();
        try {
            proxyPort = Integer.parseInt(pref.getString(G.PREF_PROXY_PORT + id, "8080"));
            if (proxyPort < 1) {
                proxyPort = 1;
            } else if ( proxyPort > 65535) {
                proxyPort = 65535;
            }
        } catch (NumberFormatException e) {
            proxyPort = 65535;
        }

        color = pref.getInt(G.PREF_COLOR + id, 0);
        if (color == 0) {
            color = Colorizer.defaultColor(context);
        }
    }

    public void save() {
        SharedPreferences pref = getPreferences();
        Editor e = pref.edit();

        e.putString(G.PREF_NAME + id, name);
        e.putString(G.PREF_HOST + id, host);
        e.putString(G.PREF_PORT + id, Integer.toString(port));
        e.putString(G.PREF_PATH + id, path);
        e.putString(G.PREF_USER + id, username);
        e.putString(G.PREF_PASS + id, password);
        e.putBoolean(G.PREF_SSL + id, useSSL);
        e.putString(G.PREF_TIMEOUT + id, Integer.toString(timeout));
        e.putString(G.PREF_RETRIES + id, Integer.toString(retries));
        e.putStringSet(G.PREF_DIRECTORIES + id, directories);
        e.putBoolean(G.PREF_PROXY + id, useProxy);
        e.putString(G.PREF_PROXY_HOST + id, proxyHost);
        e.putString(G.PREF_PROXY_PORT + id, Integer.toString(proxyPort));
        e.putInt(G.PREF_COLOR + id, color);

        e.commit();

        G.logD("Saving profile to prefs: id %s, name %s, host %s, port %d",
            new Object[] {id, name, host, port});

        e = defaultPrefs.edit();

        Set<String> ids = defaultPrefs.getStringSet(G.PREF_PROFILES, new HashSet<String>());

        if (ids.add(id)) {
            e.remove(G.PREF_PROFILES);
            e.commit();

            if (ids.size() == 1 && !defaultPrefs.getString(G.PREF_CURRENT_PROFILE, "").equals(id)) {
                e.putString(G.PREF_CURRENT_PROFILE, id);
            }
            e.putStringSet(G.PREF_PROFILES, ids);
            e.commit();

            G.logD("Adding the profile %s to the set of profiles",
                    new Object[] {id});
        }
    }

    public void delete() {
        SharedPreferences pref = getPreferences();
        Editor e = pref.edit();

        for (String key : G.UNPREFIXED_PROFILE_PREFERENCE_KEYS) {
            e.remove(key + id);
        }

        e.commit();

        e = defaultPrefs.edit();

        Set<String> ids = defaultPrefs.getStringSet(G.PREF_PROFILES, new HashSet<String>());

        if (ids.remove(id)) {
            e.remove(G.PREF_PROFILES);
            e.commit();
            e.putStringSet(G.PREF_PROFILES, ids);
            e.commit();

            G.logD("Removing the profile %s from the set of profiles",
                    new Object[] {id});
        }

        G.logD("Deleting profile from prefs: id %s, name %s, host %s, port %d",
                new Object[] {id, name, host, port});
    }

    public void fillTemporatyPreferences() {
        SharedPreferences pref = getPreferences();
        Editor e = pref.edit();

        e.putString(G.PREF_NAME, name);
        e.putString(G.PREF_HOST, host);
        e.putString(G.PREF_PORT, Integer.toString(port));
        e.putString(G.PREF_PATH, path);
        e.putString(G.PREF_USER, username);
        e.putString(G.PREF_PASS, password);
        e.putBoolean(G.PREF_SSL, useSSL);
        e.putString(G.PREF_TIMEOUT, Integer.toString(timeout));
        e.putString(G.PREF_RETRIES, Integer.toString(retries));
        e.putStringSet(G.PREF_DIRECTORIES, directories);
        e.putBoolean(G.PREF_PROXY, useProxy);
        e.putString(G.PREF_PROXY_HOST, proxyHost);
        e.putString(G.PREF_PROXY_PORT, Integer.toString(proxyPort));
        e.putInt(G.PREF_COLOR, color);

        e.apply();
    }

    public String getLastDownloadDirectory() {
        return lastDirectory;
    }

    public void setLastDownloadDirectory(String directory) {
        SharedPreferences pref = getPreferences();
        Editor e = pref.edit();

        lastDirectory = directory;

        e.putString(G.PREF_LAST_DIRECTORY + id, directory);
        e.apply();
    }

    public boolean getMoveData() {
        return moveData;
    }

    public void setMoveData(boolean move) {
        SharedPreferences pref = getPreferences();
        Editor e = pref.edit();

        moveData = move;

        e.putBoolean(G.PREF_MOVE_DATA + id, move);
        e.apply();
    }

    public boolean getDeleteLocal() {
        return deleteLocal;
    }

    public void setDeleteLocal(boolean delete) {
        SharedPreferences pref = getPreferences();
        Editor e = pref.edit();

        deleteLocal = delete;

        e.putBoolean(G.PREF_DELETE_LOCAL + id, delete);
        e.apply();
    }

    public boolean getStartPaused() {
        return startPaused;
    }

    public void setStartPaused(boolean paused) {
        SharedPreferences pref = getPreferences();
        Editor e = pref.edit();

        startPaused = paused;

        e.putBoolean(G.PREF_START_PAUSED + id, paused);
        e.commit();
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    @Override public int compareTo(TransmissionProfile another) {
        return name.compareToIgnoreCase(another.getName());
    }

    @Override public String toString() {
        return name + "://" + username + '@' + host + ':' + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TransmissionProfile that = (TransmissionProfile) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel in, int flags) {
        in.writeString(id);
        in.writeString(name);
        in.writeString(host);
        in.writeInt(port);
        in.writeString(path);
        in.writeString(username);
        in.writeString(password);
        in.writeInt(useSSL ? 1 : 0);
        in.writeInt(timeout);
        in.writeInt(retries);
        in.writeStringList(new ArrayList<>(directories));
        in.writeString(lastDirectory);
        in.writeInt(moveData ? 1 : 0);
        in.writeInt(deleteLocal ? 1 : 0);
        in.writeInt(startPaused ? 1 : 0);
        in.writeInt(useProxy ? 1 : 0);
        in.writeString(proxyHost);
        in.writeInt(proxyPort);
        in.writeInt(color);
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
        id = in.readString();
        name = in.readString();
        host = in.readString();
        port = in.readInt();
        path = in.readString();
        username = in.readString();
        password = in.readString();
        useSSL = in.readInt() == 1;
        timeout = in.readInt();
        retries = in.readInt();

        ArrayList<String> directories = new ArrayList<>();
        in.readStringList(directories);
        this.directories = new HashSet<>(directories);
        lastDirectory = in.readString();

        moveData = in.readInt() == 1;
        deleteLocal = in.readInt() == 1;
        startPaused = in.readInt() == 1;
        useProxy = in.readInt() == 1;
        proxyHost = in.readString();
        proxyPort = in.readInt();
        color = in.readInt();
    }

    private SharedPreferences getPreferences() {
        return context.getSharedPreferences(
            getPreferencesName(), Activity.MODE_PRIVATE);
    }

}
