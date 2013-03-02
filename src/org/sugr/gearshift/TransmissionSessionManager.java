package org.sugr.gearshift;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.util.Base64;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

public class TransmissionSessionManager {
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Exclude {}

    public static class TransmissionExclusionStrategy implements ExclusionStrategy {
        @Override
        public boolean shouldSkipClass(Class<?> cls) {
            return cls.getAnnotation(Exclude.class) != null;
        }

        @Override
        public boolean shouldSkipField(FieldAttributes field) {
            return field.getAnnotation(Exclude.class) != null;
        }
    }

    public static class KeyExclusionStrategy implements ExclusionStrategy {
        HashSet<String> mKeys = new HashSet<String>();
        public KeyExclusionStrategy(String... keys) {
            super();

            if (keys != null) {
                for (String key : keys)
                    mKeys.add(key);
            }
            mKeys.add("method");
            mKeys.add("arguments");
        }

        @Override
        public boolean shouldSkipClass(Class<?> cls) {
            SerializedName serializedName = cls.getAnnotation(SerializedName.class);
            if (serializedName == null)
                return false;

            return !mKeys.contains(serializedName.value());
        }

        @Override
        public boolean shouldSkipField(FieldAttributes field) {
            SerializedName serializedName = field.getAnnotation(SerializedName.class);
            if (serializedName == null)
                return false;

            return !mKeys.contains(serializedName.value());
        }
    }


    public class ManagerException extends Exception {
        private static final long serialVersionUID = 6477491498169428449L;
        int mCode;
        public ManagerException(String message, int code) {
            super(message);
            mCode = code;
        }

        public int getCode() {
            return mCode;
        }
    }

    public final static String PREF_LAST_SESSION_ID = "last_session_id";

//    private Context mContext;
    private TransmissionProfile mProfile;

    private ConnectivityManager mConnManager;

    private String mSessionId;

    private int mInvalidSessionRetries = 0;
    private SharedPreferences mDefaultPrefs;

    public TransmissionSessionManager(Context context, TransmissionProfile profile) {
//        mContext = context;
        mProfile = profile;

        mConnManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mDefaultPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        mSessionId = mDefaultPrefs.getString(PREF_LAST_SESSION_ID, null);
    }

    public boolean hasConnectivity() {
        NetworkInfo info = mConnManager.getActiveNetworkInfo();
        return (info != null);
    }

    public SessionGetResponse getSession() throws ManagerException {
        SessionGetRequest request = new SessionGetRequest();

        String json = requestData(request);
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        SessionGetResponse response = gson.fromJson(json, SessionGetResponse.class);

        return response;
    }

    public SessionStatsResponse getSessionStats() throws ManagerException {
        SessionStatsRequest request = new SessionStatsRequest();

        String json = requestData(request);
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        SessionStatsResponse response = gson.fromJson(json, SessionStatsResponse.class);

        return response;
    }

    public ActiveTorrentGetResponse getActiveTorrents(String[] fields) throws ManagerException {
        ActiveTorrentGetRequest request = new ActiveTorrentGetRequest(fields);
        String json = requestData(request);
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        ActiveTorrentGetResponse response = gson.fromJson(json, ActiveTorrentGetResponse.class);

        return response;
    }

    public TorrentGetResponse getAllTorrents(String[] fields) throws ManagerException {
        AllTorrentGetRequest request = new AllTorrentGetRequest(fields);

        String json = requestData(request);
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        TorrentGetResponse response = gson.fromJson(json, TorrentGetResponse.class);

        return response;
    }

    public TorrentGetResponse getTorrents(int[] ids, String[] fields) throws ManagerException {
        TorrentGetRequest request = new TorrentGetRequest(ids, fields);

        String json = requestData(request);
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        TorrentGetResponse response = gson.fromJson(json, TorrentGetResponse.class);

        return response;
    }

    public Response setSession(TransmissionSession session, String... keys) throws ManagerException {
        SessionSetRequest request = new SessionSetRequest(session);

        String json = requestData(request, new KeyExclusionStrategy(keys));
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        Response response = gson.fromJson(json, Response.class);

        return response;
    }

    public Response setTorrentsRemove(int[] ids, boolean delete) throws ManagerException {
        TorrentsRemoveRequest request = new TorrentsRemoveRequest(ids, delete);
        String json = requestData(request);
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        Response response = gson.fromJson(json, Response.class);

        return response;
    }

