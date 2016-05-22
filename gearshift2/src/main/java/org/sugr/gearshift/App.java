package org.sugr.gearshift;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

import com.github.zafarkhaja.semver.Version;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import rx.Observable;
import rx.schedulers.Schedulers;

public class App extends Application {
    private static App app;
    private static final String UPDATE_URL = "https://api.github.com/repos/urandom/gearshift/releases";

    public static class Update {
        public final String title;
        public final String description;
        public final String url;
        public final String downloadUrl;

        public Update(String title, String description, String url, String downloadUrl) {
            this.title = title;
            this.description = description;
            this.url = url;
            this.downloadUrl = downloadUrl;
        }
    }

    public static class UpdateException extends RuntimeException {
        public final String networkError;
        public final String jsonError;
        public UpdateException(String networkError, String jsonError) {
            super("update exception");
            this.networkError = networkError;
            this.jsonError = jsonError;
        }
    }

    @Override public void onCreate() {
        super.onCreate();

        app = this;

        Thread.setDefaultUncaughtExceptionHandler(this::handleUncaughtException);
    }

    public Observable<Update> checkForUpdates() {
        return Observable.<Update>create(subscriber -> {
            Request request = new Request.Builder().url(UPDATE_URL).build();
            Gson gson = new Gson();

            try {
                Response response = new OkHttpClient().newCall(request).execute();

                JSONObject result = gson.fromJson(response.body().charStream(), JSONArray.class).getJSONObject(0);

                String tag = result.getString("tag_name");

                Version current = Version.valueOf(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
                Version remote = Version.valueOf(tag);

                Update update = null;

                if (remote.greaterThan(current)) {
                    update = new Update(result.getString("name"),
                            result.getString("body"),
                            result.getString("html_url"),
                            result.getJSONArray("assets").getJSONObject(0).getString("browser_download_url"));
                }

                subscriber.onNext(update);
            } catch (IOException e) {
                subscriber.onError(new UpdateException(e.toString(), null));
            } catch (JSONException e) {
                subscriber.onError(new UpdateException(null, e.toString()));
            } catch (PackageManager.NameNotFoundException e) {
                subscriber.onError(new UpdateException(null, null));
            }
        }).subscribeOn(Schedulers.io()).cacheWithInitialCapacity(1);
    }

    public static App get() {
        return app;
    }

    public static SharedPreferences defaultPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(app);
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
