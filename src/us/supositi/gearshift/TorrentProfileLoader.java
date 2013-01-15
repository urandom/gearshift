package us.supositi.gearshift;

import android.content.AsyncTaskLoader;
import android.content.Context;

public class TorrentProfileLoader extends AsyncTaskLoader<TorrentProfile[]> {
    private TorrentProfile[] mProfiles;

    public TorrentProfileLoader(Context context) {
        super(context);
    }

    @Override
    public TorrentProfile[] loadInBackground() {
        TorrentProfile[] profiles = TorrentProfile.readProfiles(getContext());
        
        TorrentListActivity.logD("TPLoader: Read {0} profiles", new Object[] {profiles.length});
        return profiles;
    }

    @Override
    public void deliverResult(TorrentProfile[] profiles) {
        mProfiles = profiles;
        
        if (isStarted()) {
            TorrentListActivity.logD("TPLoader: Delivering results: {0} profiles", new Object[] {profiles.length});
            super.deliverResult(profiles);
        }
    }
    
    @Override
    protected void onStartLoading() {
        super.onStartLoading();
        
        TorrentListActivity.logD("TPLoader: onStartLoading()");
        
        if (mProfiles != null)
            deliverResult(mProfiles);
        
        if (takeContentChanged() || mProfiles == null) {
            TorrentListActivity.logD("TPLoader: forceLoad()");
            forceLoad();
        }
    }
    
    @Override
    protected void onStopLoading() {
        super.onStopLoading();
        
        TorrentListActivity.logD("TPLoader: onStopLoading()");
        cancelLoad();
    }
    
    @Override
    protected void onReset() {
        super.onReset();
        
        TorrentListActivity.logD("TPLoader: onReset()");
        
        onStopLoading();
        
        mProfiles = null;
    }
}
