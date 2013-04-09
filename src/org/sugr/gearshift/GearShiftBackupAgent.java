package org.sugr.gearshift;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.preference.PreferenceManager;

public class GearShiftBackupAgent extends BackupAgentHelper {
    @Override
    public void onCreate() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> profile_ids = prefs.getStringSet(G.PREF_PROFILES, new HashSet<String>());
        ArrayList<String> names = new ArrayList<String>();
        Context context = getApplicationContext();
        PackageManager manager = context.getPackageManager();
        PackageInfo info;
        try {
            info = manager.getPackageInfo(context.getPackageName(), 0);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
            return;
        }
        String packageName = info.packageName;

        names.add(packageName + "_preferences");

        for (String id : profile_ids) {
            names.add(G.PREF_PREFIX + id);
        }

        SharedPreferencesBackupHelper helper =
            new SharedPreferencesBackupHelper(this, names.toArray(new String[names.size()]));
        addHelper(packageName + "_backup", helper);
    }
}
