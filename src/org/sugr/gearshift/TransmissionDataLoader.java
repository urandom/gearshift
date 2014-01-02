package org.sugr.gearshift;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.AsyncTaskLoader;

import org.sugr.gearshift.TransmissionSessionManager.ManagerException;
import org.sugr.gearshift.datasource.DataSource;
import org.sugr.gearshift.datasource.TorrentStatus;

import java.io.File;
import java.util.ArrayList;


class TransmissionData {
    public TransmissionSession session = null;
    public Cursor cursor;
    public int error = 0;
    public int errorCode = 0;
    public String errorMessage;
    public boolean hasRemoved = false;
    public boolean hasAdded = false;
    public boolean hasStatusChanged = false;
    public boolean hasMetadataNeeded = false;

    public static class Errors {
        public static final int NO_CONNECTIVITY = 1;
        public static final int ACCESS_DENIED = 1 << 1;
        public static final int NO_JSON = 1 << 2;
        public static final int NO_CONNECTION = 1 << 3;
        public static final int GENERIC_HTTP = 1 << 4;
        public static final int THREAD_ERROR = 1 << 5;
        public static final int RESPONSE_ERROR = 1 << 6;
        public static final int DUPLICATE_TORRENT = 1 << 7;
        public static final int INVALID_TORRENT = 1 << 8;
        public static final int TIMEOUT = 1 << 9;
        public static final int OUT_OF_MEMORY = 1 << 10;
        public static final int JSON_PARSE_ERROR = 1 << 11;
    }

    public TransmissionData(TransmissionSession session, int error, int errorCode) {
        this.session = session;
        this.error = error;
        this.errorCode = errorCode;
    }

    public TransmissionData(TransmissionSession session, Cursor cursor, boolean hasRemoved,
                            boolean hasAdded, boolean hasStatusChanged, boolean hasMetadataNeeded) {
        this.session = session;
        this.cursor = cursor;

        this.hasRemoved = hasRemoved;
        this.hasAdded = hasAdded;
        this.hasStatusChanged = hasStatusChanged;
        this.hasMetadataNeeded = hasMetadataNeeded;
    }
}

public class TransmissionDataLoader extends AsyncTaskLoader<TransmissionData> {
    private TransmissionProfile profile;

    private TransmissionSession session;

    boolean details;

    private Cursor cursor;
    private int[] updateIds;

    private int lastError;
    private int lastErrorCode;

    private TransmissionSessionManager sessManager;

    private int iteration = 0;
    private boolean stopUpdates = false;

    private SharedPreferences sharedPrefs;

    private boolean profileChanged = false;

    private Handler intervalHandler= new Handler();
    private Runnable intervalRunner= new Runnable() {
        @Override
        public void run() {
            if (profile != null && !stopUpdates)
                onContentChanged();
        }
    };

    private TransmissionSession sessionSet;
    private String[] sessionSetKeys;

    private String torrentAction;
    private int[] torrentActionIds;
    private boolean deleteData = false;
    private String torrentLocation;
    private String torrentSetKey;
    private Object torrentSetValue;
    private boolean moveData = false;
    private String torrentAddUri;
    private String torrentAddData;
    private boolean torrentAddPaused;
    private String torrentAddDeleteLocal;

    private DataSource dataSource;

    private boolean queryOnly;

    private static final Object exceptionLock = new Object();


    public TransmissionDataLoader(Context context, TransmissionProfile profile) {
        super(context);

        this.profile = profile;

        dataSource = new DataSource(context);

        sessManager = new TransmissionSessionManager(getContext(), this.profile, dataSource);
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
    }

    public TransmissionDataLoader(Context context, TransmissionProfile profile,
                                  TransmissionSession session, boolean details, int[] ids) {
        this(context, profile);

        this.session = session;
        this.updateIds = ids;
        this.details = details;
    }

    public void setDetails(boolean details) {
        this.details = details;
    }

    public void setQueryOnly(boolean queryOnly) {
        this.queryOnly = queryOnly;
    }

    public void setProfile(TransmissionProfile profile) {
        if (this.profile == profile) {
            return;
        }
        this.profile = profile;
        profileChanged = true;
        if (this.profile != null) {
            onContentChanged();
        }
    }

    public void setSession(TransmissionSession session, String... keys) {
        sessionSet = session;
        sessionSetKeys = keys;
        onContentChanged();
    }

    public void setUpdateIds(int[] ids) {
        updateIds = ids;
        onContentChanged();
    }

