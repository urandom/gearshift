package org.sugr.gearshift.ui.loader;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.support.v4.content.AsyncTaskLoader;

import org.sugr.gearshift.G;
import org.sugr.gearshift.core.TransmissionProfile;

import java.util.ArrayList;
import java.util.List;

public class TransmissionProfileSupportLoader extends AsyncTaskLoader<TransmissionProfile[]> {
    private TransmissionProfile[] profiles;
    private boolean ignoreInvalid;

    private OnSharedPreferenceChangeListener listener = (sharedPreferences, key) -> onContentChanged();


    private OnSharedPreferenceChangeListener defaultListener = (sharedPreferences, key) -> {
        if (key.equals(G.PREF_PROFILES)) {
            onContentChanged();
        }
    };

    public TransmissionProfileSupportLoader(Context context) {
        this(context, false);
    }

    public TransmissionProfileSupportLoader(Context context, boolean ignoreInvalid) {
        super(context);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
        prefs.registerOnSharedPreferenceChangeListener(defaultListener);

        prefs = getContext().getSharedPreferences(TransmissionProfile.getPreferencesName(),
            Activity.MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(listener);

        this.ignoreInvalid = ignoreInvalid;
    }

    @Override
    public TransmissionProfile[] loadInBackground() {
        Context context = getContext().getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        TransmissionProfile[] profiles = TransmissionProfile.readProfiles(prefs);
        List<TransmissionProfile> profileList = new ArrayList<>();
        for (TransmissionProfile profile : profiles) {
            if (profile.isValid() || ignoreInvalid) {
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

        this.profiles = profiles;

        if (isStarted()) {
            G.logD("TPLoader: Delivering results: %d profiles", new Object[] {
                    profiles == null ? 0 : profiles.length
            });
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

        if (profiles != null)
            deliverResult(profiles);

        if (takeContentChanged() || profiles == null) {
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

        if (profiles != null) {
            onReleaseResources();
            profiles = null;
        }
    }

    protected void onReleaseResources() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext().getApplicationContext());
        prefs.unregisterOnSharedPreferenceChangeListener(defaultListener);

        prefs = getContext().getSharedPreferences(TransmissionProfile.getPreferencesName(),
            Activity.MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }
}
