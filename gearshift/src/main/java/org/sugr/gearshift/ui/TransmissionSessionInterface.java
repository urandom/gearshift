package org.sugr.gearshift.ui;

import org.sugr.gearshift.core.TransmissionProfile;
import org.sugr.gearshift.core.TransmissionSession;

public interface TransmissionSessionInterface {
    public void setSession(TransmissionSession session);
    public TransmissionSession getSession();

    public void setRefreshing(boolean refreshing, String type);
}
