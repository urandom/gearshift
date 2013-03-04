package org.sugr.gearshift;

import java.util.ArrayList;

public interface TransmissionSessionInterface {
    public void setProfile(TransmissionProfile profile);
    public TransmissionProfile getProfile();

    public void setSession(TransmissionSession session);
    public TransmissionSession getSession();

    public void setTorrents(ArrayList<Torrent> torrents);
    public ArrayList<Torrent> getTorrents();

    public Torrent[] getCurrentTorrents();

    public void setRefreshing(boolean refreshing);
}