    public Response setTorrentsAction(String action, int[] ids) throws ManagerException {
        TorrentsActionRequest request = new TorrentsActionRequest(action, ids);
        String json = requestData(request);
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        Response response = gson.fromJson(json, Response.class);

        return response;
    }

    public Response setTorrentsLocation(int[] ids, String location, boolean move) throws ManagerException {
        TorrentsSetLocationRequest request = new TorrentsSetLocationRequest(ids, location, move);
        String json = requestData(request);
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        Response response = gson.fromJson(json, Response.class);

        return response;
    }

    private String requestData(Object data, ExclusionStrategy... strategies) throws ManagerException {
        OutputStream os = null;
        InputStream is = null;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(
                  (mProfile.isUseSSL() ? "https://" : "http://")
                + mProfile.getHost() + ":" + mProfile.getPort()
                + mProfile.getPath());
            conn = (HttpURLConnection) url.openConnection();
            int timeout = mProfile.getTimeout() > 0
                ? mProfile.getTimeout() * 1000
                : 10000;
            conn.setReadTimeout(timeout);
            conn.setConnectTimeout(timeout);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
            conn.setUseCaches(false);
            conn.setAllowUserInteraction(false);

            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);

            if (mSessionId != null) {
                conn.setRequestProperty("X-Transmission-Session-Id", mSessionId);
            }

            String user = mProfile.getUsername();
            if (user != null && user.length() > 0) {
                conn.setRequestProperty("Authorization",
                        "Basic " + Base64.encodeToString(
                                (user + ":" + mProfile.getPassword()).getBytes(), Base64.DEFAULT));
            }

            os = conn.getOutputStream();
            Gson gson = new GsonBuilder().setExclusionStrategies(strategies)
                .addSerializationExclusionStrategy(
                        new TransmissionExclusionStrategy()).create();
            String json = gson.toJson(data);
            os.write(json.getBytes());
            os.flush();
            os.close();

            // Starts the query
            conn.connect();

            int code = conn.getResponseCode();

            // TorrentListActivity.logD("Got a response code " + code);
            if (code == 409) {
                mSessionId = getSessionId(conn);
                if (mInvalidSessionRetries < 3 && mSessionId != null) {
                    ++mInvalidSessionRetries;
                    return requestData(data);
                } else {
                    mInvalidSessionRetries = 0;
                }
            } else {
                mInvalidSessionRetries = 0;
            }

            String contentAsString = null;
            switch(code) {
                case 200:
                case 201:
                    if (conn.getHeaderField("Content-Type").startsWith("text/html")) {
                        throw new ManagerException("no-json", code);
                    }
                    is = conn.getInputStream();

                    // Convert the InputStream into a string
                    String encoding = conn.getContentEncoding();

                    if (encoding != null && encoding.equalsIgnoreCase("gzip")) {
                        is = new GZIPInputStream(is);
                    } else if (encoding != null && encoding.equalsIgnoreCase("deflate")) {
                        is = new InflaterInputStream(is);
                    }
                    contentAsString = inputStreamToString(is);
                    break;
                default:
                    throw new ManagerException(conn.getResponseMessage(), code);
            }

