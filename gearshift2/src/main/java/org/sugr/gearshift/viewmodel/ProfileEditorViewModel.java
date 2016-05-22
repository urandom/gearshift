package org.sugr.gearshift.viewmodel;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.databinding.ObservableBoolean;
import android.databinding.ObservableField;
import android.databinding.ObservableInt;
import android.view.View;

import org.sugr.gearshift.App;
import org.sugr.gearshift.R;
import org.sugr.gearshift.model.Profile;
import org.sugr.gearshift.viewmodel.util.ResourceUtils;

public class ProfileEditorViewModel extends RetainedViewModel<ProfileEditorViewModel.Consumer> {
    public final ObservableField<String> profileName = new ObservableField<>("Default");
    public final ObservableField<String> host = new ObservableField<>("");
    public final ObservableInt port = new ObservableInt(9091);
    public final ObservableBoolean useSSL = new ObservableBoolean(false);

    public final ObservableField<String> updateIntervalLabel = new ObservableField<>("");
    public final ObservableInt updateIntervalValue = new ObservableInt(0);

    public final ObservableField<String> fullUpdateLabel = new ObservableField<>("");
    public final ObservableInt fullUpdateValue = new ObservableInt(0);

    public final ObservableField<String> username = new ObservableField<>("");
    public final ObservableField<String> password = new ObservableField<>("");

    public final ObservableField<String> proxyHost = new ObservableField<>("");
    public final ObservableField<String> proxyPort = new ObservableField<>("");

    public final ObservableInt timeout = new ObservableInt(40);
    public final ObservableField<String> path = new ObservableField<>("");

    private final String[] updateIntervalEntries;
    private final int[] updateIntervalValues;

    private final String[] fullUpdateEntries;
    private final int[] fullUpdateValues;

    private final Profile profile;

    public interface Consumer {
    }

    public ProfileEditorViewModel(SharedPreferences prefs, Profile profile) {
        super(prefs);

        this.profile = profile;

        Resources resources = App.get().getResources();
        updateIntervalEntries = resources.getStringArray(R.array.pref_update_interval_entries);
        updateIntervalValues = ResourceUtils.stringArrayAsInt(R.array.pref_update_interval_values);

        fullUpdateEntries = resources.getStringArray(R.array.pref_full_update_entries);
        fullUpdateValues = ResourceUtils.stringArrayAsInt(R.array.pref_full_update_values);

        updateIntervalLabel.set(updateIntervalEntries[0]);
        updateIntervalValue.set(updateIntervalValues[0]);

        fullUpdateLabel.set(fullUpdateEntries[0]);
        fullUpdateValue.set(fullUpdateValues[0]);

        path.set(profile.path);
    }

    public void onPickUpdateInterval(View unused) {

    }

    public void onPickFullUpdate(View unused) {

    }
}
