package org.sugr.gearshift.service;

import android.text.TextUtils;

import org.sugr.gearshift.G;
import org.sugr.gearshift.core.TransmissionProfile;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

public class ConnectionProvider {
    public HttpURLConnection open(TransmissionProfile profile) throws IOException {
        String location = (profile.isUseSSL() ? "https://" : "http://")
                + profile.getHost() + ":" + profile.getPort()
                + profile.getPath();

        Proxy proxy = getProxy(profile);
        return open(location, proxy);
    }

    public HttpURLConnection open(String location, Proxy proxy) throws IOException {
        URL url = new URL(location);

        G.logD("Initializing a request to " + url);

        if (proxy == null) {
            return (HttpURLConnection) url.openConnection();
        } else {
            return (HttpURLConnection) url.openConnection(proxy);
        }
    }

    public Proxy getProxy(TransmissionProfile profile) {
        Proxy proxy = null;

        if (profile.isUseProxy() && !TextUtils.isEmpty(profile.getProxyHost())) {
            proxy = new Proxy(Proxy.Type.HTTP,
                new InetSocketAddress(profile.getProxyHost(), profile.getProxyPort()));
        }

        return proxy;
    }
}
