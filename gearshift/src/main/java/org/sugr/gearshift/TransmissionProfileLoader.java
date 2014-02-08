package org.sugr.gearshift;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;

public class TransmissionProfileLoader extends AsyncTaskLoader<TransmissionProfile[]> {
    private TransmissionProfile[] mProfiles;

    private OnSharedPreferenceChangeListener mListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            onContentChanged();
        }
    };


    private OnSharedPreferenceChangeListener mDefaultListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged( SharedPreferences sharedPreferences, String key) {
            if (key.equals(G.PREF_PROFILES)) {
                onContentChanged();
            }
        }
    };

    public TransmissionProfileLoader(Context context) {
        super(context);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
        prefs.registerOnSharedPreferenceChangeListener(mDefaultListener);

        prefs = TransmissionProfile.getPreferences(getContext());
        prefs.registerOnSharedPreferenceChangeListener(mListener);
    }

    @Override
    public TransmissionProfile[] loadInBackground() {
        TransmissionProfile[] profiles = TransmissionProfile.readProfiles(getContext().getApplicationContext());

        G.logD("TPLoader: Read %d profiles", new Object[] {profiles.length});

        return profiles;
    }

    @Override
    public void deliverResult(TransmissionProfile[] profiles) {
        if (isReset()) {
            if (profiles != null) {
                onReleaseResources();
                return;
            }
        }

        mProfiles = profiles;

        if (isStarted()) {
            G.logD("TPLoader: Delivering results: %d profiles", new Object[] {profiles.length});
            super.deliverResult(profiles);
        }
    }

    @Override
    public void onCanceled(TransmissionProfile[] profiles) {
        super.onCanceled(profiles);

        onReleaseResources();
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();

        G.logD("TPLoader: onStartLoading()");

        if (mProfiles != null)
            deliverResult(mProfiles);

        if (takeContentChanged() || mProfiles == null) {
            G.logD("TPLoader: forceLoad()");
            forceLoad();
        }
    }

    @Override
    protected void onStopLoading() {
        super.onStopLoading();

        G.logD("TPLoader: onStopLoading()");
        cancelLoad();
    }

    @Override
    protected void onReset() {
        super.onReset();

        G.logD("TPLoader: onReset()");

        onStopLoading();

        if (mProfiles != null) {
            onReleaseResources();
            mProfiles = null;
        }
    }

    protected void onReleaseResources() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
        prefs.unregisterOnSharedPreferenceChangeListener(mDefaultListener);

        prefs = TransmissionProfile.getPreferences(getContext());
        prefs.registerOnSharedPreferenceChangeListener(mListener);
    }
}
