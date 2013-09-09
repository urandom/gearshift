package org.sugr.gearshift;

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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.SerializedName;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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
            super(message == null ? "" : message);
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

    public void setProfile(TransmissionProfile profile) {
        mProfile = profile;
        mSessionId = null;
    }

    public TransmissionSession getSession() throws ManagerException {
        SessionGetRequest request = new SessionGetRequest();

        String json = requestData(request);
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        SessionGetResponse response = gson.fromJson(json, SessionGetResponse.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }

        return response.getSession();
    }

    public TransmissionSessionStats getSessionStats() throws ManagerException {
        SessionStatsRequest request = new SessionStatsRequest();

        String json = requestData(request);
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        SessionStatsResponse response = gson.fromJson(json, SessionStatsResponse.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }

        return response.getStats();
    }

    public ActiveTorrentGetResponse getActiveTorrents(String[] fields) throws ManagerException {
        ActiveTorrentGetRequest request = new ActiveTorrentGetRequest(fields);
        String json = requestData(request);
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        ActiveTorrentGetResponse response = gson.fromJson(json, ActiveTorrentGetResponse.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }

        return response;
    }

    public Torrent[] getAllTorrents(String[] fields) throws ManagerException {
        AllTorrentGetRequest request = new AllTorrentGetRequest(fields);

        String json = requestData(request);
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        TorrentGetResponse response = gson.fromJson(json, TorrentGetResponse.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }

        return response.getTorrents();
    }

    public Torrent[] getTorrents(int[] ids, String[] fields) throws ManagerException {
        TorrentGetRequest request = new TorrentGetRequest(ids, fields);

        String json = requestData(request);
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        TorrentGetResponse response = gson.fromJson(json, TorrentGetResponse.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }

        return response.getTorrents();
    }

    public void setSession(TransmissionSession session, String... keys) throws ManagerException {
        SessionSetRequest request = new SessionSetRequest(session);

        String json = requestData(request, new KeyExclusionStrategy(keys));
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        Response response = gson.fromJson(json, Response.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }
    }

    public void setTorrentsRemove(int[] ids, boolean delete) throws ManagerException {
        TorrentsRemoveRequest request = new TorrentsRemoveRequest(ids, delete);
        String json = requestData(request);
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        Response response = gson.fromJson(json, Response.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }
    }

    public void setTorrentsAction(String action, int[] ids) throws ManagerException {
        TorrentsActionRequest request = new TorrentsActionRequest(action, ids);
        String json = requestData(request);
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        Response response = gson.fromJson(json, Response.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }
    }

    public void setTorrentsLocation(int[] ids, String location, boolean move) throws ManagerException {
        TorrentsSetLocationRequest request = new TorrentsSetLocationRequest(ids, location, move);
        String json = requestData(request);
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        Response response = gson.fromJson(json, Response.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }
    }

    public void setTorrentsProperty(int[] ids, String key, Object value) throws ManagerException {
        Object request;
        if (key.equals(Torrent.SetterFields.TRACKER_REPLACE)) {
            request = new TrackerReplaceRequest(ids, value);
        } else {
            request = new TorrentsSetRequest(ids, key, value);
        }

        String json = requestData(request, new KeyExclusionStrategy("ids", key));
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        Response response = gson.fromJson(json, Response.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }
    }

    public Torrent addTorrent(String uri, String meta, String location, boolean paused)
            throws ManagerException {
        TorrentAddRequest request = new TorrentAddRequest(uri, meta, location, paused);

        String[] keys = {
            uri == null ? Torrent.AddFields.META : Torrent.AddFields.URI,
            Torrent.AddFields.LOCATION, Torrent.AddFields.PAUSED
        };

        String json = requestData(request, new KeyExclusionStrategy(keys));
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        AddTorrentResponse response = gson.fromJson(json, AddTorrentResponse.class);
        if (response.getResult().equals("success")) {
            return response.getTorrent();
        } else if (response.isDuplicate()) {
            throw new ManagerException("duplicate torrent", -2);
        } else {
            throw new ManagerException(response.getResult(), -2);
        }
    }

    public boolean testPort() throws ManagerException {
        PortTestRequest request = new PortTestRequest();

        String json = requestData(request);
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        PortTestResponse response = gson.fromJson(json, PortTestResponse.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }

        return response.isPortOpen();
    }

    public long blocklistUpdate() throws ManagerException {
        BlocklistUpdateRequest request = new BlocklistUpdateRequest();

        String json = requestData(request);
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        BlocklistUpdateResponse response = gson.fromJson(json, BlocklistUpdateResponse.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }

        return response.getBlocklistSize();
    }

    public long getFreeSpace(String defaultPath) throws ManagerException {
        FreeSpaceRequest request = new FreeSpaceRequest(
                mDefaultPrefs.getString(G.PREF_LIST_DIRECTORY, null), defaultPath);

        String json = requestData(request);
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        FreeSpaceResponse response = gson.fromJson(json, FreeSpaceResponse.class);

        if (response.getResult().equals("success")) {
            return response.getFreeSpace();
        } else if (response.getResult().equals("method name not recognized")) {
            return -1;
        } else {
            throw new ManagerException(response.getResult(), -2);
        }
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
            if (mProfile.isUseSSL()) {
                try {
                    SSLContext sc = SSLContext.getInstance("TLS");
                    sc.init(null, new TrustManager[] {
                        new X509TrustManager() {
                            @Override public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                return null;
                            }
                            @Override public void checkClientTrusted(
                                    java.security.cert.X509Certificate[] certs,
                                    String authType) {}
                            @Override public void checkServerTrusted(
                                    java.security.cert.X509Certificate[] certs,
                                    String authType) {}
                        }
                    }, new java.security.SecureRandom());
                    ((HttpsURLConnection) conn).setSSLSocketFactory(sc.getSocketFactory());
                    ((HttpsURLConnection) conn).setHostnameVerifier(new HostnameVerifier() {
                        @Override public boolean verify(String hostname, SSLSession session) {
                            return true;
                        }

                    });
                } catch (NoSuchAlgorithmException e) {
                    G.logE("Error creating an SSL context", e);
                    throw new ManagerException("ssl", -1);
                } catch (KeyManagementException e) {
                    G.logE("Error initializing the SSL context", e);
                    throw new ManagerException("ssl", -1);
                }
            }
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
                    new TransmissionExclusionStrategy()
                ).registerTypeAdapter(
                    TrackerReplaceRequest.class, new TrackerReplaceRequestSerializer()
                ).create();
            String json = gson.toJson(data);
            os.write(json.getBytes());
            os.flush();
            os.close();

            G.logD("The request is " + json);

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

            G.logD("The response is " + contentAsString);
            return contentAsString;
        } catch (java.net.SocketTimeoutException e) {
            throw new ManagerException("timeout", -1);
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

    private static class TorrentsSetRequest {
        @SerializedName("method") private final String method = "torrent-set";
        @SerializedName("arguments") private Arguments arguments;

        public TorrentsSetRequest(int[] ids, String key, Object value) {
            this.arguments = new Arguments(ids, key, value);
        }

        private static class Arguments {
            @SerializedName("ids") private int[] ids;
            @SerializedName(Torrent.SetterFields.DOWNLOAD_LIMIT) private long downloadLimit;
            @SerializedName(Torrent.SetterFields.DOWNLOAD_LIMITED) private boolean downloadLimited;
            @SerializedName(Torrent.SetterFields.PEER_LIMIT) private int peerLimit;
            @SerializedName(Torrent.SetterFields.QUEUE_POSITION) private int queuePosition;
            @SerializedName(Torrent.SetterFields.SEED_RATIO_LIMIT) private float seedRatioLimit;
            @SerializedName(Torrent.SetterFields.SEED_RATIO_MODE) private int seedRatioMode;
            @SerializedName(Torrent.SetterFields.SESSION_LIMITS) private boolean honorsSessionLimits;
            @SerializedName(Torrent.SetterFields.TORRENT_PRIORITY) private int torrentPriority;
            @SerializedName(Torrent.SetterFields.UPLOAD_LIMIT) private long uploadLimit;
            @SerializedName(Torrent.SetterFields.UPLOAD_LIMITED) private boolean uploadLimited;

            @SerializedName(Torrent.SetterFields.FILES_WANTED) private int[] filesWanted;
            @SerializedName(Torrent.SetterFields.FILES_UNWANTED) private int[] filesUnwanted;
            @SerializedName(Torrent.SetterFields.FILES_HIGH) private int[] filesHigh;
            @SerializedName(Torrent.SetterFields.FILES_NORMAL) private int[] filesNormal;
            @SerializedName(Torrent.SetterFields.FILES_LOW) private int[] filesLow;
            @SerializedName(Torrent.SetterFields.TRACKER_ADD) private String[] trackerAdd;
            @SerializedName(Torrent.SetterFields.TRACKER_REMOVE) private int[] trackerRemove;

            @SuppressWarnings("unchecked")
            public Arguments(int[] ids, String key, Object value) {
                this.ids = ids;
                if (key.equals(Torrent.SetterFields.DOWNLOAD_LIMITED)) {
                    this.downloadLimited = ((Boolean) value).booleanValue();
                } else if (key.equals(Torrent.SetterFields.SESSION_LIMITS)) {
                    this.honorsSessionLimits = ((Boolean) value).booleanValue();
                } else if (key.equals(Torrent.SetterFields.UPLOAD_LIMITED)) {
                    this.uploadLimited = ((Boolean) value).booleanValue();
                } else if (key.equals(Torrent.SetterFields.TORRENT_PRIORITY)) {
                    this.torrentPriority = ((Integer) value).intValue();
                } else if (key.equals(Torrent.SetterFields.QUEUE_POSITION)) {
                    this.queuePosition = ((Integer) value).intValue();
                } else if (key.equals(Torrent.SetterFields.PEER_LIMIT)) {
                    this.peerLimit = ((Integer) value).intValue();
                } else if (key.equals(Torrent.SetterFields.SEED_RATIO_MODE)) {
                    this.seedRatioMode = ((Integer) value).intValue();
                } else if (key.equals(Torrent.SetterFields.FILES_WANTED)) {
                    if (value instanceof Integer) {
                        this.filesWanted = new int[] { ((Integer) value).intValue() };
                    } else {
                        this.filesWanted = convertIntegerList((ArrayList<Integer>) value);
                    }
                } else if (key.equals(Torrent.SetterFields.FILES_UNWANTED)) {
                    if (value instanceof Integer) {
                        this.filesUnwanted = new int[] { ((Integer) value).intValue() };
                    } else {
                        this.filesUnwanted = convertIntegerList((ArrayList<Integer>) value);
                    }
                } else if (key.equals(Torrent.SetterFields.DOWNLOAD_LIMIT)) {
                    this.downloadLimit = ((Long) value).longValue();
                } else if (key.equals(Torrent.SetterFields.UPLOAD_LIMIT)) {
                    this.uploadLimit = ((Long) value).longValue();
                } else if (key.equals(Torrent.SetterFields.SEED_RATIO_LIMIT)) {
                    this.seedRatioLimit = ((Float) value).floatValue();
                } else if (key.equals(Torrent.SetterFields.FILES_HIGH)) {
                    this.filesHigh = convertIntegerList((ArrayList<Integer>) value);
                } else if (key.equals(Torrent.SetterFields.FILES_NORMAL)) {
                    this.filesNormal = convertIntegerList((ArrayList<Integer>) value);
                } else if (key.equals(Torrent.SetterFields.FILES_LOW)) {
                    this.filesLow = convertIntegerList((ArrayList<Integer>) value);
                } else if (key.equals(Torrent.SetterFields.TRACKER_ADD)) {
                    this.trackerAdd = convertStringList((ArrayList<String>) value);
                } else if (key.equals(Torrent.SetterFields.TRACKER_REMOVE)) {
                    this.trackerRemove = convertIntegerList((ArrayList<Integer>) value);
                }
            }
        }
    }

    private static class TrackerReplaceRequest {
        @SerializedName("method") private final String method = "torrent-set";
        @SerializedName("arguments") private Arguments arguments;

        public TrackerReplaceRequest(int[] ids, Object value) {
            this.arguments = new Arguments(ids, (Torrent.TrackerReplaceTuple) value);
        }

        public Torrent.TrackerReplaceTuple getTuple() {
            return this.arguments.getTuple();
        }

        private static class Arguments {
            @SerializedName("ids") private int[] ids;
            transient private Torrent.TrackerReplaceTuple trackerReplace;

            public Arguments(int[] ids, Torrent.TrackerReplaceTuple value) {
                this.ids = ids;
                this.trackerReplace = value;
            }

            public Torrent.TrackerReplaceTuple getTuple() {
                return this.trackerReplace;
            }
        }
    }

    private static class TorrentAddRequest {
        @SerializedName("method") private final String method = "torrent-add";
        @SerializedName("arguments") private Arguments arguments;

        public TorrentAddRequest(String uri, String meta, String location, boolean paused) {
            this.arguments = new Arguments(uri, meta, location, paused);
        }

        private static class Arguments {
            @SerializedName(Torrent.AddFields.URI) private String uri;
            @SerializedName(Torrent.AddFields.META) private String meta;
            @SerializedName(Torrent.AddFields.LOCATION) private String location;
            @SerializedName(Torrent.AddFields.PAUSED) private boolean paused;

            public Arguments(String uri, String meta, String location, boolean paused) {
                this.uri = uri;
                this.meta = meta;
                this.location = location;
                this.paused = paused;
            }
        }
    }

    private static class PortTestRequest {
        @SerializedName("method") private final String method = "port-test";
    }

    private static class BlocklistUpdateRequest {
        @SerializedName("method") private final String method = "blocklist-update";
    }

    private static class FreeSpaceRequest {
        @SerializedName("method") private final String method = "free-space";
        @SerializedName("arguments") private Arguments arguments;

        public FreeSpaceRequest(String path, String defaultPath) {
            if (path == null) {
                path = defaultPath;
            }
            this.arguments = new Arguments(path);
        }

        private static class Arguments {
            @SerializedName("path") private String path;

            public Arguments(String path) {
                this.path = path;
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

    public static class AddTorrentResponse extends Response {
        @SerializedName("arguments") private final AddTorrentArguments mArguments = null;

        public Torrent getTorrent() {
            return mArguments.torrent;
        }

        public boolean isDuplicate() {
            return mArguments.duplicate != null;
        }

        private class AddTorrentArguments {
            @SerializedName("torrent-added") public Torrent torrent;
            @SerializedName("torrent-duplicate") public Torrent duplicate;
        }
    }

    public static class PortTestResponse extends Response {
        @SerializedName("arguments") private final PortTestArguments mArguments = null;

        public boolean isPortOpen() {
            return mArguments.open;
        }

        private class PortTestArguments {
            @SerializedName("port-is-open") public boolean open;
        }
    }

    public static class BlocklistUpdateResponse extends Response {
        @SerializedName("arguments") private final BlocklistUpdateArguments mArguments = null;

        public long getBlocklistSize() {
            return mArguments.size;
        }

        private class BlocklistUpdateArguments {
            @SerializedName("blocklist-size") public long size;
        }
    }

    public static class FreeSpaceResponse extends Response {
        @SerializedName("arguments") private final FreeSpaceArguments mArguments = null;

        public long getFreeSpace() {
            return mArguments.freeSpace;
        }

        private class FreeSpaceArguments {
            @SerializedName("size-bytes") public long freeSpace;
            @SerializedName("path") public String path;
        }
    }


    public static class TrackerReplaceRequestSerializer implements JsonSerializer<TrackerReplaceRequest> {
        private static final Gson cleanGson = new Gson();

        @Override
        public JsonElement serialize(final TrackerReplaceRequest request, final Type typeOfSrc, final JsonSerializationContext context) {
            JsonElement json = cleanGson.toJsonTree(request);
            Torrent.TrackerReplaceTuple tuple = request.getTuple();

            JsonArray array = new JsonArray();
            List<Integer> ids = tuple.getIds();
            List<String> urls = tuple.getUrls();

            for (int i = 0; i < ids.size(); ++i) {
                array.add(new JsonPrimitive(ids.get(i)));
                array.add(new JsonPrimitive(urls.get(i)));
            }

            json.getAsJsonObject().getAsJsonObject("arguments").add(Torrent.SetterFields.TRACKER_REPLACE, array);

            return json;
        }
    }

    public static int[] convertIntegerList(List<Integer> list) {
        int[] ret = new int[list.size()];
        Iterator<Integer> iterator = list.iterator();
        for (int i = 0; i < ret.length; i++) {
            ret[i] = iterator.next().intValue();
        }

        return ret;
    }

    public static String[] convertStringList(List<String> list) {
        String[] ret = new String[list.size()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = list.get(i);
        }

        return ret;
    }
}
