package us.supositi.gearshift;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

public class TransmissionSessionManager {
    public @interface Exclude {};
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
    public class ManagerException extends Exception {
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
    
    private Context mContext;
    private TransmissionProfile mProfile;
    
    private ConnectivityManager mConnManager;
    
    private String mSessionId;
    
    private int mInvalidSessionRetries = 0;
    private SharedPreferences mDefaultPrefs;
    
    public TransmissionSessionManager(Context context, TransmissionProfile profile) {
        mContext = context;
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
        TransmissionSession session = null;
        SessionGetRequest request = new SessionGetRequest();
        
        String json;
        try {
            json = requestData(request);
        } catch (IOException e) {
            e.printStackTrace();
            
            throw new ManagerException(e.getMessage(), -1);
        }
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        SessionGetResponse response = gson.fromJson(json, SessionGetResponse.class);
                
        return response;
    }
    
    public SessionStatsResponse getSessionStats() throws ManagerException {
        TransmissionSessionStats stats = null;
        SessionStatsRequest request = new SessionStatsRequest();
        
        String json;
        try {
            json = requestData(request);
        } catch (IOException e) {
            e.printStackTrace();
            
            throw new ManagerException(e.getMessage(), -1);
        }
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        SessionStatsResponse response = gson.fromJson(json, SessionStatsResponse.class);
                
        return response;
    }
    
    public ActiveTorrentGetResponse getActiveTorrents(String[] fields) throws ManagerException {
        ActiveTorrentGetRequest request = new ActiveTorrentGetRequest(fields);
        String json;
        
        try {
            json = requestData(request);
        } catch (IOException e) {
            e.printStackTrace();
            
            throw new ManagerException(e.getMessage(), -1);
        }
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        ActiveTorrentGetResponse response = gson.fromJson(json, ActiveTorrentGetResponse.class);

        return response;
    }    
    
    public TorrentGetResponse getAllTorrents(String[] fields) throws ManagerException {
        AllTorrentGetRequest request = new AllTorrentGetRequest(fields);
        String json;

        try {
            json = requestData(request);
        } catch (IOException e) {
            e.printStackTrace();
            
            throw new ManagerException(e.getMessage(), -1);
        }
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        TorrentGetResponse response = gson.fromJson(json, TorrentGetResponse.class);

        return response;
    }
    
    public TorrentGetResponse getTorrents(int[] ids, String[] fields) throws ManagerException {
        TorrentGetRequest request = new TorrentGetRequest(ids, fields);
        String json;

        try {
            json = requestData(request);
        } catch (IOException e) {
            e.printStackTrace();
            
            throw new ManagerException(e.getMessage(), -1);
        }
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        TorrentGetResponse response = gson.fromJson(json, TorrentGetResponse.class);

        return response;
    }
    private String requestData(Object data) throws IOException, ManagerException {
        OutputStream os = null;
        InputStream is = null;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(
                  (mProfile.isUseSSL() ? "https://" : "http://")
                + mProfile.getHost() + ":" + mProfile.getPort()
                + mProfile.getPath());
            conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(10000 /* milliseconds */);
            conn.setConnectTimeout(15000 /* milliseconds */);
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

            os = conn.getOutputStream();
            Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
            String json = gson.toJson(data);
            os.write(json.getBytes());
            os.flush();
            os.close();

            final String user = mProfile.getUsername();
            if (user != null && user.length() > 0) {
                Authenticator.setDefault(new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(user, mProfile.getPassword().toCharArray());
                    }
                });
            }

            // Starts the query
            conn.connect();
            
            int code = conn.getResponseCode();

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

            is = conn.getInputStream();
    
            // Convert the InputStream into a string
            String contentAsString = null;
            String encoding = conn.getContentEncoding();
            
            switch(code) {
                case 200:
                case 201:
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
        } finally {
            if (os != null)
                os.close();
            if (is != null)
                is.close();
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
