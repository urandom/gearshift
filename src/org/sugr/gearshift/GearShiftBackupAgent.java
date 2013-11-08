package org.sugr.gearshift;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class GearShiftBackupAgent extends BackupAgentHelper {
    private static final String PREFS_BACKUP_KEY = "prefs";

    @Override
    public void onCreate() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> profile_ids = prefs.getStringSet(G.PREF_PROFILES, new HashSet<String>());
        ArrayList<String> names = new ArrayList<String>();
        String packageName = getPackageName();

        names.add(packageName + "_preferences");

        for (String id : profile_ids) {
            names.add(G.PREF_PREFIX + id);
        }

        SharedPreferencesBackupHelper helper =
            new SharedPreferencesBackupHelper(this, names.toArray(new String[names.size()]));
        addHelper(PREFS_BACKUP_KEY, helper);
    }
}
