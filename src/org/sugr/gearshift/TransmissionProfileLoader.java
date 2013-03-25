package org.sugr.gearshift;

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
            G.logD("TPLoader: the pref of a profile has changed.");
            onContentChanged();
        }

    };


    private OnSharedPreferenceChangeListener mDefaultListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(
                SharedPreferences sharedPreferences, String key) {
            G.logD("Detault prefs changed " + key);
            if (key.equals(G.PREF_PROFILES)) {
                G.logD("TPLoader: the pref 'profiles' has changed.");
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

        G.logD("TPLoader: Read %d profiles", new Object[] {profiles.length});

        for (TransmissionProfile prof : profiles) {
            SharedPreferences prefs = getContext().getApplicationContext().getSharedPreferences(
                    G.PREF_PREFIX + prof.getId(), Activity.MODE_PRIVATE);
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
            G.logD("TPLoader: Delivering results: %d profiles", new Object[] {profiles.length});
            super.deliverResult(profiles);
        }

        if (oldProfiles != null) {
            for (TransmissionProfile prof : oldProfiles) {
                for (TransmissionProfile newProf : mProfiles) {
                    if (!prof.getId().equals(newProf.getId())) {
                        SharedPreferences prefs = getContext().getApplicationContext().getSharedPreferences(
                                G.PREF_PREFIX + prof.getId(), Activity.MODE_PRIVATE);
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
            onReleaseResources(mProfiles);
            mProfiles = null;
        }
    }

    protected void onReleaseResources(TransmissionProfile[] profiles) {
        if (profiles != null) {
            for (TransmissionProfile prof : profiles) {
                SharedPreferences prefs = getContext().getApplicationContext().getSharedPreferences(
                        G.PREF_PREFIX + prof.getId(), Activity.MODE_PRIVATE);
                prefs.unregisterOnSharedPreferenceChangeListener(mListener);
            }
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
        prefs.unregisterOnSharedPreferenceChangeListener(mDefaultListener);
    }
}
