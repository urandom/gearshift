package us.supositi.gearshift;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class TransmissionSessionManager {
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
    
    private HttpURLConnection getJSON(String myurl, Object data) throws IOException {
        BufferedOutputStream os = null;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(myurl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(10000 /* milliseconds */);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setUseCaches(false);
            conn.setAllowUserInteraction(false);
            
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            
            if (mSessionId != null) {
                conn.setRequestProperty("X-Transmission-Session-Id", mSessionId);
            }

            conn.setChunkedStreamingMode(0);
            os = new BufferedOutputStream(conn.getOutputStream());
            Gson gson = new Gson();
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
            
            mSessionId = getSessionId(conn);
            
            if (conn.getResponseCode() == 409 && mSessionId != null) {
                if (mInvalidSessionRetries < 3) {
                    ++mInvalidSessionRetries;
                    return getJSON(myurl, data);
                } else {
                    mInvalidSessionRetries = 0;
                }
            } else {
                mInvalidSessionRetries = 0;
            }

            return conn;
        } finally {
            if (os != null)
                os.close();
            if (conn != null)
                conn.disconnect();
        }
    }
    
    private String getJSONFromConnection(HttpURLConnection conn) throws IOException {
        InputStream is = null;
        
        try {
            int code = conn.getResponseCode();
            TorrentListActivity.logD("The response is: " + code);
            is = conn.getInputStream();
    
            // Convert the InputStream into a string
            String contentAsString = null;
            
            switch(code) {
                case 200:
                case 201:
                    contentAsString = inputStreamToString(is);
                    break;
            }
            return contentAsString;
        } finally {
            if (is != null) {
                is.close();
            } 
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
