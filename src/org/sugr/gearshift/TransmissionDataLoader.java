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


public class TransmissionDataLoader extends AsyncTaskLoader<TransmissionData> {
    private TransmissionProfile profile;

    private TransmissionSession session;

    boolean details;

    private Cursor cursor;
    private String[] updateHashStrings;

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
    private String[] torrentActionHashStrings;
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
                                  TransmissionSession session, boolean details, String[] hashStrings) {
        this(context, profile);

        this.session = session;
        this.updateHashStrings = hashStrings;
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

    public void setUpdateHashStrings(String[] hashStrings) {
        updateHashStrings = hashStrings;
        onContentChanged();
    }

    public void setTorrentsRemove(String[] hashStrings, boolean delete) {
        torrentAction = "torrent-remove";
        torrentActionHashStrings = hashStrings;
        deleteData = delete;
        onContentChanged();
    }

    public void setTorrentsAction(String action, String[] hashStrings) {
        torrentAction = action;
        torrentActionHashStrings = hashStrings;
        onContentChanged();
    }

    public void setTorrentsLocation(String[] hashStrings, String location, boolean move) {
        torrentAction = "torrent-set-location";
        torrentLocation = location;
        torrentActionHashStrings = hashStrings;
        moveData = move;

        profile.setLastDownloadDirectory(location);
        profile.setMoveData(move);
        onContentChanged();
    }

    public void setTorrentProperty(String hash, String key, Object value) {
        if (key.equals(Torrent.SetterFields.FILES_WANTED)
                || key.equals(Torrent.SetterFields.FILES_UNWANTED)) {

            Runnable r = new TorrentActionRunnable(
                new String[] { hash }, "torrent-set", null, key, value,
                false, false);

            new Thread(r).start();
            return;
        }

        torrentAction = "torrent-set";
        torrentActionHashStrings = new String[] { hash };
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
                    if (cursor.getCount() > 0) {
                        if (session == null) {
                            session = dataSource.getSession();
                            session.setDownloadDirectories(profile, dataSource.getDownloadDirectories());
                        }

                        return new TransmissionData(session, cursor, true, true, false, false, true);
                    }
                }
            }
            /* Remove any previous waiting runners */
            intervalHandler.removeCallbacks(intervalRunner);

            boolean isLastErrorFatal = false;
            if (lastError > 0) {
                if (lastError != TransmissionData.Errors.DUPLICATE_TORRENT
                    && lastError != TransmissionData.Errors.INVALID_TORRENT) {
                    isLastErrorFatal = true;
                }
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
                return new TransmissionData(null, lastError, 0);
            }
            G.logD("Fetching data");

            ArrayList<Thread> threads = new ArrayList<>();

            final ArrayList<ManagerException> exceptions = new ArrayList<>();
            final TorrentStatus actionStatus = new TorrentStatus();

            /* Setters */
            if (torrentActionHashStrings != null) {
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
                                torrentActionHashStrings, torrentAction, torrentLocation,
                                torrentSetKey, torrentSetValue, deleteData, moveData);

                            switch (torrentAction) {
                                case "torrent-remove":
                                    actionStatus.hasRemoved = true;
                                    break;
                                case "torrent-set-location":
                                    actionStatus.hasAdded = true;
                                    actionStatus.hasRemoved = true;
                                    break;
                                default:
                                    actionStatus.hasStatusChanged = true;
                                    break;
                            }
                        } catch (ManagerException e) {
                            synchronized(exceptionLock) {
                                exceptions.add(e);
                            }
                        } finally {
                            torrentActionHashStrings = null;
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

                            actionStatus.hasAdded = true;
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

            if (details || iteration == 1) {
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
                if (updateHashStrings != null) {
                    status = sessManager.getTorrents(fields, updateHashStrings, false);
                } else if (active && !details) {
                    int full = Integer.parseInt(sharedPrefs.getString(G.PREF_FULL_UPDATE, "2"));

                    if (iteration % full == 0) {
                        status = sessManager.getTorrents(fields, null, iteration == 0 || isLastErrorFatal);
                    } else {
                        status = sessManager.getActiveTorrents(fields);
                    }
                } else {
                    status = sessManager.getTorrents(fields, null, iteration == 0 || isLastErrorFatal);
                }
            } catch (ManagerException e) {
                return handleError(e);
            }

            hasAdded = actionStatus.hasAdded || status.hasAdded;
            hasRemoved = actionStatus.hasRemoved || status.hasRemoved;
            hasStatusChanged = actionStatus.hasStatusChanged || status.hasStatusChanged;
            hasMetadataNeeded = actionStatus.hasIncompleteMetadata || status.hasIncompleteMetadata;

            if (exceptions.size() > 0) {
                return handleError(exceptions.get(0));
            }

            String[] unnamed = dataSource.getUnnamedTorrentHashStrings();
            if (unnamed != null && unnamed.length > 0) {
                try {
                    sessManager.getTorrents(G.concat(new String[] {Torrent.Fields.hashString}, Torrent.Fields.METADATA), unnamed, false);
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
                hasStatusChanged, hasMetadataNeeded, false);
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
            deliverResult(new TransmissionData(null, lastError, lastErrorCode));
        } else if (cursor != null && !cursor.isClosed()) {
            deliverResult(new TransmissionData(session, cursor, false, false, false, false, false));
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

    private void executeTorrentsAction(String[] hashStrings,
                String action, String location, String setKey,
                Object setValue, boolean deleteData, boolean moveData) throws ManagerException {
        switch (action) {
            case "torrent-remove":
                sessManager.removeTorrent(hashStrings, deleteData);
                break;
            case "torrent-set-location":
                sessManager.setTorrentLocation(hashStrings, location, moveData);
                break;
            case "torrent-set":
                sessManager.setTorrentProperty(hashStrings, setKey, setValue);
                break;
            default:
                sessManager.setTorrentAction(hashStrings, action);
                break;
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
        return new TransmissionData(null, lastError, 0);
    }

    private class TorrentActionRunnable implements Runnable {
        private String action, location, setKey;
        private boolean deleteData, moveData;
        private String[] hashStrings;
        private Object setValue;

        public TorrentActionRunnable(String[] hashStrings,
                String action, String location, String setKey,
                Object setValue, boolean deleteData, boolean moveData) {

            this.hashStrings = hashStrings;
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
                    hashStrings, action, location, setKey, setValue,
                    deleteData, moveData);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
