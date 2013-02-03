package us.supositi.gearshift;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import us.supositi.gearshift.TransmissionSessionManager.ManagerException;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.AsyncTaskLoader;

public class TorrentSupportLoader extends AsyncTaskLoader<ArrayList<Torrent>> {
    private ArrayList<Torrent> mTorrents = new ArrayList<Torrent>();
    private static HashMap<String, Torrent> mTorrentMap = new HashMap<String, Torrent>();
    private TransmissionProfile mProfile;
    
    private TransmissionSession mSession;
    
    private TransmissionSessionManager mSessManager;
    
    private int iteration = 0;
    private boolean mStopUpdates = false;

    private Handler mIntervalHandler = new Handler();
    private Runnable mIntervalRunner = new Runnable() {
        @Override
        public void run() {
            if (mProfile != null && !mStopUpdates)
                onContentChanged();
        }
    };

    public TorrentSupportLoader(Context context, TransmissionProfile profile) {
        super(context);
        
        mProfile = profile;
        
        mSessManager = new TransmissionSessionManager(getContext(), mProfile);
    }

    @Override
    public ArrayList<Torrent> loadInBackground() {
        if (mSession == null || iteration % 3 == 0) {
            try {
                mSession = mSessManager.getSession();
            } catch (IOException e) {
                mStopUpdates = true;
                e.printStackTrace();
            } catch (ManagerException e) {
                mStopUpdates = true;
                e.printStackTrace();
            }
        }
        /*
        for (Torrent t : torrents) {
            Torrent torrent = mTorrentMap.get(t.getId());
            if (torrent == null) {
                torrent = t;
                mTorrents.add(t);
                mTorrentMap.put(t.getId(), t);
            }
        }*/

        iteration++;
        return mTorrents;
    }

    @Override
    public void deliverResult(ArrayList<Torrent> torrents) {
        if (isReset()) {
            return;
        }

        if (isStarted()) {
            TorrentListActivity.logD("TLoader: Delivering results: {0} torrents", new Object[] {mTorrents.size()});
            super.deliverResult(torrents);
        }

        repeatLoading();
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();
        
        TorrentListActivity.logD("TLoader: onStartLoading()");
        
        if (mTorrents.size() > 0)
            deliverResult(mTorrents);
        
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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        int update = Integer.parseInt(prefs.getString(GeneralSettingsFragment.PREF_UPDATE_INTERVAL, "-1"));
        if (update >= 0 && !isReset())
            mIntervalHandler.postDelayed(mIntervalRunner, update * 1000);
    }
}
