package org.sugr.gearshift;

import android.app.Application;
import android.content.Intent;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.github.zafarkhaja.semver.Version;

import org.json.JSONArray;
import org.json.JSONObject;

public class GearShiftApplication extends Application {
    private static boolean activityVisible;
    private static boolean startupInitialized;
    private static final String UPDATE_URL = "https://api.github.com/repos/urandom/gearshift/releases";

    private RequestQueue requestQueue;

    public interface OnUpdateCheck {
        public void onNewRelease(String title, String description, String url, String downloadUrl);
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
        JsonArrayRequest request = new JsonArrayRequest(UPDATE_URL, new Response.Listener<JSONArray>() {
            @Override public void onResponse(JSONArray response) {
                try {
                    String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;

                    if (response.length() > 0) {
                        JSONObject latest = response.getJSONObject(0);

                        String url = latest.getString("html_url");
                        String tag = latest.getString("tag_name");
                        String name = latest.getString("name");
                        String description = latest.getString("body");
                        String downloadUrl = latest.getJSONArray("assets").getJSONObject(0).getString("browser_download_url");

                        Version current = Version.valueOf(version);
                        Version remote = Version.valueOf(tag);

                        if (remote.greaterThan(current)) {
                            G.logD("New update available at " + url);

                            onUpdateCheck.onNewRelease(name, description, url, downloadUrl);
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

    private void handleUncaughtException(Thread thread, Throwable e) {
        e.printStackTrace();

        Intent intent = new Intent();
        intent.setAction("org.sugr.gearshift.CRASH_REPORT");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        System.exit(1);
    }
}
