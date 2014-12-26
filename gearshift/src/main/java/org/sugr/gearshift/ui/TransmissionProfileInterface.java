package org.sugr.gearshift.ui;

import org.sugr.gearshift.core.TransmissionProfile;

import java.util.List;

public interface TransmissionProfileInterface {
    public TransmissionProfile getProfile();
    public void setProfile(TransmissionProfile profile);
    public List<TransmissionProfile> getProfiles();
}
