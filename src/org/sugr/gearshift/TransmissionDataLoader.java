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
    private TransmissionProfile mProfile;

    private TransmissionSession session;

    boolean details;

    private Cursor cursor;
    private int[] updateIds;

    private int lastError;
    private int lastErrorCode;

    private TransmissionSessionManager mSessManager;

    private int mIteration = 0;
    private boolean mStopUpdates = false;

    private SharedPreferences mDefaultPrefs;

    private boolean mProfileChanged = false;

    private Handler mIntervalHandler = new Handler();
    private Runnable mIntervalRunner = new Runnable() {
        @Override
        public void run() {
            if (mProfile != null && !mStopUpdates)
                onContentChanged();
        }
    };

    private TransmissionSession mSessionSet;
    private String[] mSessionSetKeys;

    private String mTorrentAction;
    private int[] mTorrentActionIds;
    private boolean mDeleteData = false;
    private String mTorrentLocation;
    private String mTorrentSetKey;
    private Object mTorrentSetValue;
    private boolean mMoveData = false;
    private String mTorrentAddUri;
    private String mTorrentAddData;
    private boolean mTorrentAddPaused;
    private String mTorrentAddDeleteLocal;

    private DataSource dataSource;

    private static final Object exceptionLock = new Object();

    public TransmissionDataLoader(Context context, TransmissionProfile profile) {
        super(context);

        mProfile = profile;

        dataSource = new DataSource(context);

        mSessManager = new TransmissionSessionManager(getContext(), mProfile, dataSource);
        mDefaultPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
    }

    public TransmissionDataLoader(Context context, TransmissionProfile profile,
                                  TransmissionSession session, boolean details, int[] ids) {
        this(context, profile);

        this.session = session;
        this.updateIds = ids;
        this.details = details;
    }

    public void setProfile(TransmissionProfile profile) {
        if (mProfile == profile) {
            return;
        }
        mProfile = profile;
        mProfileChanged = true;
        if (mProfile != null) {
            onContentChanged();
        }
    }

    public void setSession(TransmissionSession session, String... keys) {
        mSessionSet = session;
        mSessionSetKeys = keys;
        onContentChanged();
    }

    public void setDetails(boolean details) {
        this.details = details;

        if (details) {
            onContentChanged();
        }
    }

    public void setUpdateIds(int[] ids) {
        updateIds = ids;
        onContentChanged();
    }

    public void setTorrentsRemove(int[] ids, boolean delete) {
        mTorrentAction = "torrent-remove";
        mTorrentActionIds = ids;
        mDeleteData = delete;
        onContentChanged();
    }

    public void setTorrentsAction(String action, int[] ids) {
        mTorrentAction = action;
        mTorrentActionIds = ids;
        onContentChanged();
    }

    public void setTorrentsLocation(int[] ids, String location, boolean move) {
        mTorrentAction = "torrent-set-location";
        mTorrentLocation = location;
        mTorrentActionIds = ids;
        mMoveData = move;

        mProfile.setLastDownloadDirectory(location);
        mProfile.setMoveData(move);
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

        mTorrentAction = "torrent-set";
        mTorrentActionIds = new int[] {id};
        mTorrentSetKey = key;
        mTorrentSetValue = value;

        onContentChanged();
    }

    public void addTorrent(String uri, String data, String location, boolean paused, String deleteLocal) {
        mTorrentAddUri = uri;
        mTorrentAddData = data;
        mTorrentAddPaused = paused;
        mTorrentLocation = location;
        mTorrentAddDeleteLocal = deleteLocal;

        mProfile.setLastDownloadDirectory(location);
        mProfile.setStartPaused(paused);
        mProfile.setDeleteLocal(deleteLocal != null);
        onContentChanged();
    }

    @Override
    public TransmissionData loadInBackground() {
        /* Remove any previous waiting runners */
        mIntervalHandler.removeCallbacks(mIntervalRunner);
        mStopUpdates = false;

        boolean hasRemoved,
                hasAdded,
                hasStatusChanged,
                hasMetadataNeeded;

        if (lastError > 0) {
            lastError = 0;
            lastErrorCode = 0;
        }

        if (mProfileChanged) {
            mSessManager.setProfile(mProfile);
            mProfileChanged = false;
            mIteration = 0;
        }
        if (!mSessManager.hasConnectivity()) {
            lastError = TransmissionData.Errors.NO_CONNECTIVITY;
            session = null;
            mStopUpdates = true;
            return new TransmissionData(session, lastError, 0);
        }

        /* TODO: catch SQLiteException */
        dataSource.open();

        try {
            G.logD("Fetching data");

            if (mTorrentActionIds != null) {
                TransmissionData actionData = executeTorrentsAction(
                    mTorrentActionIds, mTorrentAction, mTorrentLocation,
                    mTorrentSetKey, mTorrentSetValue, mDeleteData, mMoveData);

                mTorrentActionIds = null;
                mTorrentAction = null;
                mTorrentSetKey = null;
                mTorrentSetValue = null;
                mDeleteData = false;

                if (actionData != null) {
                    return actionData;
                }
            }

            ArrayList<Thread> threads = new ArrayList<Thread>();

            final ArrayList<ManagerException> exceptions = new ArrayList<ManagerException>();
            /* Setters */
            if (mSessionSet != null) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        synchronized(exceptionLock) {
                            /* TODO: create a common runnable class that contains an exception property */
                            if (exceptions.size() > 0) {
                                return;
                            }
                        }
                        try {
                            mSessManager.setSession(mSessionSet, mSessionSetKeys);
                        } catch (ManagerException e) {
                            synchronized(exceptionLock) {
                                exceptions.add(e);
                            }
                        } finally {
                            mSessionSet = null;
                            mSessionSetKeys = null;
                        }
                    }
                });
                threads.add(thread);
                thread.start();

            }

            if (session == null || mIteration % 3 == 0) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        synchronized(exceptionLock) {
                            if (exceptions.size() > 0) {
                                return;
                            }
                        }
                        try {
                            mSessManager.updateSession();

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

            if (mTorrentAddUri != null || mTorrentAddData != null) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        synchronized(exceptionLock) {
                            if (exceptions.size() > 0) {
                                return;
                            }
                        }
                        try {
                            mSessManager.addTorrent(mTorrentAddUri, mTorrentAddData,
                                mTorrentLocation, mTorrentAddPaused);

                            if (mTorrentAddDeleteLocal != null) {
                                File file = new File(mTorrentAddDeleteLocal);
                                if (!file.delete()) {
                                    G.logD("Couldn't remove torrent " + file.getName());
                                }
                            }
                        } catch (ManagerException e) {
                            synchronized(exceptionLock) {
                                exceptions.add(e);
                            }
                        } finally {
                            mTorrentAddUri = null;
                            mTorrentAddData = null;
                            mTorrentAddDeleteLocal = null;
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
                                long freeSpace = mSessManager.getFreeSpace(session.getDownloadDir());
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

            boolean active = mDefaultPrefs.getBoolean(G.PREF_UPDATE_ACTIVE, false);
            TorrentStatus status;
            String[] fields;

            if (mIteration == 0) {
                fields = G.concat(Torrent.Fields.METADATA, Torrent.Fields.STATS);
            } else {
                fields = Torrent.Fields.STATS;
            }

            if (mIteration != 0 && !dataSource.hasCompleteMetadata()) {
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
                    status = mSessManager.getTorrents(fields, updateIds);
                } else if (active && !details) {
                    int full = Integer.parseInt(mDefaultPrefs.getString(G.PREF_FULL_UPDATE, "2"));

                    if (mIteration % full == 0) {
                        status = mSessManager.getTorrents(fields, null);
                    } else {
                        status = mSessManager.getActiveTorrents(fields);
                    }
                } else {
                    status = mSessManager.getTorrents(fields, null);
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
                    mSessManager.getTorrents(Torrent.Fields.METADATA, unnamed);
                } catch (ManagerException e) {
                    return handleError(e);
                }
            }

            cursor = dataSource.getTorrentCursor();
            session.setDownloadDirectories(mProfile, dataSource.getDownloadDirectories());

            mIteration++;

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

        mStopUpdates = false;
        if (lastError > 0) {
            session = null;
            deliverResult(new TransmissionData(session, lastError, lastErrorCode));
        } else if (cursor != null && !cursor.isClosed()) {
            deliverResult(new TransmissionData(session, cursor, false, false, false, false));
        }

        if (takeContentChanged() || mIteration == 0) {
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
        int update = Integer.parseInt(mDefaultPrefs.getString(G.PREF_UPDATE_INTERVAL, "-1"));
        if (update >= 0 && !isReset())
            mIntervalHandler.postDelayed(mIntervalRunner, update * 1000);
    }

    private TransmissionData executeTorrentsAction(int[] ids,
                String action, String location, String setKey,
                Object setValue, boolean deleteData, boolean moveData) {
        try {
            if (action.equals("torrent-remove")) {
                mSessManager.setTorrentsRemove(ids, deleteData);
            } else if (action.equals("torrent-set-location")) {
                mSessManager.setTorrentsLocation(ids, location, moveData);
            } else if (action.equals("torrent-set")) {
                mSessManager.setTorrentsProperty(ids, setKey, setValue);
            } else {
                mSessManager.setTorrentsAction(action, ids);
            }
        } catch (ManagerException e) {
            return handleError(e);
        }

        return null;
    }

    private TransmissionData handleError(ManagerException e) {
        mStopUpdates = true;

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
        mStopUpdates = true;

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
            executeTorrentsAction(
                ids, action, location, setKey, setValue,
                deleteData, moveData);
        }
    }
}
