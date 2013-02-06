package us.supositi.gearshift;

import java.util.ArrayList;

public interface TransmissionSessionInterface {
    public void setTorrents(ArrayList<Torrent> torrents);
    public ArrayList<Torrent> getTorrents();
}