    public void setTorrentsRemove(int[] ids, boolean delete) {
        torrentAction = "torrent-remove";
        torrentActionIds = ids;
        deleteData = delete;
        onContentChanged();
    }

    public void setTorrentsAction(String action, int[] ids) {
        torrentAction = action;
        torrentActionIds = ids;
        onContentChanged();
    }

    public void setTorrentsLocation(int[] ids, String location, boolean move) {
        torrentAction = "torrent-set-location";
        torrentLocation = location;
        torrentActionIds = ids;
        moveData = move;

        profile.setLastDownloadDirectory(location);
        profile.setMoveData(move);
        onContentChanged();
    }

    public void setTorrentProperty(int id, String key, Object value) {
        if (key.equals(Torrent.SetterFields.FILES_WANTED)
                || key.equals(Torrent.SetterFields.FILES_UNWANTED)) {

            Runnable r = new TorrentActionRunnable(
                new int[] {id}, "torrent-set", null, key, value,
                false, false);

            new Thread(r).start();
            return;
        }

        torrentAction = "torrent-set";
        torrentActionIds = new int[] {id};
        torrentSetKey = key;
        torrentSetValue = value;

        onContentChanged();
    }

    public void addTorrent(String uri, String data, String location, boolean paused, String deleteLocal) {
        torrentAddUri = uri;
        torrentAddData = data;
        torrentAddPaused = paused;
        torrentLocation = location;
        torrentAddDeleteLocal = deleteLocal;

        profile.setLastDownloadDirectory(location);
        profile.setStartPaused(paused);
        profile.setDeleteLocal(deleteLocal != null);
        onContentChanged();
    }

