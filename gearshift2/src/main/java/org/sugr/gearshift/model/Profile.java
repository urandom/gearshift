package org.sugr.gearshift.model;

import android.text.TextUtils;

import java.util.HashSet;
import java.util.Set;

public class Profile {
    public String id;
    public String name = "";
    public String host = "";
    public int port = 0;
    public String path = "";
    public String username = "";
    public String password = "";

    public boolean useSSL = false;

    public int timeout = 40;
    public int retries = 3;

    public String lastDirectory;
    public boolean moveData = true;
    public boolean deleteLocal = false;
    public boolean startPaused = false;

    public Set<String> directories = new HashSet<>();

    public String proxyHost = "";
    public int proxyPort = 8080;

    public int updateInterval = 1;
    public int fullUpdate = 2;

    public int color;

    public boolean proxyEnabled() {
        return !TextUtils.isEmpty(proxyHost) && proxyPort > 0;
    }
}
