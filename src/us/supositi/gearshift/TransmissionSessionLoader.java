package us.supositi.gearshift;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import us.supositi.gearshift.TransmissionSessionManager.ActiveTorrentGetResponse;
import us.supositi.gearshift.TransmissionSessionManager.ManagerException;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.AsyncTaskLoader;


class TransmissionSessionData {
    public TransmissionSession session = null;
    public TransmissionSessionStats stats = null;
    public Torrent[] torrents = new Torrent[0];
    
    public TransmissionSessionData(TransmissionSession session, TransmissionSessionStats stats) {
        this.session = session;
        this.stats = stats;
    }
    
    public TransmissionSessionData(TransmissionSession session, TransmissionSessionStats stats,
            Torrent[] torrents) {
        this.session = session;
        this.stats = stats;
        this.torrents = torrents;
    }
}

public class TransmissionSessionLoader extends AsyncTaskLoader<TransmissionSessionData> {
    private ArrayList<Torrent> mTorrents = new ArrayList<Torrent>();
    private static HashMap<Integer, Torrent> mTorrentMap = new HashMap<Integer, Torrent>();
    private TransmissionProfile mProfile;
    
    private TransmissionSession mSession;
    private TransmissionSessionStats mSessionStats;
    
    private TransmissionSessionManager mSessManager;
    
    private int mIteration = 0;
    private boolean mStopUpdates = false;
    
    private SharedPreferences mDefaultPrefs;
    
    private Torrent mCurrentTorrent;
    
    private ArrayList<Integer> mNeedingMoreInfo = new ArrayList<Integer>();

    private Handler mIntervalHandler = new Handler();
    private Runnable mIntervalRunner = new Runnable() {
        @Override
        public void run() {
            if (mProfile != null && !mStopUpdates)
                onContentChanged();
        }
    };

    public TransmissionSessionLoader(Context context, TransmissionProfile profile) {
        super(context);
        
        mProfile = profile;
        
        mSessManager = new TransmissionSessionManager(getContext(), mProfile);
        mDefaultPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
    }
    
    public TransmissionSessionLoader(Context context, TransmissionProfile profile, Torrent torrent) {
        super(context);
        
        mProfile = profile;
        
        mSessManager = new TransmissionSessionManager(getContext(), mProfile);
        mDefaultPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        mCurrentTorrent = torrent;
        mTorrents.add(torrent);
        mTorrentMap.put(torrent.getId(), torrent);
    }

    @Override
    public TransmissionSessionData loadInBackground() {
        /* Remove any previous waiting runners */
        mIntervalHandler.removeCallbacks(mIntervalRunner);

        if (mCurrentTorrent == null && (mSession == null || mIteration % 3 == 0)) {
            try {
                mSession = mSessManager.getSession().getSession();
            } catch (ManagerException e) {
                return handleError(e);
            }
        }
        if (mCurrentTorrent == null && mSessionStats == null) {
            try {
                mSessionStats = mSessManager.getSessionStats().getStats();
            } catch (ManagerException e) {
                return handleError(e);
            }
        }
        
        boolean active = mDefaultPrefs.getBoolean(GeneralSettingsFragment.PREF_UPDATE_ACTIVE, false);
        Torrent [] torrents;
        int[] removed = null;
        String[] fields = null;
        
        if (mIteration == 0 || mNeedingMoreInfo.size() > 0) {
            fields = concat(Torrent.Fields.METADATA, Torrent.Fields.STATS);
        } else if (mCurrentTorrent != null) {
            fields = concat(new String[] {"id"}, Torrent.Fields.STATS_EXTRA);
            if (mCurrentTorrent.getHashString() == null) {
                fields = concat(fields, Torrent.Fields.INFO_EXTRA);
            }
        } else {
            fields = Torrent.Fields.STATS;
        }
        try {
            if (mCurrentTorrent != null) {
                torrents = mSessManager.getTorrents(new int[] {mCurrentTorrent.getId()}, fields).getTorrents();
            } else if (mNeedingMoreInfo.size() > 0) {
                int ids[] = new int[mNeedingMoreInfo.size()];
                int index = 0;
                for (Integer i : mNeedingMoreInfo)
                    ids[index++] = i;
                torrents = mSessManager.getTorrents(ids, fields).getTorrents();
                mNeedingMoreInfo.clear();
            } else if (active) {
                int full = mDefaultPrefs.getInt(GeneralSettingsFragment.PREF_FULL_UPDATE, 2);
                
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
                }
            }
        }
        
        for (Torrent t : torrents) {
            if (t.getTotalSize() == 0 && t.getMetadataPercentComplete() == 1) {
                mNeedingMoreInfo.add(t.getId());
            }
            Torrent torrent;
            if ((torrent = mTorrentMap.get(t.getId())) != null) {
                torrent.updateFrom(t, fields);
            } else {
                mTorrents.add(t);
                mTorrentMap.put(t.getId(), t);
            }
        }

        mIteration++;
        return new TransmissionSessionData(
                mSession, mSessionStats, mTorrents.toArray(new Torrent[mTorrents.size()]));
    }

    @Override
    public void deliverResult(TransmissionSessionData data) {
        if (isReset()) {
            return;
        }

        if (isStarted()) {
            TorrentListActivity.logD("TLoader: Delivering results: {0} torrents", new Object[] {data.torrents.length});
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
                    mSession, mSessionStats, mTorrents.toArray(new Torrent[mTorrents.size()])));
        
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
        
        return new TransmissionSessionData(mSession, mSessionStats);
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
