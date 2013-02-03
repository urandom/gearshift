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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

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
    
    private Context mContext;
    private TransmissionProfile mProfile;
    
    private ConnectivityManager mConnManager;
    
    private String mSessionId;
    
    private int mInvalidSessionRetries = 0;
    
    public TransmissionSessionManager(Context context, TransmissionProfile profile) {
        mContext = context;
        mProfile = profile;
        
        mConnManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }
    
    public boolean hasConnectivity() {
        NetworkInfo info = mConnManager.getActiveNetworkInfo();
        return (info != null);
    }
    
    public TransmissionSession getSession() throws IOException, ManagerException {
        TransmissionSession session = null;
        SessionGetRequest request = new SessionGetRequest();
        
        String json = requestData(request);
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();
        SessionGetResponse response = gson.fromJson(json, SessionGetResponse.class);
        session = response.getSession();
                
        return session;
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

            TorrentListActivity.logD("The response is: " + code);
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
        return conn.getHeaderField("X-Transmission-Session-Id");
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

    private static class TorrentGetRequest {
       @SerializedName("method") private final String method = "torrent-get";
       @SerializedName("arguments") private Arguments arguments;

       public TorrentGetRequest(int[] ids, String[]... fields) {
           this.arguments = new Arguments(ids, concat(fields));
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

    private static class RecentTorrentGetRequest {
       @SerializedName("method") private final String method = "torrent-get";
       @SerializedName("arguments") private Arguments arguments;

       public RecentTorrentGetRequest(String[]... fields) {
           this.arguments = new Arguments(concat(fields));
       }

       private static class Arguments {
           @SerializedName("ids") private final String ids = "recently-active";
           @SerializedName("fields") private String[] fields;

           public Arguments(String[] fields) {
               this.fields = fields;
           }
       }
    }

    private static class Response {
        @SerializedName("result") protected final String mResult = null;

        public String getResult() {
            return mResult;
        }
    }

    private static class SessionGetResponse extends Response {
        @SerializedName("arguments") private final TransmissionSession mSession = null;

        public TransmissionSession getSession() {
            return mSession;
        }
    }

    public static String[] concat(String[]... arrays) {
        int len = 0;
        for (final String[] array : arrays) {
            len += array.length;
        }

        final String[] result = new String[len];

        int currentPos = 0;
        for (final String[] array : arrays) {
            System.arraycopy(array, 0, result, currentPos, array.length);
            currentPos += array.length;
        }

        return result;
    }
}
