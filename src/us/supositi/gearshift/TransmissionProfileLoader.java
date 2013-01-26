package us.supositi.gearshift;

import android.app.Activity;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;

public class TransmissionProfileLoader extends AsyncTaskLoader<TransmissionProfile[]> {
    private TransmissionProfile[] mProfiles;
    
    private OnSharedPreferenceChangeListener mListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(
                SharedPreferences sharedPreferences, String key) {
            TorrentListActivity.logD("TPLoader: the pref of a profile has changed.");
            onContentChanged();
        }
        
    };
    
    
    private OnSharedPreferenceChangeListener mDefaultListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(
                SharedPreferences sharedPreferences, String key) {
            TorrentListActivity.logD("Detault prefs changed " + key);
            if (key.equals(TransmissionProfile.PREF_PROFILES)) {
                TorrentListActivity.logD("TPLoader: the pref 'profiles' has changed.");
                onContentChanged();
            }
        }
    };

    public TransmissionProfileLoader(Context context) {
        super(context);
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
        prefs.registerOnSharedPreferenceChangeListener(mDefaultListener);
    }

    @Override
    public TransmissionProfile[] loadInBackground() {
        TransmissionProfile[] profiles = TransmissionProfile.readProfiles(getContext().getApplicationContext());
        
        TorrentListActivity.logD("TPLoader: Read {0} profiles", new Object[] {profiles.length});
        
        for (TransmissionProfile prof : profiles) {
            SharedPreferences prefs = getContext().getApplicationContext().getSharedPreferences(
                    TransmissionProfile.PREF_PREFIX + prof.getId(), Activity.MODE_PRIVATE);
            prefs.registerOnSharedPreferenceChangeListener(mListener);
        }

        return profiles;
    }

    @Override
    public void deliverResult(TransmissionProfile[] profiles) {
        if (isReset()) {
            if (profiles != null) {
                onReleaseResources(profiles);
                return;
            }
        }
        
        TransmissionProfile[] oldProfiles = mProfiles;
        mProfiles = profiles;
        
        if (isStarted()) {
            TorrentListActivity.logD("TPLoader: Delivering results: {0} profiles", new Object[] {profiles.length});
            super.deliverResult(profiles);
        }
        
        if (oldProfiles != null) {
            for (TransmissionProfile prof : oldProfiles) {
                for (TransmissionProfile newProf : mProfiles) {
                    if (!prof.getId().equals(newProf.getId())) {
                        SharedPreferences prefs = getContext().getApplicationContext().getSharedPreferences(
                                TransmissionProfile.PREF_PREFIX + prof.getId(), Activity.MODE_PRIVATE);
                        prefs.unregisterOnSharedPreferenceChangeListener(mListener);
                    }
                }
            }
        }
    }
    
    @Override
    public void onCanceled(TransmissionProfile[] profiles) {
        super.onCanceled(profiles);
        
        onReleaseResources(profiles);
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
        
        if (mProfiles != null) {
            onReleaseResources(mProfiles);
            mProfiles = null;
        }
    }
    
    protected void onReleaseResources(TransmissionProfile[] profiles) {
        for (TransmissionProfile prof : profiles) {
            SharedPreferences prefs = getContext().getApplicationContext().getSharedPreferences(
                    TransmissionProfile.PREF_PREFIX + prof.getId(), Activity.MODE_PRIVATE);
            prefs.unregisterOnSharedPreferenceChangeListener(mListener);
        }
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
        prefs.unregisterOnSharedPreferenceChangeListener(mDefaultListener);
    }
}
