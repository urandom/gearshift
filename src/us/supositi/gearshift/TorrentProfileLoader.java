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
        
        return profiles;
    }

    @Override
    public void deliverResult(TorrentProfile[] profiles) {
        mProfiles = profiles;
        
        if (isStarted())
            super.deliverResult(profiles);
    }
    
    @Override
    protected void onStartLoading() {
        super.onStartLoading();
        
        if (mProfiles != null)
            deliverResult(mProfiles);
        
        if (takeContentChanged() || mProfiles == null)
            forceLoad();
    }
    
    @Override
    protected void onStopLoading() {
        super.onStopLoading();
        cancelLoad();
    }
    
    @Override
    protected void onReset() {
        super.onReset();
        
        cancelLoad();
        
        mProfiles = null;
    }
}
