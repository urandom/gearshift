package org.sugr.gearshift.model;

public class TransmissionProfile extends Profile {
    public TransmissionProfile() {
        super();

        port = 9091;
        path = "/transmission/rpc";
    }
}
