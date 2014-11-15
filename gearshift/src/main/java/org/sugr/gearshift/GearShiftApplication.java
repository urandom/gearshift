package org.sugr.gearshift;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.sugr.gearshift.util.FeedParser;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

public class GearShiftApplication extends Application {
    private static boolean activityVisible;
    private static boolean startupInitialized;
    private static final String UPDATE_URL = "https://github.com/urandom/gearshift/releases.atom";
    private static final String RELEASE_APK = "gearshift-release.apk";

    private RequestQueue requestQueue;

    public interface OnUpdateCheck {
        public void onNewRelease(String title, String url, String downloadUrl);
        public void onCurrentRelease();
        public void onUpdateCheckError(Exception e);
    }

    @Override public void onCreate() {
        super.onCreate();

        requestQueue = Volley.newRequestQueue(this);

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread thread, Throwable ex) {
                handleUncaughtException(thread, ex);
            }
        });
    }

    public static boolean isActivityVisible() {
        return activityVisible;
    }

    public static void setActivityVisible(boolean visible) {
        activityVisible = visible;
    }

    public static boolean isStartupInitialized() {
        return startupInitialized;
    }

    public static void setStartupInitialized(boolean initialized) {
        startupInitialized = initialized;
    }

    public RequestQueue getRequestQueue() {
        return requestQueue;
    }

    public void checkForUpdates(final OnUpdateCheck onUpdateCheck) {
        StringRequest request = new StringRequest(UPDATE_URL, new Response.Listener<String>() {
            @Override public void onResponse(String response) {
                try {
                    String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;

                    List<FeedParser.Entry> entries = new FeedParser().parse(new ByteArrayInputStream(response.getBytes()));

                    if (!entries.isEmpty()) {
                        FeedParser.Entry entry = entries.get(0);
                        String url = "https://github.com" + entry.link;
                        String tag = entry.link.substring(entry.link.lastIndexOf('/') + 1);
                        String downloadUrl = "https://github.com/urandom/gearshift/releases/download/" + tag + "/" + RELEASE_APK;

                        if (versionCompare(version, tag) < 0) {
                            G.logD("New update available at " + url);

                            onUpdateCheck.onNewRelease(entry.title, url, downloadUrl);
                        } else {
                            onUpdateCheck.onCurrentRelease();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    onUpdateCheck.onUpdateCheckError(e);
                    return;
                }
            }
        }, new Response.ErrorListener() {
            @Override public void onErrorResponse(VolleyError error) {
                G.logD("Error fetching releases feed: " + error.toString());
                onUpdateCheck.onUpdateCheckError(error);
            }
        });

        requestQueue.add(request);
    }

    public Integer versionCompare(String str1, String str2) {
        String[] vals1 = str1.split("\\.");
        String[] vals2 = str2.split("\\.");

        int i = 0;
        // set index to first non-equal ordinal or length of shortest version string
        while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
            i++;
        }

        // compare first non-equal ordinal number
        if (i < vals1.length && i < vals2.length) {
            int diff = Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
            return Integer.signum(diff);
        } else {
            // the strings are equal or one string is a substring of the other
            // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
            return Integer.signum(vals1.length - vals2.length);
        }
    }

    private void handleUncaughtException(Thread thread, Throwable e) {
        e.printStackTrace();

        Intent intent = new Intent();
        intent.setAction("org.sugr.gearshift.CRASH_REPORT");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        System.exit(1);
    }
}
