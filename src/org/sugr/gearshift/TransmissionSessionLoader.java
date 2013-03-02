package org.sugr.gearshift;

import java.util.ArrayList;

import org.sugr.gearshift.TransmissionSessionManager.ActiveTorrentGetResponse;
import org.sugr.gearshift.TransmissionSessionManager.ManagerException;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.AsyncTaskLoader;
import android.util.SparseArray;


class TransmissionSessionData {
    public TransmissionSession session = null;
    public TransmissionSessionStats stats = null;
    public ArrayList<Torrent> torrents = new ArrayList<Torrent>();
    public int error = 0;
    public boolean hasRemoved = false;
    public boolean hasAdded = false;

    public static class Errors {
        public static final int NO_CONNECTIVITY = 1;
        public static final int ACCESS_DENIED = 1 << 1;
        public static final int NO_JSON = 1 << 2;
        public static final int NO_CONNECTION = 1 << 3;
        public static final int GENERIC_HTTP = 1 << 4;
        public static final int THREAD_ERROR = 1 << 5;
    };

    public TransmissionSessionData(TransmissionSession session, TransmissionSessionStats stats, int error) {
        this.session = session;
        this.stats = stats;
        this.error = error;
    }

    public TransmissionSessionData(TransmissionSession session,
            TransmissionSessionStats stats,
            ArrayList<Torrent> torrents,
            boolean hasRemoved,
            boolean hasAdded) {
        this.session = session;
        this.stats = stats;

        if (torrents != null)
            this.torrents = torrents;

        this.hasRemoved = hasRemoved;
        this.hasAdded = hasAdded;
    }
}

public class TransmissionSessionLoader extends AsyncTaskLoader<TransmissionSessionData> {
    private ArrayList<Torrent> mTorrents = new ArrayList<Torrent>();
    private static SparseArray<Torrent> mTorrentMap = new SparseArray<Torrent>();
    private TransmissionProfile mProfile;

    private TransmissionSession mSession;
    private TransmissionSessionStats mSessionStats;
    private int mLastError;

    private TransmissionSessionManager mSessManager;
    private Torrent[] mCurrentTorrents;
    private boolean mAllCurrent = false;

    private int mIteration = 0;
    private boolean mStopUpdates = false;

    private SharedPreferences mDefaultPrefs;

    private boolean mNeedsMoreInfo = false;

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
    private boolean mMoveData = false;

    private Object mLock = new Object();

    public TransmissionSessionLoader(Context context, TransmissionProfile profile) {
        super(context);

        mProfile = profile;

        mSessManager = new TransmissionSessionManager(getContext(), mProfile);
        mDefaultPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
    }

    public TransmissionSessionLoader(Context context, TransmissionProfile profile, ArrayList<Torrent> all) {
        super(context);

        mProfile = profile;

        mSessManager = new TransmissionSessionManager(getContext(), mProfile);
        mDefaultPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        if (all != null) {
            for (Torrent t : all) {
                mTorrents.add(t);
                mTorrentMap.put(t.getId(), t);
            }
        }
    }

    public void setCurrentTorrents(Torrent[] torrents) {
        mCurrentTorrents = torrents;
        mAllCurrent = false;
    }

    public void setAllCurrentTorrents(boolean set) {
        mCurrentTorrents = null;
        mAllCurrent = set;
        onContentChanged();
    }

    public void setSession(TransmissionSession session, String... keys) {
        mSessionSet = session;
        mSessionSetKeys = keys;
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
        onContentChanged();
    }

