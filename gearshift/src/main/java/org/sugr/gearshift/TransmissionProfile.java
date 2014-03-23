package org.sugr.gearshift;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;

import java.io.File;
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

    private Context context;

    public static TransmissionProfile[] readProfiles(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> profile_ids = prefs.getStringSet(G.PREF_PROFILES, new HashSet<String>());
        TransmissionProfile[] profiles = new TransmissionProfile[profile_ids.size()];
        int index = 0;

        for (String id : profile_ids) {
            profiles[index++] = new TransmissionProfile(id, context);
        }

        Arrays.sort(profiles);
        return profiles;
    }

    public static String getCurrentProfileId(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return getCurrentProfileId(prefs);
    }

    public static String getCurrentProfileId(SharedPreferences prefs) {
        return prefs.getString(G.PREF_CURRENT_PROFILE, null);
    }

    public static void setCurrentProfile(TransmissionProfile profile, Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        setCurrentProfile(profile, prefs);
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

        e.remove(G.PREF_NAME);
        e.remove(G.PREF_HOST);
        e.remove(G.PREF_PORT);
        e.remove(G.PREF_PATH);
        e.remove(G.PREF_USER);
        e.remove(G.PREF_PASS);
        e.remove(G.PREF_SSL);
        e.remove(G.PREF_TIMEOUT);
        e.remove(G.PREF_RETRIES);
        e.remove(G.PREF_DIRECTORIES);

        e.apply();
    }

    public TransmissionProfile(Context context) {
        id = generateId();

        this.context = context;
    }

    public TransmissionProfile(String id, Context context) {
        this.id = id;

        this.context = context;
        load();
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
    
    public void load() {
        load(false);
    }

    public void load(boolean fromPreferences) {
        SharedPreferences pref = getPreferences();
        boolean legacy = false;
        String dir = context.getFilesDir().getParent() + "/shared_prefs";
        File legacyFile = new File(dir, G.PREF_PREFIX + id + ".xml");

        if (!fromPreferences && pref.getString(G.PREF_NAME + id, null) == null && legacyFile.exists()) {
            legacy = true;
            pref = getLegacyPreferences(context);
        }

        name = pref.getString(getPrefName(G.PREF_NAME, legacy, fromPreferences), "").trim();
        host = pref.getString(getPrefName(G.PREF_HOST, legacy, fromPreferences), "").trim();
        try {
             port = Integer.parseInt(pref.getString(getPrefName(G.PREF_PORT, legacy, fromPreferences), "9091"));
            if ( port < 1) {
                 port = 1;
            } else if ( port > 65535) {
                 port = 65535;
            }
        } catch (NumberFormatException e) {
             port = 65535;
        }
        path = pref.getString(getPrefName(G.PREF_PATH, legacy, fromPreferences), "").trim();
        username = pref.getString(getPrefName(G.PREF_USER, legacy, fromPreferences), "").trim();
        password = pref.getString(getPrefName(G.PREF_PASS, legacy, fromPreferences), "").trim();
        useSSL = pref.getBoolean(getPrefName(G.PREF_SSL, legacy, fromPreferences), false);
        try {
            timeout = Integer.parseInt(pref.getString(getPrefName(G.PREF_TIMEOUT, legacy, fromPreferences), "-1"));
        } catch (NumberFormatException e) {
            timeout = Integer.MAX_VALUE;
        }
        try {
            retries = Integer.parseInt(pref.getString(getPrefName(G.PREF_RETRIES, legacy, fromPreferences), "-1"));
        } catch (NumberFormatException e) {
            retries = Integer.MAX_VALUE;
        }
        directories = pref.getStringSet(getPrefName(G.PREF_DIRECTORIES, legacy, fromPreferences), new HashSet<String>());

        lastDirectory = pref.getString(getPrefName(G.PREF_LAST_DIRECTORY, legacy, fromPreferences), "");
        moveData = pref.getBoolean(getPrefName(G.PREF_MOVE_DATA, legacy, fromPreferences), true);
        deleteLocal = pref.getBoolean(getPrefName(G.PREF_DELETE_LOCAL, legacy, fromPreferences), false);
        startPaused = pref.getBoolean(getPrefName(G.PREF_START_PAUSED, legacy, fromPreferences), false);

        if (legacy) {
            Editor e = pref.edit();
            e.clear();
            e.commit();

            //noinspection ResultOfMethodCallIgnored
            legacyFile.delete();

            File file = new File(dir, G.PREF_PREFIX + id + ".bak");
            if (file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }

            save();
        }
    }

    public void save() {
        save(false);
    }

    public void save(boolean fromPreferences) {
        if (fromPreferences) {
            load(true);
        }

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

        e.commit();

        G.logD("Saving profile to prefs: id %s, name %s, host %s, port %d",
            new Object[] {id, name, host, port});

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        e = prefs.edit();

        Set<String> ids = prefs.getStringSet(G.PREF_PROFILES, new HashSet<String>());

        if (ids.add(id)) {
            e.remove(G.PREF_PROFILES);
            e.commit();

            if (ids.size() == 1 && !prefs.getString(G.PREF_CURRENT_PROFILE, "").equals(id)) {
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

        e.remove(G.PREF_NAME + id);
        e.remove(G.PREF_HOST + id);
        e.remove(G.PREF_PORT + id);
        e.remove(G.PREF_PATH + id);
        e.remove(G.PREF_USER + id);
        e.remove(G.PREF_PASS + id);
        e.remove(G.PREF_SSL + id);
        e.remove(G.PREF_TIMEOUT + id);
        e.remove(G.PREF_RETRIES + id);
        e.remove(G.PREF_DIRECTORIES + id);

        e.commit();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        e = prefs.edit();

        Set<String> ids = prefs.getStringSet(G.PREF_PROFILES, new HashSet<String>());

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
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel in, int flags) {
        in.writeString(id);
        in.writeString(name);
        in.writeString(host);
        in.writeInt(port);
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
    }

    private SharedPreferences getLegacyPreferences(Context context) {
        return context.getSharedPreferences(
                G.PREF_PREFIX + id, Activity.MODE_PRIVATE);
    }

    private String getPrefName(String name, boolean legacy, boolean fromPreferences) {
        if (legacy) {
            return name;
        } else if (fromPreferences) {
            return name;
        } else {
            return  name + id;
        }
    }

    private SharedPreferences getPreferences() {
        return context.getSharedPreferences(
            getPreferencesName(), Activity.MODE_PRIVATE);
    }

}