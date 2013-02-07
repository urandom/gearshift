package org.sugr.gearshift;

import java.util.ArrayList;

public interface TransmissionSessionInterface {
    public void setTorrents(ArrayList<Torrent> torrents);
    public ArrayList<Torrent> getTorrents();
    
    public Torrent[] getCurrentTorrents();
}
