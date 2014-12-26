package org.sugr.gearshift.ui;

import android.database.Cursor;

import org.sugr.gearshift.core.TransmissionProfile;

import java.util.List;

public interface TransmissionProfileListNotificationInterface {
    public void notifyTransmissionProfileListChanged(List<TransmissionProfile> profiles);
}
