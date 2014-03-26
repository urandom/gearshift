package org.sugr.gearshift.misc;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;

import org.sugr.gearshift.G;

public class BackupAgent extends BackupAgentHelper {
    private static final String PREFS_BACKUP_KEY = "prefs";

    @Override
    public void onCreate() {
        String packageName = getPackageName();

        SharedPreferencesBackupHelper helper =
            new SharedPreferencesBackupHelper(this,
                    packageName + "_preferences", G.PROFILES_PREF_NAME);
        addHelper(PREFS_BACKUP_KEY, helper);

        G.logD("Backup agent invoked.");
    }
}