            return contentAsString;
        } catch (IOException e) {
            throw new ManagerException(e.getMessage(), -1);
        } finally {
            try {
                if (os != null)
                    os.close();
                if (is != null)
                    is.close();
            } catch(IOException e) {}

            if (conn != null)
                conn.disconnect();
        }
    }

    private String getSessionId(HttpURLConnection conn) {
        String id = conn.getHeaderField("X-Transmission-Session-Id");

        if (id != null && !id.equals("")) {
            Editor e = mDefaultPrefs.edit();
            e.putString(PREF_LAST_SESSION_ID, id);
            e.commit();
        }
        return id;
    }

    private String inputStreamToString(InputStream stream) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line+"\n");
        }
        br.close();
        return sb.toString();
    }

    private static class SessionGetRequest {
       @SerializedName("method") private final String method = "session-get";
    }

    private static class SessionSetRequest {
       @SerializedName("method") private final String method = "session-set";
       @SerializedName("arguments") private TransmissionSession arguments;

       public SessionSetRequest(TransmissionSession session) {
           this.arguments = session;
       }
    }

    private static class SessionStatsRequest {
       @SerializedName("method") private final String method = "session-stats";
    }

    private static class AllTorrentGetRequest {
        @SerializedName("method") private final String method = "torrent-get";
        @SerializedName("arguments") private Arguments arguments;

        public AllTorrentGetRequest(String[] fields) {
            this.arguments = new Arguments(fields);
        }

        private static class Arguments {
            @SerializedName("fields") private String[] fields;

            public Arguments(String[] fields) {
                this.fields = fields;
            }
        }
     }

    private static class TorrentGetRequest {
       @SerializedName("method") private final String method = "torrent-get";
       @SerializedName("arguments") private Arguments arguments;

       public TorrentGetRequest(int[] ids, String[] fields) {
           this.arguments = new Arguments(ids, fields);
       }

       private static class Arguments {
           @SerializedName("ids") private int[] ids;
           @SerializedName("fields") private String[] fields;

           public Arguments(int[] ids, String[] fields) {
               this.ids = ids;
               this.fields = fields;
           }
       }
    }

    private static class ActiveTorrentGetRequest {
       @SerializedName("method") private final String method = "torrent-get";
       @SerializedName("arguments") private Arguments arguments;

       public ActiveTorrentGetRequest(String[] fields) {
           this.arguments = new Arguments(fields);
       }

       private static class Arguments {
           @SerializedName("ids") private final String ids = "recently-active";
           @SerializedName("fields") private String[] fields;

           public Arguments(String[] fields) {
               this.fields = fields;
           }
       }
    }

    private static class TorrentsRemoveRequest {
       @SerializedName("method") private final String method = "torrent-remove";
       @SerializedName("arguments") private Arguments arguments;

       public TorrentsRemoveRequest(int[] ids, boolean delete) {
           this.arguments = new Arguments(ids, delete);
       }

       private static class Arguments {
           @SerializedName("ids") private int[] ids;
           @SerializedName("delete-local-data") private boolean delete;

           public Arguments(int[] ids, boolean delete) {
               this.ids = ids;
               this.delete = delete;
           }
       }
    }

    private static class TorrentsActionRequest {
       @SerializedName("method") private String method;
       @SerializedName("arguments") private Arguments arguments;

       public TorrentsActionRequest(String action, int[] ids) {
           this.method = action;
           this.arguments = new Arguments(ids);
       }

       private static class Arguments {
           @SerializedName("ids") private int[] ids;

           public Arguments(int[] ids) {
               this.ids = ids;
           }
       }
    }

    private static class TorrentsSetLocationRequest {
        @SerializedName("method") private final String method = "torrent-set-location";
        @SerializedName("arguments") private Arguments arguments;

        public TorrentsSetLocationRequest(int[] ids, String location, boolean move) {
            this.arguments = new Arguments(ids, location, move);
        }

        private static class Arguments {
            @SerializedName("ids") private int[] ids;
            @SerializedName("location") private String location;
            @SerializedName("move") private boolean move;

            public Arguments(int[] ids, String location, boolean move) {
                this.ids = ids;
                this.location = location;
                this.move = move;
            }
        }
     }

    public static class Response {
        @SerializedName("result") protected final String mResult = null;

        public String getResult() {
            return mResult;
        }
    }

    public static class SessionGetResponse extends Response {
        @SerializedName("arguments") private final TransmissionSession mSession = null;

        public TransmissionSession getSession() {
            return mSession;
        }
    }

    public static class SessionStatsResponse extends Response {
        @SerializedName("arguments") private final TransmissionSessionStats mStats = null;

        public TransmissionSessionStats getStats() {
            return mStats;
        }
    }

    public static class TorrentGetResponse extends Response {
        @SerializedName("arguments") private final TorrentsArguments mTorrentsArguments = null;

        public Torrent[] getTorrents() {
            return mTorrentsArguments.getTorrents();
        }

        private static class TorrentsArguments {
            @SerializedName("torrents") private final Torrent[] mTorrents = null;

            public Torrent[] getTorrents() {
                return mTorrents;
            }
        }
    }

    public static class ActiveTorrentGetResponse extends Response {
        @SerializedName("arguments") private final ActiveTorrentsArguments mActiveTorrentsArguments = null;

        public Torrent[] getTorrents() {
            return mActiveTorrentsArguments.getTorrents();
        }

        public int[] getRemoved() {
            return mActiveTorrentsArguments.getRemoved();
        }

        private static class ActiveTorrentsArguments {
            @SerializedName("torrents") private final Torrent[] mTorrents = null;
            @SerializedName("removed") private final int[] mRemoved = null;

            public Torrent[] getTorrents() {
                return mTorrents;
            }

            public int[] getRemoved() {
                return mRemoved;
            }
        }
    }
}