    @Override
    public TransmissionSessionData loadInBackground() {
        /* Remove any previous waiting runners */
        mIntervalHandler.removeCallbacks(mIntervalRunner);
        mStopUpdates = false;

        boolean hasRemoved = false, hasAdded = false;

        if (mLastError > 0) {
            mLastError = 0;
            hasAdded = true;
        }
        if (!mSessManager.hasConnectivity()) {
            mLastError = TransmissionSessionData.Errors.NO_CONNECTIVITY;
            return new TransmissionSessionData(mSession, mSessionStats, mLastError);
        }

        ArrayList<Thread> threads = new ArrayList<Thread>();
        final ArrayList<ManagerException> exceptions = new ArrayList<ManagerException>();
        /* Setters */
        if (mSessionSet != null) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    synchronized(mLock) {
                        if (exceptions.size() > 0) {
                            return;
                        }
                    }
                    try {
                        mSessManager.setSession(mSessionSet, mSessionSetKeys);
                        mSessionSet = null;
                        mSessionSetKeys = null;
                    } catch (ManagerException e) {
                        synchronized(mLock) {
                            exceptions.add(e);
                        }
                    }
                }
            });
            threads.add(thread);
            thread.start();

        }
        if (mTorrentActionIds != null) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    synchronized(mLock) {
                        if (exceptions.size() > 0) {
                            return;
                        }
                    }
                    try {
                        if (mTorrentAction.equals("torrent-remove"))
                            mSessManager.setTorrentsRemove(mTorrentActionIds, mDeleteData);
                        else if (mTorrentAction.equals("torrent-set-location"))
                            mSessManager.setTorrentsLocation(mTorrentActionIds, mTorrentLocation, mMoveData);
                        else
                            mSessManager.setTorrentsAction(mTorrentAction, mTorrentActionIds);
                        mTorrentActionIds = null;
                        mTorrentAction = null;
                        mDeleteData = false;
                    } catch (ManagerException e) {
                        synchronized(mLock) {
                            exceptions.add(e);
                        };
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }

        if (mCurrentTorrents == null && (mSession == null || mIteration % 3 == 0)) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    synchronized(mLock) {
                        if (exceptions.size() > 0) {
                            return;
                        }
                    }
                    try {
                        mSession = mSessManager.getSession().getSession();
                    } catch (ManagerException e) {
                        synchronized(mLock) {
                            exceptions.add(e);
                        };
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }
        if (mCurrentTorrents == null && mSessionStats == null) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    synchronized(mLock) {
                        if (exceptions.size() > 0) {
                            return;
                        }
                    }
                    try {
                        mSessionStats = mSessManager.getSessionStats().getStats();
                    } catch (ManagerException e) {
                        synchronized(mLock) {
                            exceptions.add(e);
                        };
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }

        boolean active = mDefaultPrefs.getBoolean(GeneralSettingsFragment.PREF_UPDATE_ACTIVE, false);
        Torrent [] torrents;
        int[] removed = null;
        int[] ids = null;
        String[] fields = null;

        if (mIteration == 0 || mNeedsMoreInfo) {
            fields = concat(Torrent.Fields.METADATA, Torrent.Fields.STATS);
            /* Force the list to re-order itself */
            hasAdded = true;
        } else if (mAllCurrent) {
            fields = concat(Torrent.Fields.STATS, Torrent.Fields.STATS_EXTRA);
            for (Torrent t : mTorrents) {
                if (t.getHashString() == null) {
                    fields = concat(fields, Torrent.Fields.INFO_EXTRA);
                    break;
                }
            }
        } else if (mCurrentTorrents != null) {
            fields = concat(new String[] {"id"}, Torrent.Fields.STATS_EXTRA);
            boolean extraAdded = false;
            ids = new int[mCurrentTorrents.length];
            int index = 0;
            for (Torrent t : mCurrentTorrents) {
                if (!extraAdded && t.getHashString() == null) {
                    fields = concat(fields, Torrent.Fields.INFO_EXTRA);
                    extraAdded = true;
                }

                ids[index++] = t.getId();
            }
        } else {
            fields = Torrent.Fields.STATS;
        }
        try {
            if (mCurrentTorrents != null) {
                torrents = mSessManager.getTorrents(ids, fields).getTorrents();
            } else if (active && !mAllCurrent) {
                int full = Integer.parseInt(mDefaultPrefs.getString(GeneralSettingsFragment.PREF_FULL_UPDATE, "2"));

                if (mIteration % full == 0) {
                    torrents = mSessManager.getAllTorrents(fields).getTorrents();
                } else {
                    ActiveTorrentGetResponse response = mSessManager.getActiveTorrents(fields);
                    torrents = response.getTorrents();
                    removed = response.getRemoved();
                }
            } else {
                torrents = mSessManager.getAllTorrents(fields).getTorrents();
            }
        } catch (ManagerException e) {
            return handleError(e);
        }

        if (removed != null) {
            for (int id : removed) {
                Torrent t = mTorrentMap.get(id);
                if (t != null) {
                    mTorrents.remove(t);
                    mTorrentMap.remove(id);
                    hasRemoved = true;
                }
            }
        } else if (mCurrentTorrents == null) {
            ArrayList<Torrent> removal = new ArrayList<Torrent>();
            for (Torrent original : mTorrents) {
                boolean found = false;
                for (Torrent t : torrents) {
                    if (original.getId() == t.getId()) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    removal.add(original);
                }
            }
            for (Torrent t : removal) {
                mTorrents.remove(t);
                mTorrentMap.remove(t.getId());
                hasRemoved = true;
            }
        }
        mNeedsMoreInfo = false;

        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                return handleError(e);
            }
        }

        if (exceptions.size() > 0) {
            return handleError(exceptions.get(0));
        }

        if (mTorrents.size() != mTorrentMap.size()) {
            /* the torrent list has been lost */
            TorrentListActivity.logD("Torrent list corruption, array size : " + mTorrents.size() + ", map size : " + mTorrentMap.size());
            mTorrents.clear();
            mTorrentMap.clear();
            hasRemoved = true;;
        }

        for (Torrent t : torrents) {
            if (t.getTotalSize() == 0 && t.getMetadataPercentComplete() < 1) {
                mNeedsMoreInfo = true;
            }
            Torrent torrent;
            if ((torrent = mTorrentMap.get(t.getId())) != null) {
                torrent.updateFrom(t, fields);
            } else {
                mTorrents.add(t);
                mTorrentMap.put(t.getId(), t);
                torrent = t;
                hasAdded = true;
            }
            torrent.setTransmissionSession(mSession);
            torrent.setTrafficText(getContext());
            torrent.setStatusText(getContext());
        }

        mIteration++;
        return new TransmissionSessionData(
                mSession, mSessionStats, mTorrents,
                hasRemoved, hasAdded);
    }

    @Override
    public void deliverResult(TransmissionSessionData data) {
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

        TorrentListActivity.logD("TLoader: onStartLoading()");

        if (mTorrents.size() > 0)
            deliverResult(new TransmissionSessionData(
                    mSession, mSessionStats, mTorrents,
                    false, false));

        if (takeContentChanged() || mTorrents.size() == 0) {
            TorrentListActivity.logD("TLoader: forceLoad()");
            forceLoad();
        }
    }

    @Override
    protected void onStopLoading() {
        super.onStopLoading();

        TorrentListActivity.logD("TLoader: onStopLoading()");
        cancelLoad();
    }

    @Override
    protected void onReset() {
        super.onReset();

        TorrentListActivity.logD("TLoader: onReset()");

        onStopLoading();

        if (mTorrents.size() > 0) {
            mTorrents.clear();
            mTorrentMap.clear();
        }
    }

    private void repeatLoading() {
        int update = Integer.parseInt(mDefaultPrefs.getString(GeneralSettingsFragment.PREF_UPDATE_INTERVAL, "-1"));
        if (update >= 0 && !isReset())
            mIntervalHandler.postDelayed(mIntervalRunner, update * 1000);
    }

    private TransmissionSessionData handleError(ManagerException e) {
        mStopUpdates = true;
        e.printStackTrace();

        TorrentListActivity.logD("Got an error while fetching data: " + e.getMessage() + " and this code: " + e.getCode());

        switch(e.getCode()) {
            case 401:
            case 403:
                mLastError = TransmissionSessionData.Errors.ACCESS_DENIED;
                break;
            case 200:
                if (e.getMessage().equals("no-json")) {
                    mLastError = TransmissionSessionData.Errors.NO_JSON;
                }
                break;
            case -1:
                mLastError = TransmissionSessionData.Errors.NO_CONNECTION;
                break;
            default:
                mLastError = TransmissionSessionData.Errors.GENERIC_HTTP;
                break;
        }

        return new TransmissionSessionData(mSession, mSessionStats, mLastError);
    }

    private TransmissionSessionData handleError(InterruptedException e) {
        mStopUpdates = true;

        mLastError = TransmissionSessionData.Errors.THREAD_ERROR;
        TorrentListActivity.logE("Got an error when processing the threads", e);

        return new TransmissionSessionData(mSession, mSessionStats, mLastError);
    }

    public static String[] concat(String[]... arrays) {
        int len = 0;
        for (final String[] array : arrays) {
            len += array.length;
        }

        final String[] result = new String[len];

        int currentPos = 0;
        for (final String[] array : arrays) {
            System.arraycopy(array, 0, result, currentPos, array.length);
            currentPos += array.length;
        }

        return result;
    }
}
