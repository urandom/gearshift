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
    public Torrent[] torrents = new Torrent[0];
    
    public TransmissionSessionData(TransmissionSession session) {
        this.session = session;
    }
    
    public TransmissionSessionData(TransmissionSession session, Torrent[] torrents) {
        this.session = session;
        this.torrents = torrents;
    }
}

public class TransmissionSessionLoader extends AsyncTaskLoader<TransmissionSessionData> {
    private ArrayList<Torrent> mTorrents = new ArrayList<Torrent>();
    private static HashMap<Integer, Torrent> mTorrentMap = new HashMap<Integer, Torrent>();
    private TransmissionProfile mProfile;
    
    private TransmissionSession mSession;
    
    private TransmissionSessionManager mSessManager;
    
    private int mIteration = 0;
    private boolean mStopUpdates = false;
    
    private SharedPreferences mDefaultPrefs;

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

    @Override
    public TransmissionSessionData loadInBackground() {
        if (mSession == null || mIteration % 3 == 0) {
            try {
                mSession = mSessManager.getSession().getSession();
            } catch (ManagerException e) {
                return handleError(e);
            }
        }
        
        boolean active = mDefaultPrefs.getBoolean(GeneralSettingsFragment.PREF_UPDATE_ACTIVE, false);
        Torrent [] torrents;
        int[] removed = null;
        String[] fields = null;
        
        try {
            if (active) {
                int full = mDefaultPrefs.getInt(GeneralSettingsFragment.PREF_FULL_UPDATE, 2);
                
                if (mIteration % full == 0) {
                    torrents = mSessManager.getAllTorrents().getTorrents();
                } else {
                    ActiveTorrentGetResponse response = mSessManager.getActiveTorrents();
                    torrents = response.getTorrents();
                    removed = response.getRemoved();
                }
            } else {
                torrents = mSessManager.getAllTorrents().getTorrents();
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
                mSession, mTorrents.toArray(new Torrent[mTorrents.size()]));
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
                    mSession, mTorrents.toArray(new Torrent[mTorrents.size()])));
        
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
        
        return new TransmissionSessionData(mSession);
    }    
}
