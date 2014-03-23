package org.sugr.gearshift;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.support.v4.content.AsyncTaskLoader;
import android.text.TextUtils;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;

public class TransmissionProfileSupportLoader extends AsyncTaskLoader<TransmissionProfile[]> {
    private TransmissionProfile[] mProfiles;

    private OnSharedPreferenceChangeListener mListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(
                SharedPreferences sharedPreferences, String key) {
            onContentChanged();
        }

    };


    private OnSharedPreferenceChangeListener mDefaultListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(
                SharedPreferences sharedPreferences, String key) {
            if (key.equals(G.PREF_PROFILES)) {
                onContentChanged();
            }
        }
    };

    public TransmissionProfileSupportLoader(Context context) {
        super(context);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
        prefs.registerOnSharedPreferenceChangeListener(mDefaultListener);

        prefs = getContext().getSharedPreferences(TransmissionProfile.getPreferencesName(),
            Activity.MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(mListener);
    }

    @Override
    public TransmissionProfile[] loadInBackground() {
        TransmissionProfile[] profiles = TransmissionProfile.readProfiles(getContext().getApplicationContext());
        List<TransmissionProfile> profileList = new ArrayList<>();
        for (TransmissionProfile profile : profiles) {
            if (!TextUtils.isEmpty(profile.getHost())) {
                profileList.add(profile);
            }
        }

        G.logD("TPLoader: Read %d profiles", new Object[] {profileList.size()});

        return profileList.toArray(new TransmissionProfile[profileList.size()]);
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

        prefs = getContext().getSharedPreferences(TransmissionProfile.getPreferencesName(),
            Activity.MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(mListener);
    }
}