    @Override
    public TransmissionData loadInBackground() {
        stopUpdates = false;

        boolean hasRemoved,
                hasAdded,
                hasStatusChanged,
                hasMetadataNeeded;


        /* TODO: catch SQLiteException */
        dataSource.open();

        try {
            if (queryOnly) {
                queryOnly = false;
                if (lastError == 0) {
                    cursor = dataSource.getTorrentCursor();

                    /* Fill the cursor window */
                    cursor.getCount();

                    if (session == null) {
                        session = dataSource.getSession();
                        session.setDownloadDirectories(profile, dataSource.getDownloadDirectories());
                    }

                    return new TransmissionData(session, cursor, true, true, false, false);
                }
            } else {
                /* Remove any previous waiting runners */
                intervalHandler.removeCallbacks(intervalRunner);
            }

            if (lastError > 0) {
                lastError = 0;
                lastErrorCode = 0;
            }

            if (profileChanged) {
                sessManager.setProfile(profile);
                profileChanged = false;
                iteration = 0;
            }
            if (!sessManager.hasConnectivity()) {
                lastError = TransmissionData.Errors.NO_CONNECTIVITY;
                session = null;
                stopUpdates = true;
                return new TransmissionData(session, lastError, 0);
            }
            G.logD("Fetching data");

            ArrayList<Thread> threads = new ArrayList<Thread>();

            final ArrayList<ManagerException> exceptions = new ArrayList<ManagerException>();

            /* Setters */
            if (torrentActionIds != null) {
                Thread thread = new Thread(new Runnable() {
                    @Override public void run() {
                        synchronized(exceptionLock) {
                            /* TODO: create a common runnable class that contains an exception property */
                            if (exceptions.size() > 0) {
                                return;
                            }
                        }
                        try {
                            executeTorrentsAction(
                                torrentActionIds, torrentAction, torrentLocation,
                                torrentSetKey, torrentSetValue, deleteData, moveData);
                        } catch (ManagerException e) {
                            synchronized(exceptionLock) {
                                exceptions.add(e);
                            }
                        } finally {
                            torrentActionIds = null;
                            torrentAction = null;
                            torrentSetKey = null;
                            torrentSetValue = null;
                            deleteData = false;
                        }
                    }
                });
                threads.add(thread);
                thread.start();
            }

            if (sessionSet != null) {
                Thread thread = new Thread(new Runnable() {
                    @Override public void run() {
                        synchronized(exceptionLock) {
                            if (exceptions.size() > 0) {
                                return;
                            }
                        }
                        try {
                            sessManager.setSession(sessionSet, sessionSetKeys);
                        } catch (ManagerException e) {
                            synchronized(exceptionLock) {
                                exceptions.add(e);
                            }
                        } finally {
                            sessionSet = null;
                            sessionSetKeys = null;
                        }
                    }
                });
                threads.add(thread);
                thread.start();
            }

            if (session == null || iteration % 3 == 0) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        synchronized(exceptionLock) {
                            if (exceptions.size() > 0) {
                                return;
                            }
                        }
                        try {
                            sessManager.updateSession();

                            session = dataSource.getSession();
                        } catch (ManagerException e) {
                            synchronized(exceptionLock) {
                                exceptions.add(e);
                            }
                        }
                    }
                });
                threads.add(thread);
                thread.start();
            }

            if (torrentAddUri != null || torrentAddData != null) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        synchronized(exceptionLock) {
                            if (exceptions.size() > 0) {
                                return;
                            }
                        }
                        try {
                            sessManager.addTorrent(torrentAddUri, torrentAddData,
                                torrentLocation, torrentAddPaused);

                            if (torrentAddDeleteLocal != null) {
                                File file = new File(torrentAddDeleteLocal);
                                if (!file.delete()) {
                                    G.logD("Couldn't remove torrent " + file.getName());
                                }
                            }
                        } catch (ManagerException e) {
                            synchronized(exceptionLock) {
                                exceptions.add(e);
                            }
                        } finally {
                            torrentAddUri = null;
                            torrentAddData = null;
                            torrentAddDeleteLocal = null;
                        }
                    }
                });
                threads.add(thread);
                thread.start();
            }

            if (session != null && session.getRPCVersion() > 14) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        synchronized(exceptionLock) {
                            if (exceptions.size() > 0) {
                                return;
                            }
                        }
                        try {
                            if (session != null) {
                                long freeSpace = sessManager.getFreeSpace(session.getDownloadDir());
                                if (freeSpace > -1) {
                                    session.setDownloadDirFreeSpace(freeSpace);
                                }
                            }
                        } catch (ManagerException e) {
                            synchronized(exceptionLock) {
                                exceptions.add(e);
                            }
                        }
                    }
                });
                threads.add(thread);
                thread.start();
            }

            boolean active = sharedPrefs.getBoolean(G.PREF_UPDATE_ACTIVE, false);
            TorrentStatus status;
            String[] fields;

            if (iteration == 0) {
                fields = G.concat(Torrent.Fields.METADATA, Torrent.Fields.STATS);
            } else {
                fields = Torrent.Fields.STATS;
            }

            if (iteration != 0 && !dataSource.hasCompleteMetadata()) {
                fields = G.concat(Torrent.Fields.METADATA, fields);
            }

            if (details) {
                fields = G.concat(fields, Torrent.Fields.STATS_EXTRA);
                if (!dataSource.hasExtraInfo()) {
                    fields = G.concat(fields, Torrent.Fields.INFO_EXTRA);
                }
            }

            for (Thread t : threads) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    return handleError(e);
                }
            }

            try {
                if (updateIds != null) {
                    status = sessManager.getTorrents(fields, updateIds);
                } else if (active && !details) {
                    int full = Integer.parseInt(sharedPrefs.getString(G.PREF_FULL_UPDATE, "2"));

                    if (iteration % full == 0) {
                        status = sessManager.getTorrents(fields, null);
                    } else {
                        status = sessManager.getActiveTorrents(fields);
                    }
                } else {
                    status = sessManager.getTorrents(fields, null);
                }
            } catch (ManagerException e) {
                return handleError(e);
            }

            hasAdded = status.hasAdded;
            hasRemoved = status.hasRemoved;
            hasStatusChanged = status.hasStatusChanged;
            hasMetadataNeeded = status.hasIncompleteMetadata;

            if (exceptions.size() > 0) {
                return handleError(exceptions.get(0));
            }

            int[] unnamed = dataSource.getUnnamedTorrentIds();
            if (unnamed != null && unnamed.length > 0) {
                try {
                    sessManager.getTorrents(Torrent.Fields.METADATA, unnamed);
                } catch (ManagerException e) {
                    return handleError(e);
                }
            }

            cursor = dataSource.getTorrentCursor();
            session.setDownloadDirectories(profile, dataSource.getDownloadDirectories());

            iteration++;

            /* Fill the cursor window */
            cursor.getCount();

            return new TransmissionData(session, cursor, hasRemoved, hasAdded,
                hasStatusChanged, hasMetadataNeeded);
        } finally {
            dataSource.close();
        }
    }

    @Override
    public void deliverResult(TransmissionData data) {
        if (isReset()) {
            return;
        }

        if (isStarted()) {
            // TorrentListActivity.logD("TLoader: Delivering results: %d torrents", new Object[] {data.torrents.size()});
            super.deliverResult(data);
        }

        repeatLoading();
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();

        G.logD("TLoader: onStartLoading()");

        stopUpdates = false;
        if (lastError > 0) {
            session = null;
            deliverResult(new TransmissionData(session, lastError, lastErrorCode));
        } else if (cursor != null && !cursor.isClosed()) {
            deliverResult(new TransmissionData(session, cursor, false, false, false, false));
        }

        if (takeContentChanged() || iteration == 0) {
            G.logD("TLoader: forceLoad()");
            forceLoad();
        }
    }

    @Override
    protected void onStopLoading() {
        super.onStopLoading();

        G.logD("TLoader: onStopLoading()");
        cancelLoad();
    }

    @Override
    protected void onReset() {
        super.onReset();

        G.logD("TLoader: onReset()");

        onStopLoading();

        if (cursor != null) {
            cursor.close();
            cursor = null;
        }
    }

    private void repeatLoading() {
        int update = Integer.parseInt(sharedPrefs.getString(G.PREF_UPDATE_INTERVAL, "-1"));
        if (update >= 0 && !isReset())
            intervalHandler.postDelayed(intervalRunner, update * 1000);
    }

    private void executeTorrentsAction(int[] ids,
                String action, String location, String setKey,
                Object setValue, boolean deleteData, boolean moveData) throws ManagerException {
        if (action.equals("torrent-remove")) {
            sessManager.setTorrentsRemove(ids, deleteData);
        } else if (action.equals("torrent-set-location")) {
            sessManager.setTorrentsLocation(ids, location, moveData);
        } else if (action.equals("torrent-set")) {
            sessManager.setTorrentsProperty(ids, setKey, setValue);
        } else {
            sessManager.setTorrentsAction(action, ids);
        }
    }

    private TransmissionData handleError(ManagerException e) {
        stopUpdates = true;

        G.logD("Got an error while fetching data: " + e.getMessage() + " and this code: " + e.getCode());

        lastErrorCode = e.getCode();
        switch(e.getCode()) {
            case 401:
            case 403:
                lastError = TransmissionData.Errors.ACCESS_DENIED;
                session = null;
                break;
            case 200:
                if (e.getMessage().equals("no-json")) {
                    lastError = TransmissionData.Errors.NO_JSON;
                    session = null;
                }
                break;
            case -1:
                if (e.getMessage().equals("timeout")) {
                    lastError = TransmissionData.Errors.TIMEOUT;
                    session = null;
                } else {
                    lastError = TransmissionData.Errors.NO_CONNECTION;
                    session = null;
                }
                break;
            case -2:
                if (e.getMessage().equals("duplicate torrent")) {
                    lastError = TransmissionData.Errors.DUPLICATE_TORRENT;
                } else if (e.getMessage().equals("invalid or corrupt torrent file")) {
                    lastError = TransmissionData.Errors.INVALID_TORRENT;
                } else {
                    lastError = TransmissionData.Errors.RESPONSE_ERROR;
                    session = null;
                    G.logE("Transmission Daemon Error!", e);
                }
                break;
            case -3:
                lastError = TransmissionData.Errors.OUT_OF_MEMORY;
                session = null;
                break;
            case -4:
                lastError = TransmissionData.Errors.JSON_PARSE_ERROR;
                session = null;
                G.logE("JSON parse error!", e);
                break;
            default:
                lastError = TransmissionData.Errors.GENERIC_HTTP;
                session = null;
                break;
        }

        return new TransmissionData(session, lastError, lastErrorCode);
    }

    private TransmissionData handleError(InterruptedException e) {
        stopUpdates = true;

        lastError = TransmissionData.Errors.THREAD_ERROR;
        G.logE("Got an error when processing the threads", e);

        session = null;
        return new TransmissionData(session, lastError, 0);
    }

    private class TorrentActionRunnable implements Runnable {
        private String action, location, setKey;
        private boolean deleteData, moveData;
        private int[] ids;
        private Object setValue;

        public TorrentActionRunnable(int[] ids,
                String action, String location, String setKey,
                Object setValue, boolean deleteData, boolean moveData) {

            this.ids = ids;
            this.action = action;
            this.location = location;
            this.setKey = setKey;
            this.setValue = setValue;
            this.deleteData = deleteData;
            this.moveData = moveData;
        }

        @Override
        public void run() {
            try {
                executeTorrentsAction(
                    ids, action, location, setKey, setValue,
                    deleteData, moveData);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
