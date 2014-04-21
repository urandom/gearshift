package org.sugr.gearshift.service;

import org.sugr.gearshift.G;
import org.sugr.gearshift.core.TransmissionProfile;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class ConnectionProvider {
    public HttpURLConnection open(TransmissionProfile profile) throws IOException {
        URL url = new URL(
            (profile.isUseSSL() ? "https://" : "http://")
                + profile.getHost() + ":" + profile.getPort()
                + profile.getPath());

        G.logD("Initializing a request to " + url);

        return (HttpURLConnection) url.openConnection();
    }
}
