package org.sugr.gearshift;

public interface TransmissionSessionInterface {
    public TransmissionProfile getProfile();

    public void setSession(TransmissionSession session);
    public TransmissionSession getSession();

    public void setRefreshing(boolean refreshing, String type);
}
